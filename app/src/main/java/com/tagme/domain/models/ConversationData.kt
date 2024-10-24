package com.tagme.domain.models

data class ConversationData(
    val conversationID: Int,
    var userData: UserData,
    var messages: MutableList<MessageData>,
    var lastMessage: MessageData?,
    var pinned: Boolean,
    var markedUnread: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ConversationData) return false

        return conversationID == other.conversationID &&
                userData == other.userData &&
                messages == other.messages &&
                lastMessage == other.lastMessage &&
                pinned == other.pinned &&
                markedUnread == other.markedUnread
    }

    override fun hashCode(): Int {
        return arrayOf(conversationID, userData, messages, lastMessage, pinned, markedUnread).contentHashCode()
    }
}