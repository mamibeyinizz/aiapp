package com.example.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.widget.Toast

data class SoundOption(
    val id: String,
    val name: String,
    val soundUri: Uri?
)

object SoundSettingsManager {
    const val PREFS_NAME = "qr_servis_settings"

    const val KEY_SOUND_GARSON = "sound_garson"
    const val KEY_SOUND_HESAP = "sound_hesap"
    const val KEY_SOUND_SIPARIS = "sound_siparis"
    const val KEY_VOLUME = "volume"
    const val KEY_VIBRATION = "vibration"

    private var activeRingtone: Ringtone? = null

    val SOUND_OPTIONS = listOf(
        SoundOption("sys_ringtone", "Zil", RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)),
        SoundOption("sys_can", "Çan", RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)),
        SoundOption("sys_ding", "Ding", RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)),
        SoundOption("sys_klasik", "Klasik Bildirim", RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)),
        SoundOption("sys_alarm", "Alarm", RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)),
        SoundOption("silent", "Sessiz", null)
    )

    fun getSoundForCallType(context: Context, callType: String): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val defaultId = when (callType) {
            "garson" -> "sys_ringtone"
            "hesap" -> "sys_can"
            "siparis" -> "sys_ding"
            else -> "sys_klasik"
        }
        val stored = when (callType) {
            "garson" -> prefs.getString(KEY_SOUND_GARSON, defaultId)
            "hesap" -> prefs.getString(KEY_SOUND_HESAP, defaultId)
            "siparis" -> prefs.getString(KEY_SOUND_SIPARIS, defaultId)
            else -> prefs.getString(KEY_SOUND_GARSON, defaultId)
        } ?: defaultId

        // Migration from legacy sound IDs
        val migrated = when (stored) {
            "sound_zil" -> "sys_ringtone"
            "sound_can" -> "sys_can"
            "sound_ding" -> "sys_ding"
            "sound_alarm" -> "sys_alarm"
            "sound_klasik" -> "sys_klasik"
            else -> stored
        }

        if (migrated != stored) {
            setSoundForCallType(context, callType, migrated)
        }

        return migrated
    }

    fun setSoundForCallType(context: Context, callType: String, soundId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = when (callType) {
            "garson" -> KEY_SOUND_GARSON
            "hesap" -> KEY_SOUND_HESAP
            "siparis" -> KEY_SOUND_SIPARIS
            else -> KEY_SOUND_GARSON
        }
        prefs.edit().putString(key, soundId).apply()
    }

    fun getVolume(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_VOLUME, 80)
    }

    fun setVolume(context: Context, volume: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_VOLUME, volume.coerceIn(0, 100)).apply()
    }

    fun isVibrationEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_VIBRATION, true)
    }

    fun setVibrationEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_VIBRATION, enabled).apply()
    }

    private fun showToast(context: Context, text: String, length: Int = Toast.LENGTH_SHORT) {
        try {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context.applicationContext, text, length).show()
            }
        } catch (_: Exception) {}
    }

    fun playSoundById(context: Context, soundId: String, volume: Int) {
        if (soundId == "silent" || volume <= 0) {
            return
        }

        val option = SOUND_OPTIONS.find { it.id == soundId }
        val soundUri: Uri? = option?.soundUri ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        if (soundUri == null) {
            Log.e("SoundSettingsManager", "soundUri null for soundId: $soundId")
            showToast(context, "Seçilen ses dosyası bulunamadı", Toast.LENGTH_SHORT)
            return
        }

        try {
            activeRingtone?.stop()

            val ringtone = RingtoneManager.getRingtone(context, soundUri)
            if (ringtone == null) {
                Log.e("SoundSettingsManager", "RingtoneManager.getRingtone returned null for uri $soundUri")
                showToast(context, "Ses çalınamadı", Toast.LENGTH_SHORT)
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ringtone.volume = (volume / 100f).coerceIn(0f, 1f)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ringtone.audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build()
            }

            activeRingtone = ringtone
            ringtone.play()
        } catch (e: Exception) {
            Log.e("SoundSettingsManager", "Error in playSoundById for sound $soundId", e)
            showToast(context, "Ses çalınırken bir sorun oluştu", Toast.LENGTH_SHORT)
        }
    }

    fun playSoundAndVibrateForCallType(context: Context, callType: String) {
        try {
            val soundId = getSoundForCallType(context, callType)
            val volume = getVolume(context)
            val vibration = isVibrationEnabled(context)

            if (soundId != "silent" && volume > 0) {
                playSoundById(context, soundId, volume)
            }

            if (vibration) {
                vibrateDevice(context)
            }
        } catch (e: Exception) {
            Log.e("SoundSettingsManager", "Error in playSoundAndVibrateForCallType", e)
            showToast(context, "Bildirim testi sırasında hata oluştu", Toast.LENGTH_SHORT)
        }
    }

    fun vibrateDevice(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(500)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
