
package com.wasal.app.data.repository

import com.wasal.app.data.local.AuthDataStore
import com.wasal.app.data.network.CoreApiClient
import com.wasal.app.data.network.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException

class TokenAuthenticator(
    private val authDataStore: AuthDataStore
) {

    /**
     * طلب رمز دخول جديد باستخدام رمز التحديث المحفوظ.
     * يجب استدعاء هذه الدالة من coroutine.
     */
    suspend fun refreshAccessToken(): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val refreshToken =
                    authDataStore.refreshToken.first()

                if (refreshToken.isNullOrBlank()) {
                    return@withContext Result.failure(
                        IOException(
                            "رمز التحديث غير موجود. يلزم تسجيل الدخول مجددًا."
                        )
                    )
                }

                val payload = JSONObject()
                    .put("refreshToken", refreshToken)
                    .toString()

                val request = CoreApiClient.post(
                    ServerConfig.AUTH_ENDPOINT + "/refresh",
                    payload
                )

                CoreApiClient.execute(request).use { response ->
                    val body = response.body?.string().orEmpty()

                    if (!response.isSuccessful) {
                        val error = runCatching {
                            JSONObject(body).optString("error")
                        }.getOrDefault("")

                        return@withContext Result.failure(
                            IOException(
                                when (error) {
                                    "INVALID_OR_EXPIRED_REFRESH_TOKEN" ->
                                        "انتهت صلاحية الجلسة. يلزم تسجيل الدخول مجددًا."

                                    "REFRESH_TOKEN_REQUIRED" ->
                                        "رمز التحديث غير موجود."

                                    else ->
                                        "تعذر تجديد الجلسة. رمز الاستجابة: ${response.code}"
                                }
                            )
                        )
                    }

                    val root = JSONObject(body)
                    val newAccessToken =
                        root.optString("accessToken")

                    if (newAccessToken.isBlank()) {
                        return@withContext Result.failure(
                            IOException(
                                "لم يرجع الخادم رمز دخول جديدًا."
                            )
                        )
                    }

                    val savedRefreshToken =
                        authDataStore.refreshToken.first()

                    val userId =
                        authDataStore.userId.first()

                    if (
                        savedRefreshToken.isNullOrBlank() ||
                        userId.isNullOrBlank()
                    ) {
                        return@withContext Result.failure(
                            IOException(
                                "بيانات الجلسة غير مكتملة."
                            )
                        )
                    }

                    authDataStore.saveSession(
                        accessToken = newAccessToken,
                        refreshToken = savedRefreshToken,
                        userId = userId
                    )

                    Result.success(newAccessToken)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
