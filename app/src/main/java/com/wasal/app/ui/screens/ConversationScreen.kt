
package com.wasal.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wasal.app.data.model.Message

private val ChatBackground = Color(0xFF0B1020)
private val ChatHeader = Color(0xFF101B3A)
private val ChatBubbleMine = Color(0xFF344E9A)
private val ChatBubbleOther = Color(0xFF172342)
private val ChatAccent = Color(0xFF6C8CFF)
private val ChatText = Color(0xFFF4F6FF)
private val ChatMuted = Color(0xFF9AA7C7)

@Composable
fun ConversationScreen(
    title: String,
    messages: List<Message> = emptyList(),
    onBack: () -> Unit = {},
    onSendMessage: (String) -> Unit = {},
    onAttachmentClick: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    isLoading: Boolean = false,
    isSending: Boolean = false,
    statusMessage: String? = null
) {
    var draft by remember { mutableStateOf("") }

    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ChatBackground)
                .imePadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ChatHeader)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "رجوع",
                        tint = ChatText
                    )
                }

                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape,
                    color = ChatBubbleOther
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            title.trim().take(1).ifBlank { "و" },
                            color = ChatAccent,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.width(10.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        color = ChatText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "محادثة وصال",
                        color = ChatMuted,
                        fontSize = 12.sp
                    )
                }

                IconButton(onClick = onMoreClick) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "خيارات",
                        tint = ChatText
                    )
                }
            }

            if (statusMessage != null) {
                Text(
                    text = statusMessage,
                    color = ChatAccent,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ChatHeader)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }

            when {
                isLoading && messages.isEmpty() -> {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = ChatAccent)
                            Spacer(Modifier.size(12.dp))
                            Text(
                                "جارٍ تحميل الرسائل...",
                                color = ChatMuted
                            )
                        }
                    }
                }

                messages.isEmpty() -> {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "ابدأ محادثتك",
                                color = ChatText,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                "اكتب رسالة أو أرفق صورة أو فيديو",
                                color = ChatMuted,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        reverseLayout = true,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = messages,
                            key = { it.id }
                        ) { message ->
                            MessageBubble(message)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ChatHeader)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onAttachmentClick,
                    enabled = !isSending
                ) {
                    Icon(
                        Icons.Default.AttachFile,
                        contentDescription = "إرفاق صورة أو فيديو أو ملف",
                        tint = ChatAccent
                    )
                }

                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            ChatBubbleOther,
                            RoundedCornerShape(22.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    enabled = !isSending,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = ChatText,
                        fontSize = 15.sp
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(
                        ChatAccent
                    ),
                    decorationBox = { innerTextField ->
                        Box {
                            if (draft.isEmpty()) {
                                Text(
                                    "اكتب رسالتك...",
                                    color = ChatMuted,
                                    fontSize = 14.sp
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                Spacer(Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        val text = draft.trim()
                        if (text.isNotEmpty() && !isSending) {
                            onSendMessage(text)
                            draft = ""
                        }
                    },
                    enabled = draft.isNotBlank() && !isSending
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = ChatAccent,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "إرسال",
                            tint = if (draft.isNotBlank()) {
                                ChatAccent
                            } else {
                                ChatMuted
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: Message) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isMine) {
            Arrangement.Start
        } else {
            Arrangement.End
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(
                    if (message.isMine) ChatBubbleMine else ChatBubbleOther,
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (message.isMine) 4.dp else 16.dp,
                        bottomEnd = if (message.isMine) 16.dp else 4.dp
                    )
                )
                .padding(horizontal = 13.dp, vertical = 9.dp)
        ) {
            when (message.type.lowercase()) {
                "image" -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = null,
                            tint = ChatAccent
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "صورة مرفقة",
                            color = ChatText,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    AttachmentLink(message.content)
                }

                "video" -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.PlayCircle,
                            contentDescription = null,
                            tint = ChatAccent
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "فيديو مرفق",
                            color = ChatText,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    AttachmentLink(message.content)
                }

                "audio" -> {
                    Text("ملف صوتي مرفق", color = ChatText)
                    AttachmentLink(message.content)
                }

                "file" -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            tint = ChatAccent
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("ملف مرفق", color = ChatText)
                    }
                    AttachmentLink(message.content)
                }

                else -> {
                    Text(
                        message.content,
                        color = ChatText,
                        fontSize = 15.sp
                    )
                }
            }

            if (message.time.isNotBlank()) {
                Spacer(Modifier.size(4.dp))
                Text(
                    message.time,
                    color = ChatMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
private fun AttachmentLink(url: String) {
    if (url.isNotBlank()) {
        Text(
            text = url,
            color = ChatAccent,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
