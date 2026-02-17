package it.sapienza.smartpantry.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import it.sapienza.smartpantry.R
import it.sapienza.smartpantry.data.supermarket.SupermarketRepository
import it.sapienza.smartpantry.ui.MainActivity
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.suspendCancellableCoroutine

class ShoppingProximityReminderWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {

    private val locationClient = LocationServices.getFusedLocationProviderClient(appContext)
    private val supermarketRepository = SupermarketRepository()

    override suspend fun doWork(): Result {
        if (!ShoppingProximityReminderManager.isEnabled(applicationContext)) {
            return Result.success()
        }

        val pendingItems = ShoppingProximityReminderManager.getPendingItems(applicationContext)
        if (pendingItems.isEmpty()) {
            return Result.success()
        }

        if (!hasLocationPermission()) {
            return Result.success()
        }

        val location = runCatching {
            getCurrentLocationOrNull() ?: getLastKnownLocationOrNull()
        }.getOrNull() ?: return Result.success()

        val nearestSupermarket = runCatching {
            supermarketRepository.findNearbySupermarkets(
                latitude = location.latitude,
                longitude = location.longitude,
                radiusMeters = ShoppingProximityReminderManager.DEFAULT_RADIUS_METERS
            ).firstOrNull()
        }.getOrElse {
            return Result.retry()
        } ?: return Result.success()

        if (!ShoppingProximityReminderManager.canNotifyForSupermarket(
                context = applicationContext,
                supermarketId = nearestSupermarket.id
            )
        ) {
            return Result.success()
        }

        val notificationSent = ShoppingProximityNotificationHelper.sendNotification(
            context = applicationContext,
            supermarketName = nearestSupermarket.name,
            pendingItems = pendingItems
        )

        if (notificationSent) {
            ShoppingProximityReminderManager.markNotified(
                context = applicationContext,
                supermarketId = nearestSupermarket.id
            )
        }

        return Result.success()
    }

    private fun hasLocationPermission(): Boolean {
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseLocationGranted = ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fineLocationGranted || coarseLocationGranted
    }

    private suspend fun getCurrentLocationOrNull(): Location? =
        suspendCancellableCoroutine { continuation ->
            val cancellationTokenSource = CancellationTokenSource()
            locationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                cancellationTokenSource.token
            )
                .addOnSuccessListener { location ->
                    if (continuation.isActive) {
                        continuation.resume(location)
                    }
                }
                .addOnFailureListener {
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }

            continuation.invokeOnCancellation {
                cancellationTokenSource.cancel()
            }
        }

    private suspend fun getLastKnownLocationOrNull(): Location? =
        suspendCoroutine { continuation ->
            locationClient.lastLocation
                .addOnSuccessListener { location ->
                    continuation.resume(location)
                }
                .addOnFailureListener {
                    continuation.resume(null)
                }
        }
}

private object ShoppingProximityNotificationHelper {
    private const val CHANNEL_ID = "shopping_proximity_alerts"
    private const val CHANNEL_NAME = "Promemoria Spesa"
    private const val CHANNEL_DESCRIPTION =
        "Notifiche quando sei vicino a un supermercato con articoli in lista"
    private const val NOTIFICATION_ID = 2001

    fun sendNotification(
        context: Context,
        supermarketName: String,
        pendingItems: List<String>
    ): Boolean {
        if (pendingItems.isEmpty()) {
            return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        ensureNotificationChannel(context)

        val notificationTitle = "Sei vicino a $supermarketName"
        val notificationBody = buildNotificationBody(pendingItems)
        val notificationIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(notificationTitle)
            .setContentText(notificationBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationBody))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        return true
    }

    private fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = CHANNEL_DESCRIPTION
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotificationBody(pendingItems: List<String>): String {
        val previewItems = pendingItems.take(3)
        val remainingCount = pendingItems.size - previewItems.size
        val previewText = previewItems.joinToString(separator = ", ")
        return if (remainingCount > 0) {
            "Ti mancano: $previewText e altri $remainingCount articoli."
        } else {
            "Ti mancano: $previewText."
        }
    }
}
