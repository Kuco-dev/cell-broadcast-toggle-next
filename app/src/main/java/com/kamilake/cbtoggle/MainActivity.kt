package com.kamilake.cbtoggle

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kamilake.cbtoggle.MainViewModel.Phase
import com.kamilake.cbtoggle.watch.WatchActivity

private val TARGET_LABELS = listOf(R.string.pkg_receiver, R.string.pkg_service, R.string.pkg_receiver)

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()
    private var accepted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        accepted = Disclaimer.isAccepted(this)
        setContent {
            AppTheme {
                if (accepted) {
                    MainScreen(vm)
                } else {
                    DisclaimerScreen(onAccept = ::accept, onExit = ::finish)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (accepted) vm.onResume()
    }

    private fun accept() {
        Disclaimer.accept(this)
        accepted = true
        vm.onResume()
    }
}

@Composable
private fun AppTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    MaterialTheme(colorScheme = scheme, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(vm: MainViewModel) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (vm.phase) {
                Phase.Checking, Phase.Connecting -> LinearProgressIndicator(Modifier.fillMaxWidth())
                Phase.NotInstalled -> InstallGuide(vm::retry)
                Phase.NotRunning -> StartGuide(
                    autoBlocker = vm.hasAutoBlocker,
                    developerOptions = vm.developerOptionsEnabled,
                    wirelessDebugging = vm.wirelessDebuggingEnabled,
                    onRecheck = vm::retry,
                )
                Phase.NeedPermission -> MessageCard(R.string.permission_needed, R.string.grant, vm::requestPermission)
                Phase.PermissionDenied -> PermissionDeniedCard(vm::retry)
                Phase.Ready -> {
                    ToggleCard(vm)
                    vm.users.forEach { UserCard(it) }
                }
            }
            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (vm.phase == Phase.Ready) WatchEntryCard()
        }
    }
}

@Composable
private fun WatchEntryCard() {
    val context = LocalContext.current
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.open_watch), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.open_watch_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(onClick = { context.startActivity(Intent(context, WatchActivity::class.java)) }) {
                Text(stringResource(R.string.watch_title))
            }
        }
    }
}

@Composable
private fun MessageCard(@StringRes message: Int, @StringRes action: Int, onAction: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(message), style = MaterialTheme.typography.bodyLarge)
            FilledTonalButton(onClick = onAction) { Text(stringResource(action)) }
        }
    }
}

@Composable
private fun PermissionDeniedCard(onRetry: () -> Unit) {
    val context = LocalContext.current
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.permission_denied), style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { context.openShizuku() }) { Text(stringResource(R.string.open_shizuku)) }
                OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
        }
    }
}

@Composable
private fun ToggleCard(vm: MainViewModel) {
    val states = vm.users.filter { it.accessible }.flatMap { u -> u.states.filter { it != STATE_NOT_INSTALLED } }
    val anyEnabled = STATE_ENABLED in states
    val summary = when {
        states.isEmpty() -> R.string.summary_none
        states.all { it == STATE_ENABLED } -> R.string.summary_on
        anyEnabled -> R.string.summary_partial
        else -> R.string.summary_off
    }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.toggle_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = anyEnabled, onCheckedChange = vm::setEnabled, enabled = !vm.busy && states.isNotEmpty())
        }
        if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    Text(
        stringResource(R.string.warning),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun UserCard(user: UserState) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.user_title, user.userId, user.name), style = MaterialTheme.typography.titleMedium)
            if (!user.accessible) {
                Text(
                    stringResource(R.string.user_inaccessible),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                TARGET_PACKAGES.forEachIndexed { i, pkg ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(TARGET_LABELS[i]), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                pkg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(stringResource(stateLabel(user.states[i])), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@StringRes
private fun stateLabel(state: Int) = when (state) {
    STATE_ENABLED -> R.string.state_enabled
    STATE_DISABLED -> R.string.state_disabled
    else -> R.string.state_not_installed
}
