package com.univibe.app

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.univibe.app.data.Api
import com.univibe.app.data.Network
import com.univibe.app.data.SessionStore

class UniVibeApp : Application() {
    lateinit var session: SessionStore
        private set
    lateinit var api: Api
        private set

    override fun onCreate() {
        super.onCreate()
        session = SessionStore(this)
        api = Network.create(session)
    }
}

val android.content.Context.app: UniVibeApp
    get() = applicationContext as UniVibeApp

/** Tiny ViewModel factory helper: vm { MyViewModel(api) } */
@Composable
inline fun <reified VM : ViewModel> vm(key: String? = null, crossinline factory: () -> VM): VM =
    viewModel(key = key, factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = factory() as T
    })

@Composable
fun rememberApi(): Api = LocalContext.current.app.api
