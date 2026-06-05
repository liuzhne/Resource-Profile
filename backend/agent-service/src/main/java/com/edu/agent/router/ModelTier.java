package com.edu.agent.router;

/**
 * H-4：模型路由 tier。
 *
 * <ul>
 *   <li>{@link #LOCAL} —— 宿主本地 LLM（llama.cpp/vLLM，含 14B）。数据不出本地，用于敏感/原始画像。</li>
 *   <li>{@link #CLOUD} —— 云端 OpenAI-compatible API。偏推理、已脱敏的方案/审核类调用。</li>
 * </ul>
 */
public enum ModelTier {
    LOCAL,
    CLOUD
}
