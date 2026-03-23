package org.example.springboot.service;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.springboot.AiService.StructOutPut;
import org.example.springboot.entity.ScenicSpot;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 推荐结果处理服务
 * 
 * 职责：
 * - 验证AI返回的景点ID是否真实存在于数据库
 * - 过滤无效的景点ID
 * - 补充景点的完整信息（图片、标题、分类、位置、价格等）
 * - 返回前端可直接渲染的富数据结构
 * 
 * @author AI Assistant
 */
@Slf4j
@Service
public class RecommendationResultService {

    @Resource
    private ScenicSpotService scenicSpotService;

    /**
     * 处理AI推荐结果：验证 + 数据补充
     * 
     * 流程：
     * 1. 提取AI返回的景点ID列表
     * 2. 验证每个ID是否在数据库中存在
     * 3. 过滤无效的ID
     * 4. 批量获取有效景点的完整信息
     * 5. 构建富数据结构返回给前端
     * 
     * @param aiRecommendations AI返回的原始推荐列表
     * @return 包含完整景点信息的推荐列表
     */
    public StructOutPut.EnrichedRecommendationList processRecommendations(
            StructOutPut.RecommendationList aiRecommendations) {
        
        log.info("=== 开始处理AI推荐结果 ===");
        
        if (aiRecommendations == null || aiRecommendations.recommendations() == null 
                || aiRecommendations.recommendations().isEmpty()) {
            log.warn("AI推荐列表为空");
            return new StructOutPut.EnrichedRecommendationList(new ArrayList<>());
        }

        List<StructOutPut.SimpleRecommendation> simpleRecommendations = 
                aiRecommendations.recommendations();
        
        log.info("AI返回推荐数量: {}", simpleRecommendations.size());

        // 第1步：验证景点ID并获取完整信息
        List<StructOutPut.EnrichedRecommendation> enrichedList = simpleRecommendations.stream()
                .map(this::enrichRecommendation)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .collect(Collectors.toList());

        log.info("处理后有效推荐数量: {} (过滤掉 {} 个无效景点)", 
                enrichedList.size(), 
                simpleRecommendations.size() - enrichedList.size());

        if (simpleRecommendations.size() > enrichedList.size()) {
            log.warn("发现 {} 个无效的景点ID，已被过滤", 
                    simpleRecommendations.size() - enrichedList.size());
        }

        log.info("=== AI推荐结果处理完成 ===");
        return new StructOutPut.EnrichedRecommendationList(enrichedList);
    }

    /**
     * 丰富单个推荐：验证景点存在性并补充完整信息
     * 
     * @param simpleRecommendation 简单推荐（仅包含ID和理由）
     * @return 包含完整信息的推荐，如果景点不存在则返回empty
     */
    private java.util.Optional<StructOutPut.EnrichedRecommendation> enrichRecommendation(
            StructOutPut.SimpleRecommendation simpleRecommendation) {
        
        Long scenicSpotId = simpleRecommendation.scenicSpotId();
        String reason = simpleRecommendation.reason();

        try {
            // 验证景点是否存在
            ScenicSpot scenicSpot = scenicSpotService.getById(scenicSpotId);
            
            if (scenicSpot == null) {
                log.warn("景点ID {} 在数据库中不存在，将被过滤", scenicSpotId);
                return java.util.Optional.empty();
            }

            log.debug("景点ID {} 验证通过，开始补充信息", scenicSpotId);

            // 补充评分和评论数（从评论表计算）
            scenicSpotService.fillRatingAndCommentCountForOne(scenicSpot);

            // 补充完整信息
            StructOutPut.EnrichedRecommendation enriched = new StructOutPut.EnrichedRecommendation(
                    scenicSpot.getId(),
                    scenicSpot.getName(),
                    scenicSpot.getImageUrl(),
                    scenicSpot.getLocation(),
                    scenicSpot.getPrice(),
                    scenicSpot.getCategoryInfo() != null ? scenicSpot.getCategoryInfo().getName() : null,
                    scenicSpot.getRating(),
                    scenicSpot.getCommentCount(),
                    reason
            );

            log.debug("景点 {} 信息补充完成（评分: {}, 评论数: {}）", 
                    scenicSpot.getName(), scenicSpot.getRating(), scenicSpot.getCommentCount());
            return java.util.Optional.of(enriched);

        } catch (Exception e) {
            log.error("处理景点ID {} 时发生错误: {}", scenicSpotId, e.getMessage(), e);
            return java.util.Optional.empty();
        }
    }
}
