package com.kamilake.cbtoggle

import android.os.IBinder
import kotlin.system.exitProcess

/** Runs in a separate process with Shizuku's identity (shell when Shizuku is started via adb). */
class CellBroadcastService : ICellBroadcastService.Stub() {

    private val toggler = PackageToggler { args ->
        val p = ProcessBuilder(listOf("/system/bin/cmd") + args).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().use { it.readText() }
        CommandResult(p.waitFor(), out)
    }

    override fun destroy() {
        exitProcess(0)
    }

    // If the Shizuku server dies first, nobody else will stop this process.
    override fun watchClient(client: IBinder) {
        client.linkToDeath({ exitProcess(0) }, 0)
    }

    override fun query(): List<UserState> = toggler.query()

    override fun setEnabled(enabled: Boolean) = toggler.setEnabled(enabled)
}
