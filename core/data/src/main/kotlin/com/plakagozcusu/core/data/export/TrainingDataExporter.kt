package com.plakagozcusu.core.data.export

import com.plakagozcusu.core.model.EvidenceFile
import com.plakagozcusu.core.model.Observation
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Exports user-confirmed plate crops with their correct text (labels.csv) for training a dedicated
 * Turkish plate OCR model later. Contains plate crops only — no scene images, times or places.
 */
class TrainingDataExporter(
    private val resolve: (EvidenceFile) -> File,
) {
    fun export(
        labelled: List<Observation>,
        out: OutputStream,
    ): Int {
        var n = 0
        ZipOutputStream(out).use { zip ->
            val csv = StringBuilder("file,label,automated_reading,raw_ocr\n")
            val samples =
                labelled.mapNotNull { o ->
                    val crop = o.plateCrop?.let(resolve)?.takeIf { it.isFile }
                    val label = o.plateText
                    if (crop != null && label != null) Triple(o, crop, label) else null
                }
            for ((o, crop, label) in samples) {
                val name = "plates/${o.id}.jpg"
                zip.putNextEntry(ZipEntry(name))
                crop.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                csv
                    .append(
                        listOf(name, label, o.originalPlateText ?: label, o.rawOcrText.orEmpty()).joinToString(",") { csvCell(it) },
                    ).append('\n')
                n++
            }
            zip.putNextEntry(ZipEntry("labels.csv"))
            zip.write(csv.toString().toByteArray())
            zip.closeEntry()
        }
        return n
    }

    private fun csvCell(v: String) = "\"" + v.replace("\"", "\"\"") + "\""
}
