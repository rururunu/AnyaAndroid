package ai.anya.companion.feature.pairing

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import ai.anya.companion.core.designsystem.component.AnyaHeroHeader
import ai.anya.companion.core.designsystem.component.AnyaLoadingIndicator
import ai.anya.companion.core.designsystem.component.AnyaPrimaryButton
import ai.anya.companion.core.designsystem.component.AnyaScreen
import ai.anya.companion.core.designsystem.component.AnyaSecondaryButton
import ai.anya.companion.core.designsystem.component.AnyaTopBar
import ai.anya.companion.core.designsystem.theme.AnyaSpace
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background

@Composable
public fun PairingRoute(
    onPaired: () -> Unit,
    initialPairUri: String? = null,
    viewModel: PairingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(initialPairUri) {
        if (!initialPairUri.isNullOrBlank()) {
            viewModel.applyPairLink(initialPairUri)
        }
    }
    LaunchedEffect(state.paired, state.error) {
        if (state.paired && state.error == null) onPaired()
    }
    PairingScreen(
        state = state,
        onHostChange = viewModel::onHostChange,
        onPortChange = viewModel::onPortChange,
        onTokenChange = viewModel::onTokenChange,
        onScanResult = viewModel::applyPairLink,
        onSubmit = viewModel::submit,
    )
}

@Composable
public fun PairingScreen(
    state: PairingUiState,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onScanResult: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (!contents.isNullOrBlank()) onScanResult(contents)
    }

    AnyaScreen(topBar = { AnyaTopBar(title = "配对", showBrand = true, subtitle = "远程连接") }) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AnyaSpace.Screen, vertical = AnyaSpace.Lg),
                verticalArrangement = Arrangement.spacedBy(AnyaSpace.Xxl),
            ) {
                AnyaHeroHeader(
                    title = "连接到 Anya",
                    subtitle = "在桌面工作台打开「连接手机」，扫码或填写配对码。",
                    markSize = 148.dp,
                )
                Column(verticalArrangement = Arrangement.spacedBy(AnyaSpace.Lg)) {
                    Text("连接信息", style = MaterialTheme.typography.titleMedium)
                    AnyaField(value = state.host, onValueChange = onHostChange, label = "主机 / Tailscale IP")
                    AnyaField(value = state.port, onValueChange = onPortChange, label = "端口")
                    AnyaField(value = state.token, onValueChange = onTokenChange, label = "配对令牌 / 配对码")
                    if (state.error != null) {
                        Text(
                            text = state.error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                AnyaSecondaryButton(
                    text = "扫码连接",
                    onClick = {
                        val options = ScanOptions()
                            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            .setPrompt("扫描桌面 Anya 上的配对二维码")
                            .setBeepEnabled(false)
                            .setOrientationLocked(true)
                        scanLauncher.launch(options)
                    },
                    enabled = !state.isSubmitting,
                )
                AnyaPrimaryButton(
                    text = if (state.isSubmitting) "连接中…" else "配对并连接",
                    onClick = onSubmit,
                    enabled = !state.isSubmitting,
                )
                Spacer(modifier = Modifier.height(AnyaSpace.Xxxl))
            }

            if (state.isSubmitting) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.92f)),
                    contentAlignment = Alignment.Center,
                ) {
                    AnyaLoadingIndicator(label = "正在配对并连接…")
                }
            }
        }
    }
}

@Composable
private fun AnyaField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(AnyaSpace.ControlRadius),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedContainerColor = MaterialTheme.colorScheme.background,
            unfocusedContainerColor = MaterialTheme.colorScheme.background,
        ),
    )
}
