package com.example

import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.NexoIcons

object NexoOnboardingStore {
    private const val PREFS = "nexo_onboarding_v3"
    private const val KEY_DONE = "first_run_done"
    fun shouldShow(context: Context): Boolean =
        !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DONE, false)
    fun markDone(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_DONE, true).apply()
    }
}

@Composable
fun NexoDriveAccountsTopBar(
    activeEmail: String?,
    isEs: Boolean,
    onSwitchAccount: (String) -> Unit,
    onAddAccount: () -> Unit,
) {
    val accounts = NexoMultiGoogleStore.accounts
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        accounts.take(5).forEach { acc ->
            val selected = acc.email.equals(activeEmail, ignoreCase = true)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                modifier = Modifier.clickable { onSwitchAccount(acc.email) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (selected) Icon(Icons.Rounded.Check, null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(acc.email.substringBefore("@"), fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (accounts.size < 5) {
            OutlinedButton(
                onClick = onAddAccount,
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Icon(Icons.Rounded.Add, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (isEs) "Cuenta (${accounts.size}/5)" else "Account (${accounts.size}/5)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun NexoDriveDisabledGate(isEs: Boolean, onEnableDrive: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(NexoIcons.Cloud, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(14.dp))
        Text(if (isEs) "GOOGLE DRIVE DESACTIVADO" else "GOOGLE DRIVE DISABLED", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(6.dp))
        Text(if (isEs) "Activa la integración cuando quieras gestionar tus cuentas en la nube." else "Enable Drive integration whenever you want to manage cloud accounts.", fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onEnableDrive) { Text(if (isEs) "ACTIVAR GOOGLE DRIVE" else "ENABLE GOOGLE DRIVE", fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun NexoFirstRunOnboardingDialog(
    context: Context,
    isEs: Boolean,
    onRequestStorage: () -> Unit,
    onConnectDrive: () -> Unit,
    onComplete: () -> Unit,
) {
    val hasStorage = if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager() else true
    AlertDialog(
        onDismissRequest = {},
        title = { Text(if (isEs) "Bienvenido a NexoFiles" else "Welcome to NexoFiles", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (isEs) "Configura tus permisos y módulos opcionales antes de comenzar:" else "Configure permissions and optional modules before starting:",
                    fontSize = 12.sp
                )
                if (!hasStorage) {
                    Button(onClick = onRequestStorage, modifier = Modifier.fillMaxWidth()) {
                        Text(if (isEs) "Autorizar acceso a archivos (obligatorio)" else "Grant storage access (required)", fontSize = 12.sp)
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (isEs) "Usar Bóveda Privada" else "Enable Private Vault", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Switch(checked = NexoFeatureFlags.vaultEnabled, onCheckedChange = { NexoFeatureFlags.setVault(context, it) })
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (isEs) "Usar Google Drive" else "Enable Google Drive", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Switch(checked = NexoFeatureFlags.driveEnabled, onCheckedChange = { NexoFeatureFlags.setDrive(context, it) })
                }
                if (NexoFeatureFlags.driveEnabled) {
                    OutlinedButton(onClick = onConnectDrive, modifier = Modifier.fillMaxWidth()) {
                        Text(if (isEs) "Vincular cuenta principal de Google" else "Link primary Google account", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { NexoOnboardingStore.markDone(context); onComplete() }) {
                Text(if (isEs) "Comenzar" else "Start")
            }
        }
    )
}
