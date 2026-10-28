package com.plakagozcusu.core.data.export

import com.plakagozcusu.core.model.EvidenceFile
import com.plakagozcusu.core.model.Observation
import java.io.File
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Builds an evidence ZIP: original files (byte-identical), a Turkish HTML report and a JSON
 * manifest with SHA-256 hashes. Each file is re-hashed while copying; a mismatch with the hash
 * stored at capture time is reported in both report and manifest, never hidden.
 */
class EvidenceExporter(
    private val resolve: (EvidenceFile) -> File,
    private val appVersion: String,
    /** Optional derivative with date/time burned in (Android); originals are always exported untouched. */
    private val stamp: ((File, String) -> ByteArray?)? = null,
) {
    data class Result(
        val records: Int,
        val files: Int,
        val integrityFailures: Int,
    )

    fun export(
        observations: List<Observation>,
        exportedAtMillis: Long,
        out: OutputStream,
    ): Result {
        var entries: List<ManifestEntry> = emptyList()
        ZipOutputStream(out).use { zip ->
            entries = observations.flatMap { o -> o.evidence().map { (kind, file) -> addEvidence(o.id, kind, file, zip) } }
            observations.forEach { o -> addStamped(o, zip) }
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifestJson(observations, entries, exportedAtMillis).toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("rapor.html"))
            zip.write(ReportHtml.render(observations, entries, exportedAtMillis, appVersion).toByteArray())
            zip.closeEntry()
        }
        return Result(observations.size, entries.count { it.actual != null }, entries.count { !it.ok })
    }

    private fun addEvidence(
        observationId: String,
        kind: String,
        file: EvidenceFile,
        zip: ZipOutputStream,
    ): ManifestEntry {
        val name = "kayitlar/$observationId/$kind.jpg"
        val src = resolve(file)
        val actual = if (src.isFile) copyHashed(src, name, zip) else null
        return ManifestEntry(observationId, kind, name, file.sha256, actual, actual == file.sha256)
    }

    private fun addStamped(
        o: Observation,
        zip: ZipOutputStream,
    ) {
        val scene = o.scene?.let(resolve)?.takeIf { it.isFile } ?: return
        val bytes = stamp?.invoke(scene, local(o.bestFrameAt, o.zoneId)) ?: return
        zip.putNextEntry(ZipEntry("kayitlar/${o.id}/sahne_damgali.jpg"))
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun copyHashed(
        src: File,
        name: String,
        zip: ZipOutputStream,
    ): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        zip.putNextEntry(ZipEntry(name))
        src.inputStream().use { input ->
            val buf = ByteArray(BUFFER)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
                zip.write(buf, 0, n)
            }
        }
        zip.closeEntry()
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun manifestJson(
        observations: List<Observation>,
        entries: List<ManifestEntry>,
        exportedAt: Long,
    ): String =
        buildString {
            append("{\n  \"format\": \"plaka-gozcusu-evidence/1\",\n")
            append("  \"exportedAtUtc\": \"").append(Instant.ofEpochMilli(exportedAt)).append("\",\n")
            append("  \"appVersion\": ").append(json(appVersion)).append(",\n")
            append("  \"notice\": ").append(json(AUTOMATED_NOTICE)).append(",\n  \"observations\": [\n")
            observations.forEachIndexed { i, o ->
                append("    {\"id\": ").append(json(o.id))
                append(", \"plateText\": ").append(json(o.plateText))
                append(", \"originalPlateText\": ").append(json(o.originalPlateText))
                append(", \"rawOcrText\": ").append(json(o.rawOcrText))
                append(", \"status\": ").append(json(o.recognitionStatus.name))
                append(", \"plateConfidence\": ").append(o.plateConfidence)
                append(", \"bestFrameAtUtc\": ").append(json(Instant.ofEpochMilli(o.bestFrameAt).toString()))
                append(", \"zoneId\": ").append(json(o.zoneId))
                append(", \"files\": [")
                append(
                    entries.filter { it.observationId == o.id }.joinToString(", ") {
                        "{\"path\": ${json(it.path)}, \"sha256\": ${json(it.expected)}, \"verified\": ${it.ok}}"
                    },
                )
                append("]}").append(if (i < observations.lastIndex) ",\n" else "\n")
            }
            append("  ]\n}\n")
        }

    internal data class ManifestEntry(
        val observationId: String,
        val kind: String,
        val path: String,
        val expected: String,
        val actual: String?,
        val ok: Boolean,
    )

    companion object {
        private const val BUFFER = 64 * 1024
        internal const val AUTOMATED_NOTICE =
            "Plaka metinleri otomatik okunmuştur ve hatalı olabilir; fotoğraflarla birlikte değerlendirilmelidir."

        internal fun json(v: String?): String =
            if (v == null) {
                "null"
            } else {
                "\"" +
                    v
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("<", "\\u003c") + "\""
            }

        internal fun local(
            millis: Long,
            zone: String,
        ): String =
            DateTimeFormatter
                .ofPattern("dd.MM.yyyy HH:mm:ss", Locale.forLanguageTag("tr"))
                .withZone(runCatching { ZoneId.of(zone) }.getOrDefault(ZoneId.systemDefault()))
                .format(Instant.ofEpochMilli(millis))
    }
}

internal fun Observation.evidence(): List<Pair<String, EvidenceFile>> =
    listOfNotNull(scene?.let { "sahne" to it }, vehicleCrop?.let { "arac" to it }, plateCrop?.let { "plaka" to it })
