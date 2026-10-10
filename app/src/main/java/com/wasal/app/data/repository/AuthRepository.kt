package com.wasal.app.data.repository

import com.wasal.app.data.local.AuthDataStore
import com.wasal.app.data.network.CoreApiClient
import com.wasal.app.data.network.ServerConfig
import org.json.JSONObject
import java.io.IOException

class AuthRepository(
    private val authDataStore: AuthDataStore
) {

    /**
     * إنشاء حساب جديد في وصال.
     *
     * يجب استدعاء الدالة من coroutine،
     * لأنها تحفظ الجلسة باستخدام DataStore.
     */
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

                        "WASAL_CODE_GENERATION_FAILED" ->
                            "تعذر إنشاء رمز وصال. حاول مرة أخرى."

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
                    userId.isBlank() ||
                    wasalCode.isBlank()
                ) {
                    return Result.failure(
                        IOException("بيانات الحساب أو الجلسة غير مكتملة.")
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
