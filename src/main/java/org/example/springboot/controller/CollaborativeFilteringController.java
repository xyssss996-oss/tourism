package org.example.springboot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.springboot.common.Result;
import org.example.springboot.service.CollaborativeFilteringService;
import org.example.springboot.service.ScenicSpotService;
import org.example.springboot.entity.ScenicSpot;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 协同过滤推荐控制器
 * 
 * 提供基于协同过滤算法的景点推荐REST API接口
 * 
 * @author AI Assistant
 */
@Tag(name = "协同过滤推荐", description = "基于用户评分的协同过滤推荐接口")
@Slf4j
@RestController
@RequestMapping("/recommendation/collaborative-filtering")
public class CollaborativeFilteringController {

    @Resource
    private CollaborativeFilteringService collaborativeFilteringService;

    @Resource
    private ScenicSpotService scenicSpotService;

    /**
     * 获取协同过滤推荐景点列表
     * 
     * 算法说明：
     * - 基于用户的协同过滤：找到相似用户，推荐他们喜欢的景点
     * - 基于物品的协同过滤：找到相似景点，推荐用户可能喜欢的景点
     * - 混合策略：结合两种算法的结果（用户权重0.6，物品权重0.4）
     * 
     * @param userId 当前用户ID
     * @param topN 返回推荐数量（默认10）
     * @return 推荐的景点列表
     */
    @Operation(summary = "获取协同过滤推荐", description = "基于用户评分数据返回推荐景点列表")
    @GetMapping("/recommend")
    public Result<List<ScenicSpot>> getRecommendations(
        @Parameter(description = "用户ID") @RequestParam Long userId,
        @Parameter(description = "推荐数量") @RequestParam(defaultValue = "10") Integer topN
    ) {
        try {
            log.info("接收到协同过滤推荐请求: userId={}, topN={}", userId, topN);

            // 获取推荐的景点ID列表
            List<Long> recommendedIds = collaborativeFilteringService
                .getCollaborativeFilteringRecommendations(userId, topN);

            // 获取景点详细信息
            List<ScenicSpot> recommendations = recommendedIds.stream()
                .map(scenicSpotService::getById)
                .collect(Collectors.toList());

            log.info("返回推荐景点数: {}", recommendations.size());
            return Result.success(recommendations);

        } catch (Exception e) {
            log.error("协同过滤推荐失败: {}", e.getMessage(), e);
            return Result.error("推荐服务暂时不可用: " + e.getMessage());
        }
    }

    /**
     * 获取推荐景点ID列表（不包含详细信息）
     * 
     * 适合需要轻量级响应的场景
     * 
     * @param userId 当前用户ID
     * @param topN 返回推荐数量（默认10）
     * @return 推荐的景点ID列表
     */
    @Operation(summary = "获取推荐景点ID列表", description = "返回推荐景点的ID列表")
    @GetMapping("/recommend-ids")
    public Result<List<Long>> getRecommendationIds(
        @Parameter(description = "用户ID") @RequestParam Long userId,
        @Parameter(description = "推荐数量") @RequestParam(defaultValue = "10") Integer topN
    ) {
        try {
            log.info("接收到推荐ID请求: userId={}, topN={}", userId, topN);

            List<Long> recommendedIds = collaborativeFilteringService
                .getCollaborativeFilteringRecommendations(userId, topN);

            return Result.success(recommendedIds);

        } catch (Exception e) {
            log.error("获取推荐ID失败: {}", e.getMessage(), e);
            return Result.error("推荐服务暂时不可用: " + e.getMessage());
        }
    }
}
