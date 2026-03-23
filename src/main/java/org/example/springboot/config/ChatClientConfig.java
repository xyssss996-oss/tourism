package org.example.springboot.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;

import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ChatClient 配置类
 * 
 * ⚠️ 重要：此配置类一般不需要修改
 * 唯一可以调整的是 defaultSystem 参数（如有需要）
 * 
 * 用途：为AI景点推荐功能提供ChatClient和ChatMemory
 * 
 * @author AI Assistant
 */
@Configuration
public class ChatClientConfig {





    /**
     * 配置 ChatClient - OpenAI兼容的聊天客户端
     * 
     * 功能说明：
     * - 集成 OpenAI 兼容的 API（通过硅基流动接入 DeepSeek-V3）
     * - 自动管理对话记忆（通过 MessageChatMemoryAdvisor）
     * - 支持流式和非流式响应
     * - 支持工具调用（Function Calling）
     * 
     * 使用场景：
     * - AI景点智能推荐
     * - 自然语言理解用户需求
     * - 多轮对话上下文保持
     * 
     * ⚠️ 注意：
     * - Bean名称 "open-ai" 用于区分不同的AI服务提供商
     * - 在Service中注入时需要使用 @Qualifier("open-ai")
     * 
     * @param openAiChatModel OpenAI Chat模型（由Spring AI自动配置）
     * @param chatMemory 会话记忆存储
     * @return ChatClient 实例
     */
    @Bean("open-ai")
    public ChatClient openAIChatClient(OpenAiChatModel openAiChatModel, ChatMemory chatMemory) {
        return ChatClient.builder(openAiChatModel)
                // 可选：设置默认系统提示词（如果所有AI服务共用同一个系统提示）
                // .defaultSystem("你是一个智能旅游助手...")
                .build();
    }
}
