package com.univibe.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.univibe.app.data.LoginRequest
import com.univibe.app.data.friendlyMessage
import com.univibe.app.rememberApi
import com.univibe.app.ui.theme.Cyan
import com.univibe.app.ui.theme.Muted
import com.univibe.app.ui.theme.Pink
import com.univibe.app.ui.theme.RetroButton
import com.univibe.app.ui.theme.RetroCard
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onLoggedIn: (String) -> Unit) {
    val api = rememberApi()
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("UniVibe", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold)
        Text("Verified student connections", color = Muted)
        Spacer(Modifier.height(24.dp))

        RetroCard(Modifier.fillMaxWidth(), color = Cyan.copy(alpha = 0.25f)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = email, onValueChange = { email = it }, label = { Text("University email") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = it }, label = { Text("Password") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                notice?.let { Text(it, fontWeight = FontWeight.SemiBold) }
                RetroButton(
                    text = if (busy) "Signing in..." else "Sign in",
                    enabled = !busy && email.isNotBlank() && password.isNotBlank(),
                    color = Pink,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        busy = true; error = null; notice = null
                        scope.launch {
                            try {
                                val res = api.login(LoginRequest(email.trim(), password))
                                when (res.next_step) {
                                    "app" -> onLoggedIn(res.token)
                                    "pending_verification" ->
                                        notice = "Your account is still waiting for verification. You'll be able to sign in once it's approved."
                                    else ->
                                        notice = "Finish setting up your profile on the website first, then sign in here."
                                }
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Throwable) {
                                error = e.friendlyMessage()
                            } finally {
                                busy = false
                            }
                        }
                    },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "New here? Create your account on the UniVibe website, then sign in.",
            color = Muted, style = MaterialTheme.typography.bodySmall,
        )
    }
}
