package com.tagme

import android.Manifest
import android.app.NotificationChannel
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.app.NotificationManager as SystemNotificationManager

class NotificationManager(private val context: Context) {
    fun showNewMessageNotification(nickname: String, message: String, conversationId: Int) {
        val intent = Intent(context, LogInActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
          //  putExtra("conversationId", conversationId)
        }

        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(context, MESSAGE_CHANNEL_ID)
            .setSmallIcon(R.drawable.tagme_logo)
            .setContentTitle(context.getString(R.string.new_message_format, nickname))
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationManager = NotificationManagerCompat.from(context)
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notificationManager.notify(conversationId, notificationBuilder.build())
    }
    fun showNewFriendRequestNotification(nickname: String, requestId: Int) {
        val intent = Intent(context, LogInActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
           // putExtra("requestId", requestId)
        }

        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notificationBuilder = NotificationCompat.Builder(context, FRIEND_REQUEST_CHANNEL_ID)
            .setSmallIcon(R.drawable.tagme_logo)
            .setContentTitle(context.getString(R.string.new_friend_request))
            .setContentText(context.getString(R.string.new_friend_request_format, nickname))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationManager = NotificationManagerCompat.from(context)
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notificationManager.notify(requestId, notificationBuilder.build())
    }
    fun createNotificationChannels() {
        val messageChannel = NotificationChannel(
            MESSAGE_CHANNEL_ID, "Message Notifications", SystemNotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for new messages"
        }

        val friendRequestChannel = NotificationChannel(
            FRIEND_REQUEST_CHANNEL_ID, "Friend Request Notifications", SystemNotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for new friend requests"
        }

        val notificationManager: SystemNotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as SystemNotificationManager
        notificationManager.createNotificationChannel(messageChannel)
        notificationManager.createNotificationChannel(friendRequestChannel)
    }

    companion object {
        const val MESSAGE_CHANNEL_ID = "MESSAGE_CHANNEL_ID"
        const val FRIEND_REQUEST_CHANNEL_ID = "FRIEND_REQUEST_CHANNEL_ID"
    }
}
