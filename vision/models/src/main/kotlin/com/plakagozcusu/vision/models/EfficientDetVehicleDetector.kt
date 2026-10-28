package com.plakagozcusu.vision.models

import android.content.Context
import com.plakagozcusu.vision.api.DetectionDecoder
import com.plakagozcusu.vision.api.ImageBuffer
import com.plakagozcusu.vision.api.VehicleDetection
import com.plakagozcusu.vision.api.VehicleDetector
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * EfficientDet-Lite0 (int8, 320x320, COCO; Apache-2.0, see docs/MODEL_CARD.md) on LiteRT CPU/XNNPACK.
 * Not thread-safe: owned by the single pipeline thread. Output tensors are looked up by name, as
 * measured with tools/inspect_tflite.py: :3 boxes, :2 classes, :1 scores, :0 count.
 */
class EfficientDetVehicleDetector(
    context: Context,
    private val minScore: Float,
    threads: Int = DEFAULT_THREADS,
) : VehicleDetector {
    override val modelVersion: String = SPEC.version

    private val interpreter = Interpreter(ModelAsset.load(context, SPEC), Interpreter.Options().setNumThreads(threads))
    private val input = ByteBuffer.allocateDirect(SIZE * SIZE * CHANNELS).order(ByteOrder.nativeOrder())
    private val boxes = Array(1) { Array(MAX_DET) { FloatArray(BOX_COORDS) } }
    private val classes = Array(1) { FloatArray(MAX_DET) }
    private val scores = Array(1) { FloatArray(MAX_DET) }
    private val count = FloatArray(1)
    private val outputs =
        mapOf(
            interpreter.getOutputIndex("StatefulPartitionedCall:3") to boxes,
            interpreter.getOutputIndex("StatefulPartitionedCall:2") to classes,
            interpreter.getOutputIndex("StatefulPartitionedCall:1") to scores,
            interpreter.getOutputIndex("StatefulPartitionedCall:0") to count,
        )

    override fun detect(image: ImageBuffer): List<VehicleDetection> {
        fillInput(image)
        interpreter.runForMultipleInputsOutputs(arrayOf(input), outputs)
        val flatBoxes = FloatArray(MAX_DET * BOX_COORDS)
        boxes[0].forEachIndexed { i, b -> b.copyInto(flatBoxes, i * BOX_COORDS) }
        val decoded = DetectionDecoder.decode(flatBoxes, classes[0], scores[0], count[0].toInt(), minScore)
        return DetectionDecoder.suppressOverlaps(decoded, CROSS_CLASS_IOU)
    }

    /** Nearest-neighbour stretch to 320x320 RGB uint8 (the model's own preprocessing, no letterbox). */
    private fun fillInput(image: ImageBuffer) {
        input.rewind()
        for (y in 0 until SIZE) {
            val sy = y * image.height / SIZE
            for (x in 0 until SIZE) {
                val p = image.argb[sy * image.width + x * image.width / SIZE]
                input.put((p shr RED_SHIFT and BYTE_MASK).toByte())
                input.put((p shr GREEN_SHIFT and BYTE_MASK).toByte())
                input.put((p and BYTE_MASK).toByte())
            }
        }
        input.rewind()
    }

    override fun close() = interpreter.close()

    companion object {
        val SPEC =
            ModelSpec(
                assetPath = "models/efficientdet_lite0_int8.tflite",
                sha256 = "2e04c53bfeac0ac2a30c057c7e2a777594ce39baaac35a92f74fb1e8c4fc4e0b",
                version = "efficientdet-lite0-detection-metadata/1 (kaggle tensorflow/efficientdet)",
            )
        const val SIZE = 320
        private const val CHANNELS = 3
        private const val MAX_DET = 25
        private const val BOX_COORDS = 4
        private const val DEFAULT_THREADS = 2
        private const val CROSS_CLASS_IOU = 0.7f
        private const val RED_SHIFT = 16
        private const val GREEN_SHIFT = 8
        private const val BYTE_MASK = 0xFF
    }
}
