package com.univibe.app.ui.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.univibe.app.data.Api
import com.univibe.app.data.AppNotification
import com.univibe.app.data.UiState
import com.univibe.app.data.load
import com.univibe.app.rememberApi
import com.univibe.app.ui.theme.Cyan
import com.univibe.app.ui.theme.EmptyBox
import com.univibe.app.ui.theme.ErrorBox
import com.univibe.app.ui.theme.LoadingBox
import com.univibe.app.ui.theme.Muted
import com.univibe.app.ui.theme.RetroCard
import com.univibe.app.vm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NotificationsViewModel(private val api: Api) : ViewModel() {
    private val _state = MutableStateFlow<UiState<List<AppNotification>>>(UiState.Loading)
    val state = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            _state.value = load { api.notifications().notifications }
            // Same behaviour as the website: opening the list marks everything read.
            runCatching { api.markNotificationsRead() }
        }
    }
}

@Composable
fun NotificationsScreen(onBack: () -> Unit) {
    val api = rememberApi()
    val model = vm { NotificationsViewModel(api) }
    val state by model.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Notifications", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        }
        when (val s = state) {
            UiState.Loading -> LoadingBox()
            is UiState.Error -> ErrorBox(s.message, onRetry = model::refresh)
            is UiState.Success ->
                if (s.data.isEmpty()) EmptyBox("You're all caught up.")
                else LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(s.data, key = { it.id }) { n ->
                        RetroCard(Modifier.fillMaxWidth(), color = if (n.is_read) androidx.compose.ui.graphics.Color.White else Cyan.copy(alpha = 0.35f)) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(n.title, fontWeight = FontWeight.Bold)
                                Text(n.body)
                                n.created_at?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                }
        }
    }
}
