package it.sanninicistyle.watchsync

import android.Manifest
import android.bluetooth.le.ScanFilter
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.IntentSender
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import it.sanninicistyle.watchsync.adb.BondedWatch
import it.sanninicistyle.watchsync.adb.WatchIdentity
import it.sanninicistyle.watchsync.shared.DiagLog
import it.sanninicistyle.watchsync.ui.HomeScreen
import it.sanninicistyle.watchsync.ui.HomeViewModel
import it.sanninicistyle.watchsync.ui.LearnRiposoScreen
import it.sanninicistyle.watchsync.ui.LicensesScreen
import it.sanninicistyle.watchsync.ui.PairScreen
import it.sanninicistyle.watchsync.ui.Permissions
import it.sanninicistyle.watchsync.ui.SettingsScreen
import it.sanninicistyle.watchsync.ui.SetupItem
import it.sanninicistyle.watchsync.ui.SetupScreen
import it.sanninicistyle.watchsync.ui.theme.WatchSyncTheme
import java.util.concurrent.Executor

private enum class Screen { HOME, SETUP, SETTINGS, PAIR, LEARN_RIPOSO, LICENSES }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        openSetup.value = intent.getBooleanExtra(EXTRA_OPEN_SETUP, false)
        setContent { WatchSyncTheme { App(openSetup) } }
    }

    // Opened from the "set up the watch" notification
    private val openSetup = mutableStateOf(false)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_SETUP, false)) openSetup.value = true
    }

    companion object {
        const val EXTRA_OPEN_SETUP = "open_setup"
    }
}

@Composable
private fun App(openSetup: androidx.compose.runtime.MutableState<Boolean>, vm: HomeViewModel = viewModel()) {
    val context = LocalContext.current
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    androidx.compose.runtime.LaunchedEffect(openSetup.value) {
        if (openSetup.value) { screen = Screen.SETUP; openSetup.value = false }
    }
    val watch by vm.watch.collectAsStateWithLifecycle()
    val modes by vm.modesUi.collectAsStateWithLifecycle()
    val phoneAlarm by vm.phoneAlarm.collectAsStateWithLifecycle()
    val watchAlarm by vm.watchAlarm.collectAsStateWithLifecycle()
    val permissions by vm.permissions.collectAsStateWithLifecycle()

    // Permissions are granted in system screens: re-read everything when we come back
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    BackHandler(enabled = screen != Screen.HOME) { screen = when (screen) {
            Screen.PAIR, Screen.LEARN_RIPOSO -> Screen.SETUP
            Screen.LICENSES -> Screen.SETTINGS
            else -> Screen.HOME
        } }

    val notificationsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refresh() }
    val associationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        DiagLog.d("Association", "dialog result ${it.resultCode}")
        vm.refresh()
    }

    fun associateWatch(address: String) =
        requestWatchAssociation(context.getSystemService(CompanionDeviceManager::class.java), context.mainExecutor, address) {
            associationLauncher.launch(IntentSenderRequest.Builder(it).build())
        }

    /** The watch's address: recorded at setup, or found among the paired devices. */
    fun watchAddress() = WatchIdentity.address(context)
        ?: BondedWatch.address(context, watch?.name)?.also { WatchIdentity.setAddress(context, it) }

    val bluetoothLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val address = if (granted) watchAddress() else null
        if (address != null) associateWatch(address) else screen = Screen.PAIR
    }

    val setupItems = listOf(
        SetupItem(R.string.perm_notifications, R.string.perm_notifications_desc, permissions.notifications) {
            notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
        SetupItem(R.string.perm_listener, R.string.perm_listener_desc, permissions.listener, info = R.string.info_listener) {
            context.startActivity(Permissions.listenerSettings(context))
        },
        SetupItem(R.string.perm_dnd, R.string.perm_dnd_desc, permissions.dndAccess) {
            context.startActivity(Permissions.dndSettings())
        },
        // The watch is set up over adb first, then the phone confirms it may manage it
        SetupItem(R.string.perm_watch_setup, R.string.perm_watch_setup_desc, permissions.watchReady && permissions.watchAssociated, R.string.start, info = R.string.info_watch_setup) {
            // Already set up: only the phone's confirmation is missing, which needs the watch's address
            val address = watchAddress()
            when {
                !permissions.watchReady -> screen = Screen.PAIR
                address != null -> associateWatch(address)
                !BondedWatch.canRead(context) -> bluetoothLauncher.launch(BondedWatch.PERMISSION)
                else -> screen = Screen.PAIR
            }
        },
        SetupItem(R.string.perm_riposo, R.string.perm_riposo_desc, permissions.riposoLearnt, R.string.start, info = R.string.info_riposo) {
            screen = Screen.LEARN_RIPOSO
        },
        SetupItem(R.string.perm_fullscreen, R.string.perm_fullscreen_desc, permissions.fullScreen, info = R.string.info_fullscreen) {
            context.startActivity(Permissions.fullScreenSettings(context))
        },
    )

    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            val forward = targetState != Screen.HOME
            val spec = spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
            (slideInHorizontally(spec) { if (forward) it / 3 else -it / 3 } + fadeIn(tween(250))) togetherWith
                (slideOutHorizontally(spec) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(180)) + scaleOut(targetScale = 0.97f))
        },
        label = "screen",
    ) { current ->
        when (current) {
            Screen.HOME -> HomeScreen(
                watch = watch, modes = modes, phoneAlarm = phoneAlarm, watchAlarm = watchAlarm,
                permissions = permissions, onToggle = vm::toggle,
                onOpenSettings = { screen = Screen.SETTINGS }, onOpenSetup = { screen = Screen.SETUP },
            )
            Screen.SETUP -> SetupScreen(items = setupItems, onBack = { screen = Screen.HOME })
            Screen.LEARN_RIPOSO -> LearnRiposoScreen(onBack = { screen = Screen.SETUP })
            Screen.LICENSES -> LicensesScreen(onBack = { screen = Screen.SETTINGS })
            Screen.PAIR -> PairScreen(
                onBack = { screen = Screen.SETUP },
                // Right after the watch is ready: the one confirmation the phone needs
                onWatchReady = { if (!permissions.watchAssociated) watchAddress()?.let { associateWatch(it) } },
            )
            Screen.SETTINGS -> SettingsScreen(
                setupItems = setupItems,
                version = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty(),
                onRelearnRiposo = { screen = Screen.LEARN_RIPOSO },
                onLicenses = { screen = Screen.LICENSES },
                onBack = { screen = Screen.HOME },
            )
        }
    }
}

/**
 * Asks the user, through the system dialog, to let WatchSync manage the paired watch
 * (CompanionDeviceManager, watch profile). This is what allows changing the global DND.
 */
private fun requestWatchAssociation(
    cdm: CompanionDeviceManager,
    executor: Executor,
    address: String,
    launch: (IntentSender) -> Unit,
) {
    // The watch is already paired and not discoverable: only its address finds it
    val request = AssociationRequest.Builder()
        .setDeviceProfile(AssociationRequest.DEVICE_PROFILE_WATCH)
        .addDeviceFilter(BluetoothDeviceFilter.Builder().setAddress(address).build())
        .setSingleDevice(true)
        .build()
    runCatching { associate(cdm, request, executor, launch) }
        .onFailure { DiagLog.w("Association", "request refused", it) }
}

private fun associate(cdm: CompanionDeviceManager, request: AssociationRequest, executor: Executor, launch: (IntentSender) -> Unit) {
    cdm.associate(request, executor, object : CompanionDeviceManager.Callback() {
        override fun onAssociationPending(intentSender: IntentSender) {
            DiagLog.d("Association", "pending, showing the system dialog")
            launch(intentSender)
        }
        override fun onAssociationCreated(associationInfo: AssociationInfo) =
            DiagLog.d("Association", "created: ${associationInfo.displayName} ${associationInfo.deviceProfile}")
        override fun onFailure(error: CharSequence?) = DiagLog.w("Association", "failed: $error")
    })
}
