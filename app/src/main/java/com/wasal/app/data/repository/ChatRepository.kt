package com.wasal.app.data.repository

import com.wasal.app.data.model.Conversation
import com.wasal.app.data.network.CoreApiClient
import com.wasal.app.data.network.ServerConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class ChatRepository {

    /**
     * جلب قائمة محادثات المستخدم من خادم وصال.
     *
     * يجب استدعاء هذه الدالة من خيط خلفي،
     * وليس مباشرة من واجهة المستخدم.
     */
    fun getConversations(
        accessToken: String
    ): Result<List<Conversation>> {

        if (accessToken.isBlank()) {
            return Result.failure(
                IllegalArgumentException(
                    "رمز الدخول غير موجود. يرجى تسجيل الدخول مجددًا."
                )
            )
        }

        return try {
            val request = CoreApiClient.get(
                ServerConfig.CONVERSATIONS_ENDPOINT,
                accessToken
            )

            CoreApiClient.execute(request).use { response ->
                val body = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    val error = runCatching {
                        JSONObject(body).optString("error")
                    }.getOrDefault("")

                    val message = when (response.code) {
                        401 -> "انتهت جلسة الدخول. يرجى تسجيل الدخول مجددًا."
                        403 -> "ليست لديك صلاحية جلب المحادثات."
                        else -> error.ifBlank {
                            "تعذر تحميل المحادثات. رمز الاستجابة: ${response.code}"
                        }
                    }

                    return Result.failure(IOException(message))
                }

                val root = JSONObject(body)

                val conversationsArray =
                    root.optJSONArray("conversations")
                        ?: root.optJSONObject("data")
                            ?.optJSONArray("conversations")
                        ?: JSONArray()

                val conversations =
                    mutableListOf<Conversation>()

                for (index in 0 until conversationsArray.length()) {
                    val item =
                        conversationsArray.optJSONObject(index)
                            ?: continue

                    val id = item.optString("id").trim()

                    if (id.isBlank()) {
                        continue
                    }

                    val title = item.optString(
                        "title",
                        item.optString("name", "محادثة وصال")
                    ).ifBlank {
                        "محادثة وصال"
                    }

                    val lastMessage = if (
                        item.isNull("lastMessage")
                    ) {
                        ""
                    } else {
                        item.optString("lastMessage", "")
                    }

                    val time = when {
                        !item.isNull("lastMessageAt") ->
                            item.optString("lastMessageAt", "")

                        !item.isNull("updatedAt") ->
                            item.optString("updatedAt", "")

                        else -> ""
                    }

                    conversations.add(
                        Conversation(
                            id = id,
                            title = title,
                            lastMessage = lastMessage,
                            time = time,
                            unreadCount = item.optInt(
                                "unreadCount",
                                0
                            )
                        )
                    )
                }

                Result.success(conversations)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
