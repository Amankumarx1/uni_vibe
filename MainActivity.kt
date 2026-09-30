package com.univibe.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.univibe.app.data.Network
import com.univibe.app.ui.auth.LoginScreen
import com.univibe.app.ui.chat.ChatScreen
import com.univibe.app.ui.chat.InboxScreen
import com.univibe.app.ui.discover.DiscoverScreen
import com.univibe.app.ui.matches.MatchesScreen
import com.univibe.app.ui.notifications.NotificationsScreen
import com.univibe.app.ui.profile.MyProfileScreen
import com.univibe.app.ui.profile.PublicProfileScreen
import com.univibe.app.ui.theme.Cyan
import com.univibe.app.ui.theme.Ink
import com.univibe.app.ui.theme.LoadingBox
import com.univibe.app.ui.theme.Paper
import com.univibe.app.ui.theme.UniVibeTheme
import com.univibe.app.ui.wall.WallScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { UniVibeTheme { Root() } }
    }
}

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Discover("discover", "Discover", Icons.Filled.Explore),
    Matches("matches", "Connect", Icons.Filled.Groups),
    Chat("inbox", "Chats", Icons.Filled.Chat),
    Wall("wall", "Wall", Icons.Filled.Forum),
    Profile("me", "Me", Icons.Filled.Person),
}

@Composable
private fun Root() {
    val context = LocalContext.current
    val session = context.app.session
    val scope = rememberCoroutineScope()

    // null = still reading token from disk
    var loaded by remember { mutableStateOf(false) }
    var loggedIn by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        loggedIn = session.load() != null
        loaded = true
    }
    // Server said our token is no longer valid -> back to login
    LaunchedEffect(Unit) {
        Network.unauthorized.collect {
            session.clear()
            loggedIn = false
        }
    }

    if (!loaded) {
        LoadingBox()
        return
    }
    if (!loggedIn) {
        LoginScreen(onLoggedIn = { token ->
            scope.launch {
                session.save(token)
                loggedIn = true
            }
        })
        return
    }
    MainScaffold(onLogout = { scope.launch { session.clear(); loggedIn = false } })
}

@Composable
private fun MainScaffold(onLogout: () -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route
    val showBar = Tab.entries.any { it.route == currentRoute }

    Scaffold(
        containerColor = Paper,
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = Paper) {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Ink, selectedTextColor = Ink, indicatorColor = Cyan,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            NavHost(nav, startDestination = Tab.Discover.route) {
                composable(Tab.Discover.route) {
                    DiscoverScreen(
                        onOpenProfile = { nav.navigate("profile/$it") },
                        onOpenChat = { nav.navigate("chat/$it") },
                        onOpenNotifications = { nav.navigate("notifications") },
                    )
                }
                composable(Tab.Matches.route) {
                    MatchesScreen(
                        onOpenProfile = { nav.navigate("profile/$it") },
                        onOpenChat = { nav.navigate("chat/$it") },
                    )
                }
                composable(Tab.Chat.route) { InboxScreen(onOpenChat = { nav.navigate("chat/$it") }) }
                composable(Tab.Wall.route) { WallScreen() }
                composable(Tab.Profile.route) { MyProfileScreen(onLogout = onLogout) }
                composable("notifications") { NotificationsScreen(onBack = { nav.popBackStack() }) }
                composable(
                    "profile/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) { PublicProfileScreen(userId = it.arguments!!.getLong("id"), onBack = { nav.popBackStack() }) }
                composable(
                    "chat/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) { ChatScreen(conversationId = it.arguments!!.getLong("id"), onBack = { nav.popBackStack() }) }
            }
        }
    }
}
