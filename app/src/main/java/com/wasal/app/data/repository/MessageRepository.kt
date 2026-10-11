
package com.wasal.app.data.repository

import android.content.Context
import com.wasal.app.data.local.AuthDataStore
import com.wasal.app.data.model.Message
import com.wasal.app.data.network.CoreApiClient
import com.wasal.app.data.network.ServerConfig
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class MessageRepository(
    private val authDataStore: AuthDataStore
) {
    private val apiClient =
        AuthenticatedApiClient(authDataStore)

    private val preferences by lazy {
        authDataStore.appContext.getSharedPreferences(
            "wasal_message_cache",
            Context.MODE_PRIVATE
        )
    }

    suspend fun getMessages(
        conversationId: String,
        accessToken: String,
        currentUserId: String,
        limit: Int = 50
    ): Result<List<Message>> {
        if (conversationId.isBlank()) {
            return Result.failure(
                IllegalArgumentException(
                    "معرّف المحادثة غير موجود."
                )
            )
        }

        if (currentUserId.isBlank()) {
            return Result.failure(
                IllegalArgumentException(
                    "معرّف المستخدم غير موجود."
                )
            )
        }

        if (accessToken.isBlank()) {
            return cachedOrFailure(
                conversationId,
                currentUserId,
                IOException("رمز الدخول غير موجود.")
            )
        }

        val safeLimit = limit.coerceIn(1, 100)
        val path =
            "${ServerConfig.MESSAGES_ENDPOINT}/conversation/" +
                "$conversationId?limit=$safeLimit"

        val responseResult = apiClient.execute { token ->
            CoreApiClient.get(path, token)
        }

        val response = responseResult.getOrElse { error ->
            return cachedOrFailure(
                conversationId,
                currentUserId,
                error
            )
        }

        if (!response.isSuccessful) {
            val error = runCatching {
                JSONObject(response.body).optString("error")
            }.getOrDefault("")

            val exception = IOException(
                when (response.code) {
                    401 -> "انتهت جلسة الدخول."
                    403 -> "ليست لديك صلاحية عرض هذه المحادثة."
                    else -> error.ifBlank {
                        "تعذر تحميل الرسائل. رمز الاستجابة: ${response.code}"
                    }
                }
            )

            return cachedOrFailure(
                conversationId,
                currentUserId,
                exception
            )
        }

        return try {
            val root = JSONObject(response.body)
            val array = root.optJSONArray("messages")
                ?: root.optJSONObject("data")
                    ?.optJSONArray("messages")
                ?: throw IOException(
                    "لم يرجع الخادم قائمة الرسائل."
                )

            val messages = mutableListOf<Message>()

            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index)
                    ?: continue

                val id = item.optString("id").trim()
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

            saveCache(
                conversationId,
                currentUserId,
                messages
            )

            Result.success(messages)
        } catch (error: Exception) {
            cachedOrFailure(
                conversationId,
                currentUserId,
                error
            )
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
                IllegalArgumentException(
                    "لا يمكن إرسال رسالة فارغة."
                )
            )
        }

        val payload = JSONObject()
            .put("conversationId", conversationId)
            .put("type", "text")
            .put("content", cleanContent)
            .toString()

        val responseResult = apiClient.execute { token ->
            CoreApiClient.post(
                ServerConfig.MESSAGES_ENDPOINT,
                payload,
                token
            )
        }

        val response = responseResult.getOrElse {
            return Result.failure(it)
        }

        if (!response.isSuccessful) {
            val error = runCatching {
                JSONObject(response.body).optString("error")
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

        val result = parseSentMessage(
            response.body,
            cleanContent,
            "text",
            currentUserId
        )

        result.onSuccess { message ->
            saveSentMessage(
                conversationId,
                currentUserId,
                message
            )
        }

        return result
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
                IllegalArgumentException(
                    "رابط المرفق غير موجود."
                )
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

        val responseResult = apiClient.execute { token ->
            CoreApiClient.post(
                ServerConfig.MESSAGES_ENDPOINT,
                payload,
                token
            )
        }

        val response = responseResult.getOrElse {
            return Result.failure(it)
        }

        if (!response.isSuccessful) {
            val error = runCatching {
                JSONObject(response.body).optString("error")
            }.getOrDefault("")

            return Result.failure(
                IOException(
                    when (response.code) {
                        401 -> "انتهت جلسة الدخول."
                        403 -> "لا يمكنك إرسال مرفق إلى هذه المحادثة."
                        else -> error.ifBlank {
                            "تعذر إرسال المرفق. رمز الاستجابة: ${response.code}"
                        }
                    }
                )
            )
        }

        val result = parseSentMessage(
            response.body,
            media.url,
            type,
            currentUserId
        )

        result.onSuccess { message ->
            saveSentMessage(
                conversationId,
                currentUserId,
                message
            )
        }

        return result
    }

    private fun cacheKey(
        conversationId: String,
        currentUserId: String
    ): String {
        return "messages_${currentUserId}_${conversationId}"
    }

    private fun saveCache(
        conversationId: String,
        currentUserId: String,
        messages: List<Message>
    ) {
        val array = JSONArray()

        messages.forEach { message ->
            array.put(
                JSONObject()
                    .put("id", message.id)
                    .put("content", message.content)
                    .put("isMine", message.isMine)
                    .put("time", message.time)
                    .put("type", message.type)
            )
        }

        preferences.edit()
            .putString(
                cacheKey(conversationId, currentUserId),
                array.toString()
            )
            .apply()
    }

    private fun readCache(
        conversationId: String,
        currentUserId: String
    ): List<Message>? {
        val json = preferences.getString(
            cacheKey(conversationId, currentUserId),
            null
        ) ?: return null

        return try {
            val array = JSONArray(json)
            val messages = mutableListOf<Message>()

            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index)
                    ?: continue

                val id = item.optString("id").trim()
                if (id.isBlank()) continue

                messages.add(
                    Message(
                        id = id,
                        content = item.optString("content", ""),
                        isMine = item.optBoolean("isMine", false),
                        time = item.optString("time", ""),
                        type = item.optString("type", "text")
                    )
                )
            }

            messages
        } catch (_: Exception) {
            null
        }
    }

    private fun cachedOrFailure(
        conversationId: String,
        currentUserId: String,
        error: Throwable
    ): Result<List<Message>> {
        val cached = readCache(
            conversationId,
            currentUserId
        )

        return if (cached != null) {
            Result.success(cached)
        } else {
            Result.failure(error)
        }
    }

    private fun saveSentMessage(
        conversationId: String,
        currentUserId: String,
        message: Message
    ) {
        val cached = readCache(
            conversationId,
            currentUserId
        ).orEmpty()

        if (cached.none { it.id == message.id }) {
            saveCache(
                conversationId,
                currentUserId,
                cached + message
            )
        }
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
                ?: throw IOException(
                    "لم يرجع الخادم بيانات الرسالة."
                )

            val id = item.optString("id")
            if (id.isBlank()) {
                throw IOException(
                    "لم يرجع الخادم معرّف الرسالة."
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
                    content = item.optString(
                        "content",
                        fallbackContent
                    ),
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
