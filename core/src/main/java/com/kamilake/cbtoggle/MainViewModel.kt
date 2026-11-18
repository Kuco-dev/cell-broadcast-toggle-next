package com.kamilake.cbtoggle

import android.app.Application
import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Binder
import android.os.IBinder
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

// Samsung "Auto Blocker"; while on it blocks adb, so Shizuku cannot start.
const val AUTO_BLOCKER_PACKAGE = "com.samsung.android.rampart"

// Shizuku pushes its binder shortly after our activity comes to the foreground.
private const val BINDER_WAIT_MS = 1000L

class MainViewModel(app: Application) : AndroidViewModel(app) {

    sealed interface Phase {
        data object Checking : Phase
        data object NotInstalled : Phase
        data object NotRunning : Phase
        data object NeedPermission : Phase
        data object PermissionDenied : Phase
        data object Connecting : Phase
        data object Ready : Phase
    }

    var phase by mutableStateOf<Phase>(Phase.Checking)
        private set
    var developerOptionsEnabled by mutableStateOf(false)
        private set
    var wirelessDebuggingEnabled by mutableStateOf<Boolean?>(null)
        private set
    var hasAutoBlocker by mutableStateOf(false)
        private set
    var users by mutableStateOf<List<UserState>>(emptyList())
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var service: ICellBroadcastService? = null
    private val clientToken = Binder()

    private val serviceArgs = Shizuku.UserServiceArgs(ComponentName(app, CellBroadcastService::class.java))
        .daemon(false)
        .processNameSuffix("service")
        // Shizuku restarts the service when this changes, so an app update runs the new code.
        .version(app.packageManager.getPackageInfo(app.packageName, 0).longVersionCode.toInt())

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder?) {
            if (binder == null || !binder.pingBinder()) return
            service = ICellBroadcastService.Stub.asInterface(binder).also { it.watchClient(clientToken) }
            phase = Phase.Ready
            refresh()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            if (phase == Phase.Ready) phase = Phase.Connecting
        }
    }

    private val onBinderReceived = Shizuku.OnBinderReceivedListener { onShizukuAvailable() }
    private val onBinderDead = Shizuku.OnBinderDeadListener {
        service = null
        phase = offlinePhase()
    }
    private val onPermissionResult = Shizuku.OnRequestPermissionResultListener { _, result ->
        if (result == PERMISSION_GRANTED) bind() else phase = Phase.PermissionDenied
    }

    init {
        Shizuku.addBinderReceivedListenerSticky(onBinderReceived)
        Shizuku.addBinderDeadListener(onBinderDead)
        Shizuku.addRequestPermissionResultListener(onPermissionResult)
        viewModelScope.launch {
            delay(BINDER_WAIT_MS)
            if (phase == Phase.Checking) phase = offlinePhase()
        }
    }

    /** Re-checks after the user returns from Shizuku or Settings. */
    fun onResume() {
        readDeveloperSettings()
        when (phase) {
            Phase.NotInstalled, Phase.NotRunning, Phase.PermissionDenied -> retry()
            else -> Unit
        }
    }

    fun retry() {
        if (Shizuku.pingBinder()) onShizukuAvailable() else phase = offlinePhase()
    }

    fun requestPermission() {
        Shizuku.requestPermission(0)
    }

    fun refresh() = launchOp { it.query() }

    fun setEnabled(enabled: Boolean) = launchOp {
        it.setEnabled(enabled)
        it.query()
    }

    private fun offlinePhase(): Phase = if (isInstalled(SHIZUKU_PACKAGE)) Phase.NotRunning else Phase.NotInstalled

    private fun isInstalled(packageName: String): Boolean = try {
        getApplication<Application>().packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun readDeveloperSettings() {
        val resolver = getApplication<Application>().contentResolver
        developerOptionsEnabled =
            Settings.Global.getInt(resolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
        // Not a public key; null when the platform does not let us read it.
        wirelessDebuggingEnabled = runCatching { Settings.Global.getInt(resolver, "adb_wifi_enabled") == 1 }.getOrNull()
        // Its on/off switch is not readable by regular apps, so only presence is checked.
        hasAutoBlocker = isInstalled(AUTO_BLOCKER_PACKAGE)
    }

    private fun onShizukuAvailable() {
        phase = when {
            Shizuku.isPreV11() -> Phase.NotInstalled
            Shizuku.checkSelfPermission() == PERMISSION_GRANTED -> return bind()
            Shizuku.shouldShowRequestPermissionRationale() -> Phase.PermissionDenied
            else -> Phase.NeedPermission
        }
    }

    private fun bind() {
        phase = Phase.Connecting
        Shizuku.bindUserService(serviceArgs, connection)
    }

    private fun launchOp(block: (ICellBroadcastService) -> List<UserState>) {
        val s = service ?: return
        if (busy) return
        busy = true
        error = null
        viewModelScope.launch {
            try {
                users = withContext(Dispatchers.IO) { block(s) }
            } catch (e: Exception) {
                error = e.message ?: e.toString()
            } finally {
                busy = false
            }
        }
    }

    override fun onCleared() {
        Shizuku.removeBinderReceivedListener(onBinderReceived)
        Shizuku.removeBinderDeadListener(onBinderDead)
        Shizuku.removeRequestPermissionResultListener(onPermissionResult)
        if (service != null && Shizuku.pingBinder()) {
            Shizuku.unbindUserService(serviceArgs, connection, true)
        }
    }
}
