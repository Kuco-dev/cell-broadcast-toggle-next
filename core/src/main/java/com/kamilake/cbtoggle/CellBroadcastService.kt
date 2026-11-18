package com.kamilake.cbtoggle

import android.os.IBinder
import kotlin.system.exitProcess

val TARGET_PACKAGES = listOf(
    "com.google.android.cellbroadcastreceiver",
    "com.google.android.cellbroadcastservice",
)

/** Runs in a separate process with Shizuku's identity (shell when Shizuku is started via adb). */
class CellBroadcastService : ICellBroadcastService.Stub() {

    override fun destroy() {
        exitProcess(0)
    }

    // If the Shizuku server dies first, nobody else will stop this process.
    override fun watchClient(client: IBinder) {
        client.linkToDeath({ exitProcess(0) }, 0)
    }

    override fun query(): List<UserState> = listUsers().map { (id, name) -> queryUser(id, name) }

    override fun setEnabled(enabled: Boolean) {
        val verb = if (enabled) "enable" else "disable-user"
        for (user in query()) {
            if (!user.accessible) continue
            TARGET_PACKAGES.forEachIndexed { i, pkg ->
                if (user.states[i] == STATE_NOT_INSTALLED) return@forEachIndexed
                val r = cmd("package", verb, "--user", user.userId.toString(), pkg)
                check(r.code == 0 && "new state" in r.out) { r.out.trim() }
            }
        }
    }

    private fun listUsers(): List<Pair<Int, String>> =
        USER_REGEX.findAll(cmd("package", "list", "users").out)
            .map { it.groupValues[1].toInt() to it.groupValues[2].trim() }
            .toList()

    private fun queryUser(id: Int, name: String): UserState {
        val enabled = listPackages(id, "-e")
        val disabled = listPackages(id, "-d")
        if (enabled == null || disabled == null) {
            return UserState(id, name, false, IntArray(TARGET_PACKAGES.size) { STATE_NOT_INSTALLED })
        }
        val states = IntArray(TARGET_PACKAGES.size) { i ->
            when (TARGET_PACKAGES[i]) {
                in enabled -> STATE_ENABLED
                in disabled -> STATE_DISABLED
                else -> STATE_NOT_INSTALLED
            }
        }
        return UserState(id, name, true, states)
    }

    /** Returns null when the user is not accessible (e.g. Secure Folder under shell). */
    private fun listPackages(userId: Int, filter: String): Set<String>? {
        val r = cmd("package", "list", "packages", "--user", userId.toString(), filter)
        if (r.code != 0 || "Exception" in r.out) return null
        return r.out.lineSequence()
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:").trim() }
            .toSet()
    }

    private class Result(val code: Int, val out: String)

    private fun cmd(vararg args: String): Result {
        val p = ProcessBuilder("/system/bin/cmd", *args).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().use { it.readText() }
        return Result(p.waitFor(), out)
    }

    private companion object {
        val USER_REGEX = Regex("""UserInfo\{(\d+):(.*):\p{XDigit}+\}""")
    }
}
