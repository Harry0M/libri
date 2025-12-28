package com.theblankstate.libri.data_retrieval

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Manages download notifications with progress and cancel support
 */
class DownloadNotificationManager(private val context: Context) {
    
    companion object {
        const val CHANNEL_ID = "download_channel"
        const val CHANNEL_NAME = "Downloads"
        const val CHANNEL_DESCRIPTION = "Book download notifications"
        const val ACTION_CANCEL_DOWNLOAD = "com.example.learncompose.CANCEL_DOWNLOAD"
        const val EXTRA_BOOK_ID = "book_id"
    }
    
    private val notificationManager = NotificationManagerCompat.from(context)
    
    init {
        createNotificationChannel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = CHANNEL_DESCRIPTION
                setSound(null, null)
            }
            
            val systemNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            systemNotificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Show download progress notification with cancel action
     */
    fun showDownloadProgress(
        bookId: Int,
        title: String,
        progress: Int,
        maxProgress: Int = 100
    ) {
        val cancelIntent = Intent(ACTION_CANCEL_DOWNLOAD).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_BOOK_ID, bookId)
        }
        
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context,
            bookId,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Downloading")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setProgress(maxProgress, progress, progress == 0)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancel",
                cancelPendingIntent
            )
            .setOnlyAlertOnce(true)
            .build()
        
        try {
            notificationManager.notify(bookId, notification)
        } catch (e: SecurityException) {
            // Handle missing notification permission
            e.printStackTrace()
        }
    }
    
    /**
     * Show download complete notification
     */
    fun showDownloadComplete(bookId: Int, title: String) {
        // Cancel any existing progress notification for this book first
        cancelNotification(bookId)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Download Complete")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            // Ensure the progress/ongoing state is cleared
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()
        
        try {
            notificationManager.notify(bookId, notification)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
    
    /**
     * Show download failed notification
     */
    fun showDownloadFailed(bookId: Int, title: String, error: String? = null) {
        // Cancel any existing progress notification for this book first
        cancelNotification(bookId)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Download Failed")
            .setContentText(error ?: title)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            // Ensure the progress/ongoing state is cleared
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()
        
        try {
            notificationManager.notify(bookId, notification)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
    
    /**
     * Cancel/dismiss notification
     */
    fun cancelNotification(bookId: Int) {
        Log.d("DownloadNotification", "Cancelling notification for book $bookId")
        notificationManager.cancel(bookId)
    }
    
    /**
     * Show download cancelled notification
     */
    fun showDownloadCancelled(bookId: Int, title: String) {
        // Cancel current notification
        cancelNotification(bookId)
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Download Cancelled")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            // Clear progress if any
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()
        
        try {
            notificationManager.notify(bookId, notification)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
}
