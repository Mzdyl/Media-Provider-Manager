package me.gm.cleaner.plugin.util

import android.content.Context
import androidx.core.content.edit

object ModuleActivationStore {
    private const val PREFS_NAME = "module_activation"
    private const val KEY_APP_PROCESS_HOOKED = "app_process_hooked"

    fun isAppProcessHooked(context: Context): Boolean =
        prefs(context).getBoolean(KEY_APP_PROCESS_HOOKED, false)

    fun markAppProcessHooked(context: Context) {
        prefs(context).edit(commit = true) {
            putBoolean(KEY_APP_PROCESS_HOOKED, true)
        }
    }

    fun resetAppProcessHooked(context: Context) {
        prefs(context).edit(commit = true) {
            putBoolean(KEY_APP_PROCESS_HOOKED, false)
        }
    }

    private fun prefs(context: Context) = context.createDeviceProtectedStorageContext()
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
