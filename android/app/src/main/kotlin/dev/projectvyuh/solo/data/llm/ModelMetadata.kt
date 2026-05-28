package dev.projectvyuh.solo.data.llm

import org.json.JSONObject

/** Metadata reported by llama.cpp for the currently loaded model. */
data class ModelMetadata(
    val description: String,
    val contextSize: Int,
    val vocabSize: Int,
    val parameterCount: Long,
    val sizeBytes: Long,
) {
    companion object {
        fun fromJson(json: String): ModelMetadata {
            val o = JSONObject(json)
            return ModelMetadata(
                description    = o.optString("description", ""),
                contextSize    = o.optInt("n_ctx"),
                vocabSize      = o.optInt("n_vocab"),
                parameterCount = o.optLong("n_params"),
                sizeBytes      = o.optLong("size_bytes"),
            )
        }
    }
}
