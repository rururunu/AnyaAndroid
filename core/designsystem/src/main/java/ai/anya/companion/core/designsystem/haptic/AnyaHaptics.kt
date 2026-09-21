package ai.anya.companion.core.designsystem.haptic

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.swmansion.pulsar.Pulsar
import com.swmansion.pulsar.presets.PresetsWrapper
import com.swmansion.pulsar.types.CompatibilityMode

/**
 * Real device vibration — Compose [androidx.compose.ui.hapticfeedback.HapticFeedback]
 * is a no-op on many OEM builds / when the system "touch vibration" toggle is off.
 *
 * Feedback comes from Pulsar presets. Every method keeps the hand-rolled waveform it used
 * before as its fallback, so a device Pulsar cannot serve still gets the old behaviour
 * instead of silently going dead.
 */
public class AnyaHaptics(private val context: Context) {
    private val pulsar: Pulsar? = runCatching { Pulsar(context.applicationContext) }.getOrNull()
    private val presets: PresetsWrapper? = pulsar?.let { engine ->
        runCatching { engine.getPresets() }.getOrNull()
    }
    private val pulsarReady: Boolean = pulsar?.let { engine ->
        presets != null && runCatching {
            engine.hapticSupport() != CompatibilityMode.NO_SUPPORT
        }.getOrDefault(false)
    } ?: false

    /** Plays [preset] through Pulsar; returns false when the fallback waveform should run. */
    private fun playPreset(preset: (PresetsWrapper) -> Unit): Boolean {
        val target = presets
        if (!pulsarReady || target == null) return false
        return runCatching { preset(target) }.isSuccess
    }

    /**
     * Linear-feeling switch haptic with stronger energy for tab/menu/session transitions.
     */
    public fun linearTick() {
        if (playPreset { it.systemSegmentTick() }) return
        vibrate(
            timings = longArrayOf(0, 12, 10, 14),
            amplitudes = intArrayOf(0, 72, 132, 192),
        )
    }

    /** Button press — strong at top, easing downward like a physical tap. */
    public fun buttonPress() {
        if (playPreset { it.systemImpactMedium() }) return
        vibrate(
            timings = longArrayOf(0, 10, 8, 12, 8),
            amplitudes = intArrayOf(0, 200, 150, 100, 60),
        )
    }

    public fun tick() {
        if (playPreset { it.systemEffectTick() }) return
        vibrate(longArrayOf(0, 18), amplitudes = intArrayOf(0, 90))
    }

    public fun confirm() {
        if (playPreset { it.systemNotificationSuccess() }) return
        vibrate(longArrayOf(0, 28, 40, 36), amplitudes = intArrayOf(0, 140, 0, 180))
    }

    public fun reject() {
        if (playPreset { it.systemNotificationError() }) return
        vibrate(longArrayOf(0, 40, 50, 40), amplitudes = intArrayOf(0, 200, 0, 120))
    }

    /**
     * Jelly bounce for the mascot's pleased wiggle: a soft lift, a pop, then two
     * decaying ticks. Uses composition primitives when the motor supports them.
     */
    public fun mascotPleased() {
        if (playPreset { it.chirp() }) return
        val vibrator = vibratorOrNull() ?: return
        try {
            if (tryMascotComposition(vibrator)) return
            vibrate(
                timings = longArrayOf(0, 12, 28, 16, 44, 12, 58, 8),
                amplitudes = intArrayOf(0, 210, 0, 145, 0, 88, 0, 40),
            )
        } catch (_: SecurityException) {
            // Missing VIBRATE (or OEM-denied) must never crash UI navigation.
        }
    }

    /** Picker or selection-row change — the classic Android selection tick. */
    public fun selection() {
        if (playPreset { it.systemSelection() }) return
        tick()
    }

    /** An operation finished successfully. */
    public fun success() {
        if (playPreset { it.systemNotificationSuccess() }) return
        confirm()
    }

    /** Non-blocking heads-up, for things worth flagging without interrupting. */
    public fun warning() {
        if (playPreset { it.systemNotificationWarning() }) return
        tick()
    }

    /** An operation failed. */
    public fun error() {
        if (playPreset { it.systemNotificationError() }) return
        reject()
    }

    /** Irreversible, high-stakes action such as a delete. */
    public fun destructive() {
        if (playPreset { it.cleave() }) return
        reject()
    }

    /** Long-press activation, or a charge that has completed. */
    public fun longPress() {
        if (playPreset { it.systemLongPress() }) return
        buttonPress()
    }

    /** An inbound message or alert that wants the user's attention. */
    public fun notification() {
        if (playPreset { it.chime() }) return
        confirm()
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

    private fun tryMascotComposition(vibrator: Vibrator): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        return try {
            val rise = VibrationEffect.Composition.PRIMITIVE_QUICK_RISE
            val click = VibrationEffect.Composition.PRIMITIVE_CLICK
            val tick = VibrationEffect.Composition.PRIMITIVE_TICK
            val lowTick = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibrationEffect.Composition.PRIMITIVE_LOW_TICK
            } else {
                null
            }
            val ids = if (lowTick != null) {
                intArrayOf(rise, click, tick, lowTick)
            } else {
                intArrayOf(rise, click, tick)
            }
            if (!vibrator.areAllPrimitivesSupported(*ids)) return false
            val composition = VibrationEffect.startComposition()
                .addPrimitive(rise, 0.5f)
                .addPrimitive(click, 1.0f, 18)
                .addPrimitive(tick, 0.55f, 40)
            if (lowTick != null) {
                composition.addPrimitive(lowTick, 0.35f, 52)
            }
            vibrator.vibrate(composition.compose())
            true
        } catch (_: Throwable) {
            false
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
