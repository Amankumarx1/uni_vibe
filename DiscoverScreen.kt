package com.univibe.app.ui.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.univibe.app.data.Api
import com.univibe.app.data.ActionRequest
import com.univibe.app.data.DiscoverProfile
import com.univibe.app.data.Network
import com.univibe.app.data.friendlyMessage
import com.univibe.app.rememberApi
import com.univibe.app.ui.theme.Chip
import com.univibe.app.ui.theme.Cyan
import com.univibe.app.ui.theme.EmptyBox
import com.univibe.app.ui.theme.ErrorBox
import com.univibe.app.ui.theme.Ink
import com.univibe.app.ui.theme.LoadingBox
import com.univibe.app.ui.theme.Mint
import com.univibe.app.ui.theme.Muted
import com.univibe.app.ui.theme.Pink
import com.univibe.app.ui.theme.RetroButton
import com.univibe.app.ui.theme.RetroCard
import com.univibe.app.ui.theme.Sun
import com.univibe.app.vm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DiscoverUi(
    val loading: Boolean = true,
    val error: String? = null,
    val queue: List<DiscoverProfile> = emptyList(),
    val mode: Int = 2,
    val network: String = "my",
    val matchedConversation: Long? = null,
    val matchedName: String = "",
    val toast: String? = null,
)

class DiscoverViewModel(private val api: Api) : ViewModel() {
    private val _ui = MutableStateFlow(DiscoverUi())
    val ui = _ui.asStateFlow()
    private var cursor: Long? = null
    private var exhausted = false
    private var fetching = false

    init { reload() }

    fun setMode(mode: Int) { _ui.value = _ui.value.copy(mode = mode); reload() }
    fun setNetwork(net: String) { _ui.value = _ui.value.copy(network = net); reload() }

    fun reload() {
        cursor = null; exhausted = false
        _ui.value = _ui.value.copy(loading = true, error = null, queue = emptyList())
        fetchMore(initial = true)
    }

    private fun fetchMore(initial: Boolean = false) {
        if (fetching || exhausted) return
        fetching = true
        viewModelScope.launch {
            try {
                val s = _ui.value
                val res = api.discover(s.mode, s.network, cursor)
                cursor = res.next_cursor
                if (res.next_cursor == null) exhausted = true
                val known = _ui.value.queue.map { it.user_id }.toSet()
                _ui.value = _ui.value.copy(loading = false, queue = _ui.value.queue + res.profiles.filter { it.user_id !in known })
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (initial) _ui.value = _ui.value.copy(loading = false, error = e.friendlyMessage())
                else _ui.value = _ui.value.copy(toast = e.friendlyMessage())
            } finally {
                fetching = false
            }
        }
    }

    fun act(profile: DiscoverProfile, connect: Boolean) {
        val s = _ui.value
        // Optimistically drop the card
        _ui.value = s.copy(queue = s.queue.filterNot { it.user_id == profile.user_id })
        if (_ui.value.queue.size < 3) fetchMore()
        viewModelScope.launch {
            try {
                val res = api.action(ActionRequest(profile.user_id, s.mode, if (connect) "connect" else "pass"))
                if (!res.success) {
                    _ui.value = _ui.value.copy(toast = res.error ?: "Action failed")
                } else if (res.is_match && res.conversation_id != null) {
                    _ui.value = _ui.value.copy(matchedConversation = res.conversation_id, matchedName = profile.display_name)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                _ui.value = _ui.value.copy(toast = e.friendlyMessage())
            }
        }
    }

    fun clearMatch() { _ui.value = _ui.value.copy(matchedConversation = null) }
    fun clearToast() { _ui.value = _ui.value.copy(toast = null) }
}

@Composable
fun DiscoverScreen(
    onOpenProfile: (Long) -> Unit,
    onOpenChat: (Long) -> Unit,
    onOpenNotifications: () -> Unit,
) {
    val api = rememberApi()
    val model = vm { DiscoverViewModel(api) }
    val ui by model.ui.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Discover", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            IconButton(onClick = onOpenNotifications) { Icon(Icons.Filled.Notifications, contentDescription = "Notifications") }
        }
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(2 to "Friends", 3 to "Networking", 1 to "Dating").forEach { (id, label) ->
                FilterChip(
                    selected = ui.mode == id, onClick = { model.setMode(id) }, label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Cyan),
                )
            }
        }
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("my" to "My campus", "other" to "Nearby campuses").forEach { (id, label) ->
                FilterChip(
                    selected = ui.network == id, onClick = { model.setNetwork(id) }, label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Sun),
                )
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            val top = ui.queue.firstOrNull()
            when {
                ui.loading -> LoadingBox()
                ui.error != null -> ErrorBox(ui.error!!, onRetry = model::reload)
                top == null -> EmptyBox("You're all caught up. Check back later or try another mode.")
                else -> ProfileCard(top, onOpenProfile = { onOpenProfile(top.user_id) }, onAct = { model.act(top, it) })
            }
        }
    }

    ui.matchedConversation?.let { convId ->
        AlertDialog(
            onDismissRequest = model::clearMatch,
            title = { Text("It's a match!") },
            text = { Text("You and ${ui.matchedName} connected. Say hi!") },
            confirmButton = { TextButton(onClick = { model.clearMatch(); onOpenChat(convId) }) { Text("Open chat") } },
            dismissButton = { TextButton(onClick = model::clearMatch) { Text("Keep browsing") } },
        )
    }
    ui.toast?.let { msg ->
        AlertDialog(
            onDismissRequest = model::clearToast,
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = model::clearToast) { Text("OK") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileCard(p: DiscoverProfile, onOpenProfile: () -> Unit, onAct: (Boolean) -> Unit) {
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        RetroCard(Modifier.weight(1f).fillMaxWidth(), onClick = onOpenProfile) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                AsyncImage(
                    model = Network.absolute(p.photos.firstOrNull() ?: p.primary_photo_path),
                    contentDescription = p.display_name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                )
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            listOfNotNull(p.display_name, p.age?.toString()).joinToString(", "),
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold,
                        )
                        if (p.student_verified) { Spacer(Modifier.padding(4.dp)); Chip("Verified", Mint) }
                    }
                    Text(p.university_name + (p.course?.let { " - $it" } ?: ""), color = Muted)
                    if (p.distance_label.isNotBlank()) Chip(p.distance_label, Sun)
                    if (p.bio.isNotBlank()) Text(p.bio)
                    p.compatibility_reasons.forEach { Text("- $it", style = MaterialTheme.typography.bodySmall, color = Muted) }
                    if (p.interests.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            p.interests.forEach { Chip(it.name) }
                        }
                    }
                    p.prompts.forEach {
                        Column {
                            Text(it.question, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                            Text(it.answer)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RetroButton("Pass", color = Color.White, modifier = Modifier.weight(1f), onClick = { onAct(false) })
            RetroButton("Connect", color = Pink, modifier = Modifier.weight(1f), onClick = { onAct(true) })
        }
    }
}
