package com.univibe.app.data

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.univibe.app.BuildConfig
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object Network {
    val baseUrl: String = BuildConfig.BASE_URL

    /** Emits when the server rejects our token (expired / suspended) so the UI can log out. */
    val unauthorized = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    fun create(session: SessionStore): Api {
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder().apply {
                    session.token?.let { header("Authorization", "Bearer $it") }
                    header("Accept", "application/json")
                }.build()
                val res = chain.proceed(req)
                // Bad credentials on login also return 401 - that is not a session expiry.
                if (res.code == 401 && !req.url.encodedPath.endsWith("auth/login") && session.token != null) {
                    unauthorized.tryEmit(Unit)
                }
                res
            }
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
                }
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(Api::class.java)
    }

    /** Server returns relative paths like /static/uploads/x.webp - make them absolute for Coil. */
    fun absolute(path: String?): String {
        if (path.isNullOrBlank()) return baseUrl.trimEnd('/') + "/static/images/avatars/user_aman.webp"
        if (path.startsWith("http")) return path
        return baseUrl.trimEnd('/') + "/" + path.trimStart('/')
    }
}
