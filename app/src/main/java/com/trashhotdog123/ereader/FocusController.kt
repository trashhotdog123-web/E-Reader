package com.trashhotdog123.ereader

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings

object FocusController {
    private const val PREFS = "ereader_focus"
    private const val PREVIOUS_FILTER = "previous_filter"

    fun hasAccess(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted

    fun openAccessSettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
    }

    fun start(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.isNotificationPolicyAccessGranted) return false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(PREVIOUS_FILTER, manager.currentInterruptionFilter).apply()
        return runCatching {
            manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            true
        }.getOrDefault(false)
    }

    fun stop(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.isNotificationPolicyAccessGranted) return
        val previous = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(PREVIOUS_FILTER, NotificationManager.INTERRUPTION_FILTER_ALL)
        runCatching { manager.setInterruptionFilter(previous) }
    }
}
