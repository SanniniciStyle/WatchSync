package it.sanninicistyle.watchsync.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import it.sanninicistyle.watchsync.R
import it.sanninicistyle.watchsync.adb.AdbEndpoint
import it.sanninicistyle.watchsync.shared.DiagLog
import it.sanninicistyle.watchsync.adb.AdbServices
import it.sanninicistyle.watchsync.adb.WatchProvisioner
import it.sanninicistyle.watchsync.adb.WatchProvisioner.Failure
import it.sanninicistyle.watchsync.adb.WatchProvisioner.Step
import it.sanninicistyle.watchsync.ui.theme.Ws
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface PairState {
    data object Input : PairState
    data class Working(val step: Step) : PairState
    data object Done : PairState
    data class Failed(val failure: Failure, val detail: String = "") : PairState
}

@OptIn(ExperimentalCoroutinesApi::class)
class PairViewModel(app: Application) : AndroidViewModel(app) {
    private val canSearch = MutableStateFlow(false)

    /** The watch, once it shows its pairing code (wireless debugging, "Pair new device"). */
    val watch: StateFlow<AdbEndpoint?> = canSearch
        .flatMapLatest { if (it) AdbServices.discover(app, AdbServices.PAIRING) else emptyFlow() }
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _state = MutableStateFlow<PairState>(PairState.Input)
    val state: StateFlow<PairState> = _state.asStateFlow()
    private var job: Job? = null

    fun startSearch() { canSearch.value = true }

    fun pair(code: String) {
        val endpoint = watch.value ?: return
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            _state.value = try {
                WatchProvisioner(getApplication()).run(endpoint, code) { _state.value = PairState.Working(it) }
                PairState.Done
            } catch (e: WatchProvisioner.ProvisionException) {
                PairState.Failed(e.failure, e.detail)
            } catch (e: Exception) {
                DiagLog.w("PairViewModel", "setup failed", e)
                PairState.Failed(Failure.NO_CONNECTION)
            }
        }
    }

    fun retry() { _state.value = PairState.Input }
}

@Composable
fun PairScreen(onBack: () -> Unit, onWatchReady: () -> Unit, vm: PairViewModel = viewModel()) {
    val context = LocalContext.current
    val watch by vm.watch.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state) { if (state is PairState.Done) onWatchReady() }

    // Android 17: finding the watch on the Wi-Fi needs the local network permission
    val localNetwork = if (Build.VERSION.SDK_INT >= 37) Manifest.permission.ACCESS_LOCAL_NETWORK else null
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.startSearch() }
    LaunchedEffect(Unit) {
        if (localNetwork == null || context.checkSelfPermission(localNetwork) == PackageManager.PERMISSION_GRANTED) vm.startSearch()
        else permissionLauncher.launch(localNetwork)
    }

    ScreenScaffold(title = stringResource(R.string.pair_title), onBack = onBack, info = R.string.info_watch_setup) {
        AnimatedContent(
            targetState = state is PairState.Input,
            transitionSpec = { (fadeIn(tween(260)) + scaleIn(initialScale = 0.96f)) togetherWith fadeOut(tween(160)) },
            label = "pair",
        ) { input ->
            if (input) InputPane(watch = watch, onPair = vm::pair)
            else ProgressPane(state = state, onRetry = vm::retry, onDone = onBack)
        }
    }
}

@Composable
private fun InputPane(watch: AdbEndpoint?, onPair: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Ws.Surface).padding(vertical = 6.dp)) {
            InstructionRow(1, R.string.pair_step_dev, R.string.pair_step_dev_hint)
            InstructionRow(2, R.string.pair_step_wireless, R.string.pair_step_wireless_hint)
        }
        WatchSearch(watch)
        CodeField(code = code, onChange = { code = it })
        val ready = code.length == 6 && watch != null
        val bg by animateColorAsState(if (ready) Ws.Amber else Ws.SurfaceHigh, spring(stiffness = Spring.StiffnessMediumLow), label = "btn")
        val fg by animateColorAsState(if (ready) Ws.OnAmber else Ws.TextFaint, spring(stiffness = Spring.StiffnessMediumLow), label = "btnText")
        PressableSurface(
            onClick = { if (ready) onPair(code) },
            color = bg,
            shape = RoundedCornerShape(32.dp),
            modifier = Modifier.fillMaxWidth().height(64.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.pair_connect), style = MaterialTheme.typography.titleMedium, color = fg)
            }
        }
    }
}

@Composable
private fun InstructionRow(number: Int, title: Int, hint: Int) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(Ws.SurfaceHigh), contentAlignment = Alignment.Center) {
            Text("$number", style = MaterialTheme.typography.labelLarge, color = Ws.Amber)
        }
        Column {
            Text(stringResource(title), style = MaterialTheme.typography.labelLarge)
            Text(stringResource(hint), style = MaterialTheme.typography.bodyMedium, color = Ws.TextFaint)
        }
    }
}

@Composable
private fun WatchSearch(watch: AdbEndpoint?) {
    val dot by animateColorAsState(if (watch != null) Ws.Mint else Ws.Amber, spring(stiffness = Spring.StiffnessLow), label = "dot")
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            if (watch == null) Pulse(color = Ws.Amber, diameter = 22.dp)
            Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
        }
        AnimatedContent(targetState = watch, transitionSpec = { slideUp() }, label = "search") { found ->
            Text(
                if (found == null) stringResource(R.string.pair_searching) else stringResource(R.string.pair_found, found.host),
                style = MaterialTheme.typography.bodyLarge,
                color = if (found == null) Ws.TextMuted else Ws.Text,
            )
        }
    }
}

/** Six digit boxes over a hidden text field: digits pop in, the next box glows. */
@Composable
private fun CodeField(code: String, onChange: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    BasicTextField(
        value = code,
        onValueChange = { new ->
            val digits = new.filter(Char::isDigit).take(6)
            onChange(digits)
            if (digits.length == 6) keyboard?.hide()
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
        decorationBox = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(6) { i ->
                    val digit = code.getOrNull(i)
                    val active = i == code.length
                    val border by animateColorAsState(
                        when { active -> Ws.Amber; digit != null -> Ws.Outline; else -> Color.Transparent },
                        spring(stiffness = Spring.StiffnessMedium), label = "border$i",
                    )
                    val pop by animateFloatAsState(
                        if (digit != null) 1f else 0.6f,
                        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "pop$i",
                    )
                    Box(
                        Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(18.dp)).background(Ws.Surface)
                            .border(2.dp, border, RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (digit != null) {
                            Text(digit.toString(), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.scale(pop))
                        }
                    }
                }
            }
        },
    )
    LaunchedEffect(Unit) { focus.requestFocus() }
}

@Composable
private fun ProgressPane(state: PairState, onRetry: () -> Unit, onDone: () -> Unit) {
    val current = (state as? PairState.Working)?.step
    val failed = state as? PairState.Failed
    val done = state is PairState.Done
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Ws.Surface).padding(vertical = 6.dp)) {
            Step.entries.forEach { step ->
                val reached = when {
                    done -> StepState.DONE
                    current != null && step.ordinal < current.ordinal -> StepState.DONE
                    current == step -> StepState.ACTIVE
                    failed != null && step == failedStep(failed.failure) -> StepState.FAILED
                    failed != null && step.ordinal < failedStep(failed.failure).ordinal -> StepState.DONE
                    else -> StepState.PENDING
                }
                ProgressRow(stepLabel(step), reached)
            }
        }
        AnimatedContent(targetState = state, transitionSpec = { slideUp() }, label = "result") { s ->
            when (s) {
                is PairState.Done -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(stringResource(R.string.pair_done), style = MaterialTheme.typography.headlineMedium, color = Ws.Mint)
                    Text(stringResource(R.string.pair_done_desc), style = MaterialTheme.typography.bodyLarge, color = Ws.TextMuted)
                    WideButton(stringResource(R.string.done), Ws.Amber, Ws.OnAmber, onDone)
                }
                is PairState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(stringResource(failureText(s.failure), s.detail), style = MaterialTheme.typography.bodyLarge, color = Ws.Text)
                    WideButton(stringResource(R.string.retry), Ws.SurfaceHigh, Ws.Text, onRetry)
                }
                else -> Box(Modifier.height(1.dp))
            }
        }
    }
}

private enum class StepState { PENDING, ACTIVE, DONE, FAILED }

@Composable
private fun ProgressRow(label: Int, state: StepState) {
    val color by animateColorAsState(
        when (state) { StepState.DONE -> Ws.Mint; StepState.ACTIVE -> Ws.Amber; StepState.FAILED -> Color(0xFFFF8A80); StepState.PENDING -> Ws.SurfaceHigh },
        spring(stiffness = Spring.StiffnessLow), label = "step",
    )
    val pop by animateFloatAsState(
        if (state == StepState.DONE) 1f else 0.85f,
        spring(Spring.DampingRatioHighBouncy, Spring.StiffnessMediumLow), label = "stepPop",
    )
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
            if (state == StepState.ACTIVE) {
                CircularProgressIndicator(color = Ws.Amber, strokeWidth = 3.dp, modifier = Modifier.size(26.dp))
            } else {
                Box(Modifier.size(30.dp).scale(pop).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                    if (state == StepState.DONE) Icon(WsIcons.Check, null, tint = Ws.Ground, modifier = Modifier.size(15.dp))
                }
            }
        }
        Text(
            stringResource(label), style = MaterialTheme.typography.labelLarge,
            color = if (state == StepState.PENDING) Ws.TextFaint else Ws.Text,
        )
    }
}

@Composable
private fun WideButton(text: String, color: Color, textColor: Color, onClick: () -> Unit) {
    PressableSurface(onClick = onClick, color = color, shape = RoundedCornerShape(32.dp), modifier = Modifier.fillMaxWidth().height(64.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.titleMedium, color = textColor)
        }
    }
}

private fun failedStep(failure: Failure) = when (failure) {
    Failure.WRONG_CODE -> Step.PAIR
    Failure.NO_CONNECTION -> Step.CONNECT
    Failure.APP_MISSING, Failure.GRANT_FAILED -> Step.GRANT
    Failure.NO_REPORT, Failure.WATCH_OUTDATED, Failure.NOT_READY -> Step.VERIFY
}

private fun stepLabel(step: Step) = when (step) {
    Step.PAIR -> R.string.pair_progress_pair
    Step.CONNECT -> R.string.pair_progress_connect
    Step.GRANT -> R.string.pair_progress_grant
    Step.VERIFY -> R.string.pair_progress_verify
}

private fun failureText(failure: Failure) = when (failure) {
    Failure.WRONG_CODE -> R.string.pair_error_code
    Failure.NO_CONNECTION -> R.string.pair_error_connection
    Failure.APP_MISSING -> R.string.pair_error_app
    Failure.GRANT_FAILED -> R.string.pair_error_grant
    Failure.NO_REPORT -> R.string.pair_error_report
    Failure.WATCH_OUTDATED -> R.string.pair_error_outdated
    Failure.NOT_READY -> R.string.pair_error_not_ready
}
