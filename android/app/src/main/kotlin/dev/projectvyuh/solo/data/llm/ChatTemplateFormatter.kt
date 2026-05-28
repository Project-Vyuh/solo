package dev.projectvyuh.solo.data.llm

import dev.projectvyuh.solo.core.model.ChatTemplate
import dev.projectvyuh.solo.domain.model.Conversation
import dev.projectvyuh.solo.domain.model.Message
import dev.projectvyuh.solo.domain.model.Role

/**
 * Formats a [Conversation] into the prompt string a specific model family
 * expects. The model has to see the *exact* special-token sequence it was
 * trained on; getting this wrong produces fluent-but-irrelevant output.
 *
 * Each [ChatTemplate] format below mirrors the reference Jinja templates
 * shipped in the respective model's tokenizer config on Hugging Face.
 *
 * We intentionally format in Kotlin (not via llama.cpp's
 * `llama_chat_apply_template`) so the logic is explicit, testable, and
 * doesn't require pulling in the `common` static library.
 */
object ChatTemplateFormatter {

    /**
     * @param conversation full chat history
     * @param template format the target model expects
     * @return string ready to pass to LlamaCppEngine.complete; ends with the
     *         "assistant turn opens here" tokens, so the next sampled token
     *         is the start of the reply.
     */
    fun format(conversation: Conversation, template: ChatTemplate): String =
        when (template) {
            ChatTemplate.GEMMA4 -> formatGemma4(conversation)
            ChatTemplate.GEMMA3 -> formatGemma3(conversation)
            ChatTemplate.CHATML -> formatChatML(conversation)
            ChatTemplate.LLAMA3 -> formatLlama3(conversation)
            ChatTemplate.PHI    -> formatPhi(conversation)
        }

    // --- Gemma 4 ------------------------------------------------------------
    //
    // Gemma 4 introduced new control tokens. Per Google's prompt-formatting
    // docs (ai.google.dev/gemma/docs/core/prompt-formatting-gemma4):
    //
    //   <|turn>system
    //   {system}<turn|>
    //   <|turn>user
    //   {user}<turn|>
    //   <|turn>model
    //   {model}<turn|>
    //
    // Unlike Gemma 3, Gemma 4 has a true `system` role — no need to fold the
    // system message into the first user turn.
    //
    // BOS is added by the tokenizer (add_bos=true). The trailing `<|turn>model`
    // opens the assistant's reply so the next sampled token is the start.
    private fun formatGemma4(conversation: Conversation): String {
        val sb = StringBuilder()
        for (m in conversation.messages) {
            val tag = when (m.role) {
                Role.SYSTEM    -> "system"
                Role.USER      -> "user"
                Role.ASSISTANT -> "model"
                Role.TOOL      -> continue   // not used in Phase 1A
            }
            sb.append("<|turn>").append(tag).append("\n")
              .append(m.content).append("<turn|>\n")
        }
        sb.append("<|turn>model\n")
        return sb.toString()
    }

    // --- Gemma 3 (retained for fallback / legacy GGUFs) ---------------------
    //
    //   <start_of_turn>user
    //   {system}\n\n{user_1}<end_of_turn>
    //   <start_of_turn>model
    //   {assistant_1}<end_of_turn>
    private fun formatGemma3(conversation: Conversation): String {
        val sb = StringBuilder()
        val (system, rest) = conversation.messages.partition { it.role == Role.SYSTEM }
        val systemPrefix = system.joinToString("\n\n") { it.content }

        var firstUserSeen = false
        for (m in rest) {
            when (m.role) {
                Role.USER -> {
                    sb.append("<start_of_turn>user\n")
                    if (!firstUserSeen && systemPrefix.isNotEmpty()) {
                        sb.append(systemPrefix).append("\n\n")
                        firstUserSeen = true
                    }
                    sb.append(m.content).append("<end_of_turn>\n")
                }
                Role.ASSISTANT -> {
                    sb.append("<start_of_turn>model\n")
                      .append(m.content).append("<end_of_turn>\n")
                }
                else -> {}
            }
        }
        sb.append("<start_of_turn>model\n")
        return sb.toString()
    }

    // --- ChatML (Qwen, OpenAI-derivative) -----------------------------------
    private fun formatChatML(conversation: Conversation): String {
        val sb = StringBuilder()
        for (m in conversation.messages) {
            val tag = when (m.role) {
                Role.SYSTEM    -> "system"
                Role.USER      -> "user"
                Role.ASSISTANT -> "assistant"
                Role.TOOL      -> "tool"
            }
            sb.append("<|im_start|>").append(tag).append("\n")
              .append(m.content).append("<|im_end|>\n")
        }
        sb.append("<|im_start|>assistant\n")
        return sb.toString()
    }

    // --- Llama 3 -------------------------------------------------------------
    private fun formatLlama3(conversation: Conversation): String {
        val sb = StringBuilder()
        for (m in conversation.messages) {
            val tag = when (m.role) {
                Role.SYSTEM    -> "system"
                Role.USER      -> "user"
                Role.ASSISTANT -> "assistant"
                Role.TOOL      -> "tool"
            }
            sb.append("<|start_header_id|>").append(tag).append("<|end_header_id|>\n\n")
              .append(m.content).append("<|eot_id|>")
        }
        sb.append("<|start_header_id|>assistant<|end_header_id|>\n\n")
        return sb.toString()
    }

    // --- Phi -----------------------------------------------------------------
    private fun formatPhi(conversation: Conversation): String {
        val sb = StringBuilder()
        for (m in conversation.messages) {
            val tag = when (m.role) {
                Role.SYSTEM    -> "system"
                Role.USER      -> "user"
                Role.ASSISTANT -> "assistant"
                Role.TOOL      -> "tool"
            }
            sb.append("<|").append(tag).append("|>\n")
              .append(m.content).append("<|end|>\n")
        }
        sb.append("<|assistant|>\n")
        return sb.toString()
    }
}
