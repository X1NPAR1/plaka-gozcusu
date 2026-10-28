package com.plakagozcusu.core.data.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.plakagozcusu.core.model.RecognitionStatus
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class) // org.json comes from the Android framework (Robolectric).
class AiPlateVerifierTest {
    private val verifier = GeminiPlateVerifier(apiKey = "test-key", model = "gemini-2.5-flash")

    private fun response(text: String) =
        JSONObject()
            .put(
                "candidates",
                org.json.JSONArray().put(
                    JSONObject().put("content", JSONObject().put("parts", org.json.JSONArray().put(JSONObject().put("text", text)))),
                ),
            ).toString()

    @Test
    fun `valid answer is normalized through the plate parser`() {
        val r = verifier.parse(response("{\"plate\": \"34 tft 123\", \"readable\": true}"))

        assertThat(r).isEqualTo(AiResult.Read("34 TFT 123"))
    }

    @Test
    fun `answers that are not valid plates are discarded, not trusted`() {
        assertThat(verifier.parse(response("{\"plate\": \"HELLO\", \"readable\": true}"))).isEqualTo(AiResult.Read(null))
        assertThat(verifier.parse(response("{\"plate\": null, \"readable\": false}"))).isEqualTo(AiResult.Read(null))
        assertThat(verifier.parse("not json")).isInstanceOf(AiResult.Permanent::class.java)
    }

    @Test
    fun `request carries the image and candidates but never the key`() {
        val body = verifier.requestBody(byteArrayOf(1, 2, 3), listOf("34 TET 123"))

        assertThat(body).contains("34 TET 123")
        assertThat(body).contains("inline_data") // org.json escapes "/" so match the field name
        assertThat(body).doesNotContain("test-key")
    }

    @Test
    fun `decision rules`() {
        assertThat(AiDecision.apply(RecognitionStatus.NEEDS_REVIEW, "34 TFT 123", "34 TFT 123"))
            .isEqualTo(RecognitionStatus.HIGH_CONFIDENCE to true)
        assertThat(AiDecision.apply(RecognitionStatus.HIGH_CONFIDENCE, "34 TET 123", "34 TFT 123"))
            .isEqualTo(RecognitionStatus.NEEDS_REVIEW to false)
        assertThat(AiDecision.apply(RecognitionStatus.UNREADABLE, null, "34 TFT 123"))
            .isEqualTo(RecognitionStatus.NEEDS_REVIEW to false)
        assertThat(AiDecision.apply(RecognitionStatus.UNREADABLE, null, null)).isEqualTo(RecognitionStatus.UNREADABLE to false)
        assertThat(AiDecision.apply(RecognitionStatus.USER_CORRECTED, "34 A 1234", "34 TFT 123"))
            .isEqualTo(RecognitionStatus.USER_CORRECTED to false)
    }
}
