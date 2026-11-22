package com.sunsetgrove.game.engine

import android.content.Context
import org.json.JSONObject

data class ModelAssetSpec(
    val key: String,
    val path: String,
    val targetHeight: Float,
    val yawOffsetDeg: Float,
)

data class NormalizationTransform(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
    val offsetZ: Float,
    val yawOffsetRad: Float,
)

/**
 * Parses app/src/main/assets/models/manifest.json and computes scale/ground-pivot
 * normalization transforms so both auto-fetched PBR models and user drop-in
 * overrides (Sketchfab / Mixamo) align cleanly on the asphalt.
 */
object AssetManifest {

    fun parseJson(rawJson: String): Map<String, ModelAssetSpec> {
        val root = JSONObject(rawJson)
        val models = root.getJSONObject("models")
        val result = LinkedHashMap<String, ModelAssetSpec>()
        val keys = models.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val obj = models.getJSONObject(key)
            result[key] = ModelAssetSpec(
                key = key,
                path = obj.getString("path"),
                targetHeight = obj.optDouble("targetHeight", 1.8).toFloat(),
                yawOffsetDeg = obj.optDouble("yawOffsetDeg", 0.0).toFloat(),
            )
        }
        return result
    }

    fun loadFromAssets(context: Context): Map<String, ModelAssetSpec> {
        val raw = context.assets.open("models/manifest.json").bufferedReader().use { it.readText() }
        return parseJson(raw)
    }

    /**
     * Given a glTF model's axis-aligned bounding box (center + halfExtent), computes
     * uniform scale and local offset so its feet/tires rest exactly at local Y = 0
     * and its XZ footprint is centered at (0, 0).
     */
    fun computeNormalization(
        center: FloatArray,
        halfExtent: FloatArray,
        targetHeight: Float,
        yawOffsetDeg: Float,
    ): NormalizationTransform {
        val rawHeight = (halfExtent[1] * 2f).coerceAtLeast(0.05f)
        val scale = if (targetHeight > 0.05f) targetHeight / rawHeight else 1f
        val minY = center[1] - halfExtent[1]
        return NormalizationTransform(
            scale = scale,
            offsetX = -center[0] * scale,
            offsetY = -minY * scale,
            offsetZ = -center[2] * scale,
            yawOffsetRad = (yawOffsetDeg * Math.PI / 180.0).toFloat(),
        )
    }
}
