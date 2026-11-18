package com.kamilake.cbtoggle.watch

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kamilake.cbtoggle.PackageToggler
import com.kamilake.cbtoggle.UserState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "WatchViewModel"
private const val CONNECT_SEARCH_MS = 8_000L
private const val PAIRING_SEARCH_MS = 20_000L

class WatchViewModel(app: Application) : AndroidViewModel(app) {

    sealed interface Phase {
        data object Idle : Phase
        data object Searching : Phase
        /** No paired watch answered; the user needs to pair with a code. */
        data object NeedPairing : Phase
        data object Pairing : Phase
        data object Ready : Phase
    }

    enum class Problem { NotFound, PairingNotFound, WrongCode, Disconnected }

    var phase by mutableStateOf<Phase>(Phase.Idle)
        private set
    var watchName by mutableStateOf<String?>(null)
        private set
    var users by mutableStateOf<List<UserState>>(emptyList())
        private set
    var busy by mutableStateOf(false)
        private set
    var problem by mutableStateOf<Problem?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private val discovery = WatchDiscovery(app)
    private val adb by lazy { WatchAdb.get(app) }
    private val toggler by lazy { PackageToggler { adb.cmd(it) } }
    private var job: Job? = null

    /** Looks for a watch this phone already paired with. */
    fun connect() {
        if (job?.isActive == true) return
        problem = null
        error = null
        phase = Phase.Searching
        job = viewModelScope.launch {
            val service = withContext(Dispatchers.IO) {
                runCatching { adb.disconnect() }
                // Other phones on the network may advertise too; only a device that already trusts us accepts.
                discovery.find(TYPE_CONNECT, CONNECT_SEARCH_MS) { candidate ->
                    val ok = runCatching { adb.connect(candidate.host, candidate.port) }.getOrDefault(false)
                    if (!ok) runCatching { adb.disconnect() }
                    ok
                }
            }
            if (service != null) {
                watchName = service.name
                loadState()
            } else {
                phase = Phase.NeedPairing
            }
        }
    }

    fun pair(code: String) {
        if (job?.isActive == true) return
        problem = null
        error = null
        phase = Phase.Pairing
        job = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val service = discovery.find(TYPE_PAIRING, PAIRING_SEARCH_MS)
                    ?: return@withContext Problem.PairingNotFound
                try {
                    adb.pair(service.host, service.port, code)
                    null
                } catch (e: Exception) {
                    Log.w(TAG, "pairing with ${service.host}:${service.port} failed", e)
                    Problem.WrongCode
                }
            }
            if (result == null) {
                phase = Phase.Idle
                connect()
            } else {
                problem = result
                phase = Phase.NeedPairing
            }
        }
    }

    fun setEnabled(enabled: Boolean) = launchOp {
        toggler.setEnabled(enabled)
        toggler.query()
    }

    fun refresh() = launchOp { toggler.query() }

    private fun loadState() {
        phase = Phase.Ready
        refresh()
    }

    private fun launchOp(block: () -> List<UserState>) {
        if (busy || phase != Phase.Ready) return
        busy = true
        error = null
        viewModelScope.launch {
            try {
                users = withContext(Dispatchers.IO) { block() }
            } catch (e: Exception) {
                if (!adb.isConnected) {
                    problem = Problem.Disconnected
                    phase = Phase.Idle
                } else {
                    error = e.message ?: e.toString()
                }
            } finally {
                busy = false
            }
        }
    }

    override fun onCleared() {
        val client = adb
        Thread { runCatching { client.disconnect() } }.start()
    }
}
