package com.example

import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URL
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

object NexoShareLinks {
    const val ANDROID_URL = "https://github.com/romanje91-glitch/NexoFiles-/releases/latest"
    var showShareDialog by mutableStateOf(false)
    fun checkUpdateNow(context: Context) {
        val main = Handler(Looper.getMainLooper())
        main.post { Toast.makeText(context, "Verificando actualizaciones...", Toast.LENGTH_SHORT).show() }
        Thread {
            runCatching {
                val c = (URL("https://api.github.com/repos/romanje91-glitch/NexoFiles-/releases/latest").openConnection() as HttpURLConnection).apply {
                    setRequestProperty("Accept", "application/vnd.github+json"); setRequestProperty("User-Agent", "NexoFiles")
                }
                val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                val tag = j.optString("tag_name","").removePrefix("v").trim()
                val cur = BuildConfig.VERSION_NAME.removePrefix("v").trim()
                var url = j.optString("html_url", ANDROID_URL)
                val arr = j.optJSONArray("assets")
                if (arr != null) for (i in 0 until arr.length()) {
                    val a = arr.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk", true)) { url = a.optString("browser_download_url", url); break }
                }
                main.post {
                    if (tag.isBlank() || tag.equals(cur, true)) Toast.makeText(context, "Al día (v$cur)", Toast.LENGTH_LONG).show()
                    else { Toast.makeText(context, "Nueva v$tag disponible", Toast.LENGTH_LONG).show(); context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            }.onFailure { e -> main.post { Toast.makeText(context, "Error OTA: ${e.localizedMessage}", Toast.LENGTH_LONG).show() } }
        }.start()
    }
}

@Composable
fun NexoShareAppDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val activeUrl = NexoShareLinks.ANDROID_URL

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Compartir NexoFiles (Android)") },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                NexoQrBitmapImage(content = activeUrl, modifier = Modifier.size(196.dp))

                Text(
                    text = "Descarga directa APK · NexoFiles ${BuildConfig.VERSION_NAME}",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp
                )
                Text(
                    text = activeUrl,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("NexoFiles", activeUrl))
                            Toast.makeText(context, "Enlace copiado", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Copiar link", fontSize = 12.sp) }

                    Button(
                        onClick = {
                            val msg = "Descarga NexoFiles para Android aquí: $activeUrl"
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "NexoFiles Android")
                                putExtra(Intent.EXTRA_TEXT, msg)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(Intent.createChooser(intent, "Compartir NexoFiles"))
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Compartir", fontSize = 12.sp) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

object NexoNotificationPrefs {
    private const val PREFS = "nexo_notif_prefs_v24b"
    const val KEY_VAULT_BACKUP = "notif_vault_backup"
    const val KEY_TORRENTS = "notif_torrents"
    const val KEY_TRANSFERS = "notif_transfers"
    const val KEY_OTA = "notif_ota"

    fun isEnabled(context: Context, key: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(key, true)

    fun setEnabled(context: Context, key: String, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(key, value).apply()
    }
}

data class NexoGoogleAccountEntry(
    val email: String,
    val name: String,
    val accessToken: String = ""
)

object NexoMultiGoogleStore {
    private const val PREFS = "nexo_multi_google_v24b"
    private const val KEY_ACCOUNTS = "accounts_json"
    private const val KEY_PRIMARY_APP_EMAIL = "primary_app_email"

    val accounts = mutableStateListOf<NexoGoogleAccountEntry>()
    var primaryAppEmail by mutableStateOf("")
    private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        loaded = true
        runCatching {
            val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            primaryAppEmail = sp.getString(KEY_PRIMARY_APP_EMAIL, "").orEmpty()
            val raw = sp.getString(KEY_ACCOUNTS, "[]") ?: "[]"
            val arr = JSONArray(raw)
            accounts.clear()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val email = o.optString("email")
                if (email.isNotBlank()) {
                    accounts.add(NexoGoogleAccountEntry(email, o.optString("name", email), o.optString("token", "")))
                }
            }
        }
    }

    private fun persist(context: Context) {
        val arr = JSONArray()
        accounts.forEach { a ->
            arr.put(JSONObject().put("email", a.email).put("name", a.name).put("token", a.accessToken))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ACCOUNTS, arr.toString())
            .putString(KEY_PRIMARY_APP_EMAIL, primaryAppEmail)
            .apply()
    }

    fun registerAccount(context: Context, email: String?, name: String?, accessToken: String? = null) {
        val clean = email?.trim().orEmpty()
        if (clean.isBlank()) return
        init(context)
        if (primaryAppEmail.isBlank()) primaryAppEmail = clean
        val idx = accounts.indexOfFirst { it.email.equals(clean, ignoreCase = true) }
        val resolvedName = name?.ifBlank { clean } ?: clean
        val resolvedToken = accessToken?.ifBlank { null } ?: if (idx >= 0) accounts[idx].accessToken else ""
        if (idx >= 0) {
            accounts[idx] = NexoGoogleAccountEntry(clean, resolvedName, resolvedToken)
        } else {
            accounts.add(NexoGoogleAccountEntry(clean, resolvedName, resolvedToken))
        }
        persist(context)
    }

    fun setPrimaryAppAccount(context: Context, email: String) {
        init(context)
        primaryAppEmail = email
        persist(context)
    }

    fun getAccount(email: String): NexoGoogleAccountEntry? =
        accounts.firstOrNull { it.email.equals(email, ignoreCase = true) }
}
