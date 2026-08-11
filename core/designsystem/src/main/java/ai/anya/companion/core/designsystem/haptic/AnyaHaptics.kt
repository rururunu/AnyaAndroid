package ai.anya.companion.core.designsystem.haptic

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Real device vibration — Compose [androidx.compose.ui.hapticfeedback.HapticFeedback]
 * is a no-op on many OEM builds / when the system "touch vibration" toggle is off.
 */
public class AnyaHaptics(private val context: Context) {
    public fun tick() {
        vibrate(longArrayOf(0, 18), amplitudes = intArrayOf(0, 90))
    }

    public fun confirm() {
        vibrate(longArrayOf(0, 28, 40, 36), amplitudes = intArrayOf(0, 140, 0, 180))
    }

    public fun reject() {
        vibrate(longArrayOf(0, 40, 50, 40), amplitudes = intArrayOf(0, 200, 0, 120))
    }

    private fun vibrate(timings: LongArray, amplitudes: IntArray) {
        val vibrator = vibratorOrNull() ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator.hasAmplitudeControl()) {
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                vibrator.vibrate(VibrationEffect.createWaveform(timings, -1))
            }
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(timings, -1)
        }
    }

    private fun vibratorOrNull(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }?.takeIf { it.hasVibrator() }
    }
}

@Composable
public fun rememberAnyaHaptics(): AnyaHaptics {
    val context = LocalContext.current
    return remember(context) { AnyaHaptics(context.applicationContext) }
}
