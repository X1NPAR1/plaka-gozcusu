package com.plakagozcusu.core.storage

import com.plakagozcusu.core.common.AppError
import com.plakagozcusu.core.common.AppResult
import com.plakagozcusu.core.model.EvidenceFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.DigestInputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * App-private evidence storage under [root] (Context.filesDir/evidence in the app).
 *
 * Every write is atomic: bytes go to a `.tmp` file in the target directory, are fsync'ed, then
 * renamed. A crash or full disk therefore never leaves a half-written evidence file under its final
 * name; leftovers are `.tmp` files that [sweepTempFiles] removes. The SHA-256 is computed from the
 * exact bytes written and stored with the record so tampering or corruption is detectable later.
 */
class EvidenceFileStore(
    private val root: File,
) {
    /** Writes [bytes] to `<yyyy>/<MM>/<dd>/<name>` (UTC date of [capturedAtMillis]). */
    fun write(
        bytes: ByteArray,
        capturedAtMillis: Long,
        name: String,
    ): AppResult<EvidenceFile> {
        require(name.isNotBlank() && !name.contains('/') && !name.contains('\\') && !name.startsWith(".")) {
            "Invalid evidence file name"
        }
        val relative = "${DATE_DIR.format(Instant.ofEpochMilli(capturedAtMillis))}/$name"
        val target = resolve(relative)
        val temp = File(target.parentFile, "$name$TEMP_SUFFIX")
        return try {
            target.parentFile.mkdirs()
            FileOutputStream(temp).use { out ->
                out.write(bytes)
                out.flush()
                out.fd.sync()
            }
            if (!temp.renameTo(target)) throw IOException("rename failed")
            AppResult.Success(EvidenceFile(relative, sha256Hex(bytes), bytes.size.toLong()))
        } catch (e: IOException) {
            temp.delete()
            AppResult.Failure(if (isNoSpace(e)) AppError.StorageFull(e) else AppError.Io(e))
        }
    }

    fun resolve(relativePath: String): File {
        val file = File(root, relativePath).canonicalFile
        // Defence in depth: paths come from our own DB, but never allow escaping the evidence root.
        require(file.path.startsWith(root.canonicalPath + File.separator)) { "Path escapes evidence root" }
        return file
    }

    fun exists(file: EvidenceFile): Boolean = resolve(file.relativePath).isFile

    /** Re-hashes the stored bytes; false if missing or changed since capture. */
    fun verify(file: EvidenceFile): Boolean {
        val f = resolve(file.relativePath)
        if (!f.isFile) return false
        return sha256Hex(f) == file.sha256
    }

    fun delete(file: EvidenceFile): Boolean {
        val f = resolve(file.relativePath)
        return !f.exists() || f.delete()
    }

    /** Removes `.tmp` leftovers from interrupted writes; returns how many were deleted. */
    fun sweepTempFiles(): Int =
        root
            .walkTopDown()
            .filter { it.isFile && it.name.endsWith(TEMP_SUFFIX) }
            .count { it.delete() }

    /** All stored evidence files (relative paths), for orphan detection against the database. */
    fun listRelativePaths(): Set<String> =
        root
            .walkTopDown()
            .filter { it.isFile && !it.name.endsWith(TEMP_SUFFIX) }
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .toSet()

    fun totalBytes(): Long = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    companion object {
        const val TEMP_SUFFIX = ".tmp"
        private val DATE_DIR = DateTimeFormatter.ofPattern("yyyy/MM/dd").withZone(ZoneOffset.UTC)
        private const val BUFFER_SIZE = 64 * 1024

        fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

        fun sha256Hex(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            DigestInputStream(file.inputStream(), digest).use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (input.read(buffer) != -1) {
                    // Reading through the DigestInputStream updates the digest.
                }
            }
            return digest.digest().toHex()
        }

        private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

        private fun isNoSpace(e: IOException): Boolean =
            e.message?.contains("ENOSPC") == true || e.message?.contains("No space", ignoreCase = true) == true
    }
}
