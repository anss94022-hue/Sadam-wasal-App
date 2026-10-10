
package com.wasal.app.data.repository

import com.wasal.app.data.model.Contact
import com.wasal.app.data.network.CoreApiClient
import com.wasal.app.data.network.ServerConfig
import org.json.JSONObject
import java.io.IOException

class ContactsRepository {

    fun findUserByWasalCode(
        wasalCode: String,
        accessToken: String
    ): Result<Contact> {
        val code = wasalCode.trim()

        if (!code.matches(Regex("^[A-Za-z0-9]{15}$"))) {
            return Result.failure(
                IllegalArgumentException(
                    "رمز وصال يجب أن يتكون من 15 حرفًا أو رقمًا."
                )
            )
        }

        return try {
            val path =
                "${ServerConfig.USERS_ENDPOINT}/search/wasal-code" +
                "?wasalCode=$code"

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
                            when (error) {
                                "USER_NOT_FOUND" ->
                                    "لم يتم العثور على مستخدم بهذا الرمز."
                                "INVALID_WASAL_CODE" ->
                                    "رمز وصال غير صالح."
                                else ->
                                    "تعذر البحث عن المستخدم. رمز الاستجابة: ${response.code}"
                            }
                        )
                    )
                }

                val root = JSONObject(body)
                val user = root.optJSONObject("user")
                    ?: return Result.failure(
                        IOException("لم يرجع الخادم بيانات المستخدم.")
                    )

                val id = user.optString("id")
                val name = user.optString(
                    "displayName",
                    "مستخدم وصال"
                )
                val returnedCode = user.optString(
                    "wasalCode",
                    code
                )

                if (id.isBlank()) {
                    return Result.failure(
                        IOException("معرّف المستخدم غير موجود.")
                    )
                }

                Result.success(
                    Contact(
                        id = id,
                        name = name.ifBlank { "مستخدم وصال" },
                        wasalCode = returnedCode
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun createConversation(
        wasalCode: String,
        accessToken: String
    ): Result<String> {
        val code = wasalCode.trim()

        if (!code.matches(Regex("^[A-Za-z0-9]{15}$"))) {
            return Result.failure(
                IllegalArgumentException("رمز وصال غير صالح.")
            )
        }

        return try {
            val payload = JSONObject()
                .put("wasalCode", code)
                .toString()

            CoreApiClient.execute(
                CoreApiClient.post(
                    ServerConfig.CONVERSATIONS_ENDPOINT,
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
                            when (error) {
                                "USER_NOT_FOUND" ->
                                    "لم يتم العثور على مستخدم بهذا الرمز."
                                "CANNOT_CHAT_WITH_SELF" ->
                                    "لا يمكنك إنشاء محادثة مع نفسك."
                                "INVALID_WASAL_CODE" ->
                                    "رمز وصال غير صالح."
                                else ->
                                    "تعذرت إضافة جهة الاتصال. رمز الاستجابة: ${response.code}"
                            }
                        )
                    )
                }

                val root = JSONObject(body)
                val conversation = root.optJSONObject("conversation")
                    ?: return Result.failure(
                        IOException("لم يرجع الخادم بيانات المحادثة.")
                    )

                val conversationId = conversation.optString("id")

                if (conversationId.isBlank()) {
                    return Result.failure(
                        IOException("معرّف المحادثة غير موجود.")
                    )
                }

                Result.success(conversationId)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
