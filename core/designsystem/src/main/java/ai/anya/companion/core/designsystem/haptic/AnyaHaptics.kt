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
    /**
     * Linear-feeling switch haptic with stronger energy for tab/menu/session transitions.
     */
    public fun linearTick() {
        vibrate(
            timings = longArrayOf(0, 12, 10, 14),
            amplitudes = intArrayOf(0, 72, 132, 192),
        )
    }

    /** Button press — strong at top, easing downward like a physical tap. */
    public fun buttonPress() {
        vibrate(
            timings = longArrayOf(0, 10, 8, 12, 8),
            amplitudes = intArrayOf(0, 200, 150, 100, 60),
        )
    }

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
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (vibrator.hasAmplitudeControl()) {
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                } else {
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, -1))
                }
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(timings, -1)
            }
        } catch (_: SecurityException) {
            // Missing VIBRATE (or OEM-denied) must never crash UI navigation.
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
