package com.wasal.app.data.repository

import com.wasal.app.data.model.Message
import com.wasal.app.data.network.CoreApiClient
import com.wasal.app.data.network.ServerConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class MessagesRepository {
    fun getMessages(conversationId: String, accessToken: String, limit: Int = 50): Result<List<Message>> {
        if (conversationId.isBlank()) return Result.failure(IllegalArgumentException("معرّف المحادثة مطلوب."))
        return try {
            val safeLimit = limit.coerceIn(1, 100)
            val path = ServerConfig.MESSAGES_ENDPOINT + "/conversation/" + conversationId + "?limit=" + safeLimit
            val request = CoreApiClient.get(path, accessToken)
            CoreApiClient.execute(request).use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) return Result.failure(IOException("تعذر تحميل الرسائل. رمز الاستجابة: ${response.code}"))
                val root = JSONObject(body)
                val array = when { root.opt("messages") is JSONArray -> root.getJSONArray("messages"); root.opt("data") is JSONArray -> root.getJSONArray("data"); else -> JSONArray() }
                val result = mutableListOf<Message>()
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    result.add(Message(id = item.optString("id", item.optString("messageId", index.toString())), content = item.optString("content", item.optString("text", "")), isMine = item.optBoolean("isMine", item.optBoolean("isOwn", false)), time = item.optString("createdAt", item.optString("time", "")), type = item.optString("type", "text")))
                }
                Result.success(result)
            }
        } catch (e: Exception) { Result.failure(e) }
    }

    fun sendTextMessage(conversationId: String, text: String, accessToken: String): Result<Message> {
        val cleanText = text.trim()
        if (conversationId.isBlank()) return Result.failure(IllegalArgumentException("معرّف المحادثة مطلوب."))
        if (cleanText.isBlank()) return Result.failure(IllegalArgumentException("لا يمكن إرسال رسالة فارغة."))
        return try {
            val payload = JSONObject().put("conversationId", conversationId).put("type", "text").put("content", cleanText).toString()
            val request = CoreApiClient.post(ServerConfig.MESSAGES_ENDPOINT + "/", payload, accessToken)
            CoreApiClient.execute(request).use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) return Result.failure(IOException("فشل إرسال الرسالة. رمز الاستجابة: ${response.code}"))
                val root = if (body.isNotBlank()) JSONObject(body) else JSONObject()
                val item = if (root.has("message") && !root.isNull("message")) root.getJSONObject("message") else root
                Result.success(Message(id = item.optString("id", item.optString("messageId", "")), content = item.optString("content", cleanText), isMine = true, time = item.optString("createdAt", item.optString("time", "")), type = item.optString("type", "text")))
            }
        } catch (e: Exception) { Result.failure(e) }
    }
}
