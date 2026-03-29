package org.example.springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.springboot.entity.Category;
import org.example.springboot.entity.Product;
import org.example.springboot.entity.dto.RecommendationResultDTO;
import org.example.springboot.entity.dto.UserProfileDTO;
import org.example.springboot.mapper.CategoryMapper;
import org.example.springboot.mapper.ProductMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 热销商品推荐策略
 * <p>
 * 基于销量排序的推荐策略，用于：
 * 1. 冷启动场景下的默认推荐
 * 2. 融合推荐的结果补充
 * 3. 作为对比算法基线
 * </p>
 *
 * @author IhaveBB
 * @date 2026/03/21
 */
@Slf4j
@Component
public class HotProductRecommendationStrategy implements RecommendationStrategy {

    @Resource
    private ProductMapper productMapper;

    @Resource
    private CategoryMapper categoryMapper;

    @Override
    public String getStrategyName() {
        return "HOT_PRODUCT";
    }

    @Override
    public boolean supportsColdStart() {
        return true;
    }

    @Override
    public double getPriorityScore(Long userId) {
        // 热销推荐优先级中等
        return 0.5;
    }

    /**
     * 执行热销推荐
     *
     * @param userId      用户ID
     * @param userProfile 用户画像
     * @param limit       推荐数量限制
     * @return 推荐结果列表
     */
    @Override
    public List<RecommendationResultDTO> recommend(Long userId, UserProfileDTO userProfile, int limit) {
        log.info("[热销推荐] 开始生成热销商品推荐");

        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getStatus, 1)
                .orderByDesc(Product::getSalesCount)
                .last("LIMIT " + limit);

        List<Product> hotProducts = productMapper.selectList(wrapper);

        List<RecommendationResultDTO> results = convertToDTOs(hotProducts);

        log.info("[热销推荐] 生成{}条热销商品推荐", results.size());
        return results;
    }

    /**
     * 转换为推荐结果DTO
     */
    private List<RecommendationResultDTO> convertToDTOs(List<Product> products) {
        return products.stream().map(product -> {
            RecommendationResultDTO dto = new RecommendationResultDTO();
            dto.setProductId(product.getId());
            dto.setProductName(product.getName());
            dto.setPrice(product.getPrice() != null ? product.getPrice().doubleValue() : 0.0);
            dto.setImageUrl(product.getImageUrl());
            dto.setCategoryId(product.getCategoryId());
            dto.setSalesCount(product.getSalesCount() != null ? product.getSalesCount() : 0);

            // 获取分类名称
            if (product.getCategoryId() != null) {
                Category category = categoryMapper.selectById(product.getCategoryId());
                if (category != null) {
                    dto.setCategoryName(category.getName());
                }
            }

            dto.setScore(0.5);
            dto.setCfScore(0.0);
            dto.setProfileScore(0.5);
            dto.setReason("热销商品");
            dto.setMatchTags(Collections.singletonList("热销"));

            return dto;
        }).collect(Collectors.toList());
    }
}
