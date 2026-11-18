package com.kamilake.cbtoggle.watch

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kamilake.cbtoggle.BodyText
import com.kamilake.cbtoggle.GuideHeader
import com.kamilake.cbtoggle.R
import com.kamilake.cbtoggle.STATE_ENABLED
import com.kamilake.cbtoggle.STATE_NOT_INSTALLED
import com.kamilake.cbtoggle.StepCard
import com.kamilake.cbtoggle.TipText
import com.kamilake.cbtoggle.watch.WatchViewModel.Phase
import com.kamilake.cbtoggle.watch.WatchViewModel.Problem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchScreen(vm: WatchViewModel, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.watch_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            vm.problem?.let { ProblemCard(it) }
            when (vm.phase) {
                Phase.Idle, Phase.Searching -> Searching()
                Phase.NeedPairing, Phase.Pairing -> PairingGuide(busy = vm.phase == Phase.Pairing, onPair = vm::pair, onRetry = vm::connect)
                Phase.Ready -> WatchToggle(vm)
            }
        }
    }
}

@Composable
private fun Searching() {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.watch_searching), style = MaterialTheme.typography.bodyLarge)
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ProblemCard(problem: Problem) {
    val text = when (problem) {
        Problem.NotFound -> R.string.watch_problem_not_found
        Problem.PairingNotFound -> R.string.watch_problem_pairing_not_found
        Problem.WrongCode -> R.string.watch_problem_wrong_code
        Problem.Disconnected -> R.string.watch_problem_disconnected
    }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Text(
            stringResource(text),
            Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun PairingGuide(busy: Boolean, onPair: (String) -> Unit, onRetry: () -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    GuideHeader(R.string.watch_guide_title, R.string.watch_guide_intro)
    StepCard(1, R.string.watch_step_wifi_title, R.string.badge_every_time) {
        BodyText(R.string.watch_step_wifi_body)
    }
    StepCard(2, R.string.watch_step_dev_title, R.string.badge_once) {
        BodyText(R.string.watch_step_dev_body)
    }
    StepCard(3, R.string.watch_step_debug_title, R.string.badge_every_time) {
        BodyText(R.string.watch_step_debug_body)
        TipText(R.string.watch_step_debug_tip)
    }
    StepCard(4, R.string.watch_step_pair_title, R.string.badge_once) {
        BodyText(R.string.watch_step_pair_body)
        OutlinedTextField(
            value = code,
            onValueChange = { v -> code = v.filter(Char::isDigit).take(6) },
            label = { Text(stringResource(R.string.watch_code_label)) },
            singleLine = true,
            enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { onPair(code) }, enabled = !busy && code.length == 6) {
            Text(stringResource(R.string.watch_pair))
        }
        if (busy) {
            TipText(R.string.watch_pairing)
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        TipText(R.string.watch_step_pair_tip)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = onRetry, enabled = !busy) { Text(stringResource(R.string.watch_already_paired)) }
    }
}

@Composable
private fun WatchToggle(vm: WatchViewModel) {
    val states = vm.users.filter { it.accessible }.flatMap { u -> u.states.filter { it != STATE_NOT_INSTALLED } }
    val anyEnabled = STATE_ENABLED in states
    @StringRes val summary = when {
        vm.users.isEmpty() && vm.error == null -> R.string.watch_loading
        states.isEmpty() -> R.string.summary_none
        states.all { it == STATE_ENABLED } -> R.string.summary_on
        anyEnabled -> R.string.summary_partial
        else -> R.string.watch_summary_off
    }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.watch_connected, vm.watchName.orEmpty()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.watch_toggle_title), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = anyEnabled, onCheckedChange = vm::setEnabled, enabled = !vm.busy && states.isNotEmpty())
            }
        }
        if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    Text(
        stringResource(R.string.watch_warning),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    vm.error?.let {
        Text(it, color = MaterialTheme.colorScheme.error)
        FilledTonalButton(onClick = vm::refresh, enabled = !vm.busy) { Text(stringResource(R.string.retry)) }
    }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.watch_after_title), style = MaterialTheme.typography.titleSmall)
            BodyText(R.string.watch_after_body)
        }
    }
}
