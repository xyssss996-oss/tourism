package org.example.springboot.service;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.springboot.entity.*;
import org.example.springboot.mapper.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 协同过滤推荐服务
 * 
 * 功能：基于用户评分数据，实时计算协同过滤推荐
 * 
 * 算法说明：
 * - 基于用户的协同过滤：找到相似用户，推荐他们喜欢的景点
 * - 基于物品的协同过滤：找到相似景点，推荐用户可能喜欢的景点
 * - 混合策略：结合两种算法的结果，提高推荐准确度
 * 
 * 数据来源：
 * - 用户评分：Comment 表中的 rating 字段
 * - 景点信息：ScenicSpot 表
 * 
 * 性能优化：
 * - 实时计算，无需预计算
 * - 缓存相似度矩阵（可选）
 * - 支持增量更新
 * 
 * @author AI Assistant
 */
@Slf4j
@Service
public class CollaborativeFilteringService {

    @Resource
    private CommentMapper commentMapper;

    @Resource
    private ScenicSpotService scenicSpotService;

    @Resource
    private ScenicCollectionMapper scenicCollectionMapper;

    @Resource
    private TicketOrderMapper ticketOrderMapper;

    @Resource
    private TicketMapper ticketMapper;

    /**
     * 获取协同过滤推荐
     * 
     * @param userId 当前用户ID
     * @param topN 返回推荐数量
     * @return 推荐的景点ID列表
     */
    public List<Long> getCollaborativeFilteringRecommendations(Long userId, int topN) {
        log.info("=== 开始协同过滤推荐 ===");
        log.info("用户ID: {}, 推荐数量: {}", userId, topN);

        try {
            // 1. 获取所有用户行为数据（评分、订单、收藏）
            List<Comment> allComments = commentMapper.selectList(null);
            List<ScenicCollection> allCollections = scenicCollectionMapper.selectList(null);
            List<TicketOrder> allOrders = ticketOrderMapper.selectList(null);
            
            log.info("总评论数: {}, 总收藏数: {}, 总订单数: {}", 
                allComments.size(), allCollections.size(), allOrders.size());

            // 2. 构建综合用户-景点行为矩阵（评分 + 订单 + 收藏）
            Map<Long, Map<Long, Double>> userItemMatrix = buildComprehensiveUserItemMatrix(
                allComments, allCollections, allOrders
            );
            log.info("用户数: {}, 景点数: {}", userItemMatrix.size(), 
                userItemMatrix.values().stream().mapToInt(Map::size).max().orElse(0));

            // 3. 检查用户是否存在
            if (!userItemMatrix.containsKey(userId)) {
                log.warn("用户 {} 没有任何行为记录，返回热门景点", userId);
                return getPopularScenicSpotsByBehavior(allComments, allCollections, allOrders, topN);
            }

            // 4. 基于用户的协同过滤
            Map<Long, Double> userBasedScores = calculateUserBasedScoresWithBehavior(
                userId, userItemMatrix
            );
            log.info("基于用户的推荐候选数: {}", userBasedScores.size());

            // 5. 基于物品的协同过滤
            Map<Long, Double> itemBasedScores = calculateItemBasedScoresWithBehavior(
                userId, userItemMatrix
            );
            log.info("基于物品的推荐候选数: {}", itemBasedScores.size());

            // 6. 融合两种算法的结果
            Map<Long, Double> finalScores = mergeScores(userBasedScores, itemBasedScores);

            // 7. 排序推荐（优先推荐用户未有行为的景点，其次推荐相似景点）
            Set<Long> userInteractedItems = userItemMatrix.get(userId).keySet();
            
            // 先获取用户未有行为的景点
            List<Long> recommendations = finalScores.entrySet().stream()
                .filter(e -> !userInteractedItems.contains(e.getKey()))
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(topN)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
            
            // 如果推荐数不足，补充用户已有行为但相似度高的景点
            if (recommendations.size() < topN) {
                int needed = topN - recommendations.size();
                List<Long> similarItems = finalScores.entrySet().stream()
                    .filter(e -> userInteractedItems.contains(e.getKey()))
                    .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                    .limit(needed)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
                recommendations.addAll(similarItems);
            }

            log.info("最终推荐数: {}", recommendations.size());
            recommendations.forEach(id -> log.info("推荐景点ID: {}", id));

            log.info("=== 协同过滤推荐完成 ===");
            return recommendations;

        } catch (Exception e) {
            log.error("=== 协同过滤推荐失败 ===");
            log.error("错误信息: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 构建综合用户-景点行为矩阵（评分 + 订单 + 收藏）
     * 
     * 行为权重：
     * - 评分：权重为评分值（1-5）
     * - 订单：权重为 4（表示用户已购买）
     * - 收藏：权重为 3（表示用户感兴趣）
     * 
     * @param comments 所有评论
     * @param collections 所有收藏
     * @param orders 所有订单
     * @return 用户ID -> (景点ID -> 综合行为分数)
     */
    private Map<Long, Map<Long, Double>> buildComprehensiveUserItemMatrix(
        List<Comment> comments,
        List<ScenicCollection> collections,
        List<TicketOrder> orders
    ) {
        Map<Long, Map<Long, Double>> matrix = new HashMap<>();

        // 1. 添加评分数据（权重：评分值）
        for (Comment comment : comments) {
            if (comment.getRating() != null && comment.getRating() > 0) {
                matrix.computeIfAbsent(comment.getUserId(), k -> new HashMap<>())
                    .merge(comment.getScenicId(), (double) comment.getRating(), Double::sum);
            }
        }

        // 2. 添加收藏数据（权重：3）
        for (ScenicCollection collection : collections) {
            matrix.computeIfAbsent(collection.getUserId(), k -> new HashMap<>())
                .merge(collection.getScenicId(), 3.0, Double::sum);
        }

        // 3. 添加订单数据（权重：4）
        // 需要通过 ticket 表获取景点ID
        Map<Long, Long> ticketToScenicMap = buildTicketToScenicMap();
        for (TicketOrder order : orders) {
            // 只计算已支付或已完成的订单
            if (order.getStatus() != null && (order.getStatus() == 1 || order.getStatus() == 4)) {
                Long scenicId = ticketToScenicMap.get(order.getTicketId());
                if (scenicId != null) {
                    matrix.computeIfAbsent(order.getUserId(), k -> new HashMap<>())
                        .merge(scenicId, 4.0, Double::sum);
                }
            }
        }

        return matrix;
    }

    /**
     * 构建 Ticket -> ScenicId 的映射
     */
    private Map<Long, Long> buildTicketToScenicMap() {
        Map<Long, Long> map = new HashMap<>();
        List<Ticket> tickets = ticketMapper.selectList(null);
        for (Ticket ticket : tickets) {
            map.put(ticket.getId(), ticket.getScenicId());
        }
        return map;
    }

    /**
     * 构建用户-景点评分矩阵（原方法，保留向后兼容）
     * 
     * @param comments 所有评论
     * @return 用户ID -> (景点ID -> 评分)
     */
    private Map<Long, Map<Long, Integer>> buildUserItemMatrix(List<Comment> comments) {
        Map<Long, Map<Long, Integer>> matrix = new HashMap<>();

        for (Comment comment : comments) {
            if (comment.getRating() != null && comment.getRating() > 0) {
                matrix.computeIfAbsent(comment.getUserId(), k -> new HashMap<>())
                    .put(comment.getScenicId(), comment.getRating());
            }
        }

        return matrix;
    }

    /**
     * 基于用户的协同过滤
     * 
     * 算法：
     * 1. 找到与当前用户相似的其他用户（基于评分相似度）
     * 2. 获取这些相似用户评分高的景点
     * 3. 根据相似度加权计算推荐分数
     * 
     * @param userId 当前用户ID
     * @param userItemMatrix 用户-景点评分矩阵
     * @param allComments 所有评论
     * @return 景点ID -> 推荐分数
     */
    private Map<Long, Double> calculateUserBasedScores(
        Long userId,
        Map<Long, Map<Long, Integer>> userItemMatrix,
        List<Comment> allComments
    ) {
        Map<Long, Double> scores = new HashMap<>();
        Map<Long, Integer> userRatings = userItemMatrix.get(userId);

        // 找到相似用户
        Map<Long, Double> userSimilarities = new HashMap<>();
        for (Map.Entry<Long, Map<Long, Integer>> entry : userItemMatrix.entrySet()) {
            Long otherUserId = entry.getKey();
            if (otherUserId.equals(userId)) continue;

            Map<Long, Integer> otherUserRatings = entry.getValue();
            double similarity = calculateUserSimilarity(userRatings, otherUserRatings);

            if (similarity > 0) {
                userSimilarities.put(otherUserId, similarity);
            }
        }

        // 根据相似用户的评分计算推荐分数
        for (Map.Entry<Long, Double> similarityEntry : userSimilarities.entrySet()) {
            Long similarUserId = similarityEntry.getKey();
            Double similarity = similarityEntry.getValue();

            Map<Long, Integer> similarUserRatings = userItemMatrix.get(similarUserId);
            for (Map.Entry<Long, Integer> ratingEntry : similarUserRatings.entrySet()) {
                Long itemId = ratingEntry.getKey();
                Integer rating = ratingEntry.getValue();

                // 只推荐评分较高的物品
                if (rating >= 4) {
                    scores.merge(itemId, similarity * rating, Double::sum);
                }
            }
        }

        return scores;
    }

    /**
     * 基于物品的协同过滤
     * 
     * 算法：
     * 1. 找到与用户已评分景点相似的其他景点
     * 2. 根据相似度和用户对原景点的评分计算推荐分数
     * 
     * @param userId 当前用户ID
     * @param userItemMatrix 用户-景点评分矩阵
     * @param allComments 所有评论
     * @return 景点ID -> 推荐分数
     */
    private Map<Long, Double> calculateItemBasedScores(
        Long userId,
        Map<Long, Map<Long, Integer>> userItemMatrix,
        List<Comment> allComments
    ) {
        Map<Long, Double> scores = new HashMap<>();
        Map<Long, Integer> userRatings = userItemMatrix.get(userId);

        // 构建物品-用户评分矩阵（用于计算物品相似度）
        Map<Long, Map<Long, Integer>> itemUserMatrix = buildItemUserMatrix(userItemMatrix);

        // 对用户评分过的每个物品
        for (Map.Entry<Long, Integer> userRatingEntry : userRatings.entrySet()) {
            Long itemId = userRatingEntry.getKey();
            Integer userRating = userRatingEntry.getValue();

            // 找到相似的物品
            Map<Long, Double> itemSimilarities = new HashMap<>();
            for (Long otherItemId : itemUserMatrix.keySet()) {
                if (otherItemId.equals(itemId)) continue;

                double similarity = calculateItemSimilarity(
                    itemUserMatrix.get(itemId),
                    itemUserMatrix.get(otherItemId)
                );

                if (similarity > 0) {
                    itemSimilarities.put(otherItemId, similarity);
                }
            }

            // 根据相似物品计算推荐分数
            for (Map.Entry<Long, Double> similarityEntry : itemSimilarities.entrySet()) {
                Long similarItemId = similarityEntry.getKey();
                Double similarity = similarityEntry.getValue();

                scores.merge(similarItemId, similarity * userRating, Double::sum);
            }
        }

        return scores;
    }

    /**
     * 计算两个用户的相似度（基于皮尔逊相关系数）
     * 
     * @param userRatings1 用户1的评分
     * @param userRatings2 用户2的评分
     * @return 相似度 [0, 1]
     */
    private double calculateUserSimilarity(
        Map<Long, Integer> userRatings1,
        Map<Long, Integer> userRatings2
    ) {
        // 找到两个用户都评分过的物品
        Set<Long> commonItems = new HashSet<>(userRatings1.keySet());
        commonItems.retainAll(userRatings2.keySet());

        if (commonItems.size() < 2) {
            return 0; // 共同评分少于2个，相似度为0
        }

        // 计算平均评分
        double avg1 = userRatings1.values().stream()
            .mapToInt(Integer::intValue)
            .average()
            .orElse(0);
        double avg2 = userRatings2.values().stream()
            .mapToInt(Integer::intValue)
            .average()
            .orElse(0);

        // 计算皮尔逊相关系数
        double numerator = 0;
        double denominator1 = 0;
        double denominator2 = 0;

        for (Long itemId : commonItems) {
            double diff1 = userRatings1.get(itemId) - avg1;
            double diff2 = userRatings2.get(itemId) - avg2;

            numerator += diff1 * diff2;
            denominator1 += diff1 * diff1;
            denominator2 += diff2 * diff2;
        }

        if (denominator1 == 0 || denominator2 == 0) {
            return 0;
        }

        double correlation = numerator / Math.sqrt(denominator1 * denominator2);
        // 将相关系数从 [-1, 1] 转换到 [0, 1]
        return (correlation + 1) / 2;
    }

    /**
     * 计算两个物品的相似度（基于用户评分向量）
     * 
     * @param itemRatings1 物品1的用户评分
     * @param itemRatings2 物品2的用户评分
     * @return 相似度 [0, 1]
     */
    private double calculateItemSimilarity(
        Map<Long, Integer> itemRatings1,
        Map<Long, Integer> itemRatings2
    ) {
        // 找到两个物品都被评分过的用户
        Set<Long> commonUsers = new HashSet<>(itemRatings1.keySet());
        commonUsers.retainAll(itemRatings2.keySet());

        if (commonUsers.size() < 2) {
            return 0;
        }

        // 计算余弦相似度
        double dotProduct = 0;
        double norm1 = 0;
        double norm2 = 0;

        for (Long userId : commonUsers) {
            double rating1 = itemRatings1.get(userId);
            double rating2 = itemRatings2.get(userId);

            dotProduct += rating1 * rating2;
            norm1 += rating1 * rating1;
            norm2 += rating2 * rating2;
        }

        if (norm1 == 0 || norm2 == 0) {
            return 0;
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    /**
     * 构建物品-用户评分矩阵
     * 
     * @param userItemMatrix 用户-景点评分矩阵
     * @return 景点ID -> (用户ID -> 评分)
     */
    private Map<Long, Map<Long, Integer>> buildItemUserMatrix(
        Map<Long, Map<Long, Integer>> userItemMatrix
    ) {
        Map<Long, Map<Long, Integer>> itemUserMatrix = new HashMap<>();

        for (Map.Entry<Long, Map<Long, Integer>> userEntry : userItemMatrix.entrySet()) {
            Long userId = userEntry.getKey();
            Map<Long, Integer> userRatings = userEntry.getValue();

            for (Map.Entry<Long, Integer> ratingEntry : userRatings.entrySet()) {
                Long itemId = ratingEntry.getKey();
                Integer rating = ratingEntry.getValue();

                itemUserMatrix.computeIfAbsent(itemId, k -> new HashMap<>())
                    .put(userId, rating);
            }
        }

        return itemUserMatrix;
    }

    /**
     * 融合两种算法的结果
     * 
     * 策略：
     * - 基于用户的推荐权重：0.6
     * - 基于物品的推荐权重：0.4
     * 
     * @param userBasedScores 基于用户的推荐分数
     * @param itemBasedScores 基于物品的推荐分数
     * @return 融合后的推荐分数
     */
    private Map<Long, Double> mergeScores(
        Map<Long, Double> userBasedScores,
        Map<Long, Double> itemBasedScores
    ) {
        Map<Long, Double> mergedScores = new HashMap<>();

        // 添加基于用户的分数
        for (Map.Entry<Long, Double> entry : userBasedScores.entrySet()) {
            mergedScores.put(entry.getKey(), entry.getValue() * 0.6);
        }

        // 添加基于物品的分数
        for (Map.Entry<Long, Double> entry : itemBasedScores.entrySet()) {
            mergedScores.merge(entry.getKey(), entry.getValue() * 0.4, Double::sum);
        }

        return mergedScores;
    }

    /**
     * 基于用户的协同过滤（综合行为版本）
     * 
     * @param userId 当前用户ID
     * @param userItemMatrix 综合用户-景点行为矩阵
     * @return 景点ID -> 推荐分数
     */
    private Map<Long, Double> calculateUserBasedScoresWithBehavior(
        Long userId,
        Map<Long, Map<Long, Double>> userItemMatrix
    ) {
        Map<Long, Double> scores = new HashMap<>();
        Map<Long, Double> userBehaviors = userItemMatrix.get(userId);

        // 找到相似用户
        Map<Long, Double> userSimilarities = new HashMap<>();
        for (Map.Entry<Long, Map<Long, Double>> entry : userItemMatrix.entrySet()) {
            Long otherUserId = entry.getKey();
            if (otherUserId.equals(userId)) continue;

            Map<Long, Double> otherUserBehaviors = entry.getValue();
            double similarity = calculateUserSimilarityWithBehavior(userBehaviors, otherUserBehaviors);

            if (similarity > 0) {
                userSimilarities.put(otherUserId, similarity);
            }
        }

        // 根据相似用户的行为计算推荐分数
        for (Map.Entry<Long, Double> similarityEntry : userSimilarities.entrySet()) {
            Long similarUserId = similarityEntry.getKey();
            Double similarity = similarityEntry.getValue();

            Map<Long, Double> similarUserBehaviors = userItemMatrix.get(similarUserId);
            for (Map.Entry<Long, Double> behaviorEntry : similarUserBehaviors.entrySet()) {
                Long itemId = behaviorEntry.getKey();
                Double behavior = behaviorEntry.getValue();

                // 只推荐行为分数较高的物品
                if (behavior >= 3) {
                    scores.merge(itemId, similarity * behavior, Double::sum);
                }
            }
        }

        return scores;
    }

    /**
     * 基于物品的协同过滤（综合行为版本）
     * 
     * @param userId 当前用户ID
     * @param userItemMatrix 综合用户-景点行为矩阵
     * @return 景点ID -> 推荐分数
     */
    private Map<Long, Double> calculateItemBasedScoresWithBehavior(
        Long userId,
        Map<Long, Map<Long, Double>> userItemMatrix
    ) {
        Map<Long, Double> scores = new HashMap<>();
        Map<Long, Double> userBehaviors = userItemMatrix.get(userId);

        // 构建物品-用户行为矩阵
        Map<Long, Map<Long, Double>> itemUserMatrix = buildItemUserMatrixWithBehavior(userItemMatrix);

        // 对用户有行为的每个物品
        for (Map.Entry<Long, Double> userBehaviorEntry : userBehaviors.entrySet()) {
            Long itemId = userBehaviorEntry.getKey();
            Double userBehavior = userBehaviorEntry.getValue();

            // 找到相似的物品
            Map<Long, Double> itemSimilarities = new HashMap<>();
            for (Long otherItemId : itemUserMatrix.keySet()) {
                if (otherItemId.equals(itemId)) continue;

                double similarity = calculateItemSimilarityWithBehavior(
                    itemUserMatrix.get(itemId),
                    itemUserMatrix.get(otherItemId)
                );

                if (similarity > 0) {
                    itemSimilarities.put(otherItemId, similarity);
                }
            }

            // 根据相似物品计算推荐分数
            for (Map.Entry<Long, Double> similarityEntry : itemSimilarities.entrySet()) {
                Long similarItemId = similarityEntry.getKey();
                Double similarity = similarityEntry.getValue();

                scores.merge(similarItemId, similarity * userBehavior, Double::sum);
            }
        }

        return scores;
    }

    /**
     * 计算两个用户的相似度（基于综合行为）
     */
    private double calculateUserSimilarityWithBehavior(
        Map<Long, Double> userBehaviors1,
        Map<Long, Double> userBehaviors2
    ) {
        // 找到两个用户都有行为的物品
        Set<Long> commonItems = new HashSet<>(userBehaviors1.keySet());
        commonItems.retainAll(userBehaviors2.keySet());

        if (commonItems.size() < 2) {
            return 0;
        }

        // 计算平均行为分数
        double avg1 = userBehaviors1.values().stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0);
        double avg2 = userBehaviors2.values().stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0);

        // 计算皮尔逊相关系数
        double numerator = 0;
        double denominator1 = 0;
        double denominator2 = 0;

        for (Long itemId : commonItems) {
            double diff1 = userBehaviors1.get(itemId) - avg1;
            double diff2 = userBehaviors2.get(itemId) - avg2;

            numerator += diff1 * diff2;
            denominator1 += diff1 * diff1;
            denominator2 += diff2 * diff2;
        }

        if (denominator1 == 0 || denominator2 == 0) {
            return 0;
        }

        double correlation = numerator / Math.sqrt(denominator1 * denominator2);
        return (correlation + 1) / 2;
    }

    /**
     * 计算两个物品的相似度（基于综合行为）
     */
    private double calculateItemSimilarityWithBehavior(
        Map<Long, Double> itemBehaviors1,
        Map<Long, Double> itemBehaviors2
    ) {
        // 找到两个物品都被用户有行为的用户
        Set<Long> commonUsers = new HashSet<>(itemBehaviors1.keySet());
        commonUsers.retainAll(itemBehaviors2.keySet());

        if (commonUsers.size() < 2) {
            return 0;
        }

        // 计算余弦相似度
        double dotProduct = 0;
        double norm1 = 0;
        double norm2 = 0;

        for (Long userId : commonUsers) {
            double behavior1 = itemBehaviors1.get(userId);
            double behavior2 = itemBehaviors2.get(userId);

            dotProduct += behavior1 * behavior2;
            norm1 += behavior1 * behavior1;
            norm2 += behavior2 * behavior2;
        }

        if (norm1 == 0 || norm2 == 0) {
            return 0;
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    /**
     * 构建物品-用户行为矩阵（综合版本）
     */
    private Map<Long, Map<Long, Double>> buildItemUserMatrixWithBehavior(
        Map<Long, Map<Long, Double>> userItemMatrix
    ) {
        Map<Long, Map<Long, Double>> itemUserMatrix = new HashMap<>();

        for (Map.Entry<Long, Map<Long, Double>> userEntry : userItemMatrix.entrySet()) {
            Long userId = userEntry.getKey();
            Map<Long, Double> userBehaviors = userEntry.getValue();

            for (Map.Entry<Long, Double> behaviorEntry : userBehaviors.entrySet()) {
                Long itemId = behaviorEntry.getKey();
                Double behavior = behaviorEntry.getValue();

                itemUserMatrix.computeIfAbsent(itemId, k -> new HashMap<>())
                    .put(userId, behavior);
            }
        }

        return itemUserMatrix;
    }

    /**
     * 获取热门景点（综合行为版本）
     * 
     * @param allComments 所有评论
     * @param allCollections 所有收藏
     * @param allOrders 所有订单
     * @param topN 返回数量
     * @return 热门景点ID列表
     */
    private List<Long> getPopularScenicSpotsByBehavior(
        List<Comment> allComments,
        List<ScenicCollection> allCollections,
        List<TicketOrder> allOrders,
        int topN
    ) {
        Map<Long, Double> scenicScores = new HashMap<>();

        // 评分贡献
        allComments.stream()
            .filter(c -> c.getRating() != null && c.getRating() >= 4)
            .forEach(c -> scenicScores.merge(c.getScenicId(), (double) c.getRating(), Double::sum));

        // 收藏贡献（权重3）
        allCollections.forEach(c -> scenicScores.merge(c.getScenicId(), 3.0, Double::sum));

        // 订单贡献（权重4）
        Map<Long, Long> ticketToScenicMap = buildTicketToScenicMap();
        allOrders.stream()
            .filter(o -> o.getStatus() != null && (o.getStatus() == 1 || o.getStatus() == 4))
            .forEach(o -> {
                Long scenicId = ticketToScenicMap.get(o.getTicketId());
                if (scenicId != null) {
                    scenicScores.merge(scenicId, 4.0, Double::sum);
                }
            });

        return scenicScores.entrySet().stream()
            .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
            .limit(topN)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }

    /**
     * 获取热门景点（当用户没有评分记录时使用）
     * 
     * @param allComments 所有评论
     * @param topN 返回数量
     * @return 热门景点ID列表
     */
    private List<Long> getPopularScenicSpots(List<Comment> allComments, int topN) {
        return allComments.stream()
            .filter(c -> c.getRating() != null && c.getRating() >= 4)
            .collect(Collectors.groupingBy(
                Comment::getScenicId,
                Collectors.averagingInt(Comment::getRating)
            ))
            .entrySet().stream()
            .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
            .limit(topN)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }
}
