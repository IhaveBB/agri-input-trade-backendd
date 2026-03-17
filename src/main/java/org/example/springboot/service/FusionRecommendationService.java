package org.example.springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.springboot.config.RecommendationConfig;
import org.example.springboot.entity.*;
import org.example.springboot.entity.dto.*;
import org.example.springboot.enumClass.RecommendationActionType;
import org.example.springboot.mapper.*;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Month;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 融合推荐算法服务
 * <p>
 * 基于物品协同过滤 (Item-CF) 与画像约束的融合推荐算法
 * 核心流程：
 * 1. 构建用户 - 商品交互矩阵（基于行为加权）
 * 2. 计算物品相似度矩阵（余弦相似度）
 * 3. 构建用户画像和商品画像
 * 4. 计算协同过滤预测得分
 * 5. 计算画像匹配得分（含业务规则约束）
 * 6. 线性融合两种得分生成最终推荐
 * </p>
 *
 * @author agri-input-trade
 * @version 1.0
 */
@Slf4j
@Service
public class FusionRecommendationService implements RecommendationStrategy {

    // ==================== 依赖注入 ====================

    @Resource
    private RecommendationConfig recommendationConfig;

    @Resource
    private ProductMapper productMapper;

    @Resource
    private CategoryMapper categoryMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private OrderMapper orderMapper;

    @Resource
    private CartMapper cartMapper;

    @Resource
    private FavoriteMapper favoriteMapper;

    @Resource
    private ProductRegionSeasonMapper productRegionSeasonMapper;

    @Resource
    private RegionMapper regionMapper;

    @Resource
    private SeasonMapper seasonMapper;

    @Resource
    private ProductCropMapper productCropMapper;

    @Resource
    private RecommendActionService recommendActionService;

    // ==================== 缓存结构 ====================

    /**
     * 用户 - 商品交互强度矩阵缓存
     * Key: userId, Value: Map<productId, interactionStrength>
     */
    private final Map<Long, Map<Long, Double>> userInteractionMatrix = new ConcurrentHashMap<>();

    /**
     * 物品相似度矩阵缓存
     * Key: productId, Value: Map<similarProductId, similarity>
     */
    private final Map<Long, Map<Long, Double>> itemSimilarityMatrix = new ConcurrentHashMap<>();

    /**
     * 用户画像缓存
     * Key: userId, Value: UserProfileDTO
     */
    private final Map<Long, UserProfileDTO> userProfileCache = new ConcurrentHashMap<>();

    /**
     * 商品画像缓存
     * Key: productId, Value: ProductProfileDTO
     */
    private final Map<Long, ProductProfileDTO> productProfileCache = new ConcurrentHashMap<>();

    /**
     * 推荐结果缓存（用于埋点归因）
     * Key: userId, Value: List of recommended product IDs with scores
     */
    private final Map<Long, List<RecommendedItem>> recommendationCache = new ConcurrentHashMap<>();

    /**
     * 内部类：推荐商品项
     */
    private static class RecommendedItem {
        private final Long productId;
        private final Double cfScore;
        private final Double profileScore;
        private final Double finalScore;

        public RecommendedItem(Long productId, Double cfScore, Double profileScore, Double finalScore) {
            this.productId = productId;
            this.cfScore = cfScore;
            this.profileScore = profileScore;
            this.finalScore = finalScore;
        }

        public Long getProductId() {
            return productId;
        }

        public Double getCfScore() {
            return cfScore;
        }

        public Double getProfileScore() {
            return profileScore;
        }

        public Double getFinalScore() {
            return finalScore;
        }
    }

    // ==================== RecommendationStrategy 接口实现 ====================

    @Override
    public String getStrategyName() {
        return "FUSION";
    }

    @Override
    public boolean supportsColdStart() {
        return true;
    }

    @Override
    public double getPriorityScore(Long userId) {
        // 正常用户优先使用融合推荐
        return userId != null ? 0.9 : 0.3;
    }

    @Override
    public List<RecommendationResultDTO> recommend(Long userId, UserProfileDTO userProfile, int limit) {
        List<RecommendationResultDTO> results = recommend(userId);
        return results.subList(0, Math.min(limit, results.size()));
    }

    // ==================== 对外接口方法 ====================

    /**
     * 为指定用户生成个性化推荐
     *
     * @param userId 用户 ID
     * @return 推荐结果列表
     */
    public List<RecommendationResultDTO> recommend(Long userId) {
        log.info("[融合推荐] 开始为用户{}生成推荐", userId);

        try {
            // 1. 刷新交互矩阵
            refreshInteractionMatrix();

            // 2. 计算或获取物品相似度矩阵
            computeItemSimilarityIfNecessary();

            // 3. 构建用户画像
            UserProfileDTO userProfile = buildUserProfile(userId);

            // 4. 获取用户已交互的商品集合
            Set<Long> interactedProducts = userInteractionMatrix.getOrDefault(userId, new HashMap<>()).keySet();

            // 5. 对每个未交互商品计算推荐得分
            List<RecommendedItem> recommendedItems = computeRecommendationScores(userId, userProfile, interactedProducts);

            // 6. 排序并截取 Top-N
            List<RecommendedItem> topNItems = recommendedItems.stream()
                    .sorted(Comparator.comparing(RecommendedItem::getFinalScore).reversed())
                    .limit(recommendationConfig.getTopN())
                    .collect(Collectors.toList());

            // 7. 缓存推荐结果（用于埋点）
            recommendationCache.put(userId, topNItems);

            // 8. 转换为返回 DTO
            List<RecommendationResultDTO> results = convertToDTOs(topNItems);

            // 9. 记录曝光埋点
            recordExposureImpressions(userId, results);

            log.info("[融合推荐] 为用户{}生成{}条推荐", userId, results.size());
            return results;

        } catch (Exception e) {
            log.error("[融合推荐] 为用户{}生成推荐失败：{}", userId, e.getMessage(), e);
            // 降级策略：返回热销商品
            return getHotProducts(recommendationConfig.getTopN());
        }
    }

    /**
     * 刷新所有用户的推荐
     * 可用于定时任务
     */
    public void refreshAllRecommendations() {
        log.info("[融合推荐] 开始刷新所有用户的推荐");

        try {
            // 1. 重建交互矩阵
            refreshInteractionMatrix();

            // 2. 重新计算物品相似度
            computeItemSimilarity();

            // 3. 清空画像缓存
            userProfileCache.clear();
            productProfileCache.clear();

            // 4. 获取所有有效用户
            LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
            userWrapper.eq(User::getStatus, 1); // 只处理启用状态的用户
            List<User> users = userMapper.selectList(userWrapper);

            // 5. 为每个有行为的用户生成推荐
            int successCount = 0;
            for (User user : users) {
                if (userInteractionMatrix.containsKey(user.getId())
                        && !userInteractionMatrix.get(user.getId()).isEmpty()) {
                    try {
                        recommend(user.getId());
                        successCount++;
                    } catch (Exception e) {
                        log.error("[融合推荐] 用户{}推荐失败：{}", user.getId(), e.getMessage());
                    }
                }
            }

            log.info("[融合推荐] 刷新完成，成功{}个用户", successCount);

        } catch (Exception e) {
            log.error("[融合推荐] 刷新所有用户推荐失败：{}", e.getMessage(), e);
        }
    }

    /**
     * 获取用户的推荐画像
     *
     * @param userId 用户 ID
     * @return 用户画像
     */
    public UserProfileDTO getUserProfile(Long userId) {
        return userProfileCache.computeIfAbsent(userId, this::buildUserProfile);
    }

    /**
     * 获取商品画像
     *
     * @param productId 商品 ID
     * @return 商品画像
     */
    public ProductProfileDTO getProductProfile(Long productId) {
        return productProfileCache.computeIfAbsent(productId, this::buildProductProfile);
    }

    // ==================== 核心算法实现 ====================

    /**
     * 刷新用户 - 商品交互矩阵
     * <p>
     * 交互强度计算公式：
     * r(u,i) = Σ w(k), k ∈ Actions(u,i)
     * 其中 w(k) 为行为 k 的权重
     * </p>
     */
    private void refreshInteractionMatrix() {
        log.info("[交互矩阵] 开始构建用户 - 商品交互矩阵");

        userInteractionMatrix.clear();

        // 1. 加载购买行为（订单状态为已完成）
        LambdaQueryWrapper<Order> orderWrapper = new LambdaQueryWrapper<>();
        orderWrapper.eq(Order::getStatus, 3); // 已完成订单
        List<Order> orders = orderMapper.selectList(orderWrapper);

        for (Order order : orders) {
            if (order.getUserId() == null || order.getProductId() == null) {
                continue;
            }
            addToInteraction(order.getUserId(), order.getProductId(),
                    recommendationConfig.getPurchaseWeight());
        }

        // 2. 加载收藏行为
        LambdaQueryWrapper<Favorite> favoriteWrapper = new LambdaQueryWrapper<>();
        favoriteWrapper.eq(Favorite::getStatus, 1); // 有效收藏
        List<Favorite> favorites = favoriteMapper.selectList(favoriteWrapper);

        for (Favorite favorite : favorites) {
            if (favorite.getUserId() == null || favorite.getProductId() == null) {
                continue;
            }
            addToInteraction(favorite.getUserId(), favorite.getProductId(),
                    recommendationConfig.getFavoriteWeight());
        }

        // 3. 加载购物车行为
        LambdaQueryWrapper<Cart> cartWrapper = new LambdaQueryWrapper<>();
        List<Cart> carts = cartMapper.selectList(cartWrapper);

        for (Cart cart : carts) {
            if (cart.getUserId() == null || cart.getProductId() == null) {
                continue;
            }
            addToInteraction(cart.getUserId(), cart.getProductId(),
                    recommendationConfig.getCartWeight());
        }

        log.info("[交互矩阵] 构建完成，共{}个用户", userInteractionMatrix.size());
    }

    /**
     * 添加交互强度到矩阵
     */
    private void addToInteraction(Long userId, Long productId, int weight) {
        userInteractionMatrix.computeIfAbsent(userId, k -> new HashMap<>())
                .merge(productId, (double) weight, Double::sum);
    }

    /**
     * 计算物品相似度矩阵（仅当缓存为空或过期时）
     * <p>
     * 使用余弦相似度公式：
     * sim(i,j) = Σ(r(u,i) * r(u,j)) / (sqrt(Σr(u,i)²) * sqrt(Σr(u,j)²))
     * </p>
     */
    private void computeItemSimilarityIfNecessary() {
        if (!itemSimilarityMatrix.isEmpty()) {
            return; // 缓存命中
        }
        computeItemSimilarity();
    }

    /**
     * 计算物品相似度矩阵
     */
    private void computeItemSimilarity() {
        log.info("[物品相似度] 开始计算物品相似度矩阵");

        itemSimilarityMatrix.clear();

        // 1. 获取所有商品 ID
        Set<Long> allProductIds = new HashSet<>();
        for (Map<Long, Double> userInteractions : userInteractionMatrix.values()) {
            allProductIds.addAll(userInteractions.keySet());
        }

        if (allProductIds.isEmpty()) {
            log.warn("[物品相似度] 没有交互数据，无法计算相似度");
            return;
        }

        List<Long> productIds = new ArrayList<>(allProductIds);

        // 2. 计算每个商品的模长（分母的一部分）
        Map<Long, Double> productNorms = new HashMap<>();
        for (Long productId : productIds) {
            double sumSq = 0.0;
            for (Map<Long, Double> interactions : userInteractionMatrix.values()) {
                Double strength = interactions.get(productId);
                if (strength != null) {
                    sumSq += strength * strength;
                }
            }
            productNorms.put(productId, Math.sqrt(sumSq));
        }

        // 3. 计算两两物品的余弦相似度
        for (int i = 0; i < productIds.size(); i++) {
            Long productId1 = productIds.get(i);
            Map<Long, Double> similarities = new HashMap<>();

            for (int j = i + 1; j < productIds.size(); j++) {
                Long productId2 = productIds.get(j);

                double similarity = computeCosineSimilarity(
                        productId1, productId2, productNorms);

                // 应用稀疏性优化：阈值过滤
                if (similarity >= recommendationConfig.getSimilarityThreshold()) {
                    similarities.put(productId2, similarity);
                    // 对称矩阵
                    itemSimilarityMatrix.computeIfAbsent(productId2, k -> new HashMap<>())
                            .put(productId1, similarity);
                }
            }

            // 4. 保留 Top-K 相似物品
            if (!similarities.isEmpty()) {
                Map<Long, Double> topK = similarities.entrySet().stream()
                        .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                        .limit(recommendationConfig.getTopK())
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue,
                                (v1, v2) -> v1,
                                LinkedHashMap::new
                        ));
                itemSimilarityMatrix.put(productId1, topK);
            }
        }

        log.info("[物品相似度] 计算完成，共{}个商品有相似度数据", itemSimilarityMatrix.size());
    }

    /**
     * 计算两个物品的余弦相似度
     */
    private double computeCosineSimilarity(Long productId1, Long productId2,
                                           Map<Long, Double> productNorms) {
        double numerator = 0.0;

        // 分子：Σ(r(u,i) * r(u,j))
        for (Map<Long, Double> interactions : userInteractionMatrix.values()) {
            Double strength1 = interactions.get(productId1);
            Double strength2 = interactions.get(productId2);
            if (strength1 != null && strength2 != null) {
                numerator += strength1 * strength2;
            }
        }

        // 分母：sqrt(Σr(u,i)²) * sqrt(Σr(u,j)²)
        double norm1 = productNorms.getOrDefault(productId1, 0.0);
        double norm2 = productNorms.getOrDefault(productId2, 0.0);

        if (norm1 == 0 || norm2 == 0) {
            return 0.0;
        }

        return numerator / (norm1 * norm2);
    }

    /**
     * 构建用户画像
     * <p>
     * 用户画像包含：
     * - 消费能力等级（基于平均订单金额）
     * - 偏好品类分布（基于历史交互）
     * - 地区特征
     * - 价格敏感度
     * </p>
     */
    private UserProfileDTO buildUserProfile(Long userId) {
        UserProfileDTO profile = new UserProfileDTO();
        profile.setUserId(userId);

        // 1. 获取用户基本信息
        User user = userMapper.selectById(userId);
        if (user == null) {
            log.warn("[用户画像] 用户{}不存在", userId);
            return profile;
        }

        // 2. 解析用户位置信息
        parseUserLocation(user, profile);

        // 3. 计算消费能力和偏好
        computeUserConsumptionAndPreferences(userId, profile);

        // 4. 计算偏好作物（从注册信息 + 购买历史）
        computeUserPreferredCrops(userId, profile, user);

        return profile;
    }

    /**
     * 解析用户位置信息
     */
    private void parseUserLocation(User user, UserProfileDTO profile) {
        String location = user.getLocation();
        if (location == null || location.isEmpty()) {
            return;
        }

        // 位置格式：省 - 市 或 省 - 市 - 区
        String[] parts = location.split("-");
        if (parts.length >= 1) {
            profile.setProvince(parts[0].trim());
        }
        if (parts.length >= 2) {
            profile.setCity(parts[1].trim());
        }

        // 根据省份映射到大区
        if (profile.getProvince() != null) {
            String region = org.example.springboot.enumClass.RecommendConstants
                    .PROVINCE_REGION_MAP.get(profile.getProvince());
            profile.setRegionName(region);

            // 查询地区 ID
            LambdaQueryWrapper<Region> regionWrapper = new LambdaQueryWrapper<>();
            regionWrapper.eq(Region::getName, profile.getRegionName());
            Region regionEntity = regionMapper.selectOne(regionWrapper);
            if (regionEntity != null) {
                profile.setRegionId(regionEntity.getId());
            }
        }
    }

    /**
     * 计算用户消费能力和品类偏好
     */
    private void computeUserConsumptionAndPreferences(Long userId, UserProfileDTO profile) {
        // 1. 统计用户购买行为
        LambdaQueryWrapper<Order> orderWrapper = new LambdaQueryWrapper<>();
        orderWrapper.eq(Order::getUserId, userId)
                .eq(Order::getStatus, 3); // 已完成订单
        List<Order> orders = orderMapper.selectList(orderWrapper);

        if (orders.isEmpty()) {
            profile.setConsumptionLevel("MEDIUM"); // 默认中等消费能力
            profile.setPriceSensitivity("MEDIUM");
            return;
        }

        // 2. 计算平均订单金额
        double totalAmount = orders.stream()
                .mapToDouble(o -> o.getTotalPrice() != null ? o.getTotalPrice().doubleValue() : 0)
                .sum();
        double avgAmount = totalAmount / orders.size();

        profile.setAvgOrderAmount(avgAmount);
        profile.setTotalPurchases(orders.size());

        // 3. 根据平均订单金额划分消费能力
        if (avgAmount >= 500) {
            profile.setConsumptionLevel("HIGH");
            profile.setPriceSensitivity("LOW");
        } else if (avgAmount >= 200) {
            profile.setConsumptionLevel("MEDIUM");
            profile.setPriceSensitivity("MEDIUM");
        } else {
            profile.setConsumptionLevel("LOW");
            profile.setPriceSensitivity("HIGH");
        }

        // 4. 计算品类偏好
        Map<Long, Long> categoryCount = new HashMap<>();
        for (Order order : orders) {
            if (order.getProductId() == null) {
                continue;
            }
            Product product = productMapper.selectById(order.getProductId());
            if (product != null && product.getCategoryId() != null) {
                categoryCount.merge(product.getCategoryId(), 1L, Long::sum);
            }
        }

        // 转换为偏好权重
        long totalCount = categoryCount.values().stream().mapToLong(Long::longValue).sum();
        List<CategoryPreferenceDTO> preferences = new ArrayList<>();
        for (Map.Entry<Long, Long> entry : categoryCount.entrySet()) {
            CategoryPreferenceDTO pref = new CategoryPreferenceDTO();
            pref.setCategoryId(entry.getKey());

            // 获取分类名称
            Category category = categoryMapper.selectById(entry.getKey());
            if (category != null) {
                pref.setCategoryName(category.getName());
            }

            pref.setWeight((double) entry.getValue() / totalCount);
            preferences.add(pref);
        }

        // 按权重排序
        preferences.sort(Comparator.comparing(CategoryPreferenceDTO::getWeight).reversed());
        profile.setCategoryPreferences(preferences);
    }

    /**
     * 计算用户偏好作物
     * <p>
     * 优先从用户注册信息获取感兴趣作物，再结合购买历史统计
     * 只考虑种子分类下的四级分类（具体作物/动物）
     * </p>
     */
    private void computeUserPreferredCrops(Long userId, UserProfileDTO profile, User user) {
        Set<Long> preferredCrops = new LinkedHashSet<>(); // 使用Set去重，保持顺序

        // 1. 优先从用户注册信息获取感兴趣作物
        if (user != null && user.getInterestedCrops() != null && !user.getInterestedCrops().isEmpty()) {
            String[] cropIds = user.getInterestedCrops().split(",");
            for (String cropId : cropIds) {
                try {
                    preferredCrops.add(Long.parseLong(cropId.trim()));
                } catch (NumberFormatException e) {
                    log.warn("[用户画像] 解析感兴趣作物ID失败: {}", cropId);
                }
            }
            log.debug("[用户画像] 从注册信息获取{}个感兴趣作物", preferredCrops.size());
        }

        // 2. 结合购买历史统计作物偏好
        LambdaQueryWrapper<Order> orderWrapper = new LambdaQueryWrapper<>();
        orderWrapper.eq(Order::getUserId, userId)
                .eq(Order::getStatus, 3); // 已完成订单
        List<Order> orders = orderMapper.selectList(orderWrapper);

        if (!orders.isEmpty()) {
            Map<Long, Integer> cropCount = new HashMap<>();

            for (Order order : orders) {
                if (order.getProductId() == null) {
                    continue;
                }

                // 查询商品关联的作物（农药/化肥/饲料等适用作物）
                LambdaQueryWrapper<org.example.springboot.entity.ProductCrop> wrapper =
                        new LambdaQueryWrapper<>();
                wrapper.eq(org.example.springboot.entity.ProductCrop::getProductId, order.getProductId());
                List<org.example.springboot.entity.ProductCrop> productCrops =
                        productCropMapper.selectList(wrapper);

                for (org.example.springboot.entity.ProductCrop pc : productCrops) {
                    if (pc.getCategoryId() != null) {
                        cropCount.merge(pc.getCategoryId(), 1, Integer::sum);
                    }
                }
            }

            // 按购买次数排序，补充到偏好列表（保留注册信息的优先级）
            cropCount.entrySet().stream()
                    .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                    .limit(5)
                    .map(Map.Entry::getKey)
                    .forEach(preferredCrops::add);
        }

        // 3. 设置结果（最多取前5个）
        List<Long> preferredCropIds = preferredCrops.stream()
                .limit(5)
                .collect(Collectors.toList());
        profile.setPreferredCropIds(preferredCropIds);

        // 4. 加载作物名称
        List<String> cropNames = new ArrayList<>();
        if (!preferredCropIds.isEmpty()) {
            LambdaQueryWrapper<Category> categoryWrapper = new LambdaQueryWrapper<>();
            categoryWrapper.in(Category::getId, preferredCropIds);
            List<Category> categories = categoryMapper.selectList(categoryWrapper);
            cropNames = categories.stream().map(Category::getName).collect(Collectors.toList());
        }
        profile.setPreferredCropNames(cropNames);
    }

    /**
     * 构建商品画像
     * <p>
     * 商品画像包含：
     * - 价格区间
     * - 所属品类
     * - 适用地区
     * - 适用季节
     * - 热销程度
     * </p>
     */
    private ProductProfileDTO buildProductProfile(Long productId) {
        ProductProfileDTO profile = new ProductProfileDTO();
        profile.setProductId(productId);

        // 1. 获取商品基本信息
        Product product = productMapper.selectById(productId);
        if (product == null) {
            log.warn("[商品画像] 商品{}不存在", productId);
            return profile;
        }

        profile.setProductName(product.getName());
        profile.setPrice(product.getPrice() != null ? product.getPrice().doubleValue() : 0.0);
        profile.setSalesCount(product.getSalesCount() != null ? product.getSalesCount() : 0);

        // 2. 设置分类信息
        profile.setCategoryId(product.getCategoryId());
        if (product.getCategoryId() != null) {
            Category category = categoryMapper.selectById(product.getCategoryId());
            if (category != null) {
                profile.setCategoryName(category.getName());
            }
        }

        // 3. 判断价格区间
        Double price = profile.getPrice();
        if (price >= 300) {
            profile.setPriceRange("HIGH");
        } else if (price >= 100) {
            profile.setPriceRange("MEDIUM");
        } else {
            profile.setPriceRange("LOW");
        }

        // 4. 判断是否热销
        profile.setIsHot(profile.getSalesCount() >= 100);

        // 5. 加载适用地区和季节
        loadProductRegionsAndSeasons(productId, profile);

        // 6. 加载适用作物
        loadProductCrops(productId, profile);

        return profile;
    }

    /**
     * 加载商品适用的地区和季节
     */
    private void loadProductRegionsAndSeasons(Long productId, ProductProfileDTO profile) {
        LambdaQueryWrapper<ProductRegionSeason> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductRegionSeason::getProductId, productId);
        List<ProductRegionSeason> associations = productRegionSeasonMapper.selectList(wrapper);

        if (associations.isEmpty()) {
            // 没有配置则默认适用所有地区
            profile.setRegionIds(new ArrayList<>());
            profile.setSeasonIds(new ArrayList<>());
            return;
        }

        List<Long> regionIds = new ArrayList<>();
        List<Long> seasonIds = new ArrayList<>();

        for (ProductRegionSeason assoc : associations) {
            if (assoc.getRegionId() != null && !regionIds.contains(assoc.getRegionId())) {
                regionIds.add(assoc.getRegionId());
            }
            if (assoc.getSeasonId() != null && !seasonIds.contains(assoc.getSeasonId())) {
                seasonIds.add(assoc.getSeasonId());
            }
        }

        profile.setRegionIds(regionIds);
        profile.setSeasonIds(seasonIds);

        // 加载地区名称
        List<String> regionNames = new ArrayList<>();
        if (!regionIds.isEmpty()) {
            LambdaQueryWrapper<Region> regionWrapper = new LambdaQueryWrapper<>();
            regionWrapper.in(Region::getId, regionIds);
            List<Region> regions = regionMapper.selectList(regionWrapper);
            regionNames = regions.stream().map(Region::getName).collect(Collectors.toList());
        }
        profile.setRegionNames(regionNames);

        // 加载季节名称
        List<String> seasonNames = new ArrayList<>();
        if (!seasonIds.isEmpty()) {
            LambdaQueryWrapper<Season> seasonWrapper = new LambdaQueryWrapper<>();
            seasonWrapper.in(Season::getId, seasonIds);
            List<Season> seasons = seasonMapper.selectList(seasonWrapper);
            seasonNames = seasons.stream().map(Season::getName).collect(Collectors.toList());
        }
        profile.setSeasonNames(seasonNames);
    }

    /**
     * 加载商品适用的作物
     */
    private void loadProductCrops(Long productId, ProductProfileDTO profile) {
        LambdaQueryWrapper<org.example.springboot.entity.ProductCrop> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(org.example.springboot.entity.ProductCrop::getProductId, productId);
        List<org.example.springboot.entity.ProductCrop> associations = productCropMapper.selectList(wrapper);

        if (associations.isEmpty()) {
            profile.setCropIds(new ArrayList<>());
            profile.setCropNames(new ArrayList<>());
            return;
        }

        List<Long> cropIds = associations.stream()
                .map(org.example.springboot.entity.ProductCrop::getCategoryId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        profile.setCropIds(cropIds);

        // 加载作物名称（从category表）
        List<String> cropNames = new ArrayList<>();
        if (!cropIds.isEmpty()) {
            LambdaQueryWrapper<Category> categoryWrapper = new LambdaQueryWrapper<>();
            categoryWrapper.in(Category::getId, cropIds);
            List<Category> categories = categoryMapper.selectList(categoryWrapper);
            cropNames = categories.stream().map(Category::getName).collect(Collectors.toList());
        }
        profile.setCropNames(cropNames);
    }

    /**
     * 计算推荐得分
     * <p>
     * 对每个未交互商品计算：
     * 1. 协同过滤得分（基于相似物品）
     * 2. 画像匹配得分（基于用户和商品画像）
     * 3. 融合最终得分
     * </p>
     */
    private List<RecommendedItem> computeRecommendationScores(Long userId,
                                                               UserProfileDTO userProfile,
                                                               Set<Long> interactedProducts) {
        List<RecommendedItem> items = new ArrayList<>();

        // 1. 获取用户交互过的商品及其强度
        Map<Long, Double> userInteractions = userInteractionMatrix.getOrDefault(userId, new HashMap<>());

        // 2. 获取所有候选商品
        LambdaQueryWrapper<Product> productWrapper = new LambdaQueryWrapper<>();
        productWrapper.eq(Product::getStatus, 1); // 只考虑上架商品
        List<Product> allProducts = productMapper.selectList(productWrapper);

        // 3. 收集所有 CF 预测所需的数据
        Map<Long, Double> cfScores = new HashMap<>();
        Map<Long, Double> cfNumerators = new HashMap<>();
        Map<Long, Double> cfDenominators = new HashMap<>();

        // 4. 对每个未交互商品计算 CF 得分
        for (Product product : allProducts) {
            if (interactedProducts.contains(product.getId())) {
                continue; // 跳过已交互商品
            }

            double cfScore = computeCFScore(product.getId(), userInteractions);
            cfScores.put(product.getId(), cfScore);
        }

        // 5. CF 得分归一化（Min-Max）
        Map<Long, Double> normalizedCfScores = normalizeScores(cfScores);

        // 6. 获取当前季节
        String currentSeason = getCurrentSeason();

        // 7. 对每个商品计算最终得分
        for (Product product : allProducts) {
            if (interactedProducts.contains(product.getId())) {
                continue;
            }

            // 获取商品画像
            ProductProfileDTO productProfile = getProductProfile(product.getId());

            // 计算 CF 得分
            Double cfScore = normalizedCfScores.getOrDefault(product.getId(), 0.0);

            // 计算画像匹配得分
            Double profileScore = computeProfileScore(userProfile, productProfile, currentSeason);

            // 线性融合
            double theta = recommendationConfig.getTheta();
            double finalScore = theta * cfScore + (1 - theta) * profileScore;

            items.add(new RecommendedItem(product.getId(), cfScore, profileScore, finalScore));
        }

        return items;
    }

    /**
     * 计算协同过滤预测得分
     * <p>
     * 公式：
     * CF(u,i) = Σ(sim(i,j) * r(u,j)) / Σ|sim(i,j)|
     * 其中 j 为用户已交互且与 i 相似的商品
     * </p>
     */
    private double computeCFScore(Long targetProductId, Map<Long, Double> userInteractions) {
        double numerator = 0.0;
        double denominator = 0.0;

        // 获取目标商品的相似商品
        Map<Long, Double> similarItems = itemSimilarityMatrix.get(targetProductId);
        if (similarItems == null || similarItems.isEmpty()) {
            return 0.0; // 没有相似商品
        }

        // 分子：Σ(sim(i,j) * r(u,j))
        // 分母：Σ|sim(i,j)|
        for (Map.Entry<Long, Double> entry : similarItems.entrySet()) {
            Long similarProductId = entry.getKey();
            Double similarity = entry.getValue();

            Double interactionStrength = userInteractions.get(similarProductId);
            if (interactionStrength != null) {
                numerator += similarity * interactionStrength;
                denominator += Math.abs(similarity);
            }
        }

        if (denominator == 0) {
            return 0.0;
        }

        return numerator / denominator;
    }

    /**
     * Min-Max 归一化
     */
    private Map<Long, Double> normalizeScores(Map<Long, Double> scores) {
        Map<Long, Double> normalized = new HashMap<>();

        if (scores.isEmpty()) {
            return normalized;
        }

        double minScore = Collections.min(scores.values());
        double maxScore = Collections.max(scores.values());
        double range = maxScore - minScore;

        if (range == 0) {
            // 所有得分相同，全部设为 0.5
            for (Long productId : scores.keySet()) {
                normalized.put(productId, 0.5);
            }
        } else {
            for (Map.Entry<Long, Double> entry : scores.entrySet()) {
                double normalizedScore = (entry.getValue() - minScore) / range;
                normalized.put(entry.getKey(), normalizedScore);
            }
        }

        return normalized;
    }

    /**
     * 计算画像匹配得分
     * <p>
     * 使用余弦相似度计算用户画像与商品画像的匹配度
     * 并应用业务规则约束（地区、季节等）
     * </p>
     */
    private double computeProfileScore(UserProfileDTO userProfile,
                                        ProductProfileDTO productProfile,
                                        String currentSeason) {
        // 1. 业务规则约束检查
        if (!checkBusinessRules(userProfile, productProfile, currentSeason)) {
            return 0.0; // 不满足约束，直接返回 0
        }

        // 2. 构建特征向量并计算余弦相似度
        return computeProfileCosineSimilarity(userProfile, productProfile);
    }

    /**
     * 检查业务规则约束
     */
    private boolean checkBusinessRules(UserProfileDTO userProfile,
                                        ProductProfileDTO productProfile,
                                        String currentSeason) {
        // 地区约束
        if (recommendationConfig.getEnableRegionConstraint()) {
            if (userProfile.getRegionId() != null && !productProfile.getRegionIds().isEmpty()) {
                // 用户有地区偏好，商品也有地区限制
                if (!productProfile.getRegionIds().contains(userProfile.getRegionId())) {
                    // 商品不适配用户所在地区
                    log.debug("[业务规则] 商品{}不适配用户地区{}",
                            productProfile.getProductId(), userProfile.getRegionName());
                    return false;
                }
            }
        }

        // 季节约束
        if (recommendationConfig.getEnableSeasonConstraint()) {
            if (!productProfile.getSeasonIds().isEmpty()) {
                // 商品有季节限制
                boolean seasonMatch = productProfile.getSeasonNames().stream()
                        .anyMatch(s -> s.contains(currentSeason));
                if (!seasonMatch) {
                    log.debug("[业务规则] 商品{}不适配当前季节{}",
                            productProfile.getProductId(), currentSeason);
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * 计算画像余弦相似度
     * <p>
     * 特征向量包括：
     * - 品类偏好匹配
     * - 价格区间匹配
     * - 消费能力匹配
     * - 适用作物匹配（农资电商特有）
     * </p>
     */
    private double computeProfileCosineSimilarity(UserProfileDTO userProfile,
                                                   ProductProfileDTO productProfile) {
        // 1. 品类偏好匹配
        double categoryScore = 0.0;
        if (userProfile.getCategoryPreferences() != null
                && !userProfile.getCategoryPreferences().isEmpty()) {
            for (CategoryPreferenceDTO pref : userProfile.getCategoryPreferences()) {
                if (pref.getCategoryId().equals(productProfile.getCategoryId())) {
                    categoryScore = pref.getWeight();
                    break;
                }
            }
        } else {
            categoryScore = 0.5;
        }

        // 2. 价格区间匹配
        double priceScore = 0.0;
        String consumptionLevel = userProfile.getConsumptionLevel();
        String priceRange = productProfile.getPriceRange();

        if (consumptionLevel.equals(priceRange)) {
            priceScore = 1.0;
        } else if (("HIGH".equals(consumptionLevel) && "MEDIUM".equals(priceRange))
                || ("MEDIUM".equals(consumptionLevel) && "LOW".equals(priceRange))) {
            priceScore = 0.7;
        } else if (("LOW".equals(consumptionLevel) && "MEDIUM".equals(priceRange))
                || ("MEDIUM".equals(consumptionLevel) && "HIGH".equals(priceRange))) {
            priceScore = 0.3;
        } else {
            priceScore = 0.1;
        }

        // 3. 适用作物匹配（农资电商特有）
        double cropScore = computeCropMatchScore(userProfile, productProfile);

        // 4. 加权平均：品类40% + 价格30% + 作物30%
        return 0.4 * categoryScore + 0.3 * priceScore + 0.3 * cropScore;
    }

    /**
     * 计算适用作物匹配得分
     * <p>
     * 基于用户偏好作物与商品适用作物的交集计算匹配度
     * 计算公式：|A ∩ B| / |A|，其中A为用户偏好作物集合，B为商品适用作物集合
     * </p>
     */
    private double computeCropMatchScore(UserProfileDTO userProfile, ProductProfileDTO productProfile) {
        List<Long> userCrops = userProfile.getPreferredCropIds();
        List<Long> productCrops = productProfile.getCropIds();

        // 用户无偏好或商品无适用作物，返回中性得分
        if (userCrops == null || userCrops.isEmpty()) {
            return 0.5;
        }
        if (productCrops == null || productCrops.isEmpty()) {
            return 0.5; // 通用商品，不给惩罚
        }

        // 计算交集
        long matchCount = userCrops.stream()
                .filter(productCrops::contains)
                .count();

        if (matchCount == 0) {
            return 0.1; // 完全不匹配，低分但不完全排除
        }

        // 匹配比例：交集 / 用户偏好作物数
        double matchRatio = (double) matchCount / userCrops.size();

        // 匹配得分：基础分0.3 + 匹配比例 * 0.7
        return 0.3 + matchRatio * 0.7;
    }

    /**
     * 获取当前季节
     */
    private String getCurrentSeason() {
        Month month = LocalDate.now().getMonth();
        return switch (month) {
            case MARCH, APRIL, MAY -> "春";
            case JUNE, JULY, AUGUST -> "夏";
            case SEPTEMBER, OCTOBER, NOVEMBER -> "秋";
            case DECEMBER, JANUARY, FEBRUARY -> "冬";
            default -> "春";
        };
    }

    /**
     * 转换为返回 DTO
     */
    private List<RecommendationResultDTO> convertToDTOs(List<RecommendedItem> items) {
        List<RecommendationResultDTO> results = new ArrayList<>();

        for (RecommendedItem item : items) {
            Product product = productMapper.selectById(item.getProductId());
            if (product == null) {
                continue;
            }

            RecommendationResultDTO dto = new RecommendationResultDTO();
            dto.setProductId(product.getId());
            dto.setProductName(product.getName());
            dto.setPrice(product.getPrice() != null ? product.getPrice().doubleValue() : 0.0);
            dto.setImageUrl(product.getImageUrl());
            dto.setCategoryId(product.getCategoryId());

            // 获取分类名称
            if (product.getCategoryId() != null) {
                Category category = categoryMapper.selectById(product.getCategoryId());
                if (category != null) {
                    dto.setCategoryName(category.getName());
                }
            }

            dto.setScore(item.getFinalScore());
            dto.setCfScore(item.getCfScore());
            dto.setProfileScore(item.getProfileScore());

            // 生成推荐原因
            dto.setReason(generateRecommendationReason(item, product));

            // 生成匹配标签
            dto.setMatchTags(generateMatchTags(item));

            results.add(dto);
        }

        return results;
    }

    /**
     * 生成推荐原因
     */
    private String generateRecommendationReason(RecommendedItem item, Product product) {
        StringBuilder reason = new StringBuilder();

        if (item.getCfScore() > 0.7) {
            reason.append("与您浏览/购买的商品相似");
        } else if (item.getProfileScore() > 0.7) {
            reason.append("符合您的偏好");
        } else if (item.getProfileScore() > 0.5) {
            reason.append("您可能感兴趣");
        } else {
            reason.append("热门推荐");
        }

        return reason.toString();
    }

    /**
     * 生成匹配标签
     */
    private List<String> generateMatchTags(RecommendedItem item) {
        List<String> tags = new ArrayList<>();

        if (item.getCfScore() > 0.5) {
            tags.add("相似商品");
        }
        if (item.getProfileScore() > 0.7) {
            tags.add("偏好匹配");
        }
        if (item.getProfileScore() > 0.5 && item.getProfileScore() <= 0.7) {
            tags.add("可能适合");
        }

        return tags;
    }

    /**
     * 记录曝光埋点
     */
    private void recordExposureImpressions(Long userId, List<RecommendationResultDTO> results) {
        if (results == null || results.isEmpty()) {
            return;
        }

        for (int i = 0; i < results.size(); i++) {
            RecommendationResultDTO dto = results.get(i);

            // 确定推荐来源
            String source;
            if (dto.getCfScore() > 0.5 && dto.getProfileScore() > 0.5) {
                source = org.example.springboot.enumClass.RecommendationActionType.SOURCE_HYBRID;
            } else if (dto.getCfScore() > 0.5) {
                source = org.example.springboot.enumClass.RecommendationActionType.SOURCE_COLLABORATIVE;
            } else if (dto.getProfileScore() > 0.5) {
                source = org.example.springboot.enumClass.RecommendationActionType.SOURCE_LOCATION;
            } else {
                source = org.example.springboot.enumClass.RecommendationActionType.SOURCE_HOT;
            }

            recommendActionService.recordExposure(
                    userId,
                    dto.getProductId(),
                    dto.getCategoryId(),
                    source,
                    org.example.springboot.enumClass.RecommendationActionType.SCENE_HOME_PAGE,
                    i + 1,
                    null
            );
        }
    }

    /**
     * 获取热销商品（降级策略）
     */
    private List<RecommendationResultDTO> getHotProducts(int limit) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getStatus, 1)
                .orderByDesc(Product::getSalesCount)
                .last("LIMIT " + limit);

        List<Product> products = productMapper.selectList(wrapper);
        List<RecommendationResultDTO> results = new ArrayList<>();

        for (Product product : products) {
            RecommendationResultDTO dto = new RecommendationResultDTO();
            dto.setProductId(product.getId());
            dto.setProductName(product.getName());
            dto.setPrice(product.getPrice() != null ? product.getPrice().doubleValue() : 0.0);
            dto.setImageUrl(product.getImageUrl());
            dto.setCategoryId(product.getCategoryId());
            dto.setScore(0.5); // 默认得分
            dto.setCfScore(0.0);
            dto.setProfileScore(0.5);
            dto.setReason("热销商品");
            dto.setMatchTags(Collections.singletonList("热销"));
            results.add(dto);
        }

        return results;
    }
}
