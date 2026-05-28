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
            ChatTemplate.GEMMA  -> formatGemma(conversation)
            ChatTemplate.CHATML -> formatChatML(conversation)
            ChatTemplate.LLAMA3 -> formatLlama3(conversation)
            ChatTemplate.PHI    -> formatPhi(conversation)
        }

    // --- Gemma ---------------------------------------------------------------
    //
    // Gemma has no dedicated system role. The reference template folds any
    // system message into the first user turn (separated by two newlines).
    // BOS is added by the tokenizer (add_bos=true), so we do NOT prepend <bos>.
    //
    // Format:
    //   <start_of_turn>user
    //   {system}\n\n{user_1}<end_of_turn>
    //   <start_of_turn>model
    //   {assistant_1}<end_of_turn>
    //   <start_of_turn>user
    //   {user_2}<end_of_turn>
    //   <start_of_turn>model
    private fun formatGemma(conversation: Conversation): String {
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
                else -> {} // SYSTEM handled above; TOOL not used in Phase 1A
            }
        }
        sb.append("<start_of_turn>model\n")
        return sb.toString()
    }

    // --- ChatML (Qwen, OpenAI-derivative) -----------------------------------
    //
    // <|im_start|>system
    // {system}<|im_end|>
    // <|im_start|>user
    // {user}<|im_end|>
    // <|im_start|>assistant
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
    //
    // <|begin_of_text|><|start_header_id|>system<|end_header_id|>
    //
    // {system}<|eot_id|><|start_header_id|>user<|end_header_id|>
    //
    // {user}<|eot_id|><|start_header_id|>assistant<|end_header_id|>
    //
    // BOS (<|begin_of_text|>) is added by the tokenizer, so omitted here.
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
    //
    // <|system|>\n{system}<|end|>\n<|user|>\n{user}<|end|>\n<|assistant|>\n
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
