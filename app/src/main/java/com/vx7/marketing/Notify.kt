package com.vx7.marketing

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Notifier {
    const val CHANNEL = "vx7_activity"
    const val EXTRA_ROUTE = "route"

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, "VX7 activity", NotificationManager.IMPORTANCE_DEFAULT)
            ch.description = "Confirmations for saved products, collections and backups"
            ctx.getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
        }
    }

    fun permissionGranted(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** True only when the system will really show our notifications (permission + not blocked in system settings). */
    fun canPost(ctx: Context): Boolean = permissionGranted(ctx) && NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    /** Returns true only if a system notification was actually handed to the system. Never throws. */
    fun post(ctx: Context, title: String, text: String, route: String): Boolean {
        return try {
            if (!canPost(ctx)) return false
            ensureChannel(ctx)
            val intent = Intent(ctx, MainActivity::class.java).apply {
                putExtra(EXTRA_ROUTE, route)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
            val pi = PendingIntent.getActivity(ctx, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_vx7)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
            NotificationManagerCompat.from(ctx).notify(id, n)
            true
        } catch (e: SecurityException) {
            false
        } catch (e: Exception) {
            false
        }
    }
}
