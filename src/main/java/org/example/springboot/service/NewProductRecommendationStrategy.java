package org.example.springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.springboot.config.RecommendationConfig;
import org.example.springboot.entity.Category;
import org.example.springboot.entity.Product;
import org.example.springboot.entity.dto.ProductProfileDTO;
import org.example.springboot.entity.dto.RecommendationResultDTO;
import org.example.springboot.entity.dto.UserProfileDTO;
import org.example.springboot.mapper.CategoryMapper;
import org.example.springboot.mapper.ProductMapper;
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

        // 3. 排序并截取Top-N
        List<ProductWithScore> topProducts = scoredProducts.stream()
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
            double score = calculateMatchScore(product, userProfile);
            result.add(new ProductWithScore(product, score));
        }

        return result;
    }

    /**
     * 计算单个商品与用户画像的匹配得分
     * <p>
     * 复用 FusionRecommendationService 的商品画像数据，
     * 打分公式与 FusionRecommendationService.computeProfileMatchScore 保持一致：
     * - 种子(ID=1)： 0.6 × 区域匹配 + 0.4 × 季节匹配
     * - 农药(ID=2)/肥料(ID=3)： 作物匹配（二元 0/1）
     * - 饲料(ID=4)/兽药(ID=5): 动物匹配（二元 0/1）
     * - 农膜(6)/农机(7)等： 中性分 0.5
     * 最终：0.9 × 画像得分 + 0.1 × 新鲜度
     * </p>
     *
     * @param product     商品
     * @param userProfile 用户画像
     * @return 匹配得分（0-1）
     * @author IhaveBB
     * @date 2026/03/29
     */
    private double calculateMatchScore(Product product, UserProfileDTO userProfile) {
        if (userProfile == null) {
            return calculateFreshnessScore(product);
        }

        // 复用 FusionRecommendationService 获取商品画像
        ProductProfileDTO productProfile = fusionRecommendationService.getProductProfile(product.getId());

        Long topCategoryId = productProfile.getTopCategoryId();

        // 画像维度得分（与 FusionRecommendationService 保持一致）
        double profileScore;
        if (topCategoryId != null && topCategoryId == 1L) {
            // 种子： 0.6 × 区域 + 0.4 × 季节
            profileScore = computeSeedScore(userProfile, productProfile);
        } else if (topCategoryId != null && (topCategoryId == 2L || topCategoryId == 3L)) {
            // 农药/肥料： 作物匹配
            profileScore = computeCropScore(userProfile, productProfile);
        } else if (topCategoryId != null && (topCategoryId == 4L || topCategoryId == 5L)) {
            // 饲料/兽药: 动物匹配
            profileScore = computeAnimalScore(userProfile, productProfile);
        } else {
            profileScore = 0.5;
        }

        // 新品新鲜度（10%）
        double freshnessScore = calculateFreshnessScore(product);

        return 0.9 * profileScore + 0.1 * freshnessScore;
    }

    /**
     * 种子类：0.6 × 区域匹配 + 0.4 × 季节匹配
     * <p>
     * 与 FusionRecommendationService.computeSeedRegionSeasonScore 公式一致
     * </p>
     *
     * @author IhaveBB
     * @date 2026/03/29
     */
    private double computeSeedScore(UserProfileDTO userProfile, ProductProfileDTO productProfile) {
        List<Long> regionIds = productProfile.getRegionIds();
        List<String> seasonNames = productProfile.getSeasonNames();

        // 区域维度
        double regionScore;
        if (regionIds == null || regionIds.isEmpty() || userProfile.getRegionId() == null) {
            regionScore = 0.5;
        } else {
            boolean regionMatch = regionIds.contains(userProfile.getRegionId())
                    || regionIds.contains(8L);
            regionScore = regionMatch ? 1.0 : 0.0;
        }

        // 季节维度
        double seasonScore;
        if (seasonNames == null || seasonNames.isEmpty()) {
            seasonScore = 0.5;
        } else {
            String currentSeason = getCurrentSeason();
            boolean seasonMatch = seasonNames.contains("全年")
                    || seasonNames.stream().anyMatch(s -> s.contains(currentSeason));
            seasonScore = seasonMatch ? 1.0 : 0.0;
        }

        return 0.6 * regionScore + 0.4 * seasonScore;
    }

    /**
     * 农药/肥料：作物匹配（二元 0/1）
     * <p>
     * 与 FusionRecommendationService.computeCropMatchScore 公式一致
     * </p>
     *
     * @author IhaveBB
     * @date 2026/03/29
     */
    private double computeCropScore(UserProfileDTO userProfile, ProductProfileDTO productProfile) {
        List<Long> userCrops = userProfile.getPreferredCropIds();
        List<Long> productCrops = productProfile.getCropIds();

        if (userCrops == null || userCrops.isEmpty()) {
            return 0.5;
        }
        if (productCrops == null || productCrops.isEmpty()) {
            return 0.5;
        }

        boolean hasMatch = userCrops.stream().anyMatch(productCrops::contains);
        return hasMatch ? 1.0 : 0.0;
    }

    /**
     * 饲料/兽药：动物匹配（二元 0/1）
     * <p>
     * 与 FusionRecommendationService.computeAnimalMatchScore 公式一致
     * </p>
     *
     * @author IhaveBB
     * @date 2026/03/29
     */
    private double computeAnimalScore(UserProfileDTO userProfile, ProductProfileDTO productProfile) {
        List<Long> userAnimals = userProfile.getPreferredAnimalIds();
        List<Long> productAnimals = productProfile.getAnimalIds();

        if (userAnimals == null || userAnimals.isEmpty()) {
            return 0.5;
        }
        if (productAnimals == null || productAnimals.isEmpty()) {
            return 0.5;
        }

        boolean hasMatch = userAnimals.stream().anyMatch(productAnimals::contains);
        return hasMatch ? 1.0 : 0.0;
    }

    /**
     * 获取当前季节名称
     *
     * @author IhaveBB
     * @date 2026/03/29
     */
    private String getCurrentSeason() {
        java.time.Month month = java.time.LocalDate.now().getMonth();
        return switch (month) {
            case MARCH, APRIL, MAY -> "春";
            case JUNE, JULY, AUGUST -> "夏";
            case SEPTEMBER, OCTOBER, NOVEMBER -> "秋";
            case DECEMBER, JANUARY, FEBRUARY -> "冬";
            default -> "春";
        };
    }

    /**
     * 计算新品新鲜度得分
     *
     * @param product 商品
     * @return 新鲜度得分（0-1）
     */
    private double calculateFreshnessScore(Product product) {
        Timestamp createdAt = product.getCreatedAt();
        if (createdAt == null) {
            return 0.5;
        }

        LocalDateTime createTime = createdAt.toLocalDateTime();
        LocalDateTime now = LocalDateTime.now();

        long daysSinceCreated = ChronoUnit.DAYS.between(createTime, now);
        int threshold = recommendationConfig.getNewProductDaysThreshold();

        return Math.max(0, 1.0 - (double) daysSinceCreated / threshold);
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
            dto.setImageUrl(product.getImageUrl());
            dto.setCategoryId(product.getCategoryId());
            dto.setSalesCount(product.getSalesCount() != null ? product.getSalesCount() : 0);

            Category category = categoryMap.get(product.getCategoryId());
            if (category != null) {
                dto.setCategoryName(category.getName());
            }

            dto.setScore(pws.getScore());
            dto.setCfScore(0.0);
            dto.setProfileScore(pws.getScore());
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

        public ProductWithScore(Product product, double score) {
            this.product = product;
            this.score = score;
        }

        public Product getProduct() {
            return product;
        }

        public double getScore() {
            return score;
        }
    }
}
