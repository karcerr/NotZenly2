package com.tagme.domain.models

import java.sql.Timestamp

data class MessageData(
    val messageId: Int,
    val authorId: Int,
    var text: String?,
    val imageId: Int?,
    val timestamp: Timestamp,
    var read: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MessageData) return false

        return messageId == other.messageId &&
                authorId == other.authorId &&
                text == other.text &&
                imageId == other.imageId &&
                timestamp == other.timestamp &&
                read == other.read
    }

    override fun hashCode(): Int {
        return arrayOf(messageId, authorId, text, imageId, timestamp, read).contentHashCode()
    }
}