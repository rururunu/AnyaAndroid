package ai.anya.companion.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ai.anya.companion.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ai.anya.companion.core.designsystem.component.AnyaLoadingIndicator
import ai.anya.companion.core.designsystem.component.AnyaSecondaryButton
import ai.anya.companion.core.designsystem.theme.AnyaSpace
import ai.anya.companion.core.domain.repository.ConnectionState
import ai.anya.companion.feature.chat.ChatRoute
import ai.anya.companion.feature.chat.NewChatSessionId
import ai.anya.companion.feature.pairing.PairingRoute
import ai.anya.companion.feature.settings.AboutSettingsRoute
import ai.anya.companion.feature.settings.ConnectionSettingsRoute
import ai.anya.companion.feature.settings.GeneralSettingsRoute
import ai.anya.companion.feature.workspace.WorkspaceRoute
import ai.anya.companion.ui.RootViewModel
import android.net.Uri
import kotlinx.coroutines.delay

object Routes {
    const val Pairing = "pairing?repairDeviceId={repairDeviceId}"
    const val Main = "main"
    const val Chat = "chat/{sessionId}?messageId={messageId}&workspaceId={workspaceId}"
    const val Workspace = "workspace"
    const val SettingsConnection = "settings/connection"
    const val SettingsGeneral = "settings/general"
    const val SettingsAbout = "settings/about"

    fun pairing(repairDeviceId: String? = null): String {
        val id = repairDeviceId?.trim().orEmpty()
        return if (id.isEmpty()) "pairing" else "pairing?repairDeviceId=${Uri.encode(id)}"
    }

    fun chat(sessionId: String, messageId: String? = null, workspaceId: String? = null): String {
        val base = "chat/${Uri.encode(sessionId)}"
        val query = buildList {
            if (!messageId.isNullOrBlank()) add("messageId=${Uri.encode(messageId)}")
            if (!workspaceId.isNullOrBlank()) add("workspaceId=${Uri.encode(workspaceId)}")
        }
        return if (query.isEmpty()) base else "$base?${query.joinToString("&")}"
    }
}

@Composable
fun AnyaNavHost(
    initialPairUri: String? = null,
    openAbout: Boolean = false,
    onOpenAboutConsumed: () -> Unit = {},
    rootViewModel: RootViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val rootState by rootViewModel.state.collectAsStateWithLifecycle()
    val startDestination = if (rootState.hasCredential) Routes.Main else "pairing"
    var coldStart by remember { mutableStateOf(rootState.hasCredential) }
    var showBootSplash by remember {
        mutableStateOf(
            rootState.hasCredential &&
                rootState.connectionState != ConnectionState.Connected,
        )
    }
    var showCancelConnect by remember { mutableStateOf(false) }

    fun goToConnectionPage() {
        showBootSplash = false
        showCancelConnect = false
        coldStart = false
        if (!rootState.hasCredential) return
        val route = navController.currentDestination?.route
        if (route == Routes.Pairing || route == Routes.SettingsConnection) return
        navController.navigate(Routes.SettingsConnection) {
            launchSingleTop = true
        }
    }

    fun cancelBootConnect() {
        rootViewModel.onBootConnectTimedOut()
        goToConnectionPage()
    }

    LaunchedEffect(rootState.connectionState, rootState.hasCredential) {
        if (!rootState.hasCredential) {
            coldStart = false
            showBootSplash = false
            showCancelConnect = false
            return@LaunchedEffect
        }
        when (rootState.connectionState) {
            ConnectionState.Connected -> {
                showBootSplash = false
                showCancelConnect = false
                coldStart = false
            }
            ConnectionState.Error -> {
                if (coldStart) goToConnectionPage()
            }
            ConnectionState.Connecting, ConnectionState.Reconnecting -> {
                if (coldStart) showBootSplash = true
            }
            ConnectionState.Disconnected -> Unit
        }
    }

    LaunchedEffect(showBootSplash, coldStart) {
        if (!showBootSplash || !coldStart) {
            showCancelConnect = false
            return@LaunchedEffect
        }
        delay(5_000)
        if (showBootSplash && coldStart &&
            rootState.connectionState != ConnectionState.Connected
        ) {
            showCancelConnect = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
        ) {
            composable(
                route = Routes.Pairing,
                arguments = listOf(
                    navArgument("repairDeviceId") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) {
                val canGoBack = navController.previousBackStackEntry != null
                PairingRoute(
                    initialPairUri = if (canGoBack) null else initialPairUri,
                    onBack = if (canGoBack) {
                        { navController.popBackStack() }
                    } else {
                        null
                    },
                    onPaired = {
                        if (!navController.popBackStack(Routes.Main, inclusive = false)) {
                            navController.navigate(Routes.Main) {
                                popUpTo(Routes.Pairing) { inclusive = true }
                            }
                        }
                    },
                )
            }
            composable(Routes.Main) {
                MainRoute(
                    onOpenSession = { id, messageId ->
                        navController.navigate(Routes.chat(id, messageId))
                    },
                    onNewSession = { workspaceId ->
                        navController.navigate(
                            Routes.chat(NewChatSessionId, workspaceId = workspaceId),
                        )
                    },
                    onOpenConnectionSettings = {
                        navController.navigate(Routes.SettingsConnection)
                    },
                    onOpenGeneralSettings = {
                        navController.navigate(Routes.SettingsGeneral)
                    },
                    onOpenAboutSettings = {
                        navController.navigate(Routes.SettingsAbout)
                    },
                    onAddDevice = {
                        navController.navigate(Routes.pairing())
                    },
                    onUnpairLast = {
                        navController.navigate(Routes.pairing()) {
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
                    navArgument("workspaceId") {
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
            composable(Routes.SettingsConnection) {
                ConnectionSettingsRoute(
                    onBack = { navController.popBackStack() },
                    onAddDevice = {
                        navController.navigate(Routes.pairing())
                    },
                    onRepairDevice = { deviceId ->
                        navController.navigate(Routes.pairing(deviceId))
                    },
                    onUnpairLast = {
                        navController.navigate(Routes.pairing()) {
                            popUpTo(Routes.Main) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.SettingsGeneral) {
                GeneralSettingsRoute(onBack = { navController.popBackStack() })
            }
            composable(Routes.SettingsAbout) {
                AboutSettingsRoute(onBack = { navController.popBackStack() })
            }
        }

        LaunchedEffect(openAbout, rootState.hasCredential) {
            if (!openAbout || !rootState.hasCredential) return@LaunchedEffect
            val current = navController.currentDestination?.route
            if (current == Routes.Pairing) {
                navController.navigate(Routes.Main) {
                    popUpTo(Routes.Pairing) { inclusive = true }
                }
            }
            if (navController.currentDestination?.route != Routes.SettingsAbout) {
                navController.navigate(Routes.SettingsAbout) {
                    launchSingleTop = true
                }
            }
            onOpenAboutConsumed()
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
                Column(
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .padding(horizontal = AnyaSpace.Screen),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
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
                            ConnectionState.Reconnecting -> stringResource(R.string.boot_reconnecting)
                            ConnectionState.Error -> stringResource(R.string.boot_error)
                            else -> stringResource(R.string.boot_preparing)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (showCancelConnect) {
                        Spacer(modifier = Modifier.height(24.dp))
                        AnyaSecondaryButton(
                            text = stringResource(R.string.boot_cancel_connect),
                            onClick = { cancelBootConnect() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
