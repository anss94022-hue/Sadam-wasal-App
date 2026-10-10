package com.wasal.app.data.repository

import com.wasal.app.data.local.AuthDataStore
import com.wasal.app.data.model.Conversation
import com.wasal.app.data.network.CoreApiClient
import com.wasal.app.data.network.ServerConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class ChatRepository(
    authDataStore: AuthDataStore
) {
    private val apiClient = AuthenticatedApiClient(authDataStore)

    suspend fun getConversations(
        accessToken: String
    ): Result<List<Conversation>> {
        if (accessToken.isBlank()) {
            return Result.failure(
                IllegalArgumentException("رمز الدخول غير موجود.")
            )
        }

        val response = apiClient.execute { token ->
            CoreApiClient.get(
                ServerConfig.CONVERSATIONS_ENDPOINT,
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
                        401 -> "انتهت جلسة الدخول. تعذر تجديدها."
                        403 -> "ليست لديك صلاحية جلب المحادثات."
                        else -> error.ifBlank {
                            "تعذر تحميل المحادثات. رمز الاستجابة: ${response.code}"
                        }
                    }
                )
            )
        }

        return try {
            val root = JSONObject(response.body)
            val conversationsArray =
                root.optJSONArray("conversations")
                    ?: root.optJSONObject("data")
                        ?.optJSONArray("conversations")
                    ?: JSONArray()

            val conversations = mutableListOf<Conversation>()

            for (index in 0 until conversationsArray.length()) {
                val item = conversationsArray.optJSONObject(index)
                    ?: continue

                val id = item.optString("id").trim()
                if (id.isBlank()) continue

                val title = item.optString(
                    "title",
                    item.optString("name", "محادثة وصال")
                ).ifBlank { "محادثة وصال" }

                val lastMessage = if (item.isNull("lastMessage")) {
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
                        unreadCount = item.optInt("unreadCount", 0)
                    )
                )
            }

            Result.success(conversations)
        } catch (error: Exception) {
            Result.failure(error)
        }
    }
}
