package org.example.springboot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.springboot.AiService.ScenicRecommendationService;
import org.example.springboot.AiService.StructOutPut;
import org.example.springboot.common.Result;
import org.example.springboot.service.RecommendationResultService;
import org.springframework.web.bind.annotation.*;

/**
 * AI景点推荐控制器
 * 
 * 提供AI智能景点推荐的REST API接口
 * 
 * @author AI Assistant
 */
@Tag(name = "AI景点推荐", description = "AI智能景点推荐相关接口")
@Slf4j
@RestController
@RequestMapping("/ai/recommendation")
public class AiRecommendationController {

    @Resource
    private ScenicRecommendationService scenicRecommendationService;

    @Resource
    private RecommendationResultService recommendationResultService;

    /**
     * 获取结构化景点推荐（原始版本）
     * 
     * 直接返回景点ID和推荐理由的列表，不返回对话式文本
     * 
     * @param request 推荐请求对象
     * @return 推荐列表
     * @deprecated 请使用 getRecommendationsWithDetails() 获取完整数据
     */
    @Operation(summary = "获取结构化景点推荐", description = "根据用户需求返回景点ID和推荐理由列表")
    @PostMapping("/recommend")
    public Result<StructOutPut.RecommendationList> getRecommendations(
        @RequestBody RecommendationRequest request
    ) {
        try {
            log.info("接收到AI推荐请求: {}", request.getUserMessage());
            
            StructOutPut.RecommendationList recommendations = 
                scenicRecommendationService.getRecommendations(request.getUserMessage());
            
            return Result.success(recommendations);
            
        } catch (Exception e) {
            log.error("AI推荐失败: {}", e.getMessage(), e);
            return Result.error("AI推荐服务暂时不可用: " + e.getMessage());
        }
    }

    /**
     * 获取完整的景点推荐（推荐使用）
     * 
     * 流程：
     * 1. 调用AI获取推荐
     * 2. 验证景点ID是否真实存在
     * 3. 过滤无效景点
     * 4. 补充完整的景点信息（图片、标题、分类等）
     * 5. 返回前端可直接渲染的富数据
     * 
     * @param request 推荐请求对象
     * @return 包含完整景点信息的推荐列表
     */
    @Operation(summary = "获取完整的景点推荐", description = "返回包含景点详细信息的推荐列表，前端可直接渲染")
    @PostMapping("/recommend-with-details")
    public Result<StructOutPut.EnrichedRecommendationList> getRecommendationsWithDetails(
        @RequestBody RecommendationRequest request
    ) {
        try {
            log.info("接收到AI推荐请求（完整版）: {}", request.getUserMessage());
            
            // 第1步：调用AI获取推荐
            StructOutPut.RecommendationList aiRecommendations = 
                scenicRecommendationService.getRecommendations(request.getUserMessage());
            
            // 第2步：处理结果（验证 + 数据补充）
            StructOutPut.EnrichedRecommendationList enrichedRecommendations = 
                recommendationResultService.processRecommendations(aiRecommendations);
            
            return Result.success(enrichedRecommendations);
            
        } catch (Exception e) {
            log.error("AI推荐失败: {}", e.getMessage(), e);
            return Result.error("AI推荐服务暂时不可用: " + e.getMessage());
        }
    }
    
    /**
     * 推荐请求DTO
     */
    public static class RecommendationRequest {
        private String userMessage;
        
        public String getUserMessage() {
            return userMessage;
        }
        
        public void setUserMessage(String userMessage) {
            this.userMessage = userMessage;
        }
    }

}
