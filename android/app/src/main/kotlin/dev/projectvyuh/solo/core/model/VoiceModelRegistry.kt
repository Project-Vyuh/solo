package dev.projectvyuh.solo.core.model

/**
 * Catalogue of Solo's voice-pipeline models — STT, VAD, TTS.
 *
 * All three are published by k2-fsa/sherpa-onnx (Apache 2.0 / MIT) on
 * GitHub Releases. The pre-converted ONNX bundles are the same files
 * sherpa-onnx's official Android samples consume, so model-vs-runtime
 * compatibility is guaranteed.
 *
 * Choices per SOLO-VOICE.md:
 *   - STT: Moonshine base-en (INT8) — 250 MB, 6.65% WER, 107ms TTFT
 *   - VAD: Silero VAD — 644 KB single ONNX, sub-ms per frame
 *   - TTS: Kokoro int8 multi-lang v1.1 — 147 MB, MOS 4.5, sub-300ms TTFA
 *
 * SHA-256 verification: GitHub Releases don't publish authoritative hashes
 * for individual assets. We leave [ModelDefinition.sha256] empty for these
 * three; HTTPS + content-length check still guards against corruption.
 * If sherpa-onnx publishes hashes in a future release, we'll backfill.
 */
object VoiceModelRegistry {

    val MOONSHINE_BASE_EN_INT8 = ModelDefinition(
        id              = "sherpa-onnx-moonshine-base-en-int8",
        displayName     = "Moonshine v2 base-en (INT8)",
        description     = "On-device streaming STT. 61M params INT8-quantized; " +
                          "Ergodic Streaming Encoder; 6.65% WER on OpenASR " +
                          "(beats Whisper Large V3 at 6× smaller).",
        downloadUrl     = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-moonshine-base-en-int8.tar.bz2",
        sha256          = "",                       // see KDoc above
        sizeBytes       = 250_807_309L,
        parameterCount  = "61M (INT8)",
        contextWindow   = 0,                        // not applicable
        quantization    = "INT8",
        format          = ModelFormat.SHERPA_TAR_BZ2,
        isMultimodal    = false,
    )

    val SILERO_VAD = ModelDefinition(
        id              = "silero-vad",
        displayName     = "Silero VAD",
        description     = "Audio-level voice activity detection. De facto " +
                          "industry standard; sub-ms per frame; robust to noise.",
        downloadUrl     = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx",
        sha256          = "",
        sizeBytes       = 643_854L,
        parameterCount  = "<1M",
        contextWindow   = 0,
        quantization    = "FP32",
        format          = ModelFormat.ONNX,
        isMultimodal    = false,
    )

    val KOKORO_INT8_MULTILANG_V1_1 = ModelDefinition(
        id              = "kokoro-int8-multi-lang-v1_1",
        displayName     = "Kokoro 82M v1.1 (INT8, multi-lang)",
        description     = "On-device streaming TTS. 82M params INT8-quantized; " +
                          "MOS ~4.5; sub-300ms TTFA; multi-voice English-first " +
                          "with multilingual variants bundled.",
        downloadUrl     = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-int8-multi-lang-v1_1.tar.bz2",
        sha256          = "",
        sizeBytes       = 147_031_220L,
        parameterCount  = "82M (INT8)",
        contextWindow   = 0,
        quantization    = "INT8",
        format          = ModelFormat.SHERPA_TAR_BZ2,
        isMultimodal    = false,
    )

    /** All voice models in download order. */
    val all: List<ModelDefinition> = listOf(
        SILERO_VAD,                  // smallest first — gives quickest first-success feedback
        MOONSHINE_BASE_EN_INT8,
        KOKORO_INT8_MULTILANG_V1_1,
    )

    val totalBytes: Long get() = all.sumOf { it.sizeBytes }
}
