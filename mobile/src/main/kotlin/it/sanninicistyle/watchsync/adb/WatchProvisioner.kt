package it.sanninicistyle.watchsync.adb

import android.content.Context
import it.sanninicistyle.watchsync.shared.DiagLog
import it.sanninicistyle.watchsync.shared.PeerInfo
import it.sanninicistyle.watchsync.shared.PeerMessenger
import it.sanninicistyle.watchsync.shared.StatusPaths
import it.sanninicistyle.watchsync.shared.WatchStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

/**
 * One-time setup of the watch app over the watch's wireless debugging: pairs with the code the
 * watch shows, grants what Wear OS only grants over adb, then switches wireless debugging off.
 */
class WatchProvisioner(private val context: Context) {
    enum class Step { PAIR, CONNECT, GRANT, VERIFY }

    enum class Failure { WRONG_CODE, NO_CONNECTION, APP_MISSING, GRANT_FAILED, NO_REPORT }

    class ProvisionException(val failure: Failure, cause: Throwable? = null) : Exception(failure.name, cause)

    private val pkg = context.packageName

    /** Runs the whole setup; [onStep] reports progress. Throws [ProvisionException] on failure. */
    suspend fun run(pairing: AdbEndpoint, code: String, onStep: (Step) -> Unit): WatchStatus = withContext(Dispatchers.IO) {
        var step = Step.PAIR
        fun enter(next: Step) { step = next; onStep(next) }
        var adb: WatchAdbConnection? = null
        try {
            enter(Step.PAIR)
            pair(pairing, code)

            enter(Step.CONNECT)
            // Once paired, the watch advertises its debugging port on the same address
            val endpoint = withTimeoutOrNull(20_000) {
                AdbServices.discover(context, AdbServices.CONNECT)
                    .mapNotNull { list -> list.firstOrNull { it.host == pairing.host } }
                    .first()
            } ?: throw ProvisionException(Failure.NO_CONNECTION)
            adb = connect(endpoint)

            enter(Step.GRANT)
            // The watch drops Wi-Fi easily: on a lost link, reconnect and run the (idempotent) setup again
            for (attempt in 1..3) {
                try {
                    grant(adb!!)
                    break
                } catch (e: ProvisionException) {
                    throw e
                } catch (e: Exception) {
                    DiagLog.w(TAG, "setup interrupted, attempt $attempt: ${chain(e)}")
                    if (attempt == 3) throw ProvisionException(Failure.NO_CONNECTION, e)
                    runCatching { adb?.close() }
                    delay(1_000)
                    adb = connect(endpoint)
                }
            }

            enter(Step.VERIFY)
            WatchIdentity.address(context)?.let { PeerMessenger(context).send(StatusPaths.ADDRESS, it.toByteArray()) }
            PeerInfo.clearWatchStatus(context)
            val status = withTimeoutOrNull(20_000) {
                PeerMessenger(context).send(StatusPaths.REQUEST)
                PeerInfo.watchStatus.filterNotNull().filter { it.ready }.first()
            }
            DiagLog.d(TAG, "watch reported $status")
            status ?: throw ProvisionException(Failure.NO_REPORT)
        } catch (e: ProvisionException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DiagLog.w(TAG, "unexpected failure during $step: ${chain(e)}")
            throw ProvisionException(
                when (step) {
                    Step.PAIR -> Failure.WRONG_CODE
                    Step.CONNECT -> Failure.NO_CONNECTION
                    Step.GRANT -> Failure.GRANT_FAILED
                    Step.VERIFY -> Failure.NO_REPORT
                },
                e,
            )
        } finally {
            // Done or not, wireless debugging must not stay on
            adb?.let { runCatching { it.shell("settings put global adb_wifi_enabled 0") } }
            runCatching { adb?.close() }
        }
    }

    private fun pair(pairing: AdbEndpoint, code: String) {
        val paired = try {
            WatchAdbConnection.create(context).use { it.pair(pairing.host, pairing.port, code) }
        } catch (e: Exception) {
            DiagLog.w(TAG, "pairing with $pairing failed: ${chain(e)}")
            // Unreachable address or closed port: a network problem, not the code
            if (e.isNetworkError()) throw ProvisionException(Failure.NO_CONNECTION, e)
            throw ProvisionException(Failure.WRONG_CODE, e)
        }
        DiagLog.d(TAG, "paired with $pairing: $paired")
        if (!paired) throw ProvisionException(Failure.WRONG_CODE)
    }

    /** Connects with a fresh client; adbd may need a moment to trust a key it has just paired. */
    private suspend fun connect(endpoint: AdbEndpoint): WatchAdbConnection {
        for (attempt in 1..4) {
            val adb = WatchAdbConnection.create(context)
            val ok = try {
                adb.connect(endpoint.host, endpoint.port)
            } catch (e: Exception) {
                DiagLog.w(TAG, "connect to $endpoint, attempt $attempt: ${chain(e)}")
                false
            }
            if (ok) {
                DiagLog.d(TAG, "connected to $endpoint")
                return adb
            }
            runCatching { adb.close() }
            delay(1_500L * attempt)
        }
        throw ProvisionException(Failure.NO_CONNECTION)
    }

    private fun grant(adb: WatchAdbConnection) {
        if (!adb.shell("pm path $pkg").ok) throw ProvisionException(Failure.APP_MISSING)
        // Lets the phone's "manage this watch" dialog point straight at this watch
        adb.shell("settings get secure bluetooth_address").output.trim().uppercase()
            .takeIf { ADDRESS.matches(it) }
            ?.let { WatchIdentity.setAddress(context, it) }

        val associations = adb.shell("cmd companiondevice list 0").output
        // The phone this watch is paired with, as Wear OS itself associated it
        val associate = if ("mPackageName='$pkg'" in associations) null else {
            val phoneMac = MAC.find(associations)?.groupValues?.get(1) ?: FALLBACK_MAC
            "cmd companiondevice associate 0 $pkg $phoneMac android.app.role.COMPANION_DEVICE_WATCH"
        }
        val required = listOfNotNull(
            // Opening the app once takes it out of the "stopped" state, so its services can run
            "am start -n $pkg/.MainActivity",
            "pm grant $pkg android.permission.WRITE_SECURE_SETTINGS",
            "pm grant $pkg android.permission.POST_NOTIFICATIONS",
            "appops set $pkg GET_USAGE_STATS allow",
            "appops set $pkg USE_FULL_SCREEN_INTENT allow",
            "appops set $pkg SCHEDULE_EXACT_ALARM allow",
            "cmd notification allow_dnd $pkg",
            "cmd notification allow_listener $pkg/$pkg.WatchModeListenerService",
            associate,
        )
        // Alarms must be detected on time even in deep doze; not essential if refused
        val optional = listOf("cmd deviceidle whitelist +$pkg")

        // One round trip for everything: the less time on Wi-Fi, the fewer chances to lose it
        val script = (required + optional).withIndex()
            .joinToString("; ") { (i, cmd) -> "$cmd >/dev/null 2>&1; echo \"$STEP_MARK$i=\$?\"" }
        val output = adb.shell(script).output
        val codes = STEP.findAll(output).associate { it.groupValues[1].toInt() to it.groupValues[2].toInt() }
        if (required.indices.any { it !in codes }) throw IOException("setup output incomplete: ${codes.size} of ${required.size + optional.size}")
        required.forEachIndexed { i, cmd ->
            if (codes[i] != 0) {
                DiagLog.w(TAG, "watch refused: $cmd -> ${codes[i]}")
                throw ProvisionException(Failure.GRANT_FAILED)
            }
        }
    }

    private fun Throwable.isNetworkError(): Boolean =
        generateSequence(this) { it.cause }.any { it is java.net.SocketException || it is java.net.SocketTimeoutException }

    private fun chain(e: Throwable) =
        generateSequence(e) { it.cause }.joinToString(" <- ") { "${it.javaClass.simpleName}: ${it.message}" }

    private companion object {
        const val TAG = "WatchProvisioner"
        const val STEP_MARK = "__ws_step_"
        val STEP = Regex("""${STEP_MARK}(\d+)=(\d+)""")
        val ADDRESS = Regex("""[0-9A-F]{2}(:[0-9A-F]{2}){5}""")
        val MAC = Regex("""mMacAddress= ?([0-9A-Fa-f]{2}(?::[0-9A-Fa-f]{2}){5})""")
        const val FALLBACK_MAC = "02:00:00:00:00:01"
    }
}
