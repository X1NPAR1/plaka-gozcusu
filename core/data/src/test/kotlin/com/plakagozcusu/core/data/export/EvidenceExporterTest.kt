package com.plakagozcusu.core.data.export

import com.google.common.truth.Truth.assertThat
import com.plakagozcusu.core.model.EvidenceFile
import com.plakagozcusu.core.model.MotionStatus
import com.plakagozcusu.core.model.Observation
import com.plakagozcusu.core.model.RecognitionStatus
import com.plakagozcusu.core.model.TravelDirection
import com.plakagozcusu.core.model.VehicleClass
import com.plakagozcusu.core.storage.EvidenceFileStore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

class EvidenceExporterTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun file(
        name: String,
        bytes: ByteArray,
    ): EvidenceFile {
        File(tmp.root, name).writeBytes(bytes)
        return EvidenceFile(name, EvidenceFileStore.sha256Hex(bytes), bytes.size.toLong())
    }

    private fun obs(
        scene: EvidenceFile,
        plate: String? = "34 AB 123",
    ) = Observation(
        id = "o1",
        sessionId = "s",
        trackId = 1,
        plateText = plate,
        originalPlateText = null,
        rawOcrText = "<b>34AB123</b>",
        perCharConfidence = null,
        plateConfidence = 0.8f,
        detectionConfidence = 0.9f,
        qualityScore = 0.7f,
        recognitionStatus = RecognitionStatus.NEEDS_REVIEW,
        motionStatus = MotionStatus.MOVING,
        direction = TravelDirection.LEFT_TO_RIGHT,
        vehicleClass = VehicleClass.CAR,
        firstSeenAt = 0,
        bestFrameAt = 0,
        lastSeenAt = 0,
        zoneId = "Europe/Istanbul",
        scene = scene,
        vehicleCrop = null,
        plateCrop = null,
        modelVersions = "m",
        appVersion = "a",
        userNote = null,
        isStarred = false,
        isProtected = true,
        createdAt = 0,
        updatedAt = 0,
    )

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val out = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                out[e.name] = z.readBytes()
            }
        }
        return out
    }

    private val exporter by lazy { EvidenceExporter({ File(tmp.root, it.relativePath) }, "0.1.0") }

    @Test
    fun `zip contains byte-identical originals, manifest and turkish report`() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val sink = ByteArrayOutputStream()

        val result = exporter.export(listOf(obs(file("a.jpg", bytes))), 0, sink)

        val zip = unzip(sink.toByteArray())
        assertThat(zip.keys).containsExactly("kayitlar/o1/sahne.jpg", "manifest.json", "rapor.html")
        assertThat(zip.getValue("kayitlar/o1/sahne.jpg")).isEqualTo(bytes)
        assertThat(String(zip.getValue("manifest.json"))).contains(EvidenceFileStore.sha256Hex(bytes))
        assertThat(String(zip.getValue("manifest.json"))).contains("\"verified\": true")
        assertThat(String(zip.getValue("rapor.html"))).contains("otomatik okunmuştur")
        assertThat(result.integrityFailures).isEqualTo(0)
    }

    @Test
    fun `tampered or missing files are reported, not hidden`() {
        val f = file("b.jpg", byteArrayOf(9))
        File(tmp.root, "b.jpg").writeBytes(byteArrayOf(8))
        val sink = ByteArrayOutputStream()

        val result = exporter.export(listOf(obs(f)), 0, sink)

        assertThat(result.integrityFailures).isEqualTo(1)
        assertThat(String(unzip(sink.toByteArray()).getValue("rapor.html"))).contains("EŞLEŞMİYOR")
    }

    @Test
    fun `report escapes html from ocr text`() {
        val sink = ByteArrayOutputStream()
        exporter.export(listOf(obs(file("c.jpg", byteArrayOf(1)))), 0, sink)

        val html = String(unzip(sink.toByteArray()).getValue("rapor.html"))
        assertThat(html).contains("&lt;b&gt;34AB123&lt;/b&gt;")
        assertThat(html).doesNotContain("<b>34AB123</b>")
    }
}
