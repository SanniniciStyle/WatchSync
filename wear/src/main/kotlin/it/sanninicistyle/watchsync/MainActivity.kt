package it.sanninicistyle.watchsync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import it.sanninicistyle.watchsync.shared.InfoPaths
import it.sanninicistyle.watchsync.shared.PeerInfo
import it.sanninicistyle.watchsync.ui.WatchHome
import it.sanninicistyle.watchsync.ui.WatchHomeViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PeerInfo.load(this)
        setContent {
            val vm: WatchHomeViewModel = viewModel()
            val phone by vm.phone.collectAsStateWithLifecycle()
            val mode by vm.mode.collectAsStateWithLifecycle()
            val ready by vm.ready.collectAsStateWithLifecycle()
            val nextAlarm by vm.nextAlarm.collectAsStateWithLifecycle()
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
            MaterialTheme {
                AppScaffold {
                    ScreenScaffold {
                        WatchHome(phone = phone, ready = ready, mode = mode, nextAlarm = nextAlarm, onToggle = vm::toggle)
                    }
                }
            }
        }
    }
}
