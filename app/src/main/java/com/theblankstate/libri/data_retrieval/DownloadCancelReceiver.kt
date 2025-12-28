package com.theblankstate.libri.data_retrieval

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * BroadcastReceiver to handle download cancel actions from notifications
 */
class DownloadCancelReceiver : BroadcastReceiver() {
    
    companion object {
        private var cancelCallback: ((Int) -> Unit)? = null
        
        /**
         * Set the callback to be invoked when a download is cancelled
         */
        fun setCancelCallback(callback: (Int) -> Unit) {
            cancelCallback = callback
        }
        
        /**
         * Clear the cancel callback
         */
        fun clearCancelCallback() {
            cancelCallback = null
        }
    }
    
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == DownloadNotificationManager.ACTION_CANCEL_DOWNLOAD) {
            val bookId = intent.getIntExtra(DownloadNotificationManager.EXTRA_BOOK_ID, -1)
            if (bookId != -1) {
                cancelCallback?.invoke(bookId)
            }
        }
    }
}
