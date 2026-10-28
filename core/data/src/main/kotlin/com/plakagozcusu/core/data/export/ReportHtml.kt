package com.plakagozcusu.core.data.export

import com.plakagozcusu.core.model.Observation
import com.plakagozcusu.core.model.RecognitionStatus

/** Self-contained Turkish HTML report (no external resources); all values HTML-escaped. */
internal object ReportHtml {
    fun render(
        observations: List<Observation>,
        entries: List<EvidenceExporter.ManifestEntry>,
        exportedAt: Long,
        appVersion: String,
    ): String =
        buildString {
            append("<!doctype html><html lang=\"tr\"><head><meta charset=\"utf-8\">")
            append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
            append("<title>Plaka Gözcüsü kanıt raporu</title><style>")
            append(
                "body{font-family:sans-serif;margin:16px;color:#111}table{border-collapse:collapse;width:100%;margin:8px 0 24px}",
            )
            append("td,th{border:1px solid #ccc;padding:6px;text-align:left;vertical-align:top}")
            append(
                ".warn{background:#fff4d6;padding:8px;border-left:4px solid #b45309}.bad{color:#b3261e;font-weight:bold}",
            )
            append("img{max-width:100%;height:auto}code{word-break:break-all}</style></head><body>")
            append("<h1>Plaka Gözcüsü kanıt raporu</h1>")
            append(
                "<p>Oluşturulma: ",
            ).append(esc(EvidenceExporter.local(exportedAt, "UTC"))).append(" UTC · Uygulama sürümü ")
            append(esc(appVersion)).append(" · Kayıt sayısı: ").append(observations.size).append("</p>")
            append("<p class=\"warn\">").append(esc(EvidenceExporter.AUTOMATED_NOTICE)).append(" ")
            append(
                "Dosyaların SHA-256 özetleri kayıt anında hesaplanmış ve dışa aktarım sırasında yeniden doğrulanmıştır.</p>",
            )
            for (o in observations) {
                append("<h2>").append(esc(o.plateText ?: statusText(o.recognitionStatus))).append("</h2><table>")
                row("Zaman", EvidenceExporter.local(o.bestFrameAt, o.zoneId) + " (" + o.zoneId + ")")
                row("Durum", statusText(o.recognitionStatus))
                row("Ham OCR metni", o.rawOcrText ?: "—")
                o.originalPlateText?.let { row("Otomatik okunan (düzeltme öncesi)", it) }
                row("Plaka güveni", "%.2f".format(o.plateConfidence))
                row("Araç türü / yön", "${o.vehicleClass} / ${o.direction}")
                o.userNote?.let { row("Not", it) }
                append("</table><table><tr><th>Dosya</th><th>SHA-256</th><th>Doğrulama</th></tr>")
                for (e in entries.filter { it.observationId == o.id }) {
                    append(
                        "<tr><td><a href=\"",
                    ).append(esc(e.path)).append("\">").append(esc(e.path)).append("</a></td>")
                    append("<td><code>").append(esc(e.expected)).append("</code></td><td>")
                    append(
                        if (e.ok) "Eşleşiyor" else "<span class=\"bad\">EŞLEŞMİYOR / EKSİK</span>",
                    ).append("</td></tr>")
                }
                append("</table>")
            }
            append("</body></html>")
        }

    private fun StringBuilder.row(
        k: String,
        v: String,
    ) {
        append("<tr><th>")
            .append(esc(k))
            .append("</th><td>")
            .append(esc(v))
            .append("</td></tr>")
    }

    private fun statusText(s: RecognitionStatus): String =
        when (s) {
            RecognitionStatus.PENDING -> "İşleniyor"
            RecognitionStatus.HIGH_CONFIDENCE -> "Yüksek güven (otomatik, doğrulanmadı)"
            RecognitionStatus.NEEDS_REVIEW -> "İnceleme gerekli"
            RecognitionStatus.UNREADABLE -> "Okunamadı"
            RecognitionStatus.NO_PLATE_VISIBLE -> "Plaka görünmüyor"
            RecognitionStatus.FAILED -> "Hata"
            RecognitionStatus.USER_VERIFIED -> "Kullanıcı doğruladı"
            RecognitionStatus.USER_CORRECTED -> "Kullanıcı düzeltti"
        }

    private fun esc(s: String): String =
        s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
}
