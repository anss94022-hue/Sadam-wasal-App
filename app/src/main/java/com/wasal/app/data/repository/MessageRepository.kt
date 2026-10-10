
package com.wasal.app.data.repository

import com.wasal.app.data.local.AuthDataStore
import com.wasal.app.data.model.Message
import com.wasal.app.data.network.CoreApiClient
import com.wasal.app.data.network.ServerConfig
import org.json.JSONObject
import java.io.IOException

class MessageRepository(
    authDataStore: AuthDataStore
) {
    private val apiClient =
        AuthenticatedApiClient(authDataStore)

    suspend fun getMessages(
        conversationId: String,
        accessToken: String,
        currentUserId: String,
        limit: Int = 50
    ): Result<List<Message>> {
        if (conversationId.isBlank() || accessToken.isBlank()) {
            return Result.failure(
                IllegalArgumentException(
                    "معرّف المحادثة أو رمز الدخول غير موجود."
                )
            )
        }

        val safeLimit = limit.coerceIn(1, 100)
        val path =
            "${ServerConfig.MESSAGES_ENDPOINT}/conversation/" +
                "$conversationId?limit=$safeLimit"

        val response = apiClient.execute { token ->
            CoreApiClient.get(path, token)
        }.getOrElse {
            return Result.failure(it)
        }

        if (!response.isSuccessful) {
            val error = runCatching {
                JSONObject(response.body).optString("error")
            }.getOrDefault("")

            return Result.failure(
                IOException(
                    when (response.code) {
                        401 -> "انتهت جلسة الدخول. تحقق من الجلسة."
                        403 -> "ليست لديك صلاحية عرض هذه المحادثة."
                        else -> error.ifBlank {
                            "تعذر تحميل الرسائل. رمز الاستجابة: ${response.code}"
                        }
                    }
                )
            )
        }

        return try {
            val root = JSONObject(response.body)
            val array = root.optJSONArray("messages")
                ?: return Result.failure(
                    IOException("لم يرجع الخادم قائمة الرسائل.")
                )

            val messages = mutableListOf<Message>()

            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id")
                if (id.isBlank()) continue

                val senderId = item.optString("senderId")
                val content = if (item.isNull("content")) {
                    ""
                } else {
                    item.optString("content", "")
                }

                val createdAt = item.optString("createdAt", "")
                val time = createdAt
                    .replace("T", " ")
                    .substringBefore(".")
                    .removeSuffix("Z")

                messages.add(
                    Message(
                        id = id,
                        content = content,
                        isMine = senderId == currentUserId,
                        time = time,
                        type = item.optString("type", "text")
                    )
                )
            }

            Result.success(messages)
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun sendTextMessage(
        conversationId: String,
        content: String,
        accessToken: String,
        currentUserId: String
    ): Result<Message> {
        val cleanContent = content.trim()

        if (conversationId.isBlank() || accessToken.isBlank()) {
            return Result.failure(
                IllegalArgumentException(
                    "معرّف المحادثة أو رمز الدخول غير موجود."
                )
            )
        }

        if (cleanContent.isBlank()) {
            return Result.failure(
                IllegalArgumentException("لا يمكن إرسال رسالة فارغة.")
            )
        }

        val payload = JSONObject()
            .put("conversationId", conversationId)
            .put("type", "text")
            .put("content", cleanContent)
            .toString()

        val response = apiClient.execute { token ->
            CoreApiClient.post(
                ServerConfig.MESSAGES_ENDPOINT,
                payload,
                token
            )
        }.getOrElse {
            return Result.failure(it)
        }

        if (!response.isSuccessful) {
            val error = runCatching {
                JSONObject(response.body).optString("error")
            }.getOrDefault("")

            return Result.failure(
                IOException(
                    when (response.code) {
                        401 -> "انتهت جلسة الدخول. تحقق من الجلسة."
                        403 -> "لا يمكنك الإرسال إلى هذه المحادثة."
                        else -> error.ifBlank {
                            "تعذر إرسال الرسالة. رمز الاستجابة: ${response.code}"
                        }
                    }
                )
            )
        }

        return parseSentMessage(
            response.body,
            cleanContent,
            "text",
            currentUserId
        )
    }

    suspend fun sendMediaMessage(
        conversationId: String,
        media: UploadedMedia,
        accessToken: String,
        currentUserId: String
    ): Result<Message> {
        if (conversationId.isBlank() || accessToken.isBlank()) {
            return Result.failure(
                IllegalArgumentException(
                    "معرّف المحادثة أو رمز الدخول غير موجود."
                )
            )
        }

        if (media.url.isBlank()) {
            return Result.failure(
                IllegalArgumentException("رابط المرفق غير موجود.")
            )
        }

        val type = when {
            media.mimeType.startsWith("image/") -> "image"
            media.mimeType.startsWith("video/") -> "video"
            media.mimeType.startsWith("audio/") -> "audio"
            else -> "file"
        }

        val payload = JSONObject()
            .put("conversationId", conversationId)
            .put("type", type)
            .put("content", media.url)
            .toString()

        val response = apiClient.execute { token ->
            CoreApiClient.post(
                ServerConfig.MESSAGES_ENDPOINT,
                payload,
                token
            )
        }.getOrElse {
            return Result.failure(it)
        }

        if (!response.isSuccessful) {
            val error = runCatching {
                JSONObject(response.body).optString("error")
            }.getOrDefault("")

            return Result.failure(
                IOException(
                    when (response.code) {
                        401 -> "انتهت جلسة الدخول. تحقق من الجلسة."
                        403 -> "لا يمكنك إرسال مرفق إلى هذه المحادثة."
                        else -> error.ifBlank {
                            "تعذر إرسال المرفق. رمز الاستجابة: ${response.code}"
                        }
                    }
                )
            )
        }

        return parseSentMessage(
            response.body,
            media.url,
            type,
            currentUserId
        )
    }

    private fun parseSentMessage(
        responseBody: String,
        fallbackContent: String,
        fallbackType: String,
        currentUserId: String
    ): Result<Message> {
        return try {
            val root = JSONObject(responseBody)
            val item = root.optJSONObject("message")
                ?: return Result.failure(
                    IOException("لم يرجع الخادم بيانات الرسالة.")
                )

            val id = item.optString("id")
            if (id.isBlank()) {
                return Result.failure(
                    IOException("لم يرجع الخادم معرّف الرسالة.")
                )
            }

            val createdAt = item.optString("createdAt", "")
            val time = createdAt
                .replace("T", " ")
                .substringBefore(".")
                .removeSuffix("Z")

            Result.success(
                Message(
                    id = id,
                    content = item.optString("content", fallbackContent),
                    isMine = item.optString("senderId") == currentUserId,
                    time = time,
                    type = item.optString("type", fallbackType)
                )
            )
        } catch (error: Exception) {
            Result.failure(error)
        }
    }
}
