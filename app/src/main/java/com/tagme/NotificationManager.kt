package com.tagme

import android.Manifest
import android.app.NotificationChannel
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.sql.Timestamp
import android.app.NotificationManager as SystemNotificationManager

class NotificationManager(private val context: Context) {
    private val messagesMap = HashMap<Int, MutableList<NotificationCompat.MessagingStyle.Message>>()
    private var iconCompat: IconCompat? = null
    fun showNewMessageNotification(
        nickname: String,
        message: String,
        conversationId: Int,
        timestamp: Timestamp,
        api: API,
        picId: Int
    ) {
        val intent = Intent(context, MapActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("started_from_notification", true)
            putExtra("conversationId", conversationId)
        }

        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        CoroutineScope(Dispatchers.Main).launch {
            val bitmap = api.getPictureData(picId)
            if (bitmap != null) {
                iconCompat = bitmapToIconCompat(bitmap)
            }

            val notificationManager = NotificationManagerCompat.from(context)
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return@launch
            }
            val sender = Person.Builder()
                .setName(nickname)
                .setIcon(iconCompat)
                .build()
            val timestampMillis = timestamp.time
            val messagingStyleMessage = NotificationCompat.MessagingStyle.Message(
                message,
                timestampMillis,
                sender
            )
            val messages = messagesMap.getOrPut(conversationId) { mutableListOf() }
            messages.add(messagingStyleMessage)

            val messagingStyle = NotificationCompat.MessagingStyle(sender)
            messages.forEach { messagingStyle.addMessage(it) }
            val notificationBuilder = NotificationCompat.Builder(context, MESSAGE_CHANNEL_ID)
                .setSmallIcon(R.drawable.tagme_logo)
                .setStyle(messagingStyle)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            notificationManager.notify(conversationId, notificationBuilder.build())
        }
    }
    fun clearMessages(conversationId: Int) {
        messagesMap.remove(conversationId)
    }
    fun showNewFriendRequestNotification(nickname: String, requestId: Int) {
        val intent = Intent(context, MapActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("started_from_notification", true)
            putExtra("requestId", requestId)
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
fun bitmapToIconCompat(bitmap: Bitmap): IconCompat {
    return IconCompat.createWithBitmap(bitmap)
}