package org.example.springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.springboot.entity.Comment;
import org.example.springboot.entity.ScenicCategory;
import org.example.springboot.entity.ScenicSpot;
import org.example.springboot.exception.ServiceException;
import org.example.springboot.mapper.CommentMapper;
import org.example.springboot.mapper.ScenicCategoryMapper;
import org.example.springboot.mapper.ScenicSpotMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ScenicSpotService {
    @Resource
    private ScenicSpotMapper scenicSpotMapper;
    
    @Resource
    private ScenicCategoryService scenicCategoryService;
    @Autowired
    private ScenicCategoryMapper scenicCategoryMapper;

    @Resource
    private CommentMapper commentMapper;

    public Page<ScenicSpot> getScenicSpotsByPage(String name, String location, Long categoryId, String sortBy, Integer currentPage, Integer size) {
        LambdaQueryWrapper<ScenicSpot> queryWrapper = new LambdaQueryWrapper<>();

        // 如果有名称搜索，进行综合搜索（名称、地区、描述）
        if (StringUtils.isNotBlank(name)) {
            queryWrapper.and(wrapper -> wrapper
                .like(ScenicSpot::getName, name)
                .or()
                .like(ScenicSpot::getLocation, name)
                .or()
                .like(ScenicSpot::getDescription, name)
            );
        }

        // 如果有单独的地区搜索
        if (StringUtils.isNotBlank(location)) {
            queryWrapper.like(ScenicSpot::getLocation, location);
        }

        // 分类筛选
        if (categoryId != null) {
            queryWrapper.eq(ScenicSpot::getCategoryId, categoryId);
        }

        // 排序处理
        if (StringUtils.isNotBlank(sortBy)) {
            switch (sortBy) {
                case "price_asc":
                    // 价格从低到高
                    queryWrapper.orderByAsc(ScenicSpot::getPrice);
                    break;
                case "price_desc":
                    // 价格从高到低
                    queryWrapper.orderByDesc(ScenicSpot::getPrice);
                    break;
                case "create_time_desc":
                    // 最新发布
                    queryWrapper.orderByDesc(ScenicSpot::getCreateTime);
                    break;
                default:
                    // 默认按ID降序排序
                    queryWrapper.orderByDesc(ScenicSpot::getId);
            }
        } else {
            // 没有指定排序方式，默认按ID降序排序，让新添加的景点排在前面
            queryWrapper.orderByDesc(ScenicSpot::getId);
        }

        Page<ScenicSpot> page = scenicSpotMapper.selectPage(new Page<>(currentPage, size), queryWrapper);

        // 填充分类信息
        fillCategoryInfo(page.getRecords());

        return page;
    }
    
    /**
     * 根据分类ID查询景点
     */
    public List<ScenicSpot> getScenicSpotsByCategoryId(Long categoryId) {
        LambdaQueryWrapper<ScenicSpot> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ScenicSpot::getCategoryId, categoryId);
        
        List<ScenicSpot> spots = scenicSpotMapper.selectList(queryWrapper);
        fillCategoryInfo(spots);
        
        return spots;
    }

    public ScenicSpot getById(Long id) {
        ScenicSpot spot = scenicSpotMapper.selectById(id);
        if (spot == null) throw new ServiceException("景点不存在");
        
        // 填充分类信息
        if (spot.getCategoryId() != null) {
            spot.setCategoryInfo(scenicCategoryService.getCategoryById(spot.getCategoryId()));
        }
        
        return spot;
    }

    public void createScenicSpot(ScenicSpot spot) {
        // 验证分类是否存在
        if (spot.getCategoryId() != null) {
            ScenicCategory category = scenicCategoryService.getCategoryById(spot.getCategoryId());
            if (category == null) {
                throw new ServiceException("所选分类不存在");
            }
        }
        
        if (scenicSpotMapper.insert(spot) <= 0) throw new ServiceException("新增景点失败");
    }

    public void updateScenicSpot(Long id, ScenicSpot spot) {
        if (scenicSpotMapper.selectById(id) == null) throw new ServiceException("景点不存在");
        spot.setId(id);
        
        // 验证分类是否存在
        if (spot.getCategoryId() != null) {
            ScenicCategory category = scenicCategoryService.getCategoryById(spot.getCategoryId());
            if (category == null) {
                throw new ServiceException("所选分类不存在");
            }
        }
        
        if (scenicSpotMapper.updateById(spot) <= 0) throw new ServiceException("更新景点失败");
    }

    public void deleteScenicSpot(Long id) {
        if (scenicSpotMapper.deleteById(id) <= 0) throw new ServiceException("删除景点失败");
    }

    public List<ScenicSpot> getAll() {
        List<ScenicSpot> spots = scenicSpotMapper.selectList(new LambdaQueryWrapper<>());
        fillCategoryInfo(spots);
        return spots;
    }
    
    /**
     * 填充景点的分类信息
     */
    private void fillCategoryInfo(List<ScenicSpot> spots) {
        if (spots == null || spots.isEmpty()) {
            return;
        }
        
        // 获取所有涉及到的分类ID
        List<Long> categoryIds = spots.stream()
                .map(ScenicSpot::getCategoryId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        
        if (categoryIds.isEmpty()) {
            return;
        }
        
        // 批量查询分类信息
        LambdaQueryWrapper<ScenicCategory> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(ScenicCategory::getId, categoryIds);
        List<ScenicCategory> categories = scenicCategoryMapper.selectList(queryWrapper);
        
        // 转换为Map便于查找
        Map<Long, ScenicCategory> categoryMap = categories.stream()
                .collect(Collectors.toMap(ScenicCategory::getId, category -> category));
        
        // 填充分类信息
        spots.forEach(spot -> {
            if (spot.getCategoryId() != null && categoryMap.containsKey(spot.getCategoryId())) {
                spot.setCategoryInfo(categoryMap.get(spot.getCategoryId()));
            }
        });
    }

    /**
     * 填充景点的评分和评论数
     * 
     * 计算方式：
     * - rating: 该景点所有评论的平均评分
     * - commentCount: 该景点的评论总数
     */
    public void fillRatingAndCommentCount(List<ScenicSpot> spots) {
        if (spots == null || spots.isEmpty()) {
            return;
        }

        // 获取所有景点的ID
        List<Long> scenicIds = spots.stream()
                .map(ScenicSpot::getId)
                .collect(Collectors.toList());

        // 批量查询所有景点的评论
        LambdaQueryWrapper<Comment> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(Comment::getScenicId, scenicIds);
        List<Comment> allComments = commentMapper.selectList(queryWrapper);

        // 按景点ID分组
        Map<Long, List<Comment>> commentsByScenic = allComments.stream()
                .collect(Collectors.groupingBy(Comment::getScenicId));

        // 填充评分和评论数
        spots.forEach(spot -> {
            List<Comment> comments = commentsByScenic.getOrDefault(spot.getId(), new ArrayList<>());
            
            // 计算评论数
            spot.setCommentCount((long) comments.size());
            
            // 计算平均评分
            if (!comments.isEmpty()) {
                double avgRating = comments.stream()
                        .mapToInt(Comment::getRating)
                        .average()
                        .orElse(0.0);
                spot.setRating(Math.round(avgRating * 10.0) / 10.0); // 保留一位小数
            } else {
                spot.setRating(null);
            }
        });
    }

    /**
     * 填充单个景点的评分和评论数
     */
    public void fillRatingAndCommentCountForOne(ScenicSpot spot) {
        if (spot == null) {
            return;
        }

        // 查询该景点的所有评论
        LambdaQueryWrapper<Comment> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Comment::getScenicId, spot.getId());
        List<Comment> comments = commentMapper.selectList(queryWrapper);

        // 计算评论数
        spot.setCommentCount((long) comments.size());

        // 计算平均评分
        if (!comments.isEmpty()) {
            double avgRating = comments.stream()
                    .mapToInt(Comment::getRating)
                    .average()
                    .orElse(0.0);
            spot.setRating(Math.round(avgRating * 10.0) / 10.0); // 保留一位小数
        } else {
            spot.setRating(null);
        }
    }

    /**
     * 获取热门景点
     * @param limit 限制数量
     * @return 热门景点列表
     */
    public List<ScenicSpot> getHotScenics(Integer limit) {
        // 这里可以根据实际需求定义热门景点的获取逻辑
        // 例如根据评分、访问量、价格等条件排序
        LambdaQueryWrapper<ScenicSpot> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByDesc(ScenicSpot::getId);
        queryWrapper.last("LIMIT " + limit);
        return scenicSpotMapper.selectList(queryWrapper);
    }

    /**
     * 根据ID列表批量查询景点
     * @param ids 景点ID列表
     * @return 景点列表
     */
    public List<ScenicSpot> getScenicSpotsByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }

        // 直接从数据库查询，避免Redis缓存的类型转换问题
        LambdaQueryWrapper<ScenicSpot> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(ScenicSpot::getId, ids);
        List<ScenicSpot> spots = scenicSpotMapper.selectList(queryWrapper);

        // 填充分类信息
        fillCategoryInfo(spots);

        return spots;
    }

    /**
     * 获取搜索建议
     * @param keyword 搜索关键词
     * @param limit 限制数量
     * @return 搜索建议列表
     */
    public List<Map<String, Object>> getSearchSuggestions(String keyword, Integer limit) {
        List<Map<String, Object>> result = new ArrayList<>();

        if (StringUtils.isBlank(keyword)) {
            return result;
        }

        // 搜索景点建议
        LambdaQueryWrapper<ScenicSpot> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.and(wrapper -> wrapper
            .like(ScenicSpot::getName, keyword)
            .or()
            .like(ScenicSpot::getLocation, keyword)
        );
        queryWrapper.orderByDesc(ScenicSpot::getId);
        queryWrapper.last("LIMIT " + limit);

        List<ScenicSpot> scenics = scenicSpotMapper.selectList(queryWrapper);

        for (ScenicSpot scenic : scenics) {
            Map<String, Object> suggestion = new HashMap<>();
            suggestion.put("id", scenic.getId());
            suggestion.put("name", scenic.getName());
            suggestion.put("location", scenic.getLocation());
            suggestion.put("imageUrl", scenic.getImageUrl());
            suggestion.put("type", "scenic");
            suggestion.put("price", scenic.getPrice());
            result.add(suggestion);
        }

        return result;
    }

    /**
     * 获取景点总数
     * 
     * @return 数据库中景点的总数量
     */
    public long count() {
        return scenicSpotMapper.selectCount(null);
    }
}