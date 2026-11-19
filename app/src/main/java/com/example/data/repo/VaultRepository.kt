package com.example.data.repo

import com.example.NexoVaultDriveMirror
import com.example.NexoNavBridge
import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.documentfile.provider.DocumentFile
import com.example.data.model.VaultFileItem
import com.example.util.FormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class VaultRepository(private val context: Context) {

  private val keyAlias = "nexofiles_vault_aes_key"
  private val vaultDir: File
    get() = File(context.filesDir, "private_vault").apply {
      if (!exists()) mkdirs()
      val noMedia = File(this, ".nomedia")
      if (!noMedia.exists()) noMedia.createNewFile()
    }

  private val previewDir: File
    get() = File(context.cacheDir, "vault_preview").apply {
      if (!exists()) mkdirs()
    }

  private val indexFile: File
    get() = File(vaultDir, "vault_index.json")

  private fun getOrCreateKey(): SecretKey {
    val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    (ks.getKey(keyAlias, null) as? SecretKey)?.let { return it }
    val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
    val spec = KeyGenParameterSpec.Builder(
      keyAlias,
      KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
    )
      .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
      .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
      .setKeySize(256)
      .build()
    keyGenerator.init(spec)
    return keyGenerator.generateKey()
  }

  suspend fun listVaultItems(): List<VaultFileItem> = withContext(Dispatchers.IO) {
    loadIndex()
  }

  suspend fun lockLocalFile(sourceFile: File, removeOriginal: Boolean = true): Result<VaultFileItem> =
    withContext(Dispatchers.IO) {
      runCatching {
        require(sourceFile.exists() && sourceFile.isFile) { "El archivo no existe" }
        val id = UUID.randomUUID().toString()
        val encryptedFile = File(vaultDir, "$id.nvault")
        encryptStreamToFile(FileInputStream(sourceFile), encryptedFile)

        val item = VaultFileItem(
          id = id,
          originalName = sourceFile.name,
          originalPath = sourceFile.absolutePath,
          vaultFilePath = encryptedFile.absolutePath,
          sizeBytes = sourceFile.length(),
          mimeType = FormatUtils.getMimeType(sourceFile.name),
          addedAt = System.currentTimeMillis(),
        )
        val updated = loadIndex().toMutableList().apply { add(0, item) }
        saveIndex(updated)
        if (removeOriginal) {
          sourceFile.delete()
        }
        item
      }
    }

  suspend fun lockFromUri(uri: Uri): Result<VaultFileItem> = withContext(Dispatchers.IO) {
    runCatching {
      val doc = DocumentFile.fromSingleUri(context, uri)
      val name = doc?.name ?: "privado_${System.currentTimeMillis()}"
      val id = UUID.randomUUID().toString()
      val encryptedFile = File(vaultDir, "$id.nvault")
      val input = context.contentResolver.openInputStream(uri)
        ?: throw IllegalStateException("No se pudo leer el elemento seleccionado")
      encryptStreamToFile(input, encryptedFile)

      val item = VaultFileItem(
        id = id,
        originalName = name,
        originalPath = "",
        vaultFilePath = encryptedFile.absolutePath,
        sizeBytes = doc?.length() ?: encryptedFile.length(),
        mimeType = doc?.type ?: FormatUtils.getMimeType(name),
        addedAt = System.currentTimeMillis(),
      )
      val updated = loadIndex().toMutableList().apply { add(0, item) }
      saveIndex(updated)
      item
    }
  }

  /**
   * Decrypts a vault entry atomically to cacheDir/vault_preview/<id>_<safeName>
   * flushing and syncing descriptors before closing so ExoPlayer / viewers receive
   * a 100% complete, seekable file immediately.
   */
  suspend fun decryptToCacheAndOpen(item: VaultFileItem): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
      val safeName = item.originalName.replace(Regex("[/\\\\:*?\"<>|]"), "_")
      val tempOut = File(previewDir, "${item.id}_$safeName")
      if (tempOut.exists() && tempOut.length() > 0L) return@runCatching tempOut
      decryptFileToDest(File(item.vaultFilePath), tempOut)
      require(tempOut.exists() && tempOut.length() > 0L) {
        "Archivo temporal de vista previa vacío o incompleto"
      }
      tempOut
    }
  }

  suspend fun purgePreviewCache() = withContext(Dispatchers.IO) {
    runCatching {
      val dir = File(context.cacheDir, "vault_preview")
      if (dir.exists()) {
        dir.listFiles()?.forEach { it.deleteRecursively() }
      }
    }
  }

  suspend fun restoreToOriginalOrDownloads(item: VaultFileItem, fallbackDir: File): Result<File> =
    withContext(Dispatchers.IO) {
      runCatching {
        val target = if (item.originalPath.isNotBlank()) {
          File(item.originalPath).also { it.parentFile?.mkdirs() }
        } else {
          if (!fallbackDir.exists()) fallbackDir.mkdirs()
          File(fallbackDir, item.originalName)
        }
        decryptFileToDest(File(item.vaultFilePath), target)
        File(item.vaultFilePath).delete()
        saveIndex(loadIndex().filterNot { it.id == item.id })
        target
      }
    }

  suspend fun deleteFromVault(item: VaultFileItem) = withContext(Dispatchers.IO) {
    File(item.vaultFilePath).delete()
    saveIndex(loadIndex().filterNot { it.id == item.id })
  }

  /**
   * Exports a Vault item into portable `.nexo_vault` binary format ("NXV1"):
   * 4B "NXV1" | 16B salt | 4B BE iterations (65536) | 12B IV | AES-256-GCM(4B metaLen + metaJson + plainBytes)
   */
  suspend fun exportVaultItemToNxv(
    item: VaultFileItem,
    passphrase: String,
    destDir: File,
  ): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
      require(passphrase.length >= 4) { "La contraseña debe tener al menos 4 caracteres" }
      if (!destDir.exists()) destDir.mkdirs()

      val tempPlain = File(destDir, ".plain_${item.id}.tmp")
      try {
        decryptFileToDest(File(item.vaultFilePath), tempPlain)
        val plainBytes = tempPlain.readBytes()
        val metaJson = JSONObject().apply {
          put("id", item.id)
          put("name", item.originalName)
          put("mime", item.mimeType)
          put("size", plainBytes.size.toLong())
          put("addedAt", item.addedAt)
        }.toString().toByteArray(Charsets.UTF_8)

        val inner = ByteArrayOutputStream(4 + metaJson.size + plainBytes.size).apply {
          DataOutputStream(this).use { dos ->
            dos.writeInt(metaJson.size)
            dos.write(metaJson)
            dos.write(plainBytes)
            dos.flush()
          }
        }.toByteArray()

        val rng = SecureRandom()
        val salt = ByteArray(16).also { rng.nextBytes(it) }
        val iv = ByteArray(12).also { rng.nextBytes(it) }
        val iterations = NXV_PBKDF2_ITERATIONS
        val aesKey = derivePbkdf2AesKey(passphrase, salt, iterations)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
        val cipherBytes = cipher.doFinal(inner)

        val cleanBase = item.originalName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val outFile = File(destDir, "${cleanBase}.nexo_vault")
        val tmpOut = File(destDir, "${cleanBase}.nexo_vault.tmp")
        FileOutputStream(tmpOut).use { fos ->
          DataOutputStream(fos).use { dos ->
            dos.writeBytes(NXV_MAGIC)
            dos.write(salt)
            dos.writeInt(iterations)
            dos.write(iv)
            dos.write(cipherBytes)
            dos.flush()
          }
          runCatching { fos.fd.sync() }
        }
        if (outFile.exists()) outFile.delete()
        if (!tmpOut.renameTo(outFile)) {
          tmpOut.copyTo(outFile, overwrite = true)
          tmpOut.delete()
        }
        outFile
      } finally {
        tempPlain.delete()
      }
    }
  }

  suspend fun exportAllVaultToNxv(passphrase: String, destDir: File): Result<List<File>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val items = loadIndex()
        require(items.isNotEmpty()) { "La bóveda está vacía" }
        items.map { exportVaultItemToNxv(it, passphrase, destDir).getOrThrow() }
      }
    }

  suspend fun exportSelectedVaultToNxv(
    items: List<VaultFileItem>,
    passphrase: String,
    destDir: File,
  ): Result<List<File>> = withContext(Dispatchers.IO) {
    runCatching {
      require(items.isNotEmpty()) { "Selecciona al menos un archivo de la bóveda" }
      items.map { exportVaultItemToNxv(it, passphrase, destDir).getOrThrow() }
    }
  }

  suspend fun importNxvToVault(nxvFile: File, passphrase: String): Result<VaultFileItem> =
    withContext(Dispatchers.IO) {
      runCatching {
        require(nxvFile.exists() && nxvFile.length() > 36) { "Archivo .nexo_vault inválido" }
        val raw = nxvFile.readBytes()
        require(raw.copyOfRange(0, 4).toString(Charsets.US_ASCII) == NXV_MAGIC) {
          "Cabecera .nexo_vault inválida"
        }
        val salt = raw.copyOfRange(4, 20)
        val dis = DataInputStream(raw.copyOfRange(20, 24).inputStream())
        val iterations = dis.readInt().coerceIn(10_000, 500_000)
        val iv = raw.copyOfRange(24, 36)
        val cipherBytes = raw.copyOfRange(36, raw.size)

        val aesKey = derivePbkdf2AesKey(passphrase, salt, iterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
        val plain = cipher.doFinal(cipherBytes)

        val metaDis = DataInputStream(plain.inputStream())
        val metaLen = metaDis.readInt()
        require(metaLen in 2..(plain.size - 4)) { "Metadatos .nexo_vault corruptos" }
        val metaBytes = ByteArray(metaLen)
        metaDis.readFully(metaBytes)
        val meta = JSONObject(String(metaBytes, Charsets.UTF_8))

        val originalName = meta.optString("name", nxvFile.name.removeSuffix(".nexo_vault"))
        val mime = meta.optString("mime", FormatUtils.getMimeType(originalName))
        val contentOffset = 4 + metaLen
        val contentBytes = plain.copyOfRange(contentOffset, plain.size)

        val id = UUID.randomUUID().toString()
        val encryptedFile = File(vaultDir, "$id.nvault")
        encryptStreamToFile(contentBytes.inputStream(), encryptedFile)

        val item = VaultFileItem(
          id = id,
          originalName = originalName,
          originalPath = "",
          vaultFilePath = encryptedFile.absolutePath,
          sizeBytes = contentBytes.size.toLong(),
          mimeType = mime,
          addedAt = meta.optLong("addedAt", System.currentTimeMillis()),
        )
        val updated = loadIndex().toMutableList().apply { add(0, item) }
        saveIndex(updated)
        item
      }
    }

  private fun derivePbkdf2AesKey(passphrase: String, salt: ByteArray, iterations: Int): SecretKeySpec {
    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, 256)
    val keyBytes = factory.generateSecret(spec).encoded
    return SecretKeySpec(keyBytes, "AES")
  }

  private fun encryptStreamToFile(input: java.io.InputStream, dest: File) {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
    val iv = cipher.iv
    val tmp = File(dest.parentFile, "${dest.name}.tmp")
    BufferedOutputStream(FileOutputStream(tmp), STREAM_BUFFER_SIZE).use { bos ->
      bos.write(iv.size)
      bos.write(iv)
      CipherOutputStream(bos, cipher).use { cos ->
        BufferedInputStream(input, STREAM_BUFFER_SIZE).use { bis ->
          val buffer = ByteArray(STREAM_BUFFER_SIZE)
          var bytesRead = bis.read(buffer)
          while (bytesRead != -1) {
            cos.write(buffer, 0, bytesRead)
            bytesRead = bis.read(buffer)
          }
        }
        cos.flush()
      }
      bos.flush()
    }
    if (dest.exists()) dest.delete()
    if (!tmp.renameTo(dest)) {
      tmp.copyTo(dest, overwrite = true)
      tmp.delete()
    }
  }

  private fun decryptFileToDest(encrypted: File, dest: File) {
    val parent = dest.parentFile ?: previewDir
    if (!parent.exists()) parent.mkdirs()
    val tmp = File(parent, "${dest.name}.tmp")
    if (tmp.exists()) tmp.delete()

    DataInputStream(BufferedInputStream(FileInputStream(encrypted), STREAM_BUFFER_SIZE)).use { dis ->
      val ivLen = dis.readUnsignedByte()
      require(ivLen in 12..16) { "Cabecera cifrada inválida ($ivLen)" }
      val iv = ByteArray(ivLen)
      dis.readFully(iv)
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
      CipherInputStream(dis, cipher).use { cis ->
        BufferedOutputStream(FileOutputStream(tmp), STREAM_BUFFER_SIZE).use { bos ->
          val buffer = ByteArray(STREAM_BUFFER_SIZE)
          var bytesRead = cis.read(buffer)
          while (bytesRead != -1) {
            bos.write(buffer, 0, bytesRead)
            bytesRead = cis.read(buffer)
          }
          bos.flush()
        }
      }
    }
    if (dest.exists()) dest.delete()
    if (!tmp.renameTo(dest)) {
      tmp.copyTo(dest, overwrite = true)
      tmp.delete()
    }
  }

  private fun loadIndex(): List<VaultFileItem> {
    if (!indexFile.exists()) return emptyList()
    return runCatching {
      val arr = JSONArray(indexFile.readText())
      (0 until arr.length()).map { i ->
        val obj = arr.getJSONObject(i)
        VaultFileItem(
          id = obj.getString("id"),
          originalName = obj.getString("originalName"),
          originalPath = obj.optString("originalPath", ""),
          vaultFilePath = obj.getString("vaultFilePath"),
          sizeBytes = obj.optLong("sizeBytes", 0L),
          mimeType = obj.optString("mimeType", "*/*"),
          addedAt = obj.optLong("addedAt", 0L),
        )
      }
    }.getOrDefault(emptyList())
  }

  private fun saveIndex(items: List<VaultFileItem>) {
    val arr = JSONArray()
    items.forEach { item ->
      arr.put(
        JSONObject().apply {
          put("id", item.id)
          put("originalName", item.originalName)
          put("originalPath", item.originalPath)
          put("vaultFilePath", item.vaultFilePath)
          put("sizeBytes", item.sizeBytes)
          put("mimeType", item.mimeType)
          put("addedAt", item.addedAt)
        }
      )
    }
    indexFile.writeText(arr.toString())
  }

  companion object {
    const val NXV_MAGIC = "NXV1"
    const val NXV_PBKDF2_ITERATIONS = 65_536
    const val STREAM_BUFFER_SIZE = 64 * 1024
  }
}
