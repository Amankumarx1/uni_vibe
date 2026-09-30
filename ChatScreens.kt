package com.univibe.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.univibe.app.data.Api
import com.univibe.app.data.ChatMessage
import com.univibe.app.data.Conversation
import com.univibe.app.data.Network
import com.univibe.app.data.SendMessageRequest
import com.univibe.app.data.UiState
import com.univibe.app.data.friendlyMessage
import com.univibe.app.data.load
import com.univibe.app.rememberApi
import com.univibe.app.ui.theme.Avatar
import com.univibe.app.ui.theme.Cyan
import com.univibe.app.ui.theme.EmptyBox
import com.univibe.app.ui.theme.ErrorBox
import com.univibe.app.ui.theme.Ink
import com.univibe.app.ui.theme.LoadingBox
import com.univibe.app.ui.theme.Muted
import com.univibe.app.ui.theme.Pink
import com.univibe.app.ui.theme.RetroCard
import com.univibe.app.vm
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- Inbox

class InboxViewModel(private val api: Api) : ViewModel() {
    private val _state = MutableStateFlow<UiState<List<Conversation>>>(UiState.Loading)
    val state = _state.asStateFlow()
    fun refresh(showSpinner: Boolean = false) {
        if (showSpinner) _state.value = UiState.Loading
        viewModelScope.launch { _state.value = load { api.conversations().conversations } }
    }
}

@Composable
fun InboxScreen(onOpenChat: (Long) -> Unit) {
    val api = rememberApi()
    val model = vm { InboxViewModel(api) }
    val state by model.state.collectAsStateWithLifecycle()

    // Refresh whenever the tab becomes visible again (e.g. after reading a chat)
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { model.refresh() }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text("Chats", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp))
        when (val s = state) {
            UiState.Loading -> LoadingBox()
            is UiState.Error -> ErrorBox(s.message, onRetry = { model.refresh(true) })
            is UiState.Success ->
                if (s.data.isEmpty()) EmptyBox("No conversations yet. Connect with someone to start chatting.")
                else LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(s.data, key = { it.id }) { c ->
                        RetroCard(Modifier.fillMaxWidth(), onClick = { onOpenChat(c.id) }) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Avatar(c.partner_photo)
                                Column(Modifier.weight(1f)) {
                                    Text(c.partner_name, fontWeight = FontWeight.Bold)
                                    Text(c.last_message_text.ifBlank { "Say hello" }, maxLines = 1, color = Muted, style = MaterialTheme.typography.bodySmall)
                                }
                                if (c.unread > 0) {
                                    Box(Modifier.clip(RoundedCornerShape(50)).background(Pink).border(1.5.dp, Ink, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                        Text(c.unread.toString(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }
        }
    }
}

// ---------------------------------------------------------------- Chat

data class ChatUi(
    val loading: Boolean = true,
    val error: String? = null,
    val myId: Long = 0,
    val messages: List<ChatMessage> = emptyList(),
    val sending: Boolean = false,
    val sendError: String? = null,
)

class ChatViewModel(private val api: Api, private val convId: Long) : ViewModel() {
    private val _ui = MutableStateFlow(ChatUi())
    val ui = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val me = api.me().user_id
                val msgs = api.messages(convId).messages
                _ui.value = ChatUi(loading = false, myId = me, messages = msgs)
                runCatching { api.markRead(convId) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                _ui.value = ChatUi(loading = false, error = e.friendlyMessage())
            }
        }
    }

    /** Fetch only messages newer than the last one we have. Called every few seconds while visible. */
    suspend fun poll() {
        val s = _ui.value
        if (s.loading || s.error != null) return
        try {
            val last = s.messages.lastOrNull()?.id
            val fresh = api.messages(convId, last).messages
            if (fresh.isNotEmpty()) {
                val known = _ui.value.messages.map { it.id }.toSet()
                _ui.value = _ui.value.copy(messages = _ui.value.messages + fresh.filter { it.id !in known })
                if (fresh.any { it.sender_user_id != s.myId }) runCatching { api.markRead(convId) }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Throwable) {
            // transient network error - the next poll will retry
        }
    }

    fun send(text: String, onSent: () -> Unit) {
        val body = text.trim()
        if (body.isEmpty() || _ui.value.sending) return
        _ui.value = _ui.value.copy(sending = true, sendError = null)
        viewModelScope.launch {
            try {
                val res = api.send(SendMessageRequest(convId, body))
                if (res.success) onSent() else _ui.value = _ui.value.copy(sendError = res.error ?: "Message not sent")
                poll()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                _ui.value = _ui.value.copy(sendError = e.friendlyMessage())
            } finally {
                _ui.value = _ui.value.copy(sending = false)
            }
        }
    }
}

@Composable
fun ChatScreen(conversationId: Long, onBack: () -> Unit) {
    val api = rememberApi()
    val model = vm(key = "chat-$conversationId") { ChatViewModel(api, conversationId) }
    val ui by model.ui.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Poll only while the screen is visible
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { delay(3000); model.poll() }
        }
    }
    LaunchedEffect(ui.messages.size) {
        if (ui.messages.isNotEmpty()) listState.animateScrollToItem(ui.messages.lastIndex)
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Chat", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                ui.loading -> LoadingBox()
                ui.error != null -> ErrorBox(ui.error!!)
                ui.messages.isEmpty() -> EmptyBox("No messages yet. Say hi!")
                else -> LazyColumn(
                    state = listState,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(ui.messages, key = { it.id }) { m -> Bubble(m, mine = m.sender_user_id == ui.myId) }
                }
            }
        }
        ui.sendError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft, onValueChange = { draft = it }, placeholder = { Text("Message") },
                modifier = Modifier.weight(1f), maxLines = 4,
            )
            IconButton(
                enabled = draft.isNotBlank() && !ui.sending,
                onClick = { model.send(draft) { draft = "" } },
            ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send") }
        }
    }
}

@Composable
private fun Bubble(m: ChatMessage, mine: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (mine) Cyan else Color.White)
                .border(2.dp, Ink, RoundedCornerShape(12.dp))
                .padding(10.dp),
        ) {
            val url = m.media_url
            if (!url.isNullOrBlank()) {
                val type = m.media_type.orEmpty()
                if (type.contains("image") || type.contains("photo")) {
                    AsyncImage(
                        model = Network.absolute(url), contentDescription = "Photo",
                        contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text("[$type attachment - open on web]", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (!m.body.isNullOrBlank()) Text(m.body)
        }
    }
}
