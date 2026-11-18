package com.kamilake.cbtoggle

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri

private const val PLAY_URL = "https://play.google.com/store/apps/details?id=$SHIZUKU_PACKAGE"
private const val GITHUB_URL = "https://github.com/RikkaApps/Shizuku/releases"
private const val MANUAL_URL = "https://shizuku.rikka.app/guide/setup/"

@Composable
fun InstallGuide(onRecheck: () -> Unit) {
    val context = LocalContext.current
    GuideHeader(R.string.install_title, R.string.install_intro)
    StepCard(1, R.string.install_step1_title) {
        BodyText(R.string.install_step1_body)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { context.openPlayStore() }) { Text(stringResource(R.string.install_play)) }
            OutlinedButton(onClick = { context.openUrl(GITHUB_URL) }) { Text(stringResource(R.string.install_github)) }
        }
    }
    StepCard(2, R.string.install_step2_title) {
        BodyText(R.string.install_step2_body)
    }
    GuideFooter(onRecheck)
}

@Composable
fun StartGuide(
    autoBlocker: Boolean,
    developerOptions: Boolean,
    wirelessDebugging: Boolean?,
    onRecheck: () -> Unit,
) {
    val context = LocalContext.current
    var step = 0
    GuideHeader(R.string.start_title, R.string.start_intro)
    if (autoBlocker) {
        StepCard(++step, R.string.step_blocker_title, R.string.badge_galaxy) {
            BodyText(R.string.step_blocker_body)
            TipText(R.string.step_blocker_tip)
            FilledTonalButton(onClick = { context.openAutoBlocker() }) {
                Text(stringResource(R.string.step_blocker_action))
            }
        }
    }
    StepCard(++step, R.string.step_dev_title, R.string.badge_once, done = developerOptions) {
        BodyText(R.string.step_dev_body)
        FilledTonalButton(onClick = { context.openSettings(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)) }) {
            Text(stringResource(R.string.step_dev_action))
        }
    }
    StepCard(
        ++step, R.string.step_wifi_title, R.string.badge_check_after_boot,
        done = wirelessDebugging?.let { it && developerOptions },
    ) {
        BodyText(R.string.step_wifi_body)
        FilledTonalButton(onClick = { context.openWirelessDebugging() }) {
            Text(stringResource(R.string.open_developer_options))
        }
    }
    StepCard(++step, R.string.step_pair_title, R.string.badge_once) {
        BodyText(R.string.step_pair_body)
        TipText(R.string.step_pair_tip)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { context.openShizuku() }) { Text(stringResource(R.string.open_shizuku)) }
            OutlinedButton(onClick = { context.openWirelessDebugging() }) {
                Text(stringResource(R.string.open_developer_options))
            }
        }
    }
    StepCard(++step, R.string.step_start_title, R.string.badge_every_boot) {
        BodyText(R.string.step_start_body)
        FilledTonalButton(onClick = { context.openShizuku() }) { Text(stringResource(R.string.open_shizuku)) }
    }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.start_pc_title), style = MaterialTheme.typography.titleSmall)
            BodyText(R.string.start_pc_body)
        }
    }
    GuideFooter(onRecheck)
}

@Composable
internal fun GuideHeader(@StringRes title: Int, @StringRes intro: Int) {
    ElevatedCard(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(intro), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
internal fun StepCard(
    number: Int,
    @StringRes title: Int,
    @StringRes badge: Int? = null,
    done: Boolean? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StepNumber(number, done == true)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                    if (badge != null) {
                        Text(
                            stringResource(badge),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (done != null) StatusLabel(done)
            }
            content()
        }
    }
}

@Composable
private fun StepNumber(number: Int, done: Boolean) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = if (done) colors.primary else colors.secondaryContainer,
        contentColor = if (done) colors.onPrimary else colors.onSecondaryContainer,
        modifier = Modifier.size(32.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(if (done) "✓" else number.toString(), style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun StatusLabel(done: Boolean) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (done) colors.primaryContainer else colors.errorContainer,
        contentColor = if (done) colors.onPrimaryContainer else colors.onErrorContainer,
    ) {
        Text(
            stringResource(if (done) R.string.status_done else R.string.status_not_done),
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun GuideFooter(onRecheck: () -> Unit) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = onRecheck) { Text(stringResource(R.string.retry)) }
        TextButton(onClick = { context.openUrl(MANUAL_URL) }) { Text(stringResource(R.string.shizuku_manual)) }
    }
    Text(
        stringResource(R.string.auto_recheck_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun BodyText(@StringRes text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
}

@Composable
internal fun TipText(@StringRes text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun Context.launch(intent: Intent): Boolean = try {
    startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
}

private fun Context.openUrl(url: String) = launch(Intent(Intent.ACTION_VIEW, url.toUri()))

private fun Context.openPlayStore() {
    if (!launch(Intent(Intent.ACTION_VIEW, "market://details?id=$SHIZUKU_PACKAGE".toUri()))) openUrl(PLAY_URL)
}

private fun Context.openSettings(intent: Intent) {
    if (!launch(intent)) launch(Intent(Settings.ACTION_SETTINGS))
}

private fun Context.openWirelessDebugging() = openSettings(
    // Undocumented extra that scrolls Settings to the "Wireless debugging" row.
    Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).putExtra(":settings:fragment_args_key", "toggle_adb_wireless"),
)

private fun Context.openAutoBlocker() {
    // Exported settings screen; the Settings-menu aliases point at an invisible event handler instead.
    val intent = Intent().setClassName(AUTO_BLOCKER_PACKAGE, "$AUTO_BLOCKER_PACKAGE.ui.MainSettingActivity")
    if (!launch(intent)) openSettings(Intent(Settings.ACTION_SECURITY_SETTINGS))
}

fun Context.openShizuku() {
    packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)?.let { launch(it) }
}
