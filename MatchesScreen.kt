package com.univibe.app.ui.matches

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.univibe.app.data.Api
import com.univibe.app.data.ConnectionItem
import com.univibe.app.data.MatchesResponse
import com.univibe.app.data.NetworkMember
import com.univibe.app.data.UiState
import com.univibe.app.data.load
import com.univibe.app.rememberApi
import com.univibe.app.ui.theme.Chip
import com.univibe.app.ui.theme.Avatar
import com.univibe.app.ui.theme.Cyan
import com.univibe.app.ui.theme.EmptyBox
import com.univibe.app.ui.theme.ErrorBox
import com.univibe.app.ui.theme.LoadingBox
import com.univibe.app.ui.theme.Mint
import com.univibe.app.ui.theme.Muted
import com.univibe.app.ui.theme.RetroButton
import com.univibe.app.ui.theme.RetroCard
import com.univibe.app.vm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MatchesViewModel(private val api: Api) : ViewModel() {
    private val _matches = MutableStateFlow<UiState<MatchesResponse>>(UiState.Loading)
    val matches = _matches.asStateFlow()
    private val _members = MutableStateFlow<UiState<List<NetworkMember>>>(UiState.Loading)
    val members = _members.asStateFlow()

    init { refresh(); search("") }

    fun refresh() {
        _matches.value = UiState.Loading
        viewModelScope.launch { _matches.value = load { api.matches() } }
    }

    fun search(q: String) {
        _members.value = UiState.Loading
        viewModelScope.launch { _members.value = load { api.network(q.trim()).members } }
    }
}

@Composable
fun MatchesScreen(onOpenProfile: (Long) -> Unit, onOpenChat: (Long) -> Unit) {
    val api = rememberApi()
    val model = vm { MatchesViewModel(api) }
    val matches by model.matches.collectAsStateWithLifecycle()
    val members by model.members.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    val titles = listOf("Friends", "Networking", "Dating", "Directory")

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text("Connections", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp))
        TabRow(selectedTabIndex = tab, containerColor = androidx.compose.ui.graphics.Color.Transparent) {
            titles.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, maxLines = 1) }) }
        }
        if (tab < 3) {
            when (val s = matches) {
                UiState.Loading -> LoadingBox()
                is UiState.Error -> ErrorBox(s.message, onRetry = model::refresh)
                is UiState.Success -> {
                    val list = when (tab) { 0 -> s.data.friends; 1 -> s.data.networking; else -> s.data.dating }
                    if (list.isEmpty()) EmptyBox("No connections here yet. Head to Discover to meet people.")
                    else LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(list, key = { it.user_id }) { c -> ConnectionRow(c, onOpenProfile, onOpenChat) }
                    }
                }
            }
        } else {
            var q by remember { mutableStateOf("") }
            OutlinedTextField(
                value = q, onValueChange = { q = it; model.search(it) },
                label = { Text("Search name, course or university") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            )
            when (val s = members) {
                UiState.Loading -> LoadingBox()
                is UiState.Error -> ErrorBox(s.message, onRetry = { model.search(q) })
                is UiState.Success ->
                    if (s.data.isEmpty()) EmptyBox("No members found.")
                    else LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(s.data, key = { it.user_id }) { m -> MemberRow(m, onOpenProfile) }
                    }
            }
        }
    }
}

@Composable
private fun ConnectionRow(c: ConnectionItem, onOpenProfile: (Long) -> Unit, onOpenChat: (Long) -> Unit) {
    RetroCard(Modifier.fillMaxWidth(), onClick = { onOpenProfile(c.user_id) }) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(c.photo)
            Column(Modifier.weight(1f)) {
                Text(c.display_name, fontWeight = FontWeight.Bold)
                Text(listOfNotNull(c.course, c.university_name).joinToString(" - "), color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            c.conversation_id?.let { id -> RetroButton("Chat", color = Cyan, onClick = { onOpenChat(id) }) }
        }
    }
}

@Composable
private fun MemberRow(m: NetworkMember, onOpenProfile: (Long) -> Unit) {
    RetroCard(Modifier.fillMaxWidth(), onClick = { onOpenProfile(m.user_id) }) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(m.photo)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(m.display_name, fontWeight = FontWeight.Bold)
                    if (m.student_verified) Chip("Verified", Mint)
                }
                Text(listOfNotNull(m.course, m.university_name).joinToString(" - "), color = Muted, style = MaterialTheme.typography.bodySmall)
                if (m.bio.isNotBlank()) Text(m.bio, maxLines = 2, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
