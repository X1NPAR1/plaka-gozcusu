package com.plakagozcusu.vision.models

import android.content.Context
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/** A bundled model and the SHA-256 recorded in docs/MODEL_CARD.md for the exact artifact shipped. */
data class ModelSpec(
    val assetPath: String,
    val sha256: String,
    val version: String,
)

class ModelIntegrityException(
    message: String,
) : IOException(message)

internal object ModelAsset {
    /**
     * Loads [spec] into a direct buffer after verifying its hash, so a corrupted or swapped model
     * fails loudly (typed ModelUnavailable in the UI) instead of producing silent garbage.
     */
    fun load(
        context: Context,
        spec: ModelSpec,
    ): ByteBuffer {
        val bytes = context.assets.open(spec.assetPath).use { it.readBytes() }
        val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        if (actual != spec.sha256) throw ModelIntegrityException("Model ${spec.assetPath} hash mismatch")
        return ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply {
            put(bytes)
            rewind()
        }
    }
}
