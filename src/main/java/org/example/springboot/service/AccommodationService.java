package org.example.springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.example.springboot.entity.Accommodation;
import org.example.springboot.entity.ScenicSpot;
import org.example.springboot.mapper.AccommodationMapper;
import org.example.springboot.mapper.AccommodationReviewMapper;
import org.example.springboot.mapper.ScenicSpotMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AccommodationService {
    
    @Resource
    private AccommodationMapper accommodationMapper;
    
    @Resource
    private ScenicSpotMapper scenicSpotMapper;
    
    @Resource
    private AccommodationReviewMapper reviewMapper;
    
    /**
     * 分页查询住宿列表
     * @param name 住宿名称（模糊查询）
     * @param scenicId 景点ID
     * @param type 住宿类型
     * @param minPrice 最低价格
     * @param maxPrice 最高价格
     * @param minRating 最低星级（注意：参数名为minRating，但实际筛选的是酒店星级starLevel）
     * @param sortBy 排序方式：price_asc(价格升序), price_desc(价格降序), rating_desc(星级降序)
     * @param currentPage 当前页码
     * @param size 每页记录数
     * @return 分页数据
     */
    public Page<Accommodation> getAccommodationsByPage(String name, Integer scenicId, String type, 
                                                      String minPrice, String maxPrice, 
                                                      String minRating, String sortBy,
                                                      Integer currentPage, Integer size) {
        LambdaQueryWrapper<Accommodation> queryWrapper = new LambdaQueryWrapper<>();
        
        // 添加查询条件
        // 住宿名称模糊查询
        if (StringUtils.isNotBlank(name)) {
            queryWrapper.like(Accommodation::getName, name);
        }
        
        if (scenicId != null) {
            queryWrapper.eq(Accommodation::getScenicId, scenicId);
        }
        
        if (StringUtils.isNotBlank(type)) {
            queryWrapper.eq(Accommodation::getType, type);
        }
        
        // 处理价格区间筛选
        // 注意：priceRange字段存储格式如"200-500"，这里使用模糊匹配
        if (StringUtils.isNotBlank(minPrice)) {
            // 如果只有最低价格（如1000+的情况）
            if (StringUtils.isBlank(maxPrice)) {
                // 价格范围大于等于minPrice，这里简化处理，实际可能需要更复杂的逻辑
                queryWrapper.and(wrapper -> 
                    wrapper.like(Accommodation::getPriceRange, minPrice)
                           .or()
                           .apply("CAST(SUBSTRING_INDEX(price_range, '-', 1) AS UNSIGNED) >= {0}", Integer.parseInt(minPrice))
                );
            } else {
                // 有明确的价格区间
                queryWrapper.and(wrapper -> {
                    // 匹配完整区间或部分重叠的区间
                    wrapper.like(Accommodation::getPriceRange, minPrice + "-" + maxPrice)
                           .or()
                           .apply("(CAST(SUBSTRING_INDEX(price_range, '-', 1) AS UNSIGNED) >= {0} " +
                                  "AND CAST(SUBSTRING_INDEX(price_range, '-', -1) AS UNSIGNED) <= {1})", 
                                  Integer.parseInt(minPrice), Integer.parseInt(maxPrice));
                });
            }
        }
        
        // 酒店星级筛选（注意：参数名为minRating，但实际筛选的是酒店星级starLevel字段）
        if (StringUtils.isNotBlank(minRating)) {
            queryWrapper.ge(Accommodation::getStarLevel, Double.parseDouble(minRating));
        }
        
        // 排序处理
        if (StringUtils.isNotBlank(sortBy)) {
            switch (sortBy) {
                case "price_asc":
                    // 价格从低到高（按priceRange字段的起始价格排序）
                    queryWrapper.last("ORDER BY CAST(SUBSTRING_INDEX(price_range, '-', 1) AS UNSIGNED) ASC");
                    break;
                case "price_desc":
                    // 价格从高到低
                    queryWrapper.last("ORDER BY CAST(SUBSTRING_INDEX(price_range, '-', 1) AS UNSIGNED) DESC");
                    break;
                case "rating_desc":
                    // 星级从高到低
                    queryWrapper.orderByDesc(Accommodation::getStarLevel);
                    break;
                default:
                    // 默认按星级降序
                    queryWrapper.orderByDesc(Accommodation::getStarLevel);
            }
        } else {
            // 没有指定排序方式，默认按星级降序
            queryWrapper.orderByDesc(Accommodation::getStarLevel);
        }
        
        Page<Accommodation> page = accommodationMapper.selectPage(new Page<>(currentPage, size), queryWrapper);
        
        // 获取关联的景点信息
        List<Long> scenicIds = page.getRecords().stream()
                .map(Accommodation::getScenicId)
                .filter(id -> id != null)
                .collect(Collectors.toList());
        
        if (!scenicIds.isEmpty()) {
            List<ScenicSpot> scenicSpots = scenicSpotMapper.selectBatchIds(scenicIds);
            Map<Long, String> scenicNameMap = scenicSpots.stream()
                    .collect(Collectors.toMap(ScenicSpot::getId, ScenicSpot::getName));
            
            // 设置关联的景点名称
            page.getRecords().forEach(accommodation -> {
                if (accommodation.getScenicId() != null) {
                    accommodation.setScenicName(scenicNameMap.get(accommodation.getScenicId()));
                }
            });
        }
        
        return page;
    }
    
    /**
     * 获取住宿详情
     * @param id 住宿ID
     * @return 住宿详情
     */
    public Accommodation getAccommodationById(Integer id) {
        Accommodation accommodation = accommodationMapper.selectById(id);
        if (accommodation != null) {
            // 查询关联景点信息
            if (accommodation.getScenicId() != null) {
                ScenicSpot scenicSpot = scenicSpotMapper.selectById(accommodation.getScenicId());
                if (scenicSpot != null) {
                    accommodation.setScenicName(scenicSpot.getName());
                }
            }
            
            // 查询评价数量和平均评分
            LambdaQueryWrapper<org.example.springboot.entity.AccommodationReview> reviewWrapper = new LambdaQueryWrapper<>();
            reviewWrapper.eq(org.example.springboot.entity.AccommodationReview::getAccommodationId, id);
            
            // 评价数量
            Long reviewCount = reviewMapper.selectCount(reviewWrapper);
            accommodation.setReviewCount(reviewCount);
            
            // 计算平均评分
            if (reviewCount > 0) {
                List<org.example.springboot.entity.AccommodationReview> reviews = reviewMapper.selectList(reviewWrapper);
                java.math.BigDecimal totalRating = java.math.BigDecimal.ZERO;
                for (org.example.springboot.entity.AccommodationReview review : reviews) {
                    totalRating = totalRating.add(review.getRating());
                }
                java.math.BigDecimal averageRating = totalRating.divide(
                    new java.math.BigDecimal(reviewCount), 
                    1, 
                    java.math.RoundingMode.HALF_UP
                );
                accommodation.setAverageRating(averageRating);
            } else {
                // 没有评价时使用默认评分或starLevel
                accommodation.setAverageRating(accommodation.getStarLevel() != null ? 
                    accommodation.getStarLevel() : java.math.BigDecimal.ZERO);
            }
        }
        return accommodation;
    }
    
    /**
     * 添加住宿信息
     * @param accommodation 住宿信息
     * @return 是否成功
     */
    public boolean addAccommodation(Accommodation accommodation) {
        return accommodationMapper.insert(accommodation) > 0;
    }
    
    /**
     * 更新住宿信息
     * @param accommodation 住宿信息
     * @return 是否成功
     */
    public boolean updateAccommodation(Accommodation accommodation) {
        return accommodationMapper.updateById(accommodation) > 0;
    }
    
    /**
     * 删除住宿信息
     * @param id 住宿ID
     * @return 是否成功
     */
    public boolean deleteAccommodation(Integer id) {
        return accommodationMapper.deleteById(id) > 0;
    }
    
    /**
     * 获取住宿类型列表
     * @return 住宿类型列表
     */
    public List<String> getAccommodationTypes() {
        LambdaQueryWrapper<Accommodation> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.select(Accommodation::getType);
        queryWrapper.groupBy(Accommodation::getType);
        
        List<Accommodation> accommodations = accommodationMapper.selectList(queryWrapper);
        return accommodations.stream().map(Accommodation::getType).collect(Collectors.toList());
    }
} 