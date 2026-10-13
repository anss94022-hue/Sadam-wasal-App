package com.wasal.app.data.model

data class Message(
    val id: String,
    val content: String,
    val isMine: Boolean,
    val time: String = "",
    val type: String = "text",
    val status: MessageStatus = MessageStatus.SENT
)

enum class MessageStatus {
    SENDING,
    SENT,
    DELIVERED,
    READ,
    FAILED
}
