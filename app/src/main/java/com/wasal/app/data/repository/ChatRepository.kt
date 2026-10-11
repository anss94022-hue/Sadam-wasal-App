
package com.wasal.app.data.repository

import com.wasal.app.data.local.AuthDataStore
import com.wasal.app.data.model.Conversation
import com.wasal.app.data.network.CoreApiClient
import com.wasal.app.data.network.ServerConfig
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class ChatRepository(
    private val authDataStore: AuthDataStore
) {
    private val apiClient = AuthenticatedApiClient(authDataStore)

    private val preferences by lazy {
        authDataStore.appContext.getSharedPreferences(
            "wasal_conversation_cache",
            android.content.Context.MODE_PRIVATE
        )
    }

    suspend fun getConversations(
        accessToken: String
    ): Result<List<Conversation>> {
        val userId = authDataStore.userId.first().orEmpty()

        if (accessToken.isBlank()) {
            return cachedOrFailure(
                userId,
                IOException("رمز الدخول غير موجود.")
            )
        }

        val responseResult = apiClient.execute { token ->
            CoreApiClient.get(
                ServerConfig.CONVERSATIONS_ENDPOINT,
                token
            )
        }

        val response = responseResult.getOrElse { error ->
            return cachedOrFailure(userId, error)
        }

        if (!response.isSuccessful) {
            val error = runCatching {
                JSONObject(response.body).optString("error")
            }.getOrDefault("")

            val exception = IOException(
                when (response.code) {
                    401 -> "انتهت جلسة الدخول. تعذر تجديدها."
                    403 -> "ليست لديك صلاحية جلب المحادثات."
                    else -> error.ifBlank {
                        "تعذر تحميل المحادثات. رمز الاستجابة: ${response.code}"
                    }
                }
            )

            return cachedOrFailure(userId, exception)
        }

        return try {
            val root = JSONObject(response.body)
            val conversationsArray =
                root.optJSONArray("conversations")
                    ?: root.optJSONObject("data")
                        ?.optJSONArray("conversations")
                    ?: throw IOException(
                        "لم يرجع الخادم قائمة المحادثات."
                    )

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

            saveCache(userId, conversations)
            Result.success(conversations)
        } catch (error: Exception) {
            cachedOrFailure(userId, error)
        }
    }

    private fun cacheKey(userId: String): String {
        return "conversations_$userId"
    }

    private fun saveCache(
        userId: String,
        conversations: List<Conversation>
    ) {
        if (userId.isBlank()) return

        val array = JSONArray()

        conversations.forEach { conversation ->
            array.put(
                JSONObject()
                    .put("id", conversation.id)
                    .put("title", conversation.title)
                    .put("lastMessage", conversation.lastMessage)
                    .put("time", conversation.time)
                    .put("unreadCount", conversation.unreadCount)
            )
        }

        preferences.edit()
            .putString(cacheKey(userId), array.toString())
            .apply()
    }

    private fun readCache(
        userId: String
    ): List<Conversation>? {
        if (userId.isBlank()) return null

        val json = preferences.getString(
            cacheKey(userId),
            null
        ) ?: return null

        return try {
            val array = JSONArray(json)
            val conversations = mutableListOf<Conversation>()

            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id").trim()

                if (id.isBlank()) continue

                conversations.add(
                    Conversation(
                        id = id,
                        title = item.optString(
                            "title",
                            "محادثة وصال"
                        ).ifBlank { "محادثة وصال" },
                        lastMessage = item.optString(
                            "lastMessage",
                            ""
                        ),
                        time = item.optString("time", ""),
                        unreadCount = item.optInt("unreadCount", 0)
                    )
                )
            }

            conversations
        } catch (_: Exception) {
            null
        }
    }

    private fun cachedOrFailure(
        userId: String,
        error: Throwable
    ): Result<List<Conversation>> {
        val cached = readCache(userId)

        return if (cached != null) {
            Result.success(cached)
        } else {
            Result.failure(error)
        }
    }
}
