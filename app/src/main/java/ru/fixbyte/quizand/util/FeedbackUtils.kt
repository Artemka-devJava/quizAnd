package ru.fixbyte.quizand.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Короткая вибрация + звуковой сигнал при нажатии игровых кнопок (Ответить, Открыть/Закрыть
 * раунд, Верно/Неверно) — по аналогии с кнопкой ответа в теле/радио-викторинах.
 * Тихо проглатывает ошибки: на некоторых устройствах/эмуляторах нет вибромотора
 * или звуковой поток недоступен, и это не должно ломать игровой процесс.
 */
fun triggerButtonFeedback(context: Context) {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    } catch (_: Exception) {
    }

    try {
        val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                toneGenerator.release()
            } catch (_: Exception) {
            }
        }, 250)
    } catch (_: Exception) {
    }
}
