package com.univibe.app.ui.profile

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.shape.RoundedCornerShape
import coil.compose.AsyncImage
import com.univibe.app.data.Api
import com.univibe.app.data.BlockRequest
import com.univibe.app.data.Me
import com.univibe.app.data.Network
import com.univibe.app.data.PublicProfile
import com.univibe.app.data.ReportRequest
import com.univibe.app.data.UiState
import com.univibe.app.data.UpdateProfileRequest
import com.univibe.app.data.friendlyMessage
import com.univibe.app.data.load
import com.univibe.app.rememberApi
import com.univibe.app.ui.theme.Avatar
import com.univibe.app.ui.theme.Chip
import com.univibe.app.ui.theme.Cyan
import com.univibe.app.ui.theme.ErrorBox
import com.univibe.app.ui.theme.LoadingBox
import com.univibe.app.ui.theme.Mint
import com.univibe.app.ui.theme.Muted
import com.univibe.app.ui.theme.Pink
import com.univibe.app.ui.theme.RetroButton
import com.univibe.app.ui.theme.RetroCard
import com.univibe.app.ui.theme.RowLabel
import com.univibe.app.ui.theme.SectionTitle
import com.univibe.app.ui.theme.Sun
import com.univibe.app.vm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- My profile

class MyProfileViewModel(private val api: Api) : ViewModel() {
    private val _state = MutableStateFlow<UiState<Me>>(UiState.Loading)
    val state = _state.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.value = UiState.Loading
        viewModelScope.launch { _state.value = load { api.me() } }
    }

    fun save(name: String, course: String, year: Int, bio: String, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                val res = api.updateProfile(UpdateProfileRequest(name.trim(), course.trim(), year, bio.trim()))
                if (res.success) { onDone(); refresh() } else _message.value = res.error ?: "Could not save"
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                _message.value = e.friendlyMessage()
            }
        }
    }

    fun clearMessage() { _message.value = null }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MyProfileScreen(onLogout: () -> Unit) {
    val api = rememberApi()
    val model = vm { MyProfileViewModel(api) }
    val state by model.state.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        when (val s = state) {
            UiState.Loading -> LoadingBox()
            is UiState.Error -> Column {
                ErrorBox(s.message, onRetry = model::refresh, modifier = Modifier.weight(1f))
                RetroButton("Sign out", color = Pink, modifier = Modifier.fillMaxWidth().padding(16.dp), onClick = onLogout)
            }
            is UiState.Success -> {
                val me = s.data
                Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Avatar(me.photo, size = 72.dp)
                        Column {
                            Text(me.display_name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                            Text(me.email, color = Muted, style = MaterialTheme.typography.bodySmall)
                            if (me.student_verified) Chip("Verified student", Mint)
                        }
                    }
                    RetroCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            RowLabel("University", me.university_name)
                            RowLabel("Course", me.course ?: "-")
                            RowLabel("Year", me.study_year?.toString() ?: "-")
                            me.age?.let { RowLabel("Age", it.toString()) }
                            if (me.bio.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(me.bio) }
                        }
                    }
                    if (me.interests.isNotEmpty()) {
                        SectionTitle("Interests")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            me.interests.forEach { Chip(it.name) }
                        }
                    }
                    RetroButton("Edit profile", modifier = Modifier.fillMaxWidth(), onClick = { editing = true })
                    RetroButton("Sign out", color = Pink, modifier = Modifier.fillMaxWidth(), onClick = onLogout)
                    Text(
                        "Photos, interests and privacy settings can be changed on the website for now.",
                        color = Muted, style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (editing) EditProfileDialog(me, onDismiss = { editing = false }) { n, c, y, b ->
                    model.save(n, c, y, b) { editing = false }
                }
            }
        }
    }
    message?.let { msg ->
        AlertDialog(onDismissRequest = model::clearMessage, text = { Text(msg) },
            confirmButton = { TextButton(onClick = model::clearMessage) { Text("OK") } })
    }
}

@Composable
private fun EditProfileDialog(me: Me, onDismiss: () -> Unit, onSave: (String, String, Int, String) -> Unit) {
    var name by remember { mutableStateOf(me.display_name) }
    var course by remember { mutableStateOf(me.course.orEmpty()) }
    var year by remember { mutableStateOf((me.study_year ?: 1).toString()) }
    var bio by remember { mutableStateOf(me.bio) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit profile") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Display name") }, singleLine = true)
                OutlinedTextField(course, { course = it }, label = { Text("Course") }, singleLine = true)
                OutlinedTextField(year, { year = it.filter(Char::isDigit).take(1) }, label = { Text("Study year (1-6)") }, singleLine = true)
                OutlinedTextField(bio, { bio = it.take(1000) }, label = { Text("Bio") }, minLines = 3)
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && course.isNotBlank(),
                onClick = { onSave(name, course, (year.toIntOrNull() ?: 1).coerceIn(1, 6), bio) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---------------------------------------------------------------- Public profile

class PublicProfileViewModel(private val api: Api, private val userId: Long) : ViewModel() {
    private val _state = MutableStateFlow<UiState<PublicProfile>>(UiState.Loading)
    val state = _state.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.value = UiState.Loading
        viewModelScope.launch { _state.value = load { api.profile(userId) } }
    }

    fun block(onDone: () -> Unit) = perform({ api.block(BlockRequest(userId)) }, onDone)
    fun report(reason: String, onDone: () -> Unit) = perform({ api.report(ReportRequest(userId, reason)) }, onDone)

    private fun perform(call: suspend () -> com.univibe.app.data.SimpleResponse, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                val res = call()
                if (res.success) { _message.value = res.message; onDone() } else _message.value = res.error ?: "Failed"
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                _message.value = e.friendlyMessage()
            }
        }
    }

    fun clearMessage() { _message.value = null }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PublicProfileScreen(userId: Long, onBack: () -> Unit) {
    val api = rememberApi()
    val model = vm(key = "profile-$userId") { PublicProfileViewModel(api, userId) }
    val state by model.state.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    var confirmBlock by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Profile", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        }
        when (val s = state) {
            UiState.Loading -> LoadingBox()
            is UiState.Error -> ErrorBox(s.message, onRetry = model::refresh)
            is UiState.Success -> {
                val p = s.data
                Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        p.photos.forEach {
                            AsyncImage(
                                model = Network.absolute(it), contentDescription = null, contentScale = ContentScale.Crop,
                                modifier = Modifier.width(260.dp).aspectRatio(1f).clip(RoundedCornerShape(10.dp)),
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(listOfNotNull(p.display_name, p.age?.toString()).joinToString(", "),
                            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                        if (p.student_verified) Chip("Verified", Mint)
                    }
                    val line = listOfNotNull(p.university_name, p.course, p.study_year?.let { "Year $it" }).joinToString(" - ")
                    if (line.isNotBlank()) Text(line, color = Muted)
                    if (p.bio.isNotBlank()) Text(p.bio)
                    if (p.shared_tags.isNotEmpty()) {
                        SectionTitle("In common")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            p.shared_tags.forEach { Chip(it, Sun) }
                        }
                    }
                    if (p.interests.isNotEmpty()) {
                        SectionTitle("Interests")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            p.interests.forEach { Chip(it.name) }
                        }
                    }
                    p.prompts.forEach {
                        RetroCard(Modifier.fillMaxWidth(), color = Cyan.copy(alpha = 0.25f)) {
                            Column(Modifier.padding(12.dp)) {
                                Text(it.question, fontWeight = FontWeight.Bold)
                                Text(it.answer)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        RetroButton("Report", color = Sun, modifier = Modifier.weight(1f), onClick = { reporting = true })
                        RetroButton("Block", color = Pink, modifier = Modifier.weight(1f), onClick = { confirmBlock = true })
                    }
                }
            }
        }
    }

    if (confirmBlock) AlertDialog(
        onDismissRequest = { confirmBlock = false },
        title = { Text("Block this person?") },
        text = { Text("They won't be able to see you or message you, and you won't see them.") },
        confirmButton = { TextButton(onClick = { confirmBlock = false; model.block(onDone = onBack) }) { Text("Block") } },
        dismissButton = { TextButton(onClick = { confirmBlock = false }) { Text("Cancel") } },
    )
    if (reporting) AlertDialog(
        onDismissRequest = { reporting = false },
        title = { Text("Report reason") },
        text = {
            Column {
                listOf("Harassment", "Fake profile", "Inappropriate content", "Spam", "Other").forEach { reason ->
                    TextButton(onClick = { reporting = false; model.report(reason) {} }) { Text(reason) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { reporting = false }) { Text("Cancel") } },
    )
    message?.let { msg ->
        AlertDialog(onDismissRequest = model::clearMessage, text = { Text(msg) },
            confirmButton = { TextButton(onClick = model::clearMessage) { Text("OK") } })
    }
}
