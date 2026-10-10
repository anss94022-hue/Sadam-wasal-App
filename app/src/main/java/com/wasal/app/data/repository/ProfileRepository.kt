
package com.wasal.app.data.repository

import com.wasal.app.data.network.CoreApiClient
import com.wasal.app.data.network.ServerConfig
import org.json.JSONObject
import java.io.IOException

data class ProfileUser(
    val id: String,
    val displayName: String,
    val wasalCode: String
)

class ProfileRepository {

    /**
     * جلب بيانات حساب وصال الحالي من الخادم.
     * يجب استدعاء الدالة من Dispatchers.IO.
     */
    fun getMyProfile(
        userId: String,
        accessToken: String
    ): Result<ProfileUser> {

        if (userId.isBlank() || accessToken.isBlank()) {
            return Result.failure(
                IOException("جلسة وصال غير مكتملة.")
            )
        }

        return try {
            val path =
                ServerConfig.USERS_ENDPOINT + "/" + userId

            CoreApiClient.execute(
                CoreApiClient.get(path, accessToken)
            ).use { response ->

                val body = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    return Result.failure(
                        IOException(
                            "تعذر جلب بيانات الحساب. رمز الاستجابة: ${response.code}"
                        )
                    )
                }

                val user = JSONObject(body)
                    .optJSONObject("user")
                    ?: return Result.failure(
                        IOException(
                            "لم يرجع الخادم بيانات الحساب."
                        )
                    )

                val id = user.optString("id")
                val displayName = user.optString("displayName")
                val wasalCode = user.optString("wasalCode")

                if (
                    id.isBlank() ||
                    !wasalCode.matches(
                        Regex("^[A-Za-z0-9]{15}$")
                    )
                ) {
                    return Result.failure(
                        IOException(
                            "بيانات الحساب أو رمز وصال غير مكتملة."
                        )
                    )
                }

                Result.success(
                    ProfileUser(
                        id = id,
                        displayName = displayName
                            .ifBlank { "مستخدم وصال" },
                        wasalCode = wasalCode
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
