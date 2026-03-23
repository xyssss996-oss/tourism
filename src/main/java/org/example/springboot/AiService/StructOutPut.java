package org.example.springboot.AiService;

import java.time.LocalDateTime;

/**
 * AI服务输出结构定义
 * 
 * 职责：定义AI服务返回的数据结构（使用Java Record）
 * 
 * @author AI Assistant
 */
public class StructOutPut {

    /**
     * AI聊天会话信息
     * 
     * @param sessionId 会话唯一标识
     * @param userId 用户ID
     * @param createTime 创建时间
     * @param status 会话状态（active-活跃, closed-已关闭）
     */
    public record ChatSession(
        String sessionId,
        Long userId,
        LocalDateTime createTime,
        String status
    ) {}

    /**
     * AI聊天消息
     * 
     * @param id 消息ID
     * @param sessionId 所属会话ID
     * @param role 角色（user-用户, assistant-AI助手）
     * @param content 消息内容
     * @param createTime 创建时间
     */
    public record ChatMessage(
        Long id,
        String sessionId,
        String role,
        String content,
        LocalDateTime createTime
    ) {}

    /**
     * 景点推荐结果（仅包含ID和理由，用于AI返回）
     * 
     * @param scenicSpotId 景点ID
     * @param reason 推荐理由
     */
    public record SimpleRecommendation(
        Long scenicSpotId,
        String reason
    ) {}

    /**
     * 景点推荐列表响应（AI返回的原始结构）
     * 
     * @param recommendations 推荐列表
     */
    public record RecommendationList(
        java.util.List<SimpleRecommendation> recommendations
    ) {}

    /**
     * 完整的景点推荐结果（包含景点详细信息）
     * 用于前端直接渲染，无需额外请求
     * 
     * @param scenicSpotId 景点ID
     * @param name 景点名称
     * @param imageUrl 景点封面图片URL
     * @param location 景点位置
     * @param price 景点门票价格
     * @param categoryName 景点分类名称
     * @param rating 景点评分
     * @param commentCount 评论数量
     * @param reason 推荐理由
     */
    public record EnrichedRecommendation(
        Long scenicSpotId,
        String name,
        String imageUrl,
        String location,
        java.math.BigDecimal price,
        String categoryName,
        Double rating,
        Long commentCount,
        String reason
    ) {}

    /**
     * 完整的景点推荐列表响应（包含景点详细信息）
     * 
     * @param recommendations 完整推荐列表
     */
    public record EnrichedRecommendationList(
        java.util.List<EnrichedRecommendation> recommendations
    ) {}
}
