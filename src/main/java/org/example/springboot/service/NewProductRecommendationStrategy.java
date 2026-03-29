package org.example.springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.springboot.config.RecommendationConfig;
import org.example.springboot.entity.Category;
import org.example.springboot.entity.Product;
import org.example.springboot.entity.dto.CategoryPreferenceDTO;
import org.example.springboot.entity.dto.RecommendationResultDTO;
import org.example.springboot.entity.dto.UserProfileDTO;
import org.example.springboot.mapper.CategoryMapper;
import org.example.springboot.mapper.ProductCropMapper;
import org.example.springboot.mapper.ProductMapper;
import org.example.springboot.mapper.ProductRegionSeasonMapper;
import org.example.springboot.mapper.RegionMapper;
import org.example.springboot.mapper.SeasonMapper;
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

    /**
     * 新品画像服务
     */
    @Resource
    private FusionRecommendationService fusionRecommendationService;

    @Resource
    private ProductCropMapper productCropMapper;

    @Resource
    private ProductRegionSeasonMapper productRegionSeasonMapper;

    @Resource
    private RegionMapper regionMapper;

    @Resource
    private SeasonMapper seasonMapper;

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
        // 新用户优先使用新品推荐
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

        // 计算时间阈值
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
     * 评分维度：
     * 1. 品类偏好匹配（30%）
     * 2. 价格区间匹配（25%）
     * 3. 地域匹配（20%）
     * 4. 适用作物匹配（15%，农资电商特有）
     * 5. 新品新鲜度（10%）
     * </p>
     *
     * @param product     商品
     * @param userProfile 用户画像
     * @return 匹配得分（0-1）
     */
    private double calculateMatchScore(Product product, UserProfileDTO userProfile) {
        if (userProfile == null) {
            // 无用户画像时，仅按新鲜度排序
            return calculateFreshnessScore(product);
        }

        // 判断是否为种子类商品
        boolean isSeed = isSeedProduct(product.getCategoryId());

        // 1. 品类偏好匹配（30%）
        double categoryScore = calculateCategoryScore(product, userProfile);

        // 2. 价格区间匹配（25%）
        double priceScore = calculatePriceScore(product, userProfile);

        // 3. 按商品类型计算第三维度（35% = 原20%+15%）
        double thirdDimensionScore;
        if (isSeed) {
            // 种子：地域+季节匹配
            thirdDimensionScore = calculateRegionScore(product, userProfile);
        } else {
            // 非种子（农药、肥料等）：适用作物匹配
            thirdDimensionScore = calculateCropScore(product, userProfile);
        }

        // 4. 新品新鲜度（10%）
        double freshnessScore = calculateFreshnessScore(product);

        // 加权求和
        return 0.3 * categoryScore + 0.25 * priceScore + 0.35 * thirdDimensionScore + 0.1 * freshnessScore;
    }

    /**
     * 判断商品是否属于种子分类（一级分类ID=1为种子）
     */
    private boolean isSeedProduct(Long categoryId) {
        if (categoryId == null) {
            return false;
        }
        Long currentId = categoryId;
        int maxDepth = 10;
        while (maxDepth-- > 0) {
            Category category = categoryMapper.selectById(currentId);
            if (category == null) {
                return false;
            }
            if (category.getLevel() != null && category.getLevel() == 1) {
                return category.getId() == 1L;
            }
            if (category.getParentId() == null || category.getParentId() == 0L) {
                return category.getId() == 1L;
            }
            currentId = category.getParentId();
        }
        return false;
    }

    /**
     * 计算品类匹配得分
     */
    private double calculateCategoryScore(Product product, UserProfileDTO userProfile) {
        List<CategoryPreferenceDTO> preferences = userProfile.getCategoryPreferences();
        if (preferences == null || preferences.isEmpty()) {
            return 0.5;
        }

        for (CategoryPreferenceDTO pref : preferences) {
            if (pref.getCategoryId().equals(product.getCategoryId())) {
                return pref.getWeight();
            }
        }

        return 0.1;
    }

    /**
     * 计算价格区间匹配得分
     */
    private double calculatePriceScore(Product product, UserProfileDTO userProfile) {
        String consumptionLevel = userProfile.getConsumptionLevel();
        if (consumptionLevel == null) {
            return 0.5;
        }

        double price = product.getPrice() != null ? product.getPrice().doubleValue() : 0.0;
        String priceRange;
        if (price >= recommendationConfig.getHighPriceThreshold()) {
            priceRange = "HIGH";
        } else if (price >= recommendationConfig.getMediumPriceThreshold()) {
            priceRange = "MEDIUM";
        } else {
            priceRange = "LOW";
        }

        if (consumptionLevel.equals(priceRange)) {
            return 1.0;
        } else if (("HIGH".equals(consumptionLevel) && "MEDIUM".equals(priceRange))
                || ("MEDIUM".equals(consumptionLevel) && "LOW".equals(priceRange))) {
            return recommendationConfig.getPriceNearMatchScore();
        } else if (("LOW".equals(consumptionLevel) && "MEDIUM".equals(priceRange))
                || ("MEDIUM".equals(consumptionLevel) && "HIGH".equals(priceRange))) {
            return recommendationConfig.getPriceFarMatchScore();
        } else {
            return recommendationConfig.getPriceNoMatchScore();
        }
    }

    /**
     * 计算地域+季节匹配得分
     * <p>
     * 基于商品的适用区域-季节配置进行匹配
     * 配置示例：春季 华南、冬季春季秋季 华北、全年 华东
     * </p>
     */
    private double calculateRegionScore(Product product, UserProfileDTO userProfile) {
        if (userProfile == null) {
            return 0.5;
        }

        // 1. 获取商品的区域-季节配置
        LambdaQueryWrapper<org.example.springboot.entity.ProductRegionSeason> wrapper =
                new LambdaQueryWrapper<>();
        wrapper.eq(org.example.springboot.entity.ProductRegionSeason::getProductId, product.getId());
        List<org.example.springboot.entity.ProductRegionSeason> regionSeasonConfigs =
                productRegionSeasonMapper.selectList(wrapper);

        // 商品未配置区域-季节限制，默认为通用商品
        if (regionSeasonConfigs == null || regionSeasonConfigs.isEmpty()) {
            return 0.5;
        }

        // 2. 获取用户所在区域ID
        Long userRegionId = userProfile.getRegionId();
        if (userRegionId == null) {
            return 0.5;
        }

        // 3. 获取当前季节
        String currentSeason = getCurrentSeasonName();

        // 4. 检查是否存在匹配的配置（区域匹配 + 季节匹配）
        boolean regionMatch = false;
        boolean seasonMatch = false;

        for (org.example.springboot.entity.ProductRegionSeason config : regionSeasonConfigs) {
            // 区域匹配检查
            if (config.getRegionId() != null && config.getRegionId().equals(userRegionId)) {
                regionMatch = true;

                // 季节匹配检查
                if (config.getSeasonId() != null) {
                    // 查询季节名称
                    org.example.springboot.entity.Season season =
                            seasonMapper.selectById(config.getSeasonId());
                    if (season != null && season.getName() != null) {
                        // 支持"全年"或当前季节匹配
                        if ("全年".equals(season.getName()) || season.getName().contains(currentSeason)) {
                            seasonMatch = true;
                            break;
                        }
                    }
                }
            }
        }

        // 5. 计算得分
        if (regionMatch && seasonMatch) {
            return 1.0; // 完全匹配（区域+季节）
        } else if (regionMatch) {
            return 0.6; // 仅区域匹配，季节不匹配
        } else {
            return 0.2; // 区域不匹配
        }
    }

    /**
     * 获取当前季节名称
     */
    private String getCurrentSeasonName() {
        java.time.Month month = java.time.LocalDate.now().getMonth();
        return switch (month) {
            case MARCH, APRIL, MAY -> "春季";
            case JUNE, JULY, AUGUST -> "夏季";
            case SEPTEMBER, OCTOBER, NOVEMBER -> "秋季";
            case DECEMBER, JANUARY, FEBRUARY -> "冬季";
            default -> "春季";
        };
    }

    /**
     * 计算适用作物匹配得分（农资电商特有）
     * <p>
     * 基于用户偏好作物与商品适用作物的匹配度
     * </p>
     */
    private double calculateCropScore(Product product, UserProfileDTO userProfile) {
        if (userProfile == null) {
            return 0.5;
        }

        // 获取用户偏好作物
        List<Long> userCrops = userProfile.getPreferredCropIds();
        if (userCrops == null || userCrops.isEmpty()) {
            return 0.5; // 无偏好，返回中性得分
        }

        // 获取商品适用作物
        LambdaQueryWrapper<org.example.springboot.entity.ProductCrop> wrapper =
                new LambdaQueryWrapper<>();
        wrapper.eq(org.example.springboot.entity.ProductCrop::getProductId, product.getId());
        List<org.example.springboot.entity.ProductCrop> productCrops =
                productCropMapper.selectList(wrapper);

        if (productCrops == null || productCrops.isEmpty()) {
            return 0.5; // 商品无适用作物限制，通用商品
        }

        List<Long> productCropIds = productCrops.stream()
                .map(org.example.springboot.entity.ProductCrop::getCategoryId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        if (productCropIds.isEmpty()) {
            return 0.5;
        }

        // 计算交集
        long matchCount = userCrops.stream()
                .filter(productCropIds::contains)
                .count();

        if (matchCount == 0) {
            return recommendationConfig.getCropNoMatchScore(); // 完全不匹配
        }

        // 匹配比例：交集 / 用户偏好作物数
        double matchRatio = (double) matchCount / userCrops.size();

        // 匹配得分：基础分 + 匹配比例 × 乘数（由配置控制）
        return recommendationConfig.getCropBaseScore()
                + matchRatio * recommendationConfig.getCropMatchMultiplier();
    }

    /**
     * 计算新品新鲜度得分
     * <p>
     * 越新上架的商品得分越高
     * </p>
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

        // 线性衰减：刚上架得1分，接近阈值时趋近于0
        return Math.max(0, 1.0 - (double) daysSinceCreated / threshold);
    }

    /**
     * 转换为推荐结果DTO
     * <p>
     * 使用批量查询分类，避免 N+1 问题。
     * </p>
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

        // 批量查询分类（1次查询，避免N+1）
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
