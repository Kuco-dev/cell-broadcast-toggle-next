package com.kamilake.cbtoggle

import android.content.Context

/** Remembers whether the user agreed to the first-run disclaimer on this device. */
object Disclaimer {
    private const val PREFS = "settings"
    private const val KEY_ACCEPTED = "disclaimer_accepted"

    fun isAccepted(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ACCEPTED, false)

    fun accept(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ACCEPTED, true).apply()
    }
}
