
package com.wasal.app.data.repository

import com.wasal.app.data.model.Message
import com.wasal.app.data.network.CoreApiClient
import com.wasal.app.data.network.ServerConfig
import org.json.JSONObject
import java.io.IOException

class MessageRepository {

    /**
     * جلب رسائل محادثة من خادم وصال.
     * استدعِ هذه الدالة من خيط خلفي.
     */
    fun getMessages(
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

        return try {
            val safeLimit = limit.coerceIn(1, 100)
            val path =
                "${ServerConfig.MESSAGES_ENDPOINT}/conversation/" +
                    "$conversationId?limit=$safeLimit"

            CoreApiClient.execute(
                CoreApiClient.get(path, accessToken)
            ).use { response ->
                val body = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    val error = runCatching {
                        JSONObject(body).optString("error")
                    }.getOrDefault("")

                    return Result.failure(
                        IOException(
                            when (response.code) {
                                401 -> "انتهت جلسة الدخول."
                                403 -> "ليست لديك صلاحية عرض هذه المحادثة."
                                else -> error.ifBlank {
                                    "تعذر تحميل الرسائل. رمز الاستجابة: ${response.code}"
                                }
                            }
                        )
                    )
                }

                val root = JSONObject(body)
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
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * إرسال رسالة نصية إلى خادم وصال.
     * استدعِ هذه الدالة من خيط خلفي.
     */
    fun sendTextMessage(
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

        return try {
            val payload = JSONObject()
                .put("conversationId", conversationId)
                .put("type", "text")
                .put("content", cleanContent)
                .toString()

            CoreApiClient.execute(
                CoreApiClient.post(
                    ServerConfig.MESSAGES_ENDPOINT,
                    payload,
                    accessToken
                )
            ).use { response ->
                val body = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    val error = runCatching {
                        JSONObject(body).optString("error")
                    }.getOrDefault("")

                    return Result.failure(
                        IOException(
                            when (response.code) {
                                401 -> "انتهت جلسة الدخول."
                                403 -> "لا يمكنك الإرسال إلى هذه المحادثة."
                                else -> error.ifBlank {
                                    "تعذر إرسال الرسالة. رمز الاستجابة: ${response.code}"
                                }
                            }
                        )
                    )
                }

                val root = JSONObject(body)
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
                        content = item.optString("content", cleanContent),
                        isMine = item.optString("senderId") == currentUserId,
                        time = time,
                        type = item.optString("type", "text")
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
