package dev.chatads.eval.judge;

import dev.chatads.llm.TokenUsage;

/** One chat completion against a judge model. Faked in tests; the real one is {@link OpenAiCompatibleJudgeClient}. */
public interface JudgeClient {

    record Completion(String content, TokenUsage usage) { }

    Completion complete(String systemPrompt, String userMessage);

    /** Model name, for reports and the price table. */
    String model();
}
