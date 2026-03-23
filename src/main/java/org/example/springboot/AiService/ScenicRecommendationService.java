package org.example.springboot.AiService;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.springboot.service.AiChatSessionService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;

/**
 * AI景点推荐服务
 * 
 * 功能：提供智能景点推荐的AI服务
 * 
 * 使用场景：
 * - 用户在首页输入旅游需求
 * - AI理解需求并推荐合适的景点
 * - 支持多轮对话，保持上下文
 * 
 * @author AI Assistant
 */
@Slf4j
@Service
public class ScenicRecommendationService {

    @Autowired
    @Qualifier("open-ai")
    private ChatClient chatClient;

    @Autowired
    private ChatMemory chatMemory;

    @Resource
    private AiChatSessionService aiChatSessionService;

    @Resource
    private Tools tools;

    @Resource
    private org.example.springboot.service.ScenicSpotService scenicSpotService;

    /**
     * 获取结构化的景点推荐列表
     * 
     * 特点：
     * - 返回结构化数据，包含景点ID和推荐理由
     * - 不返回对话式文本
     * - 适合前端直接展示
     * 
     * @param userMessage 用户需求描述
     * @return 结构化的推荐列表
     */
    public StructOutPut.RecommendationList getRecommendations(String userMessage) {
        log.info("=== 开始AI景点推荐 ===");
        log.info("用户需求: {}", userMessage);
        
        try {
            // 预处理用户消息
            String processedMessage = preprocessUserMessage(userMessage);
            log.info("预处理后消息: {}", processedMessage);
            
            // 获取数据库中景点总数
            long totalScenicSpots = scenicSpotService.count();
            log.info("数据库景点总数: {}", totalScenicSpots);
            
            // 使用包含统计信息的动态Prompt
            String systemPrompt = PromptManage.getScenicRecommendationPrompt(totalScenicSpots);
            
            log.info("调用AI服务，工具已注入: {}", tools != null);
            
            // 调用AI服务，获取结构化输出
            StructOutPut.RecommendationList result = chatClient.prompt()
                .system(systemPrompt)
                .user(processedMessage)
                .tools(tools)
                .call()
                .entity(StructOutPut.RecommendationList.class);
            
            log.info("AI返回推荐数量: {}", 
                result.recommendations() != null ? result.recommendations().size() : 0);
            
            if (result.recommendations() != null) {
                result.recommendations().forEach(r -> 
                    log.info("推荐景点ID: {}, 理由: {}", r.scenicSpotId(), r.reason())
                );
            }
            
            log.info("=== AI景点推荐完成 ===");
            return result;
            
        } catch (Exception e) {
            log.error("=== AI景点推荐失败 ===");
            log.error("错误信息: {}", e.getMessage(), e);
            throw new RuntimeException("AI推荐服务调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 预处理用户消息
     * 
     * 用途：
     * - 转换相对时间表达（如"今天"、"周末"转为具体日期）
     * - 清理特殊字符
     * - 补充上下文信息
     * 
     * @param userMessage 原始用户消息
     * @return 处理后的消息
     */
    private String preprocessUserMessage(String userMessage) {
        // 这里可以添加日期转换、关键词规范化等预处理逻辑
        // 目前暂时直接返回原消息
        return userMessage;
    }

    /**
     * 清除会话记忆
     * 
     * @param sessionId 会话ID
     */
    public void clearSessionMemory(String sessionId) {
        log.info("清除AI景点推荐会话记忆，会话ID: {}", sessionId);
        try {
            chatMemory.clear(sessionId);
            log.info("会话记忆清除成功，会话ID: {}", sessionId);
        } catch (Exception e) {
            log.error("清除会话记忆失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 获取会话历史消息
     * 
     * @param sessionId 会话ID
     * @return 消息列表
     */
    public java.util.List<StructOutPut.ChatMessage> getSessionHistory(String sessionId) {
        log.info("获取AI景点推荐会话历史，会话ID: {}", sessionId);
        try {
            return aiChatSessionService.getSessionMessages(sessionId);
        } catch (Exception e) {
            log.error("获取会话历史失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取会话历史失败", e);
        }
    }
}
