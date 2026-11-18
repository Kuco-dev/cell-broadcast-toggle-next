package com.kamilake.cbtoggle

val TARGET_PACKAGES = listOf(
    "com.google.android.cellbroadcastreceiver",
    "com.google.android.cellbroadcastservice",
    // AOSP receiver, used instead of the Google one on Wear OS (e.g. Galaxy Watch).
    "com.android.cellbroadcastreceiver",
)

class CommandResult(val code: Int, val out: String)

/** Lists and toggles the target packages through `cmd`, run either locally or over adb. */
class PackageToggler(private val cmd: (List<String>) -> CommandResult) {

    fun query(): List<UserState> = listUsers().map { (id, name) -> queryUser(id, name) }

    fun setEnabled(enabled: Boolean) {
        val verb = if (enabled) "enable" else "disable-user"
        for (user in query()) {
            if (!user.accessible) continue
            TARGET_PACKAGES.forEachIndexed { i, pkg ->
                if (user.states[i] == STATE_NOT_INSTALLED) return@forEachIndexed
                val r = cmd(listOf("package", verb, "--user", user.userId.toString(), pkg))
                check(r.code == 0 && "new state" in r.out) { r.out.trim() }
            }
        }
    }

    private fun listUsers(): List<Pair<Int, String>> =
        USER_REGEX.findAll(cmd(listOf("package", "list", "users")).out)
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
        val r = cmd(listOf("package", "list", "packages", "--user", userId.toString(), filter))
        if (r.code != 0 || "Exception" in r.out) return null
        return r.out.lineSequence()
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:").trim() }
            .toSet()
    }

    private companion object {
        val USER_REGEX = Regex("""UserInfo\{(\d+):(.*):\p{XDigit}+\}""")
    }
}
