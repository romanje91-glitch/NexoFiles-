package com.example

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Environment
import java.io.File

object NexoMasterDirs {
    val rootDir: File
        get() = File(Environment.getExternalStorageDirectory(), "NexoFiles").apply { if (!exists()) mkdirs() }
    val receivedDir: File
        get() = File(rootDir, "Received").apply { if (!exists()) mkdirs() }
    val torrentsDir: File
        get() = File(rootDir, "Torrents").apply { if (!exists()) mkdirs() }
    val browserDownloadsDir: File
        get() = File(rootDir, "BrowserDownloads").apply { if (!exists()) mkdirs() }
    val exportsDir: File
        get() = File(rootDir, "Exports").apply { if (!exists()) mkdirs() }

    fun ensureAll() {
        runCatching {
            rootDir; receivedDir; torrentsDir; browserDownloadsDir; exportsDir
        }
    }
}

object NexoVaultFlipGuard {
    private const val PREFS = "nexo_vault_flip_prefs"
    private const val KEY_ENABLED = "flip_close_enabled"
    private const val KEY_TARGET_PKG = "flip_target_pkg"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun getTargetPackage(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TARGET_PKG, "com.whatsapp") ?: "com.whatsapp"

    fun setTargetPackage(context: Context, pkg: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_TARGET_PKG, pkg).apply()
    }

    fun registerListener(context: Context, onFaceDown: () -> Unit): () -> Unit {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return {}
        val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return {}
        var wasFaceUp = false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val z = event.values[2]
                if (z > 6.5f) wasFaceUp = true
                if (wasFaceUp && z < -7.5f) {
                    wasFaceUp = false
                    onFaceDown()
                    val pkg = getTargetPackage(context)
                    val launch = context.packageManager.getLaunchIntentForPackage(pkg)
                    if (launch != null) {
                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(launch) }
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sm.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
        return { runCatching { sm.unregisterListener(listener) } }
    }
}
