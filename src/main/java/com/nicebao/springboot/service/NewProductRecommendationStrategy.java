package com.nicebao.springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import com.nicebao.springboot.config.RecommendationConfig;
import com.nicebao.springboot.entity.Category;
import com.nicebao.springboot.entity.Product;
import com.nicebao.springboot.entity.dto.ProductProfileDTO;
import com.nicebao.springboot.entity.dto.RecommendationResultDTO;
import com.nicebao.springboot.entity.dto.UserProfileDTO;
import com.nicebao.springboot.mapper.CategoryMapper;
import com.nicebao.springboot.mapper.ProductMapper;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 新品推荐策略
 * <p>
 * 针对新上架商品的冷启动推荐策略
 * 新品判定标准：上架时间小于阈值 且 销量小于阈值
 * 推荐逻辑：基于用户画像与商品画像的匹配度排序
 * 打分复用 FusionRecommendationService 的画像数据，保持公式一致
 * </p>
 *
 * @author IhaveBB
 * @date 2026/03/21
 */
@Slf4j
@Component
public class NewProductRecommendationStrategy implements RecommendationStrategy {

    @Resource
    private ProductMapper productMapper;

    @Resource
    private CategoryMapper categoryMapper;

    @Resource
    private RecommendationConfig recommendationConfig;

    @Resource
    private FusionRecommendationService fusionRecommendationService;

    @Override
    public String getStrategyName() {
        return "NEW_PRODUCT";
    }

    @Override
    public boolean supportsColdStart() {
        return true;
    }

    @Override
    public double getPriorityScore(Long userId) {
        return userId == null ? 0.8 : 0.4;
    }

    /**
     * 执行新品推荐
     *
     * @param userId      用户ID（可为空，表示未登录用户）
     * @param userProfile 用户画像
     * @param limit       推荐数量限制
     * @return 推荐结果列表
     */
    @Override
    public List<RecommendationResultDTO> recommend(Long userId, UserProfileDTO userProfile, int limit) {
        log.info("[新品推荐] 开始为用户{}生成新品推荐", userId);

        // 1. 获取新品列表
        List<Product> newProducts = getNewProducts();

        if (newProducts.isEmpty()) {
            log.warn("[新品推荐] 暂无新品数据");
            return Collections.emptyList();
        }

        // 2. 基于用户画像计算匹配得分
        List<ProductWithScore> scoredProducts = calculateMatchScores(newProducts, userProfile);

        // 3. 过滤掉画像不匹配的商品，排序并截取Top-N
        List<ProductWithScore> topProducts = scoredProducts.stream()
                .filter(pws -> pws.getProfileScore() > 0)
                .sorted(Comparator.comparing(ProductWithScore::getScore).reversed())
                .limit(limit)
                .collect(Collectors.toList());

        // 4. 转换为DTO
        List<RecommendationResultDTO> results = convertToDTOs(topProducts);

        log.info("[新品推荐] 为用户{}生成{}条新品推荐", userId, results.size());
        return results;
    }

    /**
     * 获取新品列表
     * <p>
     * 新品判定：上架时间小于阈值 且 销量小于阈值
     * </p>
     *
     * @return 新品列表
     */
    private List<Product> getNewProducts() {
        int daysThreshold = recommendationConfig.getNewProductDaysThreshold();
        int salesThreshold = recommendationConfig.getNewProductSalesThreshold();

        LocalDateTime thresholdTime = LocalDateTime.now().minus(daysThreshold, ChronoUnit.DAYS);
        Timestamp thresholdTimestamp = Timestamp.valueOf(thresholdTime);

        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getStatus, 1)
                .gt(Product::getStock, 0)
                .gt(Product::getCreatedAt, thresholdTimestamp)
                .lt(Product::getSalesCount, salesThreshold)
                .orderByDesc(Product::getCreatedAt);

        return productMapper.selectList(wrapper);
    }

    /**
     * 计算商品与用户画像的匹配得分
     *
     * @param products    商品列表
     * @param userProfile 用户画像
     * @return 带得分的商品列表
     */
    private List<ProductWithScore> calculateMatchScores(List<Product> products, UserProfileDTO userProfile) {
        List<ProductWithScore> result = new ArrayList<>();

        for (Product product : products) {
            result.add(calculateMatchScore(product, userProfile));
        }

        return result;
    }

    /**
     * 计算单个商品与用户画像的匹配得分
     * <p>
     * 复用 FusionRecommendationService 的商品画像数据和画像匹配函数，
     * 确保新品推荐与融合推荐使用同一套地区、季节、作物、动物匹配规则。
     * </p>
     *
     * @param product     商品
     * @param userProfile 用户画像
     * @return 匹配得分（0-1）
     * @author IhaveBB
     * @date 2026/03/29
     */
    private ProductWithScore calculateMatchScore(Product product, UserProfileDTO userProfile) {
        if (userProfile == null) {
            return new ProductWithScore(product, 0.5, 0.5);
        }

        // 复用 FusionRecommendationService 获取商品画像
        ProductProfileDTO productProfile = fusionRecommendationService.getProductProfile(product.getId());

        // 画像维度得分（与融合推荐使用同一个函数）
        double profileScore = fusionRecommendationService.computeProfileMatchScore(userProfile, productProfile);
        return new ProductWithScore(product, profileScore, profileScore);
    }

    /**
     * 转换为推荐结果DTO
     *
     * @param scoredProducts 带得分的商品列表
     * @return 推荐结果DTO列表
     * @author IhaveBB
     * @date 2026/03/22
     */
    private List<RecommendationResultDTO> convertToDTOs(List<ProductWithScore> scoredProducts) {
        if (scoredProducts.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量查询分类
        Set<Long> categoryIds = scoredProducts.stream()
                .map(pws -> pws.getProduct().getCategoryId())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Category> categoryMap = categoryIds.isEmpty() ? Collections.emptyMap()
                : categoryMapper.selectBatchIds(new ArrayList<>(categoryIds)).stream()
                        .collect(Collectors.toMap(Category::getId, c -> c));

        List<RecommendationResultDTO> results = new ArrayList<>();

        for (ProductWithScore pws : scoredProducts) {
            Product product = pws.getProduct();
            RecommendationResultDTO dto = new RecommendationResultDTO();

            dto.setProductId(product.getId());
            dto.setProductName(product.getName());
            dto.setPrice(product.getPrice() != null ? product.getPrice().doubleValue() : 0.0);
            dto.setStock(product.getStock() != null ? product.getStock() : 0);
            dto.setImageUrl(product.getImageUrl());
            dto.setCategoryId(product.getCategoryId());
            dto.setSalesCount(product.getSalesCount() != null ? product.getSalesCount() : 0);

            Category category = categoryMap.get(product.getCategoryId());
            if (category != null) {
                dto.setCategoryName(category.getName());
            }

            dto.setScore(pws.getScore());
            dto.setCfScore(0.0);
            dto.setProfileScore(pws.getProfileScore());
            dto.setReason("新品推荐");
            dto.setMatchTags(Arrays.asList("新品", "上市" + calculateDaysSinceLaunch(product) + "天"));

            results.add(dto);
        }

        return results;
    }

    /**
     * 计算上架天数
     */
    private String calculateDaysSinceLaunch(Product product) {
        if (product.getCreatedAt() == null) {
            return "0";
        }
        long days = ChronoUnit.DAYS.between(product.getCreatedAt().toLocalDateTime(), LocalDateTime.now());
        return String.valueOf(days);
    }

    /**
     * 内部类：带得分的商品
     */
    private static class ProductWithScore {
        private final Product product;
        private final double score;
        private final double profileScore;

        public ProductWithScore(Product product, double score, double profileScore) {
            this.product = product;
            this.score = score;
            this.profileScore = profileScore;
        }

        public Product getProduct() {
            return product;
        }

        public double getScore() {
            return score;
        }

        public double getProfileScore() {
            return profileScore;
        }
    }
}
