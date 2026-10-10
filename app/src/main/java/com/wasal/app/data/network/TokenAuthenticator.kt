package com.wasal.app.data.network

/**
 * نقطة تأسيسية لتجديد رمز الدخول.
 *
 * يجب ربطها لاحقًا بالمسار الفعلي:
 * POST /api/auth/refresh
 *
 * لا تستخدم Refresh Token باعتباره Access Token.
 * لا تطل مدة صلاحية Access Token لحل مشكلة انتهاء الرمز.
 */
class TokenAuthenticator
