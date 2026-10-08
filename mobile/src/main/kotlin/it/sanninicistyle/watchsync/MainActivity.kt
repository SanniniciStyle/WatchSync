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
import it.sanninicistyle.watchsync.ui.HomeScreen
import it.sanninicistyle.watchsync.ui.HomeViewModel
import it.sanninicistyle.watchsync.ui.Permissions
import it.sanninicistyle.watchsync.ui.SettingsScreen
import it.sanninicistyle.watchsync.ui.SetupItem
import it.sanninicistyle.watchsync.ui.SetupScreen
import it.sanninicistyle.watchsync.ui.theme.WatchSyncTheme
import java.util.concurrent.Executor

private enum class Screen { HOME, SETUP, SETTINGS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { WatchSyncTheme { App() } }
    }
}

@Composable
private fun App(vm: HomeViewModel = viewModel()) {
    val context = LocalContext.current
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    val watch by vm.watch.collectAsStateWithLifecycle()
    val modes by vm.modesUi.collectAsStateWithLifecycle()
    val phoneAlarm by vm.phoneAlarm.collectAsStateWithLifecycle()
    val watchAlarm by vm.watchAlarm.collectAsStateWithLifecycle()
    val permissions by vm.permissions.collectAsStateWithLifecycle()

    // Permissions are granted in system screens: re-read everything when we come back
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }

    val notificationsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refresh() }
    val associationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { vm.refresh() }

    val setupItems = listOf(
        SetupItem(R.string.perm_notifications, R.string.perm_notifications_desc, permissions.notifications) {
            notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
        SetupItem(R.string.perm_listener, R.string.perm_listener_desc, permissions.listener) {
            context.startActivity(Permissions.listenerSettings(context))
        },
        SetupItem(R.string.perm_dnd, R.string.perm_dnd_desc, permissions.dndAccess) {
            context.startActivity(Permissions.dndSettings())
        },
        SetupItem(R.string.perm_watch, R.string.perm_watch_desc, permissions.watchAssociated) {
            requestWatchAssociation(context.getSystemService(CompanionDeviceManager::class.java), context.mainExecutor) {
                associationLauncher.launch(IntentSenderRequest.Builder(it).build())
            }
        },
        SetupItem(R.string.perm_fullscreen, R.string.perm_fullscreen_desc, permissions.fullScreen) {
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
            Screen.SETTINGS -> SettingsScreen(
                setupItems = setupItems,
                version = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty(),
                onBack = { screen = Screen.HOME },
            )
        }
    }
}

/**
 * Asks the user, through the system dialog, to let WatchSync manage the paired watch
 * (CompanionDeviceManager, watch profile). This is what allows changing the global DND.
 */
private fun requestWatchAssociation(cdm: CompanionDeviceManager, executor: Executor, launch: (IntentSender) -> Unit) {
    val request = AssociationRequest.Builder()
        .setDeviceProfile(AssociationRequest.DEVICE_PROFILE_WATCH)
        .addDeviceFilter(BluetoothDeviceFilter.Builder().build())
        .setSingleDevice(false)
        .build()
    cdm.associate(request, executor, object : CompanionDeviceManager.Callback() {
        override fun onAssociationPending(intentSender: IntentSender) = launch(intentSender)
        override fun onAssociationCreated(associationInfo: AssociationInfo) = Unit
        override fun onFailure(error: CharSequence?) = Unit
    })
}
