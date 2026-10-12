package com.wasal.app.data.repository

import com.wasal.app.data.local.AuthDataStore
import com.wasal.app.data.network.CoreApiClient
import com.wasal.app.data.network.ServerConfig
import org.json.JSONObject
import java.io.IOException

class AuthRepository(
    private val authDataStore: AuthDataStore
) {

    suspend fun createAccount(
        displayName: String = "مستخدم وصال"
    ): Result<String> {
        val cleanName = displayName.trim()
            .ifBlank { "مستخدم وصال" }

        return try {
            val payload = JSONObject()
                .put("displayName", cleanName)
                .toString()

            val request = CoreApiClient.post(
                ServerConfig.AUTH_ENDPOINT + "/register",
                payload
            )

            val accessToken: String
            val refreshToken: String
            val userId: String
            val wasalCode: String

            CoreApiClient.execute(request).use { response ->
                val body = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    val error = runCatching {
                        JSONObject(body).optString("error")
                    }.getOrDefault("")

                    val message = when (error) {
                        "DISPLAY_NAME_REQUIRED" ->
                            "يرجى إدخال اسم العرض."

                        "DISPLAY_NAME_TOO_LONG" ->
                            "اسم العرض أطول من الحد المسموح."

                        "WASAL_CODE_GENERATION_FAILED" ->
                            "تعذر إنشاء رمز وصال. حاول مرة أخرى."

                        "ACCOUNT_CONFLICT" ->
                            "حدث تعارض أثناء إنشاء الحساب. حاول مرة أخرى."

                        "ACCOUNT_CREATION_FAILED" ->
                            "فشل إنشاء الحساب في الخادم. راجع سجلات الخادم لمعرفة السبب."

                        else ->
                            "تعذر إنشاء الحساب. رمز الاستجابة: ${response.code}"
                    }

                    return Result.failure(IOException(message))
                }

                val root = JSONObject(body)

                val user = root.optJSONObject("user")
                    ?: return Result.failure(
                        IOException("لم يرجع الخادم بيانات المستخدم.")
                    )

                val tokens = root.optJSONObject("tokens")
                    ?: return Result.failure(
                        IOException("لم يرجع الخادم رموز الجلسة.")
                    )

                accessToken = tokens.optString("accessToken")
                refreshToken = tokens.optString("refreshToken")
                userId = user.optString("id")
                wasalCode = user.optString("wasalCode")

                if (
                    accessToken.isBlank() ||
                    refreshToken.isBlank() ||
                    userId.isBlank()
                ) {
                    return Result.failure(
                        IOException("بيانات الحساب أو الجلسة غير مكتملة.")
                    )
                }

                if (!wasalCode.matches(Regex("^[A-Za-z0-9]{15}$"))) {
                    return Result.failure(
                        IOException(
                            "الخادم أعاد رمز وصال غير صالح؛ يجب أن يتكون من 15 حرفًا أو رقمًا."
                        )
                    )
                }
            }

            authDataStore.saveSession(
                accessToken = accessToken,
                refreshToken = refreshToken,
                userId = userId
            )

            Result.success(wasalCode)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
