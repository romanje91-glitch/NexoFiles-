package com.example.data.repo

import com.example.NexoVaultExtraStore
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.example.R
import com.example.data.db.AutoBackupRuleEntity
import com.example.data.db.NexoDao
import com.example.data.model.DriveFileItem
import com.example.data.prefs.UserPreferencesRepository
import com.example.util.FormatUtils
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed class DriveAuthStepResult {
  data class Authorized(val accessToken: String, val email: String?, val name: String?) : DriveAuthStepResult()
  data class NeedsConsent(val pendingIntent: PendingIntent) : DriveAuthStepResult()
  data class Error(val message: String) : DriveAuthStepResult()
}

class GoogleDriveRepository(
  private val context: Context,
  private val dao: NexoDao,
  private val prefs: UserPreferencesRepository,
) {

  private val client = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)
    .build()

  private val driveScopes = listOf(
    Scope("https://www.googleapis.com/auth/drive.file"),
    Scope("https://www.googleapis.com/auth/drive"),
    Scope("https://www.googleapis.com/auth/userinfo.email"),
    Scope("https://www.googleapis.com/auth/userinfo.profile"),
  )

  fun observeAutoBackupRules(): Flow<List<AutoBackupRuleEntity>> = dao.observeAutoBackupRules()

  suspend fun signInWithCredentialManagerIfConfigured(activity: Activity): Pair<String?, String?> {
    val webClientId = context.getString(R.string.google_web_client_id).trim()
    if (webClientId.isEmpty()) return null to null
    return runCatching {
      val credentialManager = CredentialManager.create(activity)
      val option = GetSignInWithGoogleOption.Builder(webClientId).build()
      val request = GetCredentialRequest.Builder()
        .addCredentialOption(option)
        .build()
      val response = credentialManager.getCredential(activity, request)
      val cred = response.credential
      if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val googleCred = GoogleIdTokenCredential.createFrom(cred.data)
        prefs.setDriveAccount(googleCred.id, googleCred.displayName)
        googleCred.id to googleCred.displayName
      } else {
        null to null
      }
    }.getOrDefault(null to null)
  }

  suspend fun authorizeDrive(activity: Activity): DriveAuthStepResult {
    signInWithCredentialManagerIfConfigured(activity)

    return runCatching {
      val authRequest = AuthorizationRequest.builder()
        .setRequestedScopes(driveScopes)
        .build()
      val result = suspendCancellableCoroutine { cont ->
        Identity.getAuthorizationClient(activity)
          .authorize(authRequest)
          .addOnSuccessListener { cont.resume(it) }
          .addOnFailureListener { cont.resumeWithException(it) }
      }
      if (result.hasResolution() && result.pendingIntent != null) {
        DriveAuthStepResult.NeedsConsent(result.pendingIntent!!)
      } else {
        val token = result.accessToken
        if (token.isNullOrBlank()) {
          DriveAuthStepResult.Error("No se recibió token de acceso de Google Drive.")
        } else {
          val (email, name) = fetchUserInfo(token)
          if (email != null) prefs.setDriveAccount(email, name)
          DriveAuthStepResult.Authorized(token, email, name)
        }
      }
    }.getOrElse { e ->
      DriveAuthStepResult.Error(
        e.localizedMessage ?: "Error al autorizar con Google Identity Services"
      )
    }
  }

  /**
   * Refreshes an existing Google Drive OAuth2 access token silently in background
   * when an account is already linked, without interrupting the user unless re-consent is required.
   */
  suspend fun trySilentAuthorize(): DriveAuthStepResult {
    return runCatching {
      val authRequest = AuthorizationRequest.builder()
        .setRequestedScopes(driveScopes)
        .build()
      val result = suspendCancellableCoroutine { cont ->
        Identity.getAuthorizationClient(context)
          .authorize(authRequest)
          .addOnSuccessListener { cont.resume(it) }
          .addOnFailureListener { cont.resumeWithException(it) }
      }
      if (result.hasResolution() && result.pendingIntent != null) {
        DriveAuthStepResult.NeedsConsent(result.pendingIntent!!)
      } else {
        val token = result.accessToken
        if (token.isNullOrBlank()) {
          DriveAuthStepResult.Error("Token silencioso no disponible")
        } else {
          val (email, name) = fetchUserInfo(token)
          if (email != null) prefs.setDriveAccount(email, name)
          DriveAuthStepResult.Authorized(token, email, name)
        }
      }
    }.getOrElse { e ->
      DriveAuthStepResult.Error(e.localizedMessage ?: "No se pudo refrescar sesión de Drive")
    }
  }

  suspend fun handleAuthorizationIntentResult(data: Intent?): DriveAuthStepResult {
    return runCatching {
      val authResult = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
      val token = authResult.accessToken
      if (token.isNullOrBlank()) {
        DriveAuthStepResult.Error("Autorización cancelada o sin token válido.")
      } else {
        val (email, name) = fetchUserInfo(token)
        if (email != null) prefs.setDriveAccount(email, name)
        DriveAuthStepResult.Authorized(token, email, name)
      }
    }.getOrElse { e ->
      DriveAuthStepResult.Error(e.localizedMessage ?: "Error completando consentimiento de Google Drive")
    }
  }

  suspend fun signOut() {
    runCatching {
      CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
    }
    prefs.setDriveAccount(null, null)
  }

  private suspend fun fetchUserInfo(accessToken: String): Pair<String?, String?> = withContext(Dispatchers.IO) {
    runCatching {
      val req = Request.Builder()
        .url("https://www.googleapis.com/oauth2/v3/userinfo")
        .header("Authorization", "Bearer $accessToken")
        .build()
      client.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) return@use null to null
        val json = JSONObject(resp.body?.string().orEmpty())
        json.optString("email").takeIf { it.isNotBlank() } to
          json.optString("name").takeIf { it.isNotBlank() }
      }
    }.getOrDefault(null to null)
  }

  suspend fun listFiles(accessToken: String, folderId: String = "root"): Result<List<DriveFileItem>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val query = URLEncoder.encode("'$folderId' in parents and trashed = false", "UTF-8")
        val fields = URLEncoder.encode("files(id,name,mimeType,size,modifiedTime)", "UTF-8")
        val url = "https://www.googleapis.com/drive/v3/files?q=$query&fields=$fields&orderBy=folder,name&pageSize=100"
        val req = Request.Builder()
          .url(url)
          .header("Authorization", "Bearer $accessToken")
          .build()
        client.newCall(req).execute().use { resp ->
          val body = resp.body?.string().orEmpty()
          if (!resp.isSuccessful) {
            throw IllegalStateException("Google Drive API (${resp.code}): $body")
          }
          val filesJson = JSONObject(body).optJSONArray("files")
          val items = mutableListOf<DriveFileItem>()
          if (filesJson != null) {
            for (i in 0 until filesJson.length()) {
              val obj = filesJson.getJSONObject(i)
              val mime = obj.optString("mimeType")
              items.add(
                DriveFileItem(
                  id = obj.getString("id"),
                  name = obj.optString("name", "Sin nombre"),
                  mimeType = mime,
                  sizeBytes = obj.optString("size", "0").toLongOrNull() ?: 0L,
                  modifiedTime = obj.optString("modifiedTime", ""),
                  isFolder = mime == "application/vnd.google-apps.folder",
                )
              )
            }
          }
          items.filterNot {
            it.name.equals(VAULT_DRIVE_FOLDER_NAME, ignoreCase = true) ||
            it.name.equals("NexoVault_Encrypted", ignoreCase = true) ||
            it.name.endsWith(".nexo_vault", ignoreCase = true)
          }
        }
      }
    }

  suspend fun ensureVaultBackupFolder(accessToken: String): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
      val query = URLEncoder.encode(
        "name = '$VAULT_DRIVE_FOLDER_NAME' and mimeType = 'application/vnd.google-apps.folder' and 'root' in parents and trashed = false",
        "UTF-8",
      )
      val fields = URLEncoder.encode("files(id,name)", "UTF-8")
      val req = Request.Builder()
        .url("https://www.googleapis.com/drive/v3/files?q=$query&fields=$fields&pageSize=5")
        .header("Authorization", "Bearer $accessToken")
        .build()
      val existingId = client.newCall(req).execute().use { resp ->
        val body = resp.body?.string().orEmpty()
        if (!resp.isSuccessful) throw IllegalStateException("Google Drive API (${resp.code}): $body")
        val arr = JSONObject(body).optJSONArray("files")
        if (arr != null && arr.length() > 0) arr.getJSONObject(0).getString("id") else null
      }
      existingId ?: createFolder(accessToken, VAULT_DRIVE_FOLDER_NAME, "root").getOrThrow().id
    }
  }

  suspend fun ensureVaultSubfolder(accessToken: String, subfolderName: String): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
      val rootId = ensureVaultBackupFolder(accessToken).getOrThrow()
      val clean = subfolderName.trim()
      if (clean.isBlank()) return@runCatching rootId
      val safe = clean.replace("'", "\\'")
      val query = URLEncoder.encode(
        "name = '$safe' and mimeType = 'application/vnd.google-apps.folder' and '$rootId' in parents and trashed = false",
        "UTF-8",
      )
      val fields = URLEncoder.encode("files(id,name)", "UTF-8")
      val req = Request.Builder()
        .url("https://www.googleapis.com/drive/v3/files?q=$query&fields=$fields&pageSize=5")
        .header("Authorization", "Bearer $accessToken")
        .build()
      val existingId = client.newCall(req).execute().use { resp ->
        val body = resp.body?.string().orEmpty()
        if (!resp.isSuccessful) throw IllegalStateException("Google Drive API (${resp.code}): $body")
        val arr = JSONObject(body).optJSONArray("files")
        if (arr != null && arr.length() > 0) arr.getJSONObject(0).getString("id") else null
      }
      existingId ?: createFolder(accessToken, clean, rootId).getOrThrow().id
    }
  }

  suspend fun moveDriveFileToFolder(accessToken: String, fileId: String, newParentId: String): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
      val getReq = Request.Builder()
        .url("https://www.googleapis.com/drive/v3/files/$fileId?fields=parents")
        .header("Authorization", "Bearer $accessToken")
        .build()
      val prevParents = client.newCall(getReq).execute().use { resp ->
        val body = resp.body?.string().orEmpty()
        if (!resp.isSuccessful) throw IllegalStateException("Google Drive API (${resp.code}): $body")
        val arr = JSONObject(body).optJSONArray("parents")
        val list = mutableListOf<String>()
        if (arr != null) for (i in 0 until arr.length()) list.add(arr.getString(i))
        list
      }
      if (prevParents.contains(newParentId)) return@runCatching
      val removeParam = if (prevParents.isNotEmpty()) "&removeParents=${URLEncoder.encode(prevParents.joinToString(","), "UTF-8")}" else ""
      val patchReq = Request.Builder()
        .url("https://www.googleapis.com/drive/v3/files/$fileId?addParents=${URLEncoder.encode(newParentId, "UTF-8")}$removeParam")
        .header("Authorization", "Bearer $accessToken")
        .patch("{}".toRequestBody("application/json; charset=utf-8".toMediaType()))
        .build()
      client.newCall(patchReq).execute().use { resp ->
        if (!resp.isSuccessful) {
          val body = resp.body?.string().orEmpty()
          throw IllegalStateException("Error moviendo respaldo en Drive (${resp.code}): $body")
        }
      }
    }
  }

  suspend fun listVaultBackups(accessToken: String): Result<List<DriveFileItem>> = withContext(Dispatchers.IO) {
    runCatching {
      val rootId = ensureVaultBackupFolder(accessToken).getOrThrow()
      val rootChildren = listFiles(accessToken, rootId).getOrThrow()
      val collected = mutableListOf<DriveFileItem>()
      for (child in rootChildren) {
        if (!child.isFolder && child.name.endsWith(".nexo_vault", ignoreCase = true)) {
          NexoVaultExtraStore.driveBackupFolderMap[child.id] = ""
          collected.add(child)
        } else if (child.isFolder) {
          val subFiles = listFiles(accessToken, child.id).getOrNull().orEmpty()
          for (sf in subFiles) {
            if (!sf.isFolder && sf.name.endsWith(".nexo_vault", ignoreCase = true)) {
              NexoVaultExtraStore.driveBackupFolderMap[sf.id] = child.name
              collected.add(sf)
            }
          }
        }
      }
      collected
    }
  }

  suspend fun createFolder(accessToken: String, name: String, parentId: String = "root"): Result<DriveFileItem> =
    withContext(Dispatchers.IO) {
      runCatching {
        val metadata = JSONObject().apply {
          put("name", name)
          put("mimeType", "application/vnd.google-apps.folder")
          put("parents", org.json.JSONArray().put(parentId))
        }
        val req = Request.Builder()
          .url("https://www.googleapis.com/drive/v3/files?fields=id,name,mimeType,modifiedTime")
          .header("Authorization", "Bearer $accessToken")
          .post(metadata.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
          .build()
        client.newCall(req).execute().use { resp ->
          val body = resp.body?.string().orEmpty()
          if (!resp.isSuccessful) throw IllegalStateException("Error creando carpeta en Drive: ${resp.code}")
          val obj = JSONObject(body)
          DriveFileItem(
            id = obj.getString("id"),
            name = obj.optString("name", name),
            mimeType = "application/vnd.google-apps.folder",
            sizeBytes = 0L,
            modifiedTime = obj.optString("modifiedTime", ""),
            isFolder = true,
          )
        }
      }
    }

  suspend fun uploadFile(
    accessToken: String,
    localFile: File,
    parentFolderId: String = "root",
  ): Result<DriveFileItem> = withContext(Dispatchers.IO) {
    runCatching {
      require(localFile.exists() && localFile.isFile) { "El archivo local no existe" }
      val mime = FormatUtils.getMimeType(localFile.name)
      val metadata = JSONObject().apply {
        put("name", localFile.name)
        put("parents", org.json.JSONArray().put(parentFolderId))
      }.toString()

      if (localFile.length() <= 4L * 1024L * 1024L) {
        val multipart = MultipartBody.Builder()
          .setType("multipart/related".toMediaType())
          .addPart(metadata.toRequestBody("application/json; charset=utf-8".toMediaType()))
          .addPart(localFile.asRequestBody(mime.toMediaType()))
          .build()

        val req = Request.Builder()
          .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,mimeType,size,modifiedTime")
          .header("Authorization", "Bearer $accessToken")
          .post(multipart)
          .build()

        client.newCall(req).execute().use { resp ->
          val body = resp.body?.string().orEmpty()
          if (!resp.isSuccessful) throw IllegalStateException("Error subiendo archivo (${resp.code}): $body")
          val obj = JSONObject(body)
          DriveFileItem(
            id = obj.getString("id"),
            name = obj.optString("name", localFile.name),
            mimeType = obj.optString("mimeType", mime),
            sizeBytes = obj.optString("size", localFile.length().toString()).toLongOrNull() ?: localFile.length(),
            modifiedTime = obj.optString("modifiedTime", ""),
            isFolder = false,
          )
        }
      } else {
        // Resumable upload para archivos > 4 MB (evita cuelgues y timeouts de multipart)
        val initReq = Request.Builder()
          .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&fields=id,name,mimeType,size,modifiedTime")
          .header("Authorization", "Bearer $accessToken")
          .header("X-Upload-Content-Type", mime)
          .header("X-Upload-Content-Length", localFile.length().toString())
          .post(metadata.toRequestBody("application/json; charset=utf-8".toMediaType()))
          .build()

        val uploadUrl = client.newCall(initReq).execute().use { resp ->
          if (!resp.isSuccessful) throw IllegalStateException("Error iniciando subida resumable (${resp.code})")
          resp.header("Location") ?: throw IllegalStateException("Google Drive no devolvió URL resumable")
        }

        val putReq = Request.Builder()
          .url(uploadUrl)
          .header("Content-Type", mime)
          .put(localFile.asRequestBody(mime.toMediaType()))
          .build()

        client.newCall(putReq).execute().use { resp ->
          val body = resp.body?.string().orEmpty()
          if (!resp.isSuccessful) throw IllegalStateException("Error subiendo resumable (${resp.code}): $body")
          val obj = JSONObject(body)
          DriveFileItem(
            id = obj.getString("id"),
            name = obj.optString("name", localFile.name),
            mimeType = obj.optString("mimeType", mime),
            sizeBytes = obj.optString("size", localFile.length().toString()).toLongOrNull() ?: localFile.length(),
            modifiedTime = obj.optString("modifiedTime", ""),
            isFolder = false,
          )
        }
      }
    }
  }

  suspend fun downloadFile(accessToken: String, driveFile: DriveFileItem, destDir: File): Result<File> =
    withContext(Dispatchers.IO) {
      runCatching {
        if (!destDir.exists()) destDir.mkdirs()
        val dest = File(destDir, driveFile.name)
        val req = Request.Builder()
          .url("https://www.googleapis.com/drive/v3/files/${driveFile.id}?alt=media")
          .header("Authorization", "Bearer $accessToken")
          .build()
        client.newCall(req).execute().use { resp ->
          if (!resp.isSuccessful) throw IllegalStateException("Error descargando de Drive (${resp.code})")
          resp.body?.byteStream()?.use { input ->
            FileOutputStream(dest).use { output -> input.copyTo(output) }
          } ?: throw IllegalStateException("Respuesta vacía de Drive")
        }
        dest
      }
    }

  suspend fun deleteFile(accessToken: String, fileId: String): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
      val req = Request.Builder()
        .url("https://www.googleapis.com/drive/v3/files/$fileId")
        .header("Authorization", "Bearer $accessToken")
        .delete()
        .build()
      client.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful && resp.code != 204) {
          throw IllegalStateException("No se pudo eliminar de Drive (${resp.code})")
        }
      }
    }
  }

  suspend fun addAutoBackupRule(
    localFolderPath: String,
    driveFolderId: String,
    driveFolderName: String,
    wifiOnly: Boolean,
    frequency: String = "WIFI_AUTO",
  ) {
    val folder = File(localFolderPath)
    dao.insertAutoBackupRule(
      AutoBackupRuleEntity(
        localFolderPath = localFolderPath,
        localFolderName = folder.name.ifBlank { localFolderPath },
        driveFolderId = driveFolderId,
        driveFolderName = driveFolderName,
        enabled = true,
        wifiOnly = wifiOnly,
        frequency = frequency,
      )
    )
  }

  suspend fun toggleAutoBackupRule(rule: AutoBackupRuleEntity, enabled: Boolean) {
    dao.updateAutoBackupRule(rule.copy(enabled = enabled))
  }

  suspend fun deleteAutoBackupRule(id: Long) {
    dao.deleteAutoBackupRuleById(id)
  }

  suspend fun runAutoBackupNow(accessToken: String, onlyRuleIds: Set<Long>? = null): Result<Int> = withContext(Dispatchers.IO) {
    runCatching {
      val allRules = dao.getEnabledAutoBackupRules()
      val rules = if (onlyRuleIds.isNullOrEmpty()) allRules else allRules.filter { it.id in onlyRuleIds }
      var uploadedTotal = 0
      for (rule in rules) {
        val localDir = File(rule.localFolderPath)
        if (!localDir.exists() || !localDir.isDirectory) continue
        val remoteFiles = listFiles(accessToken, rule.driveFolderId).getOrNull() ?: emptyList()
        val existingNames = remoteFiles.map { it.name }.toSet()
        var ruleCount = 0
        localDir.listFiles()?.filter { it.isFile && !it.name.startsWith(".") }?.forEach { file ->
          if (file.name !in existingNames) {
            val res = uploadFile(accessToken, file, rule.driveFolderId)
            if (res.isSuccess) {
              ruleCount++
              uploadedTotal++
            }
          }
        }
        dao.updateAutoBackupRule(
          rule.copy(
            lastSyncAt = System.currentTimeMillis(),
            lastSyncCount = ruleCount,
          )
        )
      }
      uploadedTotal
    }
  }

  companion object {
    const val VAULT_DRIVE_FOLDER_NAME = ".nexo_system_appdata_vault"
  }
}
