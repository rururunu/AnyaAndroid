package ai.anya.companion.navigation

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.delay

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

    var coldStartPending by remember { mutableStateOf(rootState.hasCredential) }
    var showBootSplash by remember { mutableStateOf(rootState.hasCredential) }
    LaunchedEffect(rootState.connectionState, rootState.hasCredential) {
        if (!rootState.hasCredential) {
            coldStartPending = false
            showBootSplash = false
            return@LaunchedEffect
        }
        if (!coldStartPending) return@LaunchedEffect
        when (rootState.connectionState) {
            ConnectionState.Connected, ConnectionState.Error -> {
                delay(280)
                showBootSplash = false
                coldStartPending = false
            }
            ConnectionState.Connecting, ConnectionState.Reconnecting -> {
                showBootSplash = true
            }
            ConnectionState.Disconnected -> {
                delay(1_600)
                if (coldStartPending && rootState.connectionState == ConnectionState.Disconnected) {
                    showBootSplash = false
                    coldStartPending = false
                }
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
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                AnyaLoadingIndicator(
                    label = when (rootState.connectionState) {
                        ConnectionState.Reconnecting -> "正在重新连接…"
                        ConnectionState.Error -> "连接失败，请稍后重试"
                        else -> "请稍等，正在为您准备……"
                    },
                )
            }
        }
    }
}
