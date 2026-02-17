package it.sapienza.smartpantry.reminder

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import it.sapienza.smartpantry.ui.model.ShoppingSection
import java.util.concurrent.TimeUnit

object ShoppingProximityReminderManager {
    const val DEFAULT_RADIUS_METERS = 300

    private const val PREFS_NAME = "shopping_proximity_reminder_prefs"
    private const val KEY_ENABLED = "key_enabled"
    private const val KEY_PENDING_ITEMS = "key_pending_items"
    private const val KEY_LAST_NOTIFIED_AT = "key_last_notified_at"
    private const val KEY_LAST_NOTIFIED_SUPERMARKET_ID = "key_last_notified_supermarket_id"

    private const val UNIQUE_PERIODIC_WORK_NAME = "shopping_proximity_periodic_work"
    private const val UNIQUE_IMMEDIATE_WORK_NAME = "shopping_proximity_immediate_work"
    private const val MIN_NOTIFICATION_INTERVAL_MS = 60 * 60 * 1000L

    fun isEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_ENABLED, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()

        if (enabled) {
            if (getPendingItems(context).isNotEmpty()) {
                schedulePeriodicCheck(context)
                scheduleImmediateCheck(context)
            }
        } else {
            cancelScheduledChecks(context)
        }
    }

    fun syncShoppingItems(context: Context, sections: List<ShoppingSection>) {
        val pendingItems = sections
            .flatMap { section -> section.items }
            .filter { !it.isChecked }
            .mapNotNull { item ->
                item.name.trim().takeIf { it.isNotEmpty() }
            }
            .distinct()

        val pendingItemsSet = pendingItems.toSet()
        prefs(context).edit()
            .putStringSet(KEY_PENDING_ITEMS, pendingItemsSet)
            .apply()

        if (!isEnabled(context)) {
            return
        }

        if (pendingItems.isEmpty()) {
            cancelScheduledChecks(context)
        } else {
            schedulePeriodicCheck(context)
            scheduleImmediateCheck(context)
        }
    }

    fun getPendingItems(context: Context): List<String> {
        return prefs(context).getStringSet(KEY_PENDING_ITEMS, emptySet())
            .orEmpty()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .sorted()
    }

    fun canNotifyForSupermarket(
        context: Context,
        supermarketId: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val preferences = prefs(context)
        val lastNotifiedAt = preferences.getLong(KEY_LAST_NOTIFIED_AT, 0L)
        val elapsedTime = nowMillis - lastNotifiedAt
        if (elapsedTime < MIN_NOTIFICATION_INTERVAL_MS) {
            return false
        }

        val lastSupermarketId = preferences
            .getString(KEY_LAST_NOTIFIED_SUPERMARKET_ID, null)
            .orEmpty()
        return lastSupermarketId != supermarketId || elapsedTime >= MIN_NOTIFICATION_INTERVAL_MS
    }

    fun markNotified(
        context: Context,
        supermarketId: String,
        atMillis: Long = System.currentTimeMillis()
    ) {
        prefs(context).edit()
            .putLong(KEY_LAST_NOTIFIED_AT, atMillis)
            .putString(KEY_LAST_NOTIFIED_SUPERMARKET_ID, supermarketId)
            .apply()
    }

    private fun schedulePeriodicCheck(context: Context) {
        val periodicRequest = PeriodicWorkRequestBuilder<ShoppingProximityReminderWorker>(
            15,
            TimeUnit.MINUTES
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicRequest
        )
    }

    private fun scheduleImmediateCheck(context: Context) {
        val immediateRequest = OneTimeWorkRequestBuilder<ShoppingProximityReminderWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            immediateRequest
        )
    }

    private fun cancelScheduledChecks(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_PERIODIC_WORK_NAME)
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_IMMEDIATE_WORK_NAME)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
