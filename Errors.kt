package com.univibe.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import java.io.IOException

/** Turns any failure into a short message; pulls the server "error" field from JSON error bodies. */
fun Throwable.friendlyMessage(): String = when (this) {
    is HttpException -> {
        val body = runCatching { response()?.errorBody()?.string() }.getOrNull()
        val serverMsg = runCatching {
            Json.parseToJsonElement(body ?: "").jsonObject["error"]?.jsonPrimitive?.content
        }.getOrNull()
        serverMsg ?: "Server error (${code()})"
    }
    is IOException -> "Can't reach the server. Check your connection."
    else -> message ?: "Something went wrong."
}

/** Small result wrapper used by all ViewModels. */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Error(val message: String) : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
}

suspend fun <T> load(block: suspend () -> T): UiState<T> =
    try {
        UiState.Success(block())
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Throwable) {
        UiState.Error(e.friendlyMessage())
    }
