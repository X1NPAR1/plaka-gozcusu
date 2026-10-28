package com.plakagozcusu.vision.api

import com.plakagozcusu.core.model.VehicleClass
import com.plakagozcusu.vision.api.geometry.RectF

/**
 * Decodes the TFLite detection post-process outputs (boxes [N,4] as ymin,xmin,ymax,xmax normalized;
 * classes [N]; scores [N]; count) into vehicle detections. Kept in pure Kotlin so it is verified
 * against the Python LiteRT reference dump (vision/models/src/test/resources) without a device.
 */
object DetectionDecoder {
    // COCO label-map indices of the bundled EfficientDet-Lite model (read from its labelmap.txt).
    val COCO_VEHICLE_CLASSES: Map<Int, VehicleClass> =
        mapOf(2 to VehicleClass.CAR, 3 to VehicleClass.MOTORCYCLE, 5 to VehicleClass.BUS, 7 to VehicleClass.TRUCK)

    private const val BOX_STRIDE = 4

    fun decode(
        boxes: FloatArray,
        classes: FloatArray,
        scores: FloatArray,
        count: Int,
        minScore: Float,
        classMap: Map<Int, VehicleClass> = COCO_VEHICLE_CLASSES,
    ): List<VehicleDetection> {
        val n = minOf(count, scores.size, classes.size, boxes.size / BOX_STRIDE)
        return (0 until n).mapNotNull { i ->
            val cls = classMap[classes[i].toInt()] ?: return@mapNotNull null
            if (scores[i] < minScore) return@mapNotNull null
            val o = i * BOX_STRIDE
            val box =
                RectF(
                    left = boxes[o + 1].coerceIn(0f, 1f),
                    top = boxes[o].coerceIn(0f, 1f),
                    right = boxes[o + 3].coerceIn(0f, 1f),
                    bottom = boxes[o + 2].coerceIn(0f, 1f),
                )
            if (box.area <= 0f) null else VehicleDetection(box, cls, scores[i])
        }
    }

    /** Class-agnostic NMS; the model already applies NMS, this merges car/truck double labels. */
    fun suppressOverlaps(
        detections: List<VehicleDetection>,
        iouThreshold: Float,
    ): List<VehicleDetection> {
        val kept = mutableListOf<VehicleDetection>()
        for (d in detections.sortedByDescending { it.score }) {
            if (kept.none { it.box.iou(d.box) > iouThreshold }) kept += d
        }
        return kept
    }
}
