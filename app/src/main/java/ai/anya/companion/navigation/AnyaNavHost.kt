package ai.anya.companion.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ai.anya.companion.core.designsystem.component.AnyaLoadingIndicator
import ai.anya.companion.core.domain.repository.ConnectionState
import ai.anya.companion.feature.chat.ChatRoute
import ai.anya.companion.feature.pairing.PairingRoute
import ai.anya.companion.feature.workspace.WorkspaceRoute
import ai.anya.companion.ui.RootViewModel
import android.net.Uri

object Routes {
    const val Pairing = "pairing"
    const val Main = "main"
    const val Chat = "chat/{sessionId}?messageId={messageId}"
    const val Workspace = "workspace"

    fun chat(sessionId: String, messageId: String? = null): String {
        val base = "chat/${Uri.encode(sessionId)}"
        return if (messageId.isNullOrBlank()) {
            base
        } else {
            "$base?messageId=${Uri.encode(messageId)}"
        }
    }
}

@Composable
fun AnyaNavHost(
    initialPairUri: String? = null,
    rootViewModel: RootViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val rootState by rootViewModel.state.collectAsStateWithLifecycle()
    val startDestination = if (rootState.hasCredential) Routes.Main else Routes.Pairing

    // Only block cold start while actively connecting — already-connected users enter main immediately.
    var coldStart by remember { mutableStateOf(rootState.hasCredential) }
    var showBootSplash by remember {
        mutableStateOf(
            rootState.hasCredential &&
                (
                    rootState.connectionState == ConnectionState.Connecting ||
                        rootState.connectionState == ConnectionState.Reconnecting
                    ),
        )
    }
    LaunchedEffect(rootState.connectionState, rootState.hasCredential) {
        if (!rootState.hasCredential) {
            coldStart = false
            showBootSplash = false
            return@LaunchedEffect
        }
        when (rootState.connectionState) {
            ConnectionState.Connected -> {
                showBootSplash = false
                coldStart = false
            }
            ConnectionState.Connecting, ConnectionState.Reconnecting -> {
                if (coldStart) showBootSplash = true
            }
            ConnectionState.Error, ConnectionState.Disconnected -> {
                showBootSplash = false
                coldStart = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
        ) {
            composable(Routes.Pairing) {
                PairingRoute(
                    initialPairUri = initialPairUri,
                    onPaired = {
                        navController.navigate(Routes.Main) {
                            popUpTo(Routes.Pairing) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.Main) {
                MainRoute(
                    onOpenSession = { id, messageId ->
                        navController.navigate(Routes.chat(id, messageId))
                    },
                    onRePair = {
                        navController.navigate(Routes.Pairing) {
                            popUpTo(Routes.Main) { inclusive = true }
                        }
                    },
                )
            }
            composable(
                route = Routes.Chat,
                arguments = listOf(
                    navArgument("sessionId") { type = NavType.StringType },
                    navArgument("messageId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) {
                ChatRoute(onBack = { navController.popBackStack() })
            }
            composable(Routes.Workspace) {
                WorkspaceRoute()
            }
        }

        if (showBootSplash && rootState.hasCredential) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background,
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.colorScheme.background,
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AnyaLoadingIndicator(
                        size = 112.dp,
                        label = null,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Anya",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when (rootState.connectionState) {
                            ConnectionState.Reconnecting -> "正在重新连接…"
                            ConnectionState.Error -> "连接失败，请稍后重试"
                            else -> "正在为你准备…"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
