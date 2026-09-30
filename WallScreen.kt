package com.univibe.app.ui.wall

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.univibe.app.data.Api
import com.univibe.app.data.CommentRequest
import com.univibe.app.data.NewPostRequest
import com.univibe.app.data.UiState
import com.univibe.app.data.WallComment
import com.univibe.app.data.WallPost
import com.univibe.app.data.friendlyMessage
import com.univibe.app.data.load
import com.univibe.app.rememberApi
import com.univibe.app.ui.theme.Avatar
import com.univibe.app.ui.theme.Chip
import com.univibe.app.ui.theme.Cyan
import com.univibe.app.ui.theme.EmptyBox
import com.univibe.app.ui.theme.ErrorBox
import com.univibe.app.ui.theme.LoadingBox
import com.univibe.app.ui.theme.Muted
import com.univibe.app.ui.theme.Pink
import com.univibe.app.ui.theme.RetroButton
import com.univibe.app.ui.theme.RetroCard
import com.univibe.app.ui.theme.Sun
import com.univibe.app.vm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WallViewModel(private val api: Api) : ViewModel() {
    private val _posts = MutableStateFlow<UiState<List<WallPost>>>(UiState.Loading)
    val posts = _posts.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    // Compose state so the filter chips recompose when a filter changes
    var scope by mutableStateOf("my"); private set
    var category by mutableStateOf("all"); private set
    var sort by mutableStateOf("recent"); private set

    init { refresh() }

    fun setFilters(scope: String = this.scope, category: String = this.category, sort: String = this.sort) {
        this.scope = scope; this.category = category; this.sort = sort
        refresh()
    }

    fun refresh() {
        _posts.value = UiState.Loading
        viewModelScope.launch { _posts.value = load { api.wall(scope, category, sort).posts } }
    }

    fun toggleLike(post: WallPost) {
        viewModelScope.launch {
            try {
                val res = api.like(post.id)
                val cur = _posts.value
                if (cur is UiState.Success) {
                    _posts.value = UiState.Success(cur.data.map {
                        if (it.id == post.id) it.copy(liked = res.liked, likes_count = res.likes_count) else it
                    })
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                _message.value = e.friendlyMessage()
            }
        }
    }

    fun createPost(category: String, title: String, content: String, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                val res = api.newPost(NewPostRequest(category, title, content))
                if (res.success) { onDone(); refresh() } else _message.value = res.error ?: "Could not post"
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                _message.value = e.friendlyMessage()
            }
        }
    }

    suspend fun comments(postId: Long): List<WallComment> = api.comments(postId).comments

    suspend fun addComment(postId: Long, text: String): Boolean =
        try {
            val ok = api.comment(postId, CommentRequest(text)).success
            if (ok) {
                val cur = _posts.value
                if (cur is UiState.Success) {
                    _posts.value = UiState.Success(cur.data.map {
                        if (it.id == postId) it.copy(comments_count = it.comments_count + 1) else it
                    })
                }
            }
            ok
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            _message.value = e.friendlyMessage(); false
        }

    fun clearMessage() { _message.value = null }
}

private val categories = listOf("all" to "All", "discussion" to "Discussion", "tip" to "Tips", "lost_found" to "Lost & found")

@Composable
fun WallScreen() {
    val api = rememberApi()
    val model = vm { WallViewModel(api) }
    val posts by model.posts.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    var showNew by remember { mutableStateOf(false) }
    var commentsFor by remember { mutableStateOf<WallPost?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Text("Campus Wall", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(model.scope == "my", { model.setFilters(scope = "my") }, { Text("My campus") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Sun))
                FilterChip(model.scope == "all", { model.setFilters(scope = "all") }, { Text("All campuses") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Sun))
                FilterChip(model.sort == "popular", { model.setFilters(sort = if (model.sort == "popular") "recent" else "popular") }, { Text("Popular") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Pink))
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { (id, label) ->
                    FilterChip(model.category == id, { model.setFilters(category = id) }, { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Cyan))
                }
            }
            when (val s = posts) {
                UiState.Loading -> LoadingBox()
                is UiState.Error -> ErrorBox(s.message, onRetry = model::refresh)
                is UiState.Success ->
                    if (s.data.isEmpty()) EmptyBox("Nothing pinned here yet. Be the first to post!")
                    else LazyColumn(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp, 12.dp, 12.dp, 88.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(s.data, key = { it.id }) { p ->
                            PostCard(p, onLike = { model.toggleLike(p) }, onComments = { commentsFor = p })
                        }
                    }
            }
        }
        FloatingActionButton(
            onClick = { showNew = true }, containerColor = Pink,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        ) { Icon(Icons.Filled.Add, contentDescription = "New post") }
    }

    if (showNew) NewPostDialog(onDismiss = { showNew = false }, onSubmit = { c, t, b -> model.createPost(c, t, b) { showNew = false } })
    commentsFor?.let { post -> CommentsDialog(post, model, onDismiss = { commentsFor = null }) }
    message?.let { msg ->
        AlertDialog(onDismissRequest = model::clearMessage, text = { Text(msg) },
            confirmButton = { TextButton(onClick = model::clearMessage) { Text("OK") } })
    }
}

@Composable
private fun PostCard(p: WallPost, onLike: () -> Unit, onComments: () -> Unit) {
    RetroCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Avatar(p.author_photo, size = 36.dp)
                Column(Modifier.weight(1f)) {
                    Text(p.author_name, fontWeight = FontWeight.Bold)
                    Text(p.university_name, color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                if (p.is_pinned) Chip("Pinned", Sun)
                Chip(p.category.replace('_', ' '))
            }
            Text(p.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            Text(p.content)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onLike) {
                    Icon(if (p.liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Like", tint = if (p.liked) Pink else androidx.compose.ui.graphics.Color.Unspecified)
                }
                Text(p.likes_count.toString())
                TextButton(onClick = onComments) { Text("${p.comments_count} comments") }
            }
        }
    }
}

@Composable
private fun NewPostDialog(onDismiss: () -> Unit, onSubmit: (String, String, String) -> Unit) {
    var category by remember { mutableStateOf("discussion") }
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New post") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("discussion" to "Discussion", "tip" to "Tip", "lost_found" to "Lost & found").forEach { (id, label) ->
                        FilterChip(category == id, { category = id }, { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Cyan))
                    }
                }
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true)
                OutlinedTextField(content, { content = it }, label = { Text("What's up?") }, minLines = 3)
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank() && content.isNotBlank(), onClick = { onSubmit(category, title, content) }) { Text("Post") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CommentsDialog(post: WallPost, model: WallViewModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var comments by remember { mutableStateOf<UiState<List<WallComment>>>(UiState.Loading) }
    var draft by remember { mutableStateOf("") }

    fun reload() { scope.launch { comments = load { model.comments(post.id) } } }
    LaunchedEffect(post.id) { reload() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(post.title, maxLines = 1) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    when (val c = comments) {
                        UiState.Loading -> Text("Loading...")
                        is UiState.Error -> Text(c.message)
                        is UiState.Success ->
                            if (c.data.isEmpty()) Text("No comments yet.", color = Muted)
                            else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                c.data.takeLast(20).forEach {
                                    Column {
                                        Text(it.display_name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                                        Text(it.content)
                                    }
                                }
                            }
                    }
                }
                OutlinedTextField(draft, { draft = it }, label = { Text("Add a comment") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = draft.isNotBlank(), onClick = {
                scope.launch { if (model.addComment(post.id, draft.trim())) { draft = ""; reload() } }
            }) { Text("Send") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
