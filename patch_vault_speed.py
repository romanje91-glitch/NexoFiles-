import os, re

R = "app/src/main/java/com/example"
def rf(rel): return open(os.path.join(R, rel), encoding="utf-8").read()
def wf(rel, t): open(os.path.join(R, rel), "w", encoding="utf-8").write(t)
def imp(c, ls):
    m = re.search(r"^package\s+[\w\.]+\s*\n", c, re.M)
    a = "".join(f"import {i}\n" for i in ls if f"import {i}" not in c)
    return c if not a else c[:m.end()] + a + c[m.end():]

# ------------------------------------------------------------------
# 1. GoogleDriveRepository.kt: uploadFile con soporte RESUMABLE (> 4 MB) + MULTIPART (<= 4 MB)
# ------------------------------------------------------------------
gd_rel = "data/repo/GoogleDriveRepository.kt"
gd = rf(gd_rel)

old_up = gd[gd.find("  suspend fun uploadFile("):gd.find("  suspend fun downloadFile(")]
new_up = """  suspend fun uploadFile(
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

"""
assert old_up.strip().startswith("suspend fun uploadFile("), "No se encontró uploadFile"
gd = gd.replace(old_up, new_up, 1)
wf(gd_rel, gd)
print("[OK] 1. GoogleDriveRepository.kt: Subida híbrida multipart (<=4MB) + resumable (>4MB).")

# ------------------------------------------------------------------
# 2. ToolsAndNetworkViewModel.kt: Caché de carpetas + 3 subidas paralelas en Dispatchers.IO
# ------------------------------------------------------------------
tn_rel = "ui/viewmodel/ToolsAndNetworkViewModel.kt"
tn = rf(tn_rel)
tn = imp(tn, [
    "kotlinx.coroutines.async",
    "kotlinx.coroutines.awaitAll",
    "kotlinx.coroutines.sync.Semaphore",
    "kotlinx.coroutines.sync.withPermit",
    "java.util.concurrent.atomic.AtomicInteger"
])

old_loop = """          var uploaded = 0
          for ((idx, item) in pendingUpload.withIndex()) {
            ensureActive()
            while (vaultBackupPaused) {
              ensureActive()
              delay(300)
            }
            val curPct = (((idx.toFloat() / totalToUpload.toFloat()) * 0.92f + 0.05f) * 100f).toInt().coerceIn(5, 98)
            updateVaultBackupNotification("Respaldo Bóveda ($curPct%)", "Subiendo ${item.originalName} (${idx + 1}/$totalToUpload)", curPct, true)
            _vaultState.value = _vaultState.value.copy(
              backupCurrentFileName = item.originalName,
              backupDoneCount = idx,
              backupProgress = ((idx.toFloat() / totalToUpload.toFloat()) * 0.92f + 0.05f).coerceIn(0.05f, 0.98f),
            )
            val folderName = NexoVaultExtraStore.itemFolderMap[item.id].orEmpty()
            val targetFolderId = container.googleDrive.ensureVaultSubfolder(token, folderName).getOrThrow()
            val singleDir = File(stageDir, item.id).apply { mkdirs() }
            val exported = container.vault.exportSelectedVaultToNxv(listOf(item), passphrase, singleDir).getOrThrow()
            for (nxv in exported) {
              val res = container.googleDrive.uploadFile(token, nxv, targetFolderId)
              if (res.isSuccess) uploaded++
            }
            val doneNow = idx + 1
            val elapsedMs = (System.currentTimeMillis() - startMs).coerceAtLeast(300L)
            val avgPerItemMs = elapsedMs / doneNow
            val remSec = (((totalToUpload - doneNow) * avgPerItemMs) / 1000L).toInt().coerceAtLeast(0)
            _vaultState.value = _vaultState.value.copy(
              backupDoneCount = doneNow,
              backupProgress = (doneNow.toFloat() / totalToUpload.toFloat()).coerceIn(0f, 1f),
              backupEtaSeconds = remSec,
            )
          }
          Triple(uploaded, skippedAlreadySynced, totalToUpload)"""

new_loop = """          val uploadedCount = AtomicInteger(0)
          val doneCount = AtomicInteger(0)
          val folderIdCache = mutableMapOf<String, String>()
          // Pre-resolver carpetas únicas UNA sola vez (evita N llamadas repetidas a Drive)
          pendingUpload.map { NexoVaultExtraStore.itemFolderMap[it.id].orEmpty() }.distinct().forEach { fn ->
            folderIdCache[fn] = container.googleDrive.ensureVaultSubfolder(token, fn).getOrThrow()
          }
          val sem = Semaphore(3)
          kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            pendingUpload.map { item ->
              async {
                sem.withPermit {
                  ensureActive()
                  while (vaultBackupPaused) {
                    ensureActive()
                    delay(300)
                  }
                  val folderName = NexoVaultExtraStore.itemFolderMap[item.id].orEmpty()
                  val targetFolderId = folderIdCache[folderName] ?: container.googleDrive.ensureVaultSubfolder(token, folderName).getOrThrow()
                  val singleDir = File(stageDir, item.id).apply { mkdirs() }
                  try {
                    val exported = container.vault.exportSelectedVaultToNxv(listOf(item), passphrase, singleDir).getOrThrow()
                    for (nxv in exported) {
                      val res = container.googleDrive.uploadFile(token, nxv, targetFolderId)
                      if (res.isSuccess) uploadedCount.incrementAndGet()
                    }
                  } finally {
                    singleDir.deleteRecursively()
                  }
                  val doneNow = doneCount.incrementAndGet()
                  val pctFloat = (doneNow.toFloat() / totalToUpload.toFloat()).coerceIn(0.05f, 1f)
                  val curPct = (pctFloat * 100f).toInt().coerceIn(5, 99)
                  val elapsedMs = (System.currentTimeMillis() - startMs).coerceAtLeast(300L)
                  val remSec = (((totalToUpload - doneNow) * (elapsedMs / doneNow)) / 1000L).toInt().coerceAtLeast(0)
                  updateVaultBackupNotification("Respaldo Bóveda ($curPct%)", "Subiendo ${item.originalName} ($doneNow/$totalToUpload)", curPct, true)
                  _vaultState.value = _vaultState.value.copy(
                    backupCurrentFileName = item.originalName,
                    backupDoneCount = doneNow,
                    backupProgress = pctFloat,
                    backupEtaSeconds = remSec,
                  )
                }
              }
            }.awaitAll()
          }
          Triple(uploadedCount.get(), skippedAlreadySynced, totalToUpload)"""

assert old_loop in tn, "No se encontró el bucle de respaldo en ToolsAndNetworkViewModel.kt"
tn = tn.replace(old_loop, new_loop, 1)
wf(tn_rel, tn)
print("[OK] 2. ToolsAndNetworkViewModel.kt: 3 subidas paralelas en Dispatchers.IO + caché de carpetas.")
