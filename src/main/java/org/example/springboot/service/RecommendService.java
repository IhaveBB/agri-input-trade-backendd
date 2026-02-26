package org.example.springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.springboot.common.Result;
import org.example.springboot.config.RecommendLocationConfig;
import org.example.springboot.entity.Product;
import org.example.springboot.entity.Order;
import org.example.springboot.entity.Favorite;
import org.example.springboot.entity.User;
import org.example.springboot.enumClass.RecommendConstants;
import org.example.springboot.enumClass.RecommendConstants.LocationLevel;
import org.example.springboot.enumClass.RecommendActionType;
import org.example.springboot.mapper.OrderMapper;
import org.example.springboot.mapper.FavoriteMapper;
import org.example.springboot.mapper.ProductMapper;
import org.example.springboot.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 推荐服务实现类
 * 使用基于用户的协同过滤算法，结合地理位置权重
 */
@Service
public class RecommendService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RecommendService.class);

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private FavoriteMapper favoriteMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RecommendLocationConfig locationConfig;

    @Autowired
    private RecommendActionService recommendActionService;

    /**
     * 推荐结果缓存（用于后续埋点归因）
     * Key: userId, Value: 推荐商品列表（包含位置信息）
     */
    private final Map<Long, List<RecommendedProduct>> recommendationCache = new HashMap<>();

    /**
     * 用户位置信息缓存
     */
    private final Map<Long, UserLocationInfo> userLocationCache = new HashMap<>();

    /**
     * 用户行为评分矩阵缓存
     */
    private Map<Long, Map<Long, Double>> userRatingMatrix;

    /**
     * 用户平均评分缓存
     */
    private Map<Long, Double> userMeanRatingCache;

    /**
     * 推荐商品信息封装类（用于埋点归因）
     */
    private static class RecommendedProduct {
        private final Long productId;
        private final Long categoryId;
        private final String source;
        private final Integer position;

        public RecommendedProduct(Long productId, Long categoryId, String source, Integer position) {
            this.productId = productId;
            this.categoryId = categoryId;
            this.source = source;
            this.position = position;
        }

        public Long getProductId() { return productId; }
        public Long getCategoryId() { return categoryId; }
        public String getSource() { return source; }
        public Integer getPosition() { return position; }
    }

    /**
     * 用户位置信息封装类
     */
    private static class UserLocationInfo {
        private final String province;
        private final String city;
        private final String region;

        UserLocationInfo(String location) {
            if (location == null || location.isEmpty()) {
                this.province = null;
                this.city = null;
                this.region = null;
                return;
            }
            // 格式：省-市 或 省-市-区
            String[] parts = location.split("-");
            this.province = parts.length >= 1 ? parts[0].trim() : null;
            this.city = parts.length >= 2 ? parts[1].trim() : null;
            this.region = this.province != null ? RecommendConstants.PROVINCE_REGION_MAP.get(this.province) : null;
        }

        public String getProvince() {
            return province;
        }

        public String getCity() {
            return city;
        }

        public String getRegion() {
            return region;
        }
    }

    /**
     * 初始化时加载缓存
     */
    @PostConstruct
    public void init() {
        try {
            refreshLocationCache();
            buildRatingMatrix();
        } catch (Exception e) {
            LOGGER.warn("[推荐服务] 初始化缓存失败，将使用延迟加载: {}", e.getMessage());
        }
    }

    /**
     * 刷新用户位置缓存
     */
    public void refreshLocationCache() {
        List<User> users = userMapper.selectList(null);
        userLocationCache.clear();
        for (User user : users) {
            if (user.getLocation() != null && !user.getLocation().isEmpty()) {
                userLocationCache.put(user.getId(), new UserLocationInfo(user.getLocation()));
            }
        }
        LOGGER.info("[推荐服务] 用户位置缓存已刷新，共 {} 条记录", userLocationCache.size());
    }

    /**
     * 构建用户-商品评分矩阵
     * 购买行为权重: 2.0, 收藏行为权重: 1.0
     */
    private void buildRatingMatrix() {
        userRatingMatrix = new HashMap<>(64);
        userMeanRatingCache = new HashMap<>(64);

        // 获取已完成订单
        LambdaQueryWrapper<Order> orderWrapper = new LambdaQueryWrapper<>();
        orderWrapper.eq(Order::getStatus, 3);
        List<Order> orders = orderMapper.selectList(orderWrapper);

        // 获取有效收藏
        LambdaQueryWrapper<Favorite> favoriteWrapper = new LambdaQueryWrapper<>();
        favoriteWrapper.eq(Favorite::getStatus, 1);
        List<Favorite> favorites = favoriteMapper.selectList(favoriteWrapper);

        // 构建评分矩阵：用户 -> 商品 -> 评分
        for (Order order : orders) {
            Map<Long, Double> userRatings = userRatingMatrix.computeIfAbsent(order.getUserId(), k -> new HashMap<>());
            // 购买行为权重为2.0
            userRatings.merge(order.getProductId(), RecommendConstants.BehaviorWeight.PURCHASE, Double::sum);
        }

        for (Favorite favorite : favorites) {
            Map<Long, Double> userRatings = userRatingMatrix.computeIfAbsent(favorite.getUserId(), k -> new HashMap<>());
            // 收藏行为权重为1.0
            userRatings.merge(favorite.getProductId(), RecommendConstants.BehaviorWeight.FAVORITE, Double::sum);
        }

        // 计算每个用户的平均评分
        for (Map.Entry<Long, Map<Long, Double>> entry : userRatingMatrix.entrySet()) {
            Long userId = entry.getKey();
            Map<Long, Double> ratings = entry.getValue();
            double meanRating = ratings.values().stream()
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0.0);
            userMeanRatingCache.put(userId, meanRating);
        }

        LOGGER.info("[推荐服务] 用户评分矩阵已构建，共 {} 个用户", userRatingMatrix.size());
    }

    /**
     * 获取用户位置信息
     */
    private UserLocationInfo getUserLocation(Long userId) {
        return userLocationCache.get(userId);
    }

    /**
     * 计算两个用户的位置权重
     *
     * @param userId1 用户1ID
     * @param userId2 用户2ID
     * @return 位置权重因子
     */
    private double calculateLocationWeight(Long userId1, Long userId2) {
        UserLocationInfo loc1 = getUserLocation(userId1);
        UserLocationInfo loc2 = getUserLocation(userId2);

        // 任一用户无位置信息，返回默认值
        if (loc1 == null || loc2 == null || loc1.getProvince() == null || loc2.getProvince() == null) {
            return locationConfig.getDefaultWeight();
        }

        LocationLevel level = getLocationMatchLevel(loc1, loc2);
        switch (level) {
            case SAME_CITY:
                LOGGER.debug("[推荐服务] 用户{}和{}同市，权重: {}", userId1, userId2, locationConfig.getSameCity());
                return locationConfig.getSameCity();
            case SAME_PROVINCE:
                LOGGER.debug("[推荐服务] 用户{}和{}同省，权重: {}", userId1, userId2, locationConfig.getSameProvince());
                return locationConfig.getSameProvince();
            case SAME_REGION:
                LOGGER.debug("[推荐服务] 用户{}和{}同区域，权重: {}", userId1, userId2, locationConfig.getSameRegion());
                return locationConfig.getSameRegion();
            default:
                return locationConfig.getDefaultWeight();
        }
    }

    /**
     * 获取位置匹配级别
     */
    private LocationLevel getLocationMatchLevel(UserLocationInfo loc1, UserLocationInfo loc2) {
        // 同市判断
        if (loc1.getCity() != null && loc2.getCity() != null
                && loc1.getCity().equals(loc2.getCity())) {
            return LocationLevel.SAME_CITY;
        }
        // 同省判断
        if (loc1.getProvince().equals(loc2.getProvince())) {
            return LocationLevel.SAME_PROVINCE;
        }
        // 同区域判断
        if (loc1.getRegion() != null && loc2.getRegion() != null
                && loc1.getRegion().equals(loc2.getRegion())) {
            return LocationLevel.SAME_REGION;
        }
        return LocationLevel.DIFFERENT_REGION;
    }

    /**
     * 计算用户相似度矩阵
     */
    private Map<Long, Map<Long, Double>> calculateUserSimilarity() {
        // 刷新缓存
        refreshLocationCache();
        buildRatingMatrix();

        Map<Long, Map<Long, Double>> similarityMatrix = new HashMap<>(64);
        List<Long> userIds = new ArrayList<>(userRatingMatrix.keySet());

        for (int i = 0; i < userIds.size(); i++) {
            Long user1 = userIds.get(i);
            Map<Long, Double> userSimilarities = new HashMap<>(16);
            similarityMatrix.put(user1, userSimilarities);

            for (int j = i + 1; j < userIds.size(); j++) {
                Long user2 = userIds.get(j);

                // 行为相似度（皮尔逊相关系数）
                double behaviorSimilarity = calculatePearsonCorrelation(user1, user2);

                // 皮尔逊相关系数可能为负，负相似度表示偏好相反，不参与推荐
                // 只有正相似度才乘以位置权重
                double locationWeight = calculateLocationWeight(user1, user2);
                double finalSimilarity = behaviorSimilarity > 0
                        ? behaviorSimilarity * locationWeight
                        : 0.0;

                userSimilarities.put(user2, finalSimilarity);
                similarityMatrix.computeIfAbsent(user2, k -> new HashMap<>(16))
                               .put(user1, finalSimilarity);
            }
        }

        return similarityMatrix;
    }

    /**
     * 计算皮尔逊相关系数
     *
     * 公式：r = Σ((xi - x̄)(yi - ȳ)) / (√Σ(xi - x̄)² × √Σ(yi - ȳ)²)
     *
     * 优点：消除用户评分偏好的影响
     * 例如：用户A给所有商品打4-5分，用户B给所有商品打1-2分
     *      余弦相似度可能很低，但皮尔逊相关系数很高（趋势一致）
     */
    private double calculatePearsonCorrelation(Long userId1, Long userId2) {
        Map<Long, Double> ratings1 = userRatingMatrix.get(userId1);
        Map<Long, Double> ratings2 = userRatingMatrix.get(userId2);

        if (ratings1 == null || ratings2 == null || ratings1.isEmpty() || ratings2.isEmpty()) {
            return 0.0;
        }

        // 获取用户平均评分
        Double mean1 = userMeanRatingCache.get(userId1);
        Double mean2 = userMeanRatingCache.get(userId2);

        if (mean1 == null || mean2 == null) {
            return 0.0;
        }

        // 找共同商品
        Set<Long> commonProducts = new HashSet<>(ratings1.keySet());
        commonProducts.retainAll(ratings2.keySet());

        int commonCount = commonProducts.size();
        // 共同商品太少，相似度不可靠
        if (commonCount < RecommendConstants.SimilarityThreshold.MIN_COMMON_ITEMS) {
            LOGGER.debug("[推荐服务] 用户{}和{}共同商品数{} < 最小阈值{}，返回0",
                    userId1, userId2, commonCount, RecommendConstants.SimilarityThreshold.MIN_COMMON_ITEMS);
            return 0.0;
        }

        // 计算皮尔逊公式的分子和分母
        double numerator = 0.0;  // Σ((xi - x̄)(yi - ȳ))
        double sumSq1 = 0.0;     // Σ(xi - x̄)²
        double sumSq2 = 0.0;     // Σ(yi - ȳ)²

        for (Long productId : commonProducts) {
            double diff1 = ratings1.get(productId) - mean1;
            double diff2 = ratings2.get(productId) - mean2;

            numerator += diff1 * diff2;
            sumSq1 += diff1 * diff1;
            sumSq2 += diff2 * diff2;
        }

        double denominator = Math.sqrt(sumSq1) * Math.sqrt(sumSq2);

        // 分母为0，说明该用户评分都相同，无意义
        if (denominator == 0) {
            return 0.0;
        }

        double correlation = numerator / denominator;

        LOGGER.debug("[推荐服务] 用户{}和{}的皮尔逊相关系数: {}, 共同商品: {}",
                userId1, userId2, correlation, commonCount);

        return correlation;
    }

    /**
     * 为指定用户生成推荐
     *
     * @param userId 用户ID
     * @return 推荐结果
     */
    public Result<?> generateRecommendations(Long userId) {
        try {
            // 刷新缓存
            refreshLocationCache();
            buildRatingMatrix();

            // 获取当前用户位置信息
            UserLocationInfo currentUserLoc = getUserLocation(userId);
            LOGGER.info("[推荐服务] 用户{}的位置信息: {}",
                    userId,
                    currentUserLoc != null ? currentUserLoc.getProvince() + "-" + currentUserLoc.getCity() : "无");

            // 获取用户已购买/收藏的商品及评分
            Map<Long, Double> userRatings = getUserRatings(userId);

            // 获取相似用户
            Map<Long, Double> similarUsers = findSimilarUsers(userId, userRatings);

            // 打印相似用户信息
            logSimilarUsers(similarUsers);

            // 收集推荐商品
            List<Product> recommendations = getRecommendedProducts(userId, userRatings, similarUsers);

            // 记录推荐曝光埋点
            recordRecommendationExposure(userId, recommendations);

            return Result.success(recommendations);
        } catch (Exception e) {
            LOGGER.error("[推荐服务] 生成推荐失败: {}", e.getMessage(), e);
            return Result.error("-1", "生成推荐失败: " + e.getMessage());
        }
    }

    /**
     * 获取用户评分
     */
    private Map<Long, Double> getUserRatings(Long userId) {
        Map<Long, Double> userRatings = userRatingMatrix.get(userId);
        if (userRatings == null) {
            userRatings = new HashMap<>();
            // 从数据库加载
            LambdaQueryWrapper<Order> orderWrapper = new LambdaQueryWrapper<>();
            orderWrapper.eq(Order::getUserId, userId).eq(Order::getStatus, 3);
            List<Order> userOrders = orderMapper.selectList(orderWrapper);

            LambdaQueryWrapper<Favorite> favoriteWrapper = new LambdaQueryWrapper<>();
            favoriteWrapper.eq(Favorite::getUserId, userId).eq(Favorite::getStatus, 1);
            List<Favorite> userFavorites = favoriteMapper.selectList(favoriteWrapper);

            for (Order order : userOrders) {
                userRatings.merge(order.getProductId(), RecommendConstants.BehaviorWeight.PURCHASE, Double::sum);
            }
            for (Favorite favorite : userFavorites) {
                userRatings.merge(favorite.getProductId(), RecommendConstants.BehaviorWeight.FAVORITE, Double::sum);
            }
        }

        if (userRatings.isEmpty()) {
            LOGGER.warn("[推荐服务] 用户{}没有任何订单或收藏记录", userId);
        }

        return userRatings;
    }

    /**
     * 查找相似用户
     */
    private Map<Long, Double> findSimilarUsers(Long userId, Map<Long, Double> userRatings) {
        // 获取相似度矩阵
        Map<Long, Map<Long, Double>> similarityMatrix = calculateUserSimilarity();

        // 合并相似度
        Map<Long, Double> similarUsers = new HashMap<>(16);
        if (similarityMatrix.containsKey(userId)) {
            similarUsers.putAll(similarityMatrix.get(userId));
        }
        for (Map.Entry<Long, Map<Long, Double>> entry : similarityMatrix.entrySet()) {
            if (entry.getValue().containsKey(userId)) {
                similarUsers.put(entry.getKey(), entry.getValue().get(userId));
            }
        }

        // 动态调整相似度阈值
        double threshold = getSimilarityThreshold(userRatings.size());

        // 过滤和排序
        return similarUsers.entrySet().stream()
                .filter(entry -> entry.getValue() >= threshold)
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(RecommendConstants.RecommendLimit.MAX_SIMILAR_USERS)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * 根据用户活跃度获取相似度阈值
     */
    private double getSimilarityThreshold(int ratingCount) {
        if (ratingCount < 3) {
            return RecommendConstants.SimilarityThreshold.NEW_USER;
        } else if (ratingCount > 10) {
            return RecommendConstants.SimilarityThreshold.ACTIVE_USER;
        }
        return RecommendConstants.SimilarityThreshold.NORMAL_USER;
    }

    /**
     * 记录相似用户日志
     */
    private void logSimilarUsers(Map<Long, Double> similarUsers) {
        for (Map.Entry<Long, Double> entry : similarUsers.entrySet()) {
            Long similarUserId = entry.getKey();
            double similarity = entry.getValue();
            UserLocationInfo loc = getUserLocation(similarUserId);
            LOGGER.info("[推荐服务] 相似用户: {}, 相似度: {}, 位置: {}",
                    similarUserId, similarity,
                    loc != null ? loc.getProvince() + "-" + loc.getCity() : "无");
        }
    }

    /**
     * 获取推荐商品列表
     */
    private List<Product> getRecommendedProducts(Long userId, Map<Long, Double> userRatings,
                                                Map<Long, Double> similarUsers) {
        // 收集推荐商品及其得分
        Map<Long, Double> productScores = calculateProductScores(userRatings, similarUsers, userId);

        if (productScores.isEmpty()) {
            LOGGER.info("[推荐服务] 没有找到相似用户，使用基于销量的推荐");
            return productMapper.selectList(
                    new LambdaQueryWrapper<Product>()
                            .orderByDesc(Product::getSalesCount)
                            .last("LIMIT " + RecommendConstants.RecommendLimit.MAX_RECOMMEND_COUNT)
            );
        }

        // 按得分排序
        List<Map.Entry<Long, Double>> sortedProducts = new ArrayList<>(productScores.entrySet());
        sortedProducts.sort((e1, e2) -> e2.getValue().compareTo(e1.getValue()));

        List<Long> recommendedIds = sortedProducts.stream()
                .limit(RecommendConstants.RecommendLimit.MAX_RECOMMEND_COUNT)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        return productMapper.selectList(
                new LambdaQueryWrapper<Product>()
                        .in(Product::getId, recommendedIds)
        );
    }

    /**
     * 计算商品推荐得分
     *
     * 使用皮尔逊相关系数后的预测评分公式：
     * 预测评分 = 用户平均分 + Σ(相似度 × (商品评分 - 用户平均分)) / Σ|相似度|
     *
     * 这样可以消除用户评分偏好的影响
     */
    private Map<Long, Double> calculateProductScores(Map<Long, Double> userRatings,
                                                     Map<Long, Double> similarUsers,
                                                     Long targetUserId) {
        Map<Long, Double> productScores = new HashMap<>(32);
        Map<Long, Double> weightSum = new HashMap<>(32);

        // 获取目标用户的平均评分
        double targetUserMean = userMeanRatingCache.getOrDefault(targetUserId, 0.0);

        for (Map.Entry<Long, Double> entry : similarUsers.entrySet()) {
            Long similarUserId = entry.getKey();
            double similarity = entry.getValue();

            // 相似度为0，跳过
            if (similarity <= 0) {
                continue;
            }

            Map<Long, Double> similarUserRatings = userRatingMatrix.get(similarUserId);
            if (similarUserRatings == null) {
                continue;
            }

            // 获取相似用户的平均评分
            double similarUserMean = userMeanRatingCache.getOrDefault(similarUserId, 0.0);

            // 遍历相似用户的所有评分商品
            for (Map.Entry<Long, Double> ratingEntry : similarUserRatings.entrySet()) {
                Long productId = ratingEntry.getKey();
                double rating = ratingEntry.getValue();

                // 排除用户已购买/收藏的商品
                if (userRatings.containsKey(productId)) {
                    continue;
                }

                // 使用中心化评分：(商品评分 - 用户平均分)
                double centeredRating = rating - similarUserMean;

                // 加权累加
                productScores.merge(productId, similarity * centeredRating, Double::sum);
                weightSum.merge(productId, similarity, Double::sum);
            }
        }

        // 归一化：预测评分 = 用户平均分 + 加权中心化评分总和 / 相似度之和
        for (Long productId : productScores.keySet()) {
            double totalWeight = weightSum.get(productId);
            if (totalWeight > 0) {
                double centeredScore = productScores.get(productId) / totalWeight;
                productScores.put(productId, targetUserMean + centeredScore);
            } else {
                productScores.put(productId, targetUserMean);
            }
        }

        return productScores;
    }

    /**
     * 记录推荐曝光埋点
     */
    private void recordRecommendationExposure(Long userId, List<Product> recommendations) {
        if (recommendations == null || recommendations.isEmpty()) {
            return;
        }

        // 缓存推荐结果，用于后续归因
        List<RecommendedProduct> cachedProducts = new ArrayList<>();

        // 根据是否有相似用户决定推荐来源
        String source = RecommendActionType.SOURCE_HOT; // 默认热门推荐

        // 检查是否存在协同过滤推荐结果
        // 这里简化处理：协同过滤有结果时标记为混合推荐
        if (userRatingMatrix.containsKey(userId) && !userRatingMatrix.get(userId).isEmpty()) {
            source = RecommendActionType.SOURCE_HYBRID;
        }

        for (int i = 0; i < recommendations.size(); i++) {
            Product product = recommendations.get(i);
            RecommendedProduct recommendedProduct = new RecommendedProduct(
                    product.getId(),
                    product.getCategoryId(),
                    source,
                    i + 1 // 位置从1开始
            );
            cachedProducts.add(recommendedProduct);

            // 记录曝光（异步）
            recommendActionService.recordExposure(
                    userId,
                    product.getId(),
                    product.getCategoryId(),
                    source,
                    RecommendActionType.SCENE_HOME_PAGE,
                    i + 1,
                    null
            );
        }

        // 缓存推荐结果，用于后续点击归因
        recommendationCache.put(userId, cachedProducts);
    }

    /**
     * 记录用户点击商品（用于推荐效果归因）
     * 在ProductController的详情接口中调用
     */
    public void recordProductClick(Long userId, Long productId, Long categoryId) {
        if (userId == null || productId == null) {
            return;
        }

        // 从缓存中查找推荐来源
        String source = RecommendActionType.SOURCE_NATURAL; // 默认自然流量
        Integer position = null;
        List<Long> contextProductIds = null;

        List<RecommendedProduct> cached = recommendationCache.get(userId);
        if (cached != null) {
            for (RecommendedProduct rp : cached) {
                if (rp.getProductId().equals(productId)) {
                    source = rp.getSource();
                    position = rp.getPosition();
                    break;
                }
            }
            // 获取同批次推荐的其他商品ID
            contextProductIds = cached.stream()
                    .map(RecommendedProduct::getProductId)
                    .collect(Collectors.toList());
        }

        // 记录点击
        recommendActionService.recordClick(
                userId, productId, categoryId,
                source, RecommendActionType.SCENE_HOME_PAGE,
                position, contextProductIds
        );
    }

    /**
     * 记录用户收藏商品（用于推荐效果归因）
     * 在FavoriteService中添加收藏时调用
     */
    public void recordProductCollect(Long userId, Long productId, Long categoryId) {
        if (userId == null || productId == null) {
            return;
        }

        String source = RecommendActionType.SOURCE_NATURAL;
        Integer position = null;
        List<Long> contextProductIds = null;

        List<RecommendedProduct> cached = recommendationCache.get(userId);
        if (cached != null) {
            for (RecommendedProduct rp : cached) {
                if (rp.getProductId().equals(productId)) {
                    source = rp.getSource();
                    position = rp.getPosition();
                    break;
                }
            }
            contextProductIds = cached.stream()
                    .map(RecommendedProduct::getProductId)
                    .collect(Collectors.toList());
        }

        recommendActionService.recordCollect(
                userId, productId, categoryId,
                source, RecommendActionType.SCENE_HOME_PAGE,
                position, contextProductIds
        );
    }

    /**
     * 记录用户加入购物车（用于推荐效果归因）
     * 在CartService中添加购物车时调用
     */
    public void recordProductCart(Long userId, Long productId, Long categoryId) {
        if (userId == null || productId == null) {
            return;
        }

        String source = RecommendActionType.SOURCE_NATURAL;
        Integer position = null;
        List<Long> contextProductIds = null;

        List<RecommendedProduct> cached = recommendationCache.get(userId);
        if (cached != null) {
            for (RecommendedProduct rp : cached) {
                if (rp.getProductId().equals(productId)) {
                    source = rp.getSource();
                    position = rp.getPosition();
                    break;
                }
            }
            contextProductIds = cached.stream()
                    .map(RecommendedProduct::getProductId)
                    .collect(Collectors.toList());
        }

        recommendActionService.recordCart(
                userId, productId, categoryId,
                source, RecommendActionType.SCENE_HOME_PAGE,
                position, contextProductIds
        );
    }

    /**
     * 记录用户购买（用于推荐效果归因）
     * 在OrderService创建订单时调用
     */
    public void recordProductBuy(Long userId, Long productId, Long categoryId) {
        if (userId == null || productId == null) {
            return;
        }

        String source = RecommendActionType.SOURCE_NATURAL;
        Integer position = null;
        List<Long> contextProductIds = null;

        List<RecommendedProduct> cached = recommendationCache.get(userId);
        if (cached != null) {
            for (RecommendedProduct rp : cached) {
                if (rp.getProductId().equals(productId)) {
                    source = rp.getSource();
                    position = rp.getPosition();
                    break;
                }
            }
            contextProductIds = cached.stream()
                    .map(RecommendedProduct::getProductId)
                    .collect(Collectors.toList());
        }

        recommendActionService.recordBuy(
                userId, productId, categoryId,
                source, RecommendActionType.SCENE_HOME_PAGE,
                position, contextProductIds
        );
    }

    /**
     * 定时更新所有用户推荐
     */
    public void updateRecommendations() {
        try {
            refreshLocationCache();
            buildRatingMatrix();

            List<Long> userIds = new ArrayList<>(userRatingMatrix.keySet());

            for (Long userId : userIds) {
                generateRecommendations(userId);
            }

            LOGGER.info("[推荐服务] 成功更新所有用户推荐");
        } catch (Exception e) {
            LOGGER.error("[推荐服务] 更新推荐失败: {}", e.getMessage(), e);
        }
    }
}
