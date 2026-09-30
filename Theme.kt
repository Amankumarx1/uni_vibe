package com.univibe.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.univibe.app.data.Network

val Paper = Color(0xFFFFFEF7)
val Ink = Color(0xFF0C0F14)
val Cyan = Color(0xFF8BE4F0)
val Pink = Color(0xFFFF7F8A)
val Sun = Color(0xFFFFE066)
val Mint = Color(0xFFB8F0C8)
val Muted = Color(0xFF5B6470)

private val Scheme = lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
    secondary = Cyan,
    onSecondary = Ink,
    tertiary = Pink,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    error = Color(0xFFB3261E),
)

@Composable
fun UniVibeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}

/** Paper card with a hard ink border and an offset shadow - the site's "retro" look. */
@Composable
fun RetroCard(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    shadow: Dp = 4.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    val base = modifier
        .drawBehind {
            val s = shadow.toPx()
            drawRoundRect(Ink, topLeft = Offset(s, s), size = size, cornerRadius = CornerRadius(10.dp.toPx()))
        }
        .clip(shape)
        .background(color)
        .border(BorderStroke(2.dp, Ink), shape)
    Box(if (onClick != null) base.clickable(onClick = onClick) else base) { content() }
}

@Composable
fun RetroButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Cyan,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(2.dp, Ink),
        colors = ButtonDefaults.buttonColors(
            containerColor = color, contentColor = Ink,
            disabledContainerColor = Color(0xFFE5E5E0), disabledContentColor = Muted,
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    ) { Text(text, fontWeight = FontWeight.Bold) }
}

@Composable
fun Avatar(path: String?, size: Dp = 48.dp, modifier: Modifier = Modifier) {
    AsyncImage(
        model = Network.absolute(path),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .border(2.dp, Ink, CircleShape)
            .background(Cyan),
    )
}

@Composable
fun Chip(text: String, color: Color = Cyan) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .border(1.5.dp, Ink, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) { Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Ink) }
}

@Composable
fun ErrorBox(message: String, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, textAlign = TextAlign.Center)
        if (onRetry != null) {
            Box(Modifier.padding(top = 12.dp)) { RetroButton("Try again", onClick = onRetry) }
        }
    }
}

@Composable
fun EmptyBox(message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(message, color = Muted, textAlign = TextAlign.Center)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
}

@Composable
fun RowLabel(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = Muted, modifier = Modifier.weight(0.35f))
        Text(value, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.65f))
    }
}
