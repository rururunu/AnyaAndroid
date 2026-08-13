package ai.anya.companion

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import ai.anya.companion.core.data.local.AppLocale
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import ai.anya.companion.core.designsystem.theme.AnyaTheme
import ai.anya.companion.core.domain.repository.AppUpdateMonitor
import ai.anya.companion.core.domain.repository.ConnectionRepository
import ai.anya.companion.navigation.AnyaNavHost
import ai.anya.companion.notify.CompanionNotifier
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var pairUri by mutableStateOf<String?>(null)
    private var openAbout by mutableStateOf(false)

    @Inject
    lateinit var connectionRepository: ConnectionRepository

    @Inject
    lateinit var updateMonitor: AppUpdateMonitor

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) maybeStartKeepAliveService()
        }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeIntent(intent)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            maybeStartKeepAliveService()
        } else {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) maybeStartKeepAliveService()
        }
        setContent {
            AnyaTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AnyaNavHost(
                        initialPairUri = pairUri,
                        openAbout = openAbout,
                        onOpenAboutConsumed = { openAbout = false },
                    )
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun maybeStartKeepAliveService() {
        val serviceIntent = Intent(this, KeepAliveService::class.java)
        // Use startForegroundService: required for API 26+.
        startForegroundService(serviceIntent)
    }

    override fun onResume() {
        super.onResume()
        // Background sockets are often silently killed by the OS / carrier NAT;
        // verify and reconnect as soon as the user comes back.
        connectionRepository.nudge()
        updateMonitor.onForeground()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeIntent(intent)
    }

    private fun consumeIntent(intent: Intent?) {
        pairUri = intent?.dataString
        if (intent?.getBooleanExtra(CompanionNotifier.EXTRA_OPEN_ABOUT, false) == true) {
            openAbout = true
        }
    }
}
