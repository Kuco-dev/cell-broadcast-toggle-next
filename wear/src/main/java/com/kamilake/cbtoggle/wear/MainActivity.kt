package com.kamilake.cbtoggle.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.dynamicColorScheme
import com.kamilake.cbtoggle.Disclaimer
import com.kamilake.cbtoggle.MainViewModel
import com.kamilake.cbtoggle.MainViewModel.Phase
import com.kamilake.cbtoggle.SHIZUKU_PACKAGE
import com.kamilake.cbtoggle.STATE_ENABLED
import com.kamilake.cbtoggle.STATE_NOT_INSTALLED
import com.kamilake.cbtoggle.TARGET_PACKAGES
import com.kamilake.cbtoggle.core.R as CoreR

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()
    private var accepted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        accepted = Disclaimer.isAccepted(this)
        setContent {
            MaterialTheme(colorScheme = dynamicColorScheme(LocalContext.current) ?: ColorScheme()) {
                AppScaffold {
                    if (accepted) {
                        MainScreen(vm)
                    } else {
                        DisclaimerScreen(onAccept = ::accept, onExit = ::finish)
                    }
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
private fun DisclaimerScreen(onAccept: () -> Unit, onExit: () -> Unit) {
    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(onClick = onAccept) { Text(stringResource(CoreR.string.disclaimer_accept)) }
        },
    ) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item { ListHeader { Text(stringResource(CoreR.string.disclaimer_title)) } }
            item { Text(stringResource(CoreR.string.disclaimer_body), style = MaterialTheme.typography.bodySmall) }
            item {
                OutlinedButton(
                    onClick = onExit,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(CoreR.string.disclaimer_exit)) },
                )
            }
        }
    }
}

@Composable
private fun MainScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val openShizuku: () -> Unit = {
        context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)?.let { context.startActivity(it) }
    }
    val phase = vm.phase
    val users = vm.users
    val busy = vm.busy
    val error = vm.error
    val listState = rememberTransformingLazyColumnState()

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item { ListHeader { Text(stringResource(R.string.app_name), textAlign = TextAlign.Center) } }
            when (phase) {
                Phase.Checking, Phase.Connecting -> item { CircularProgressIndicator(modifier = Modifier.size(40.dp)) }
                Phase.NotInstalled -> message(R.string.shizuku_not_installed, R.string.retry, vm::retry)
                Phase.NotRunning -> {
                    message(R.string.shizuku_not_running, R.string.retry, vm::retry)
                    action(R.string.open_shizuku, openShizuku)
                }
                Phase.NeedPermission -> message(R.string.permission_needed, R.string.grant, vm::requestPermission)
                Phase.PermissionDenied -> {
                    message(R.string.permission_denied, R.string.retry, vm::retry)
                    action(R.string.open_shizuku, openShizuku)
                }
                Phase.Ready -> {
                    val accessible = users.filter { it.accessible }
                    val states = accessible.flatMap { u -> u.states.filter { it != STATE_NOT_INSTALLED } }
                    val anyEnabled = STATE_ENABLED in states
                    val summary = when {
                        states.isEmpty() -> R.string.summary_none
                        states.all { it == STATE_ENABLED } -> R.string.summary_on
                        anyEnabled -> R.string.summary_partial
                        else -> R.string.summary_off
                    }
                    item {
                        SwitchButton(
                            checked = anyEnabled,
                            onCheckedChange = vm::setEnabled,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy && states.isNotEmpty(),
                            label = { Text(stringResource(R.string.toggle_title)) },
                            secondaryLabel = { Text(stringResource(summary)) },
                        )
                    }
                    for (user in accessible) {
                        TARGET_PACKAGES.forEachIndexed { i, pkg ->
                            val state = user.states[i]
                            if (state == STATE_NOT_INSTALLED) return@forEachIndexed
                            item {
                                Text(
                                    "$pkg · " + stringResource(
                                        if (state == STATE_ENABLED) R.string.state_enabled else R.string.state_disabled,
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                    item {
                        Text(
                            stringResource(R.string.warning),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            if (error != null) {
                item { Text(error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
            }
        }
    }
}

private fun TransformingLazyColumnScope.message(@StringRes text: Int, @StringRes button: Int, onClick: () -> Unit) {
    item { Text(stringResource(text), textAlign = TextAlign.Center) }
    item {
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(button)) })
    }
}

private fun TransformingLazyColumnScope.action(@StringRes text: Int, onClick: () -> Unit) {
    item {
        FilledTonalButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(text)) })
    }
}
