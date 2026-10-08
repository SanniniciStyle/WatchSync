package it.sanninicistyle.watchsync.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.AdbStream
import it.sanninicistyle.watchsync.shared.DiagLog
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * ADB client the phone uses to reach the watch's wireless debugging. Its identity (RSA key and
 * self-signed certificate) is created once and kept in no-backup storage: the watch remembers it
 * after pairing, so it must never leave this phone.
 */
class WatchAdbConnection private constructor(
    private val key: PrivateKey,
    private val cert: Certificate,
) : AbsAdbConnectionManager() {
    init {
        // Version of the adbd we talk to: any Wear OS 4+ watch is at least Android 13
        setApi(Build.VERSION_CODES.TIRAMISU)
    }

    override fun getPrivateKey() = key
    override fun getCertificate() = cert
    override fun getDeviceName() = "WatchSync"

    /** Runs [command] in the watch's shell and returns its output and exit code. */
    fun shell(command: String): ShellResult {
        val output = deadline(SHELL_TIMEOUT_MS, ::abort) {
            openStream("shell:$command; echo \"$EXIT_MARK$?\"").use { stream -> readAll(stream) }
        }
        val code = output.substringAfterLast(EXIT_MARK, "").trim().toIntOrNull() ?: -1
        return ShellResult(output.substringBeforeLast(EXIT_MARK).trim(), code).also {
            DiagLog.d(TAG, "$ $command -> ${it.code} ${it.output.take(200)}")
        }
    }

    /** Drops the connection at once: unblocks any call stuck on a peer that stopped answering. */
    fun abort() {
        runCatching { adbConnection?.close() }
    }

    /**
     * Reads a shell stream to its end. libadb 3.1.1 throws "Stream closed." when the peer's close
     * races the last chunk, and can lose output queued at close: read the stream itself and treat
     * "closed" as the end. Fixed upstream in MuntashirAkon/libadb-android#35; once a release
     * includes it, this can go back to reading the stream's InputStream.
     */
    private fun readAll(stream: AdbStream): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val n = try {
                stream.read(buffer, 0, buffer.size)
            } catch (e: IOException) {
                if (stream.isClosed) -1 else throw e
            }
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toString(Charsets.UTF_8.name())
    }

    data class ShellResult(val output: String, val code: Int) {
        val ok get() = code == 0
    }

    companion object {
        private const val TAG = "WatchAdb"
        private const val SHELL_TIMEOUT_MS = 30_000L
        private const val EXIT_MARK = "__ws_exit="

        fun create(context: Context): WatchAdbConnection {
            val dir = File(context.noBackupFilesDir, "adb").apply { mkdirs() }
            val keyFile = File(dir, "key.pk8")
            val certFile = File(dir, "cert.der")
            if (keyFile.exists() && certFile.exists()) {
                runCatching {
                    val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
                    val cert = certFile.inputStream().use { CertificateFactory.getInstance("X.509").generateCertificate(it) }
                    return WatchAdbConnection(key, cert)
                }.onFailure { DiagLog.w(TAG, "stored adb identity unreadable, creating a new one", it) }
            }
            val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val name = X500Name("CN=WatchSync")
            val now = System.currentTimeMillis()
            val holder = JcaX509v3CertificateBuilder(
                name, BigInteger.valueOf(now), Date(now - 86_400_000L), Date(now + 30L * 365 * 86_400_000L), name, pair.public,
            ).build(JcaContentSignerBuilder("SHA256withRSA").build(pair.private))
            val cert = JcaX509CertificateConverter().getCertificate(holder)
            keyFile.writeBytes(pair.private.encoded)
            certFile.writeBytes(cert.encoded)
            return WatchAdbConnection(pair.private, cert)
        }
    }
}

private val deadlineThreads = Executors.newCachedThreadPool { task ->
    Thread(task, "adb-call").apply { isDaemon = true }
}

/**
 * Runs a blocking libadb call with a time limit. libadb has no timeouts of its own, so a peer that
 * stops answering (or a device on the network posing as the watch) could otherwise hang the setup
 * forever: on timeout [onTimeout] drops the connection and the call fails. Errors thrown by the
 * library, even out-of-memory ones from oversized packets, become IOExceptions instead of crashing.
 */
internal fun <T> deadline(millis: Long, onTimeout: () -> Unit = {}, call: () -> T): T {
    val future = deadlineThreads.submit(Callable(call))
    return try {
        future.get(millis, TimeUnit.MILLISECONDS)
    } catch (e: TimeoutException) {
        future.cancel(true)
        runCatching(onTimeout)
        throw IOException("adb call timed out after $millis ms", e)
    } catch (e: ExecutionException) {
        when (val cause = e.cause) {
            is Exception -> throw cause
            else -> throw IOException("adb call failed", cause)
        }
    }
}

/** An adb service a device on the local network advertises over mDNS. */
data class AdbEndpoint(val host: String, val port: Int)

object AdbServices {
    /** "Pair device with pairing code" is open on the device. */
    const val PAIRING = "_adb-tls-pairing._tcp"

    /** Wireless debugging is on and accepts paired clients. */
    const val CONNECT = "_adb-tls-connect._tcp"

    /**
     * Devices advertising [type] on the Wi-Fi network, kept up to date. This phone's own wireless
     * debugging is left out: only other devices (the watch) are of interest.
     */
    fun discover(context: Context, type: String): Flow<List<AdbEndpoint>> = callbackFlow {
        val nsd = context.getSystemService(NsdManager::class.java)
        val own = ownAddresses()
        val found = ConcurrentHashMap<String, AdbEndpoint>()
        val callbacks = ConcurrentHashMap<String, NsdManager.ServiceInfoCallback>()
        fun publish() = trySend(found.values.sortedBy { it.host })

        val discovery = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(service: NsdServiceInfo) {
                val name = service.serviceName
                if (callbacks.containsKey(name)) return
                val callback = object : NsdManager.ServiceInfoCallback {
                    override fun onServiceUpdated(info: NsdServiceInfo) {
                        val host = info.hostAddresses.firstOrNull { it is Inet4Address }?.hostAddress ?: return
                        if (host in own) return
                        found[name] = AdbEndpoint(host, info.port)
                        publish()
                    }
                    override fun onServiceLost() { found.remove(name); publish() }
                    override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) = Unit
                    override fun onServiceInfoCallbackUnregistered() = Unit
                }
                callbacks[name] = callback
                nsd.registerServiceInfoCallback(service, context.mainExecutor, callback)
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                found.remove(service.serviceName)
                callbacks.remove(service.serviceName)?.let { runCatching { nsd.unregisterServiceInfoCallback(it) } }
                publish()
            }

            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                DiagLog.w("WatchAdb", "mDNS discovery of $serviceType failed: $errorCode")
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }
        publish()
        nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, discovery)
        awaitClose {
            runCatching { nsd.stopServiceDiscovery(discovery) }
            callbacks.values.forEach { runCatching { nsd.unregisterServiceInfoCallback(it) } }
        }
    }

    private fun ownAddresses(): Set<String> =
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .flatMap { it.inetAddresses.toList() }
            .mapNotNull { it.hostAddress }
            .toSet()
}
