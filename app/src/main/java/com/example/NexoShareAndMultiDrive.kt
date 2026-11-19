package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

object NexoShareLinks {
    const val ANDROID_URL = "https://github.com/romanje91-glitch/NexoFiles-/releases/latest"
    const val IPHONE_PWA_URL = "https://romanje91-glitch.github.io/NexoFiles-/"
}

@Composable
fun NexoShareAppDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var selectedPlatform by remember { mutableStateOf("ANDROID") }
    val activeUrl = if (selectedPlatform == "ANDROID") NexoShareLinks.ANDROID_URL else NexoShareLinks.IPHONE_PWA_URL
    val activeLabel = if (selectedPlatform == "ANDROID") "Android (APK Directo)" else "iPhone (Web App Safari)"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Compartir NexoFiles") },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ANDROID" to "🤖 Android", "IPHONE" to "🍎 iPhone").forEach { (id, lbl) ->
                        val active = selectedPlatform == id
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f).clickable { selectedPlatform = id }
                        ) {
                            Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = lbl,
                                    color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                NexoQrBitmapImage(content = activeUrl, modifier = Modifier.size(190.dp))

                Text(
                    text = activeLabel,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp
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
                    ) { Text("Copiar", fontSize = 12.sp) }

                    Button(
                        onClick = {
                            val msg = if (selectedPlatform == "ANDROID") {
                                "Descarga NexoFiles para Android aquí: $activeUrl"
                            } else {
                                "Instala NexoFiles Web App en tu iPhone (Safari) aquí: $activeUrl"
                            }
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "NexoFiles")
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

data class NexoGoogleAccountEntry(val email: String, val name: String)

object NexoMultiGoogleStore {
    private const val PREFS = "nexo_multi_google_v23b"
    private const val KEY_ACCOUNTS = "accounts_json"

    val accounts = mutableStateListOf<NexoGoogleAccountEntry>()
    private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        loaded = true
        runCatching {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ACCOUNTS, "[]") ?: "[]"
            val arr = JSONArray(raw)
            accounts.clear()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val email = o.optString("email")
                if (email.isNotBlank()) accounts.add(NexoGoogleAccountEntry(email, o.optString("name", email)))
            }
        }
    }

    fun registerAccount(context: Context, email: String?, name: String?) {
        val clean = email?.trim().orEmpty()
        if (clean.isBlank()) return
        init(context)
        if (accounts.none { it.email.equals(clean, ignoreCase = true) }) {
            accounts.add(NexoGoogleAccountEntry(clean, name?.ifBlank { clean } ?: clean))
            val arr = JSONArray()
            accounts.forEach { a -> arr.put(JSONObject().put("email", a.email).put("name", a.name)) }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ACCOUNTS, arr.toString()).apply()
        }
    }
}
