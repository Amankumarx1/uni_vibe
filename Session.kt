package com.univibe.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("univibe_session")
private val TOKEN = stringPreferencesKey("token")

/** Holds the bearer token. Cached in memory so the OkHttp interceptor never blocks on disk. */
class SessionStore(private val context: Context) {
    @Volatile var token: String? = null
        private set

    suspend fun load(): String? {
        token = context.store.data.map { it[TOKEN] }.first()
        return token
    }

    suspend fun save(value: String) {
        token = value
        context.store.edit { it[TOKEN] = value }
    }

    suspend fun clear() {
        token = null
        context.store.edit { it.remove(TOKEN) }
    }
}
