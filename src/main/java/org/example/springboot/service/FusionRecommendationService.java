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
 * @author IhaveBB
 * @date 2026/03/21
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
    private ProductAnimalMapper productAnimalMapper;

    @Resource
    private RecommendActionService recommendActionService;

    @Resource
    private RecommendActionMapper recommendActionMapper;

    @Resource
    private ReviewMapper reviewMapper;

    // ==================== 缓存结构 ====================

    /**
     * 交互矩阵重建锁，防止并发重建导致数据混乱
     */
    private final Object matrixLock = new Object();

    /**
     * 交互矩阵最后重建时间（毫秒时间戳）
     */
    private volatile long matrixLastRefreshTime = 0;

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

            if (!results.isEmpty()) {
                RecommendationResultDTO top1 = results.get(0);
                log.info("[融合推荐] 为用户{}生成{}条推荐，Top1：商品{}「{}」（CF={}, 画像={}, 综合={}）",
                        userId, results.size(), top1.getProductId(), top1.getProductName(),
                        String.format("%.3f", top1.getCfScore()),
                        String.format("%.3f", top1.getProfileScore()),
                        String.format("%.3f", top1.getScore()));
            } else {
                log.warn("[融合推荐] 为用户{}生成推荐结果为空，将触发降级", userId);
            }
            return results;

        } catch (Exception e) {
            log.error("[融合推荐] 为用户{}生成推荐失败：{}", userId, e.getMessage(), e);
            // 降级策略：返回热销商品
            return getHotProducts(recommendationConfig.getTopN());
        }
    }

    /**
     * 基于用户注册画像的纯画像推荐（新用户冷启动专用）
     * <p>
     * 适用场景：用户有注册信息（地域、关注作物）但无任何交互历史。
     * 跳过协同过滤环节（CF得分=0），仅通过画像匹配得分排序推荐商品：
     * 地域约束 + 季节约束 + 品类偏好 + 价格区间 + 适用作物。
     * 异常时降级为热销推荐。
     * </p>
     *
     * @param userId      用户ID
     * @param userProfile 用户画像（包含注册信息：地域、偏好作物）
     * @param limit       推荐数量
     * @return 推荐结果列表
     * @author IhaveBB
     * @date 2026/03/22
     */
    public List<RecommendationResultDTO> recommendByProfileOnly(Long userId,
                                                                 UserProfileDTO userProfile,
                                                                 int limit) {
        log.info("[画像冷启动] 用户{}有注册信息（地域: {}, 偏好作物: {}），开始纯画像推荐",
                userId, userProfile.getRegionName(),
                userProfile.getPreferredCropNames() != null
                        ? String.join(",", userProfile.getPreferredCropNames()) : "无");

        try {
            String currentSeason = getCurrentSeason();
            log.info("[画像冷启动] 当前季节: {}，开始遍历商品库计算画像匹配得分", currentSeason);

            // 获取所有上架商品
            LambdaQueryWrapper<Product> productWrapper = new LambdaQueryWrapper<>();
            productWrapper.eq(Product::getStatus, 1);
            List<Product> allProducts = productMapper.selectList(productWrapper);
            log.info("[画像冷启动] 候选商品总数: {}", allProducts.size());

            List<RecommendedItem> items = new ArrayList<>();
            int filteredCount = 0;

            for (Product product : allProducts) {
                ProductProfileDTO productProfile = getProductProfile(product.getId());
                double profileScore = computeProfileScore(userProfile, productProfile, currentSeason);

                if (profileScore <= 0) {
                    filteredCount++;
                    continue; // 不满足地域或季节约束的商品直接跳过
                }
                // CF 得分为 0（无交互历史），直接以画像得分作为最终排序依据
                items.add(new RecommendedItem(product.getId(), 0.0, profileScore, profileScore));
            }

            // 排序并截取 Top-N
            List<RecommendedItem> topN = items.stream()
                    .sorted(Comparator.comparing(RecommendedItem::getFinalScore).reversed())
                    .limit(limit)
                    .collect(Collectors.toList());

            List<RecommendationResultDTO> results = convertToDTOs(topN);

            // 记录曝光埋点
            recordExposureImpressions(userId, results);

            log.info("[画像冷启动] 为用户{}生成{}条画像推荐（画像匹配: {}/{}，约束过滤: {}个）",
                    userId, results.size(), items.size(), allProducts.size(), filteredCount);

            return results;

        } catch (Exception e) {
            log.error("[画像冷启动] 为用户{}生成画像推荐失败: {}，降级为热销推荐",
                    userId, e.getMessage(), e);
            return getHotProducts(limit);
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
     * 使用户画像缓存失效
     * <p>
     * 当用户更新个人信息（地域、感兴趣作物、感兴趣动物等）时调用，
     * 确保下次推荐时重新构建画像，而不是使用旧的缓存数据。
     * </p>
     *
     * @param userId 用户 ID
     * @author IhaveBB
     * @date 2026/03/29
     */
    public void invalidateUserProfile(Long userId) {
        if (userId != null) {
            userProfileCache.remove(userId);
            log.info("[画像缓存] 已清除用户 {} 的画像缓存", userId);
        }
    }

    /**
     * 使商品画像缓存失效
     * <p>
     * 当商品信息（适用作物、适用动物、区域-季节配置等）更新时调用，
     * 确保下次推荐时重新构建商品画像，而不是使用旧的缓存数据。
     * 同时清除相关用户的推荐结果缓存，避免返回基于旧画像的推荐。
     * </p>
     *
     * @param productId 商品 ID
     * @author IhaveBB
     * @date 2026/03/30
     */
    public void invalidateProductProfile(Long productId) {
        if (productId != null) {
            productProfileCache.remove(productId);
            // 清除所有用户的推荐结果缓存，因为商品画像变更会影响推荐排序
            recommendationCache.clear();
            log.info("[画像缓存] 已清除商品 {} 的画像缓存及所有推荐结果缓存", productId);
        }
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

    /**
     * 检查指定商品是否在当前用户的推荐列表中
     * <p>
     * 用于埋点归因：判断用户行为是来源于推荐列表还是自然浏览。
     * </p>
     *
     * @param userId    用户ID
     * @param productId 商品ID
     * @return true=该商品在用户的推荐列表中，false=不在推荐列表中
     * @author IhaveBB
     * @date 2026/03/22
     */
    public boolean isProductRecommended(Long userId, Long productId) {
        if (userId == null || productId == null) {
            return false;
        }
        List<RecommendedItem> recommendedItems = recommendationCache.get(userId);
        if (recommendedItems == null || recommendedItems.isEmpty()) {
            return false;
        }
        return recommendedItems.stream().anyMatch(item -> item.getProductId().equals(productId));
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
        synchronized (matrixLock) {
            long cacheExpireMs = recommendationConfig.getSimilarityCacheExpireSeconds() * 1000L;
            long now = System.currentTimeMillis();
            // 缓存未过期，跳过重建（避免并发请求重复查询数据库）
            if (!userInteractionMatrix.isEmpty() && (now - matrixLastRefreshTime) < cacheExpireMs) {
                return;
            }
            doRefreshInteractionMatrix();
            matrixLastRefreshTime = System.currentTimeMillis();
        }
    }

    /**
     * 实际执行交互矩阵重建
     */
    private void doRefreshInteractionMatrix() {
        log.info("[交互矩阵] 开始构建用户-商品交互矩阵（整合浏览/收藏/加购/购买/评分 5 类行为）...");

        userInteractionMatrix.clear();
        // 交互矩阵重建后相似度矩阵必须同步清空，确保重新计算
        itemSimilarityMatrix.clear();

        // 1. 加载购买行为（订单状态为已完成）
        LambdaQueryWrapper<Order> orderWrapper = new LambdaQueryWrapper<>();
        orderWrapper.eq(Order::getStatus, 3); // 已完成订单
        List<Order> orders = orderMapper.selectList(orderWrapper);
        int purchaseCount = 0;
        for (Order order : orders) {
            if (order.getUserId() == null || order.getProductId() == null) {
                continue;
            }
            addToInteraction(order.getUserId(), order.getProductId(),
                    recommendationConfig.getPurchaseWeight());
            purchaseCount++;
        }
        log.info("[交互矩阵] 购买行为: {}条（权重={}）", purchaseCount, recommendationConfig.getPurchaseWeight());

        // 2. 加载收藏行为
        LambdaQueryWrapper<Favorite> favoriteWrapper = new LambdaQueryWrapper<>();
        favoriteWrapper.eq(Favorite::getStatus, 1); // 有效收藏
        List<Favorite> favorites = favoriteMapper.selectList(favoriteWrapper);
        int favoriteCount = 0;
        for (Favorite favorite : favorites) {
            if (favorite.getUserId() == null || favorite.getProductId() == null) {
                continue;
            }
            addToInteraction(favorite.getUserId(), favorite.getProductId(),
                    recommendationConfig.getFavoriteWeight());
            favoriteCount++;
        }
        log.info("[交互矩阵] 收藏行为: {}条（权重={}）", favoriteCount, recommendationConfig.getFavoriteWeight());

        // 3. 加载购物车行为
        LambdaQueryWrapper<Cart> cartWrapper = new LambdaQueryWrapper<>();
        List<Cart> carts = cartMapper.selectList(cartWrapper);
        int cartCount = 0;
        for (Cart cart : carts) {
            if (cart.getUserId() == null || cart.getProductId() == null) {
                continue;
            }
            addToInteraction(cart.getUserId(), cart.getProductId(),
                    recommendationConfig.getCartWeight());
            cartCount++;
        }
        log.info("[交互矩阵] 加购行为: {}条（权重={}）", cartCount, recommendationConfig.getCartWeight());

        // 4. 加载浏览（点击）行为（来自推荐埋点记录）
        // 规则：①停留时长 < 5秒的不计入 ②同一用户同一商品1分钟内的重复点击去重 ③封顶权重
        LambdaQueryWrapper<RecommendAction> clickWrapper = new LambdaQueryWrapper<>();
        clickWrapper.eq(RecommendAction::getActionType, "CLICK")
                .orderByAsc(RecommendAction::getCreatedAt);
        List<RecommendAction> clicks = recommendActionMapper.selectList(clickWrapper);
        int clickCount = 0;
        int filteredByDuration = 0;
        int filteredByDedup = 0;
        // 用于去重：key = userId_productId, value = 上一次有效点击时间
        Map<String, java.time.LocalDateTime> lastClickTimeMap = new HashMap<>();
        // 用于封顶：key = userId_productId, value = 已累加的点击权重
        Map<String, Double> clickWeightMap = new HashMap<>();
        int clickWeightCap = recommendationConfig.getClickWeight() * 3; // 封顶 = 单次权重 × 3
        for (RecommendAction click : clicks) {
            if (click.getUserId() == null || click.getProductId() == null) {
                continue;
            }
            // ① 停留时长过滤：duration < 5秒视为无效浏览
            if (click.getDuration() != null && click.getDuration() < 5) {
                filteredByDuration++;
                continue;
            }
            // ② 1分钟内重复点击去重
            String dedupKey = click.getUserId() + "_" + click.getProductId();
            java.time.LocalDateTime lastTime = lastClickTimeMap.get(dedupKey);
            if (lastTime != null && click.getCreatedAt() != null
                    && java.time.Duration.between(lastTime, click.getCreatedAt()).toMinutes() < 1) {
                filteredByDedup++;
                continue;
            }
            if (click.getCreatedAt() != null) {
                lastClickTimeMap.put(dedupKey, click.getCreatedAt());
            }
            // ③ 封顶：同一用户同一商品的点击权重不超过上限
            double currentWeight = clickWeightMap.getOrDefault(dedupKey, 0.0);
            if (currentWeight >= clickWeightCap) {
                continue;
            }
            double addWeight = Math.min(recommendationConfig.getClickWeight(), clickWeightCap - currentWeight);
            clickWeightMap.merge(dedupKey, addWeight, Double::sum);
            addToInteraction(click.getUserId(), click.getProductId(), addWeight);
            clickCount++;
        }
        log.info("[交互矩阵] 浏览行为: {}条有效（权重={}，封顶={}），过滤：停留不足{}条，去重{}条",
                clickCount, recommendationConfig.getClickWeight(), clickWeightCap,
                filteredByDuration, filteredByDedup);

        // 5. 加载评分行为（评分作为双向信号：高分正向，低分负向）
        // 公式：reviewWeight = (rating - 3) × reviewWeightBase
        //   5星 → +2 × base, 4星 → +1 × base, 3星 → 0, 2星 → -1 × base, 1星 → -2 × base
        // 同时，若用户购买后打了低分（≤2星），降低该订单的购买权重
        LambdaQueryWrapper<Review> reviewWrapper = new LambdaQueryWrapper<>();
        reviewWrapper.isNotNull(Review::getRating).eq(Review::getStatus, 1);
        List<Review> reviews = reviewMapper.selectList(reviewWrapper);
        int reviewCount = 0;
        int lowRatingPenaltyCount = 0;
        for (Review review : reviews) {
            if (review.getUserId() == null || review.getProductId() == null
                    || review.getRating() == null || review.getRating() <= 0) {
                continue;
            }
            // 评分双向权重：(rating - 3) × reviewWeight
            double reviewInteractionWeight = (review.getRating() - 3) * recommendationConfig.getReviewWeight();
            addToInteraction(review.getUserId(), review.getProductId(), reviewInteractionWeight);
            // 购买后低分惩罚：如果该用户购买过此商品且评分 ≤ 2，回扣购买权重
            if (review.getRating() <= 2) {
                double penalty = -recommendationConfig.getPurchaseWeight() * 0.5;
                addToInteraction(review.getUserId(), review.getProductId(), penalty);
                lowRatingPenaltyCount++;
            }
            reviewCount++;
        }
        log.info("[交互矩阵] 评分行为: {}条（双向权重：(rating-3)×{}），低分惩罚(≤2星): {}条（回扣购买权重50%）",
                reviewCount, recommendationConfig.getReviewWeight(), lowRatingPenaltyCount);

        int totalActions = purchaseCount + favoriteCount + cartCount + clickCount + reviewCount;
        log.info("[交互矩阵] 构建完成：共{}个用户有交互数据，行为记录总计{}条",
                userInteractionMatrix.size(), totalActions);
    }

    /**
     * 添加交互强度到矩阵
     *
     * @param userId    用户ID
     * @param productId 商品ID
     * @param weight    交互权重（支持小数，用于评分行为：rating × reviewWeight）
     */
    private void addToInteraction(Long userId, Long productId, double weight) {
        Map<Long, Double> userMap = userInteractionMatrix.computeIfAbsent(userId, k -> new HashMap<>());
        userMap.merge(productId, weight, Double::sum);
        // 如果累加后权重 <= 0（如低分惩罚抵消了购买权重），移除该条目
        if (userMap.getOrDefault(productId, 0.0) <= 0) {
            userMap.remove(productId);
        }
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
     * <p>
     * 使用余弦相似度公式：
     * sim(i,j) = Σ(r(u,i) * r(u,j)) / (||r(·,i)|| × ||r(·,j)||)
     * <br/>
     * BUG FIX：先双向收集所有相似度到临时 Map，再统一对每个商品做 TopK，
     * 保证对称矩阵两个方向的相似商品列表均受 TopK 约束。
     * </p>
     */
    private void computeItemSimilarity() {
        log.info("[物品相似度] 开始计算物品相似度矩阵，当前交互用户数: {}", userInteractionMatrix.size());

        itemSimilarityMatrix.clear();

        // 1. 获取所有有交互记录的商品 ID
        Set<Long> allProductIds = new HashSet<>();
        for (Map<Long, Double> userInteractions : userInteractionMatrix.values()) {
            allProductIds.addAll(userInteractions.keySet());
        }

        if (allProductIds.isEmpty()) {
            log.warn("[物品相似度] 没有交互数据，无法计算相似度矩阵");
            return;
        }

        List<Long> productIds = new ArrayList<>(allProductIds);
        int totalProducts = productIds.size();
        long totalPairs = (long) totalProducts * (totalProducts - 1) / 2;
        log.info("[物品相似度] 参与计算商品数: {}，预计商品对数: {}", totalProducts, totalPairs);

        // 2. 预计算每个商品的模长（向量范数）
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

        // 3. 计算两两物品的余弦相似度，双向收集到临时 Map
        // 不在此处直接写最终矩阵，避免 TopK 对称方向不一致的问题
        Map<Long, Map<Long, Double>> rawSimilarities = new HashMap<>();
        int validPairCount = 0;

        for (int i = 0; i < productIds.size(); i++) {
            Long productId1 = productIds.get(i);

            for (int j = i + 1; j < productIds.size(); j++) {
                Long productId2 = productIds.get(j);

                double similarity = computeCosineSimilarity(productId1, productId2, productNorms);

                // 阈值过滤，低于阈值的相似度视为不相似
                if (similarity >= recommendationConfig.getSimilarityThreshold()) {
                    validPairCount++;
                    // 双向收集（两个方向均记录，等待后续统一 TopK）
                    rawSimilarities.computeIfAbsent(productId1, k -> new HashMap<>())
                            .put(productId2, similarity);
                    rawSimilarities.computeIfAbsent(productId2, k -> new HashMap<>())
                            .put(productId1, similarity);
                }
            }
        }

        // 4. 对每个商品统一应用 TopK，保证两个方向均受约束
        int topK = recommendationConfig.getTopK();
        for (Map.Entry<Long, Map<Long, Double>> entry : rawSimilarities.entrySet()) {
            Map<Long, Double> topKSimilarities = entry.getValue().entrySet().stream()
                    .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                    .limit(topK)
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (v1, v2) -> v1,
                            LinkedHashMap::new
                    ));
            itemSimilarityMatrix.put(entry.getKey(), topKSimilarities);
        }

        log.info("[物品相似度] 计算完成：有效相似对数={}（阈值>={}），有相似度商品数={}，TopK={}",
                validPairCount, recommendationConfig.getSimilarityThreshold(),
                itemSimilarityMatrix.size(), topK);
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

        // 5. 计算偏好动物（从注册信息）
        computeUserPreferredAnimals(profile, user);

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

        // 3. 根据平均订单金额划分消费能力（阈值可通过配置文件调整）
        if (avgAmount >= recommendationConfig.getHighConsumptionThreshold()) {
            profile.setConsumptionLevel("HIGH");
            profile.setPriceSensitivity("LOW");
        } else if (avgAmount >= recommendationConfig.getMediumConsumptionThreshold()) {
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
     * 计算用户偏好动物（从注册信息）
     *
     * @author IhaveBB
     * @date 2026/03/29
     */
    private void computeUserPreferredAnimals(UserProfileDTO profile, User user) {
        if (user == null || user.getInterestedAnimals() == null
                || user.getInterestedAnimals().isEmpty()) {
            profile.setPreferredAnimalIds(new ArrayList<>());
            profile.setPreferredAnimalNames(new ArrayList<>());
            return;
        }

        List<Long> animalIds = new ArrayList<>();
        String[] parts = user.getInterestedAnimals().split(",");
        for (String part : parts) {
            try {
                animalIds.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException e) {
                log.warn("[用户画像] 解析感兴趣动物ID失败: {}", part);
            }
        }
        profile.setPreferredAnimalIds(animalIds);

        // 加载动物名称
        List<String> animalNames = new ArrayList<>();
        if (!animalIds.isEmpty()) {
            LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<>();
            wrapper.in(Category::getId, animalIds);
            List<Category> categories = categoryMapper.selectList(wrapper);
            animalNames = categories.stream().map(Category::getName).collect(Collectors.toList());
        }
        profile.setPreferredAnimalNames(animalNames);
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

        // 3. 判断价格区间（阈值可通过配置文件调整）
        Double price = profile.getPrice();
        if (price >= recommendationConfig.getHighPriceThreshold()) {
            profile.setPriceRange("HIGH");
        } else if (price >= recommendationConfig.getMediumPriceThreshold()) {
            profile.setPriceRange("MEDIUM");
        } else {
            profile.setPriceRange("LOW");
        }

        // 4. 判断是否热销（阈值可通过配置文件调整）
        profile.setIsHot(profile.getSalesCount() >= recommendationConfig.getHotSalesThreshold());

        // 5. 判断是否为种子类商品，按类型加载画像维度
        boolean isSeed = isSeedProduct(product.getCategoryId());
        profile.setIsSeed(isSeed);

        // 记录一级分类 ID，用于打分时按类型分支
        Long topCategoryId = getTopLevelCategoryId(product.getCategoryId());
        profile.setTopCategoryId(topCategoryId);

        if (isSeed) {
            // 种子：加载适用地域+季节
            loadProductRegionsAndSeasons(productId, profile);
        } else if (topCategoryId != null && (topCategoryId == 4L || topCategoryId == 5L)) {
            // 饲料(ID=4)、兽药(ID=5)：加载适用动物
            loadProductAnimals(productId, profile);
        } else {
            // 非种子（农药、肥料等）：加载适用作物
            loadProductCrops(productId, profile);
        }

        return profile;
    }

    /**
     * 判断商品是否属于种子分类（一级分类ID=1为种子）
     * <p>
     * 商品可能挂在二级、三级或四级分类下，需要沿 parentId 向上追溯到一级分类
     * </p>
     */
    private boolean isSeedProduct(Long categoryId) {
        if (categoryId == null) {
            return false;
        }
        Long topCategoryId = getTopLevelCategoryId(categoryId);
        return topCategoryId != null && topCategoryId == 1L;
    }

    /**
     * 获取一级分类ID（沿 parentId 向上追溯）
     */
    private Long getTopLevelCategoryId(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        Long currentId = categoryId;
        int maxDepth = 10;
        while (maxDepth-- > 0) {
            Category category = categoryMapper.selectById(currentId);
            if (category == null) {
                return null;
            }
            if (category.getLevel() != null && category.getLevel() == 1) {
                return category.getId();
            }
            if (category.getParentId() == null || category.getParentId() == 0L) {
                return category.getId();
            }
            currentId = category.getParentId();
        }
        return null;
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
            profile.setRegionSeasonPairs(new ArrayList<>());
            return;
        }

        List<Long> regionIds = new ArrayList<>();
        List<Long> seasonIds = new ArrayList<>();
        // 保持区域-季节配对关系（核心：区域和季节是绑定的，不能拆开独立判断）
        List<ProductProfileDTO.RegionSeasonPair> pairs = new ArrayList<>();

        for (ProductRegionSeason assoc : associations) {
            if (assoc.getRegionId() != null && !regionIds.contains(assoc.getRegionId())) {
                regionIds.add(assoc.getRegionId());
            }
            if (assoc.getSeasonId() != null && !seasonIds.contains(assoc.getSeasonId())) {
                seasonIds.add(assoc.getSeasonId());
            }
            // 记录配对关系
            if (assoc.getRegionId() != null && assoc.getSeasonId() != null) {
                pairs.add(new ProductProfileDTO.RegionSeasonPair(assoc.getRegionId(), assoc.getSeasonId()));
            }
        }

        profile.setRegionIds(regionIds);
        profile.setSeasonIds(seasonIds);
        profile.setRegionSeasonPairs(pairs);

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
     * 加载商品适用的动物
     *
     * @author IhaveBB
     * @date 2026/03/29
     */
    private void loadProductAnimals(Long productId, ProductProfileDTO profile) {
        LambdaQueryWrapper<ProductAnimal> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductAnimal::getProductId, productId);
        List<ProductAnimal> associations = productAnimalMapper.selectList(wrapper);

        if (associations.isEmpty()) {
            profile.setAnimalIds(new ArrayList<>());
            profile.setAnimalNames(new ArrayList<>());
            return;
        }

        List<Long> animalIds = associations.stream()
                .map(ProductAnimal::getCategoryId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        profile.setAnimalIds(animalIds);

        // 加载动物名称（从category表畜禽子树）
        List<String> animalNames = new ArrayList<>();
        if (!animalIds.isEmpty()) {
            LambdaQueryWrapper<Category> categoryWrapper = new LambdaQueryWrapper<>();
            categoryWrapper.in(Category::getId, animalIds);
            List<Category> categories = categoryMapper.selectList(categoryWrapper);
            animalNames = categories.stream().map(Category::getName).collect(Collectors.toList());
        }
        profile.setAnimalNames(animalNames);
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

        // 3. 对每个未交互商品计算 CF 得分
        Map<Long, Double> cfScores = new HashMap<>();
        int skippedInteracted = 0;
        for (Product product : allProducts) {
            if (interactedProducts.contains(product.getId())) {
                skippedInteracted++;
                continue; // 跳过已交互商品（已购买/收藏等，无需重复推荐）
            }
            double cfScore = computeCFScore(product.getId(), userInteractions);
            cfScores.put(product.getId(), cfScore);
        }
        log.info("[推荐评分] 用户{}：候选商品{}个（已交互{}个已排除），开始计算CF得分",
                userId, allProducts.size(), skippedInteracted);

        // 4. CF 得分归一化（Min-Max）
        Map<Long, Double> normalizedCfScores = normalizeScores(cfScores);

        // 5. 获取当前季节
        String currentSeason = getCurrentSeason();
        log.info("[推荐评分] 当前季节：{}，季节/地域约束已启用：季节={}, 地域={}",
                currentSeason,
                recommendationConfig.getEnableSeasonConstraint(),
                recommendationConfig.getEnableRegionConstraint());

        // 6. 对每个商品计算最终得分（CF + 画像融合）
        double theta = recommendationConfig.getTheta();
        int filteredByProfile = 0;
        for (Product product : allProducts) {
            if (interactedProducts.contains(product.getId())) {
                continue;
            }

            ProductProfileDTO productProfile = getProductProfile(product.getId());
            Double cfScore = normalizedCfScores.getOrDefault(product.getId(), 0.0);
            Double profileScore = computeProfileScore(userProfile, productProfile, currentSeason);

            if (profileScore == 0.0) {
                filteredByProfile++;
            }

            // 线性融合：finalScore = θ × CF得分 + (1-θ) × 画像得分
            double finalScore = theta * cfScore + (1 - theta) * profileScore;
            items.add(new RecommendedItem(product.getId(), cfScore, profileScore, finalScore));
        }

        log.info("[推荐评分] 评分完成：有效得分商品{}个，画像约束过滤{}个（theta={}）",
                items.size(), filteredByProfile, theta);

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
            // 所有CF得分相同（通常全为0，冷启动场景），统一置0避免虚假信号
            for (Long productId : scores.keySet()) {
                normalized.put(productId, 0.0);
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
        // 直接计算画像匹配得分（不再使用硬约束过滤）
        return computeProfileMatchScore(userProfile, productProfile, currentSeason);
    }

    /**
     * 检查业务规则约束
     */
    /**
     * 计算画像匹配得分
     * <p>
     * 根据商品一级分类采用不同打分策略：
     * - 种子(ID=1)：0.6 × 区域匹配 + 0.4 × 季节匹配
     * - 农药(ID=2)、肥料(ID=3)：作物匹配（有交集=1.0，无交集=0.0）
     * - 饲料(ID=4)、兽药(ID=5)：动物匹配（有交集=1.0，无交集=0.0）
     * - 农膜(ID=6)、农机(ID=7)：中性分 0.5
     * 信息不全时各维度给 0.5（中性分）
     * </p>
     *
     * @author IhaveBB
     * @date 2026/03/29
     */
    private double computeProfileMatchScore(UserProfileDTO userProfile,
                                             ProductProfileDTO productProfile,
                                             String currentSeason) {
        Long topCategoryId = productProfile.getTopCategoryId();

        if (topCategoryId == null) {
            return 0.5; // 无法判断类型，中性分
        }

        switch (topCategoryId.intValue()) {
            case 1: // 种子
                return computeSeedRegionSeasonScore(userProfile, productProfile, currentSeason);
            case 2: // 农药
            case 3: // 肥料
                return computeCropMatchScore(userProfile, productProfile);
            case 4: // 饲料
            case 5: // 兽药
                return computeAnimalMatchScore(userProfile, productProfile);
            default: // 农膜(6)、农机(7)等
                return 0.5;
        }
    }

    // ==================== [已注释] 原始三维画像打分 ====================
    // 品类偏好和价格区间两个维度已按需求注释掉，只保留第三维度作为唯一打分依据。
    // 以下为原始代码，保留备查。

    /*
    private boolean checkBusinessRules(UserProfileDTO userProfile,
                                        ProductProfileDTO productProfile,
                                        String currentSeason) {
        // 地区约束
        if (recommendationConfig.getEnableRegionConstraint()) {
            if (userProfile.getRegionId() != null && !productProfile.getRegionIds().isEmpty()) {
                if (!productProfile.getRegionIds().contains(userProfile.getRegionId())) {
                    return false;
                }
            }
        }
        // 季节约束
        if (recommendationConfig.getEnableSeasonConstraint()) {
            if (!productProfile.getSeasonIds().isEmpty()) {
                boolean seasonMatch = productProfile.getSeasonNames().stream()
                        .anyMatch(s -> "全年".equals(s) || s.contains(currentSeason));
                if (!seasonMatch) {
                    return false;
                }
            }
        }
        return true;
    }

    private double computeProfileCosineSimilarity(UserProfileDTO userProfile,
                                                   ProductProfileDTO productProfile) {
        // [已注释] 品类偏好匹配维度
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

        // [已注释] 价格区间匹配维度
        double priceScore = 0.5;
        String consumptionLevel = userProfile.getConsumptionLevel();
        String priceRange = productProfile.getPriceRange();
        if (consumptionLevel == null || priceRange == null) {
            priceScore = 0.5;
        } else if (consumptionLevel.equals(priceRange)) {
            priceScore = 1.0;
        } else if (("HIGH".equals(consumptionLevel) && "MEDIUM".equals(priceRange))
                || ("MEDIUM".equals(consumptionLevel) && "LOW".equals(priceRange))) {
            priceScore = recommendationConfig.getPriceNearMatchScore();
        } else if (("LOW".equals(consumptionLevel) && "MEDIUM".equals(priceRange))
                || ("MEDIUM".equals(consumptionLevel) && "HIGH".equals(priceRange))) {
            priceScore = recommendationConfig.getPriceFarMatchScore();
        } else {
            priceScore = recommendationConfig.getPriceNoMatchScore();
        }

        // 第三维度
        double thirdDimensionScore;
        if (Boolean.TRUE.equals(productProfile.getIsSeed())) {
            thirdDimensionScore = computeRegionSeasonScore(userProfile, productProfile);
        } else {
            thirdDimensionScore = computeCropMatchScore(userProfile, productProfile);
        }

        return recommendationConfig.getCategoryWeight() * categoryScore
                + recommendationConfig.getPriceWeight() * priceScore
                + recommendationConfig.getCropWeight() * thirdDimensionScore;
    }
    */

    /**
     * 种子类商品：基于「区域-季节配对」的画像打分
     * <p>
     * 核心逻辑：区域和季节是绑定的（商家上架时配置 华北-春季, 华南-夏季 等配对），
     * 不能把区域和季节拆开独立判断。
     * </p>
     * <p>
     * 步骤：
     * 1. 在商品的所有 regionSeasonPairs 中，找到区域匹配用户所在区域的配对
     *    （用户大区 == 配对区域，或 配对区域 == 全国(8)）
     * 2. 在匹配到的配对中，检查季节：
     *    - 当前季节与配对季节一致 → 满分 1.0（正季购买）
     *    - 当前季节是配对季节的上一季节 → 0.6（提前购买，如冬季买春季种子）
     *    - 配对季节为全年(5) → 1.0
     * 3. 无匹配区域或无匹配季节 → 0.0
     * 4. 信息不全 → 0.5（中性分）
     * </p>
     *
     * @author IhaveBB
     * @date 2026/03/29
     */
    private double computeSeedRegionSeasonScore(UserProfileDTO userProfile,
                                                 ProductProfileDTO productProfile,
                                                 String currentSeason) {
        List<ProductProfileDTO.RegionSeasonPair> pairs = productProfile.getRegionSeasonPairs();
        Long userRegionId = userProfile.getRegionId();

        // --- 信息不全时返回中性分 ---
        if (pairs == null || pairs.isEmpty()) {
            return 0.5; // 商品无区域-季节配置，中性分
        }
        if (userRegionId == null) {
            return 0.5; // 用户无区域信息，中性分
        }

        // --- 第一步：在配对列表中筛选出区域匹配的配对 ---
        // 全国(regionId=8)视为任意区域都匹配
        List<ProductProfileDTO.RegionSeasonPair> matchedPairs = pairs.stream()
                .filter(p -> userRegionId.equals(p.getRegionId()) || Long.valueOf(8L).equals(p.getRegionId()))
                .collect(Collectors.toList());

        if (matchedPairs.isEmpty()) {
            // 商品不适用于用户所在区域
            return 0.0;
        }

        // --- 第二步：在区域匹配的配对中检查季节 ---
        Long currentSeasonId = getCurrentSeasonId();
        if (currentSeasonId == null) {
            return 0.5; // 无法确定当前季节，中性分
        }

        double bestScore = 0.0;
        for (ProductProfileDTO.RegionSeasonPair pair : matchedPairs) {
            Long pairSeasonId = pair.getSeasonId();

            // 全年(5) → 始终满分
            if (Long.valueOf(5L).equals(pairSeasonId)) {
                return 1.0;
            }

            // 当前季节与配对季节完全一致 → 满分
            if (currentSeasonId.equals(pairSeasonId)) {
                bestScore = Math.max(bestScore, 1.0);
                continue;
            }

            // 提前购买：当前季节是配对季节的上一季节
            // 例如：配对季节是春季(1)，当前是冬季(4) → 农民冬季提前购买春季种子
            Long seasonBefore = getSeasonBefore(pairSeasonId);
            if (currentSeasonId.equals(seasonBefore)) {
                bestScore = Math.max(bestScore, 0.6);
            }
        }

        return bestScore;
    }

    /**
     * 获取指定季节的上一个季节 ID
     * <p>
     * 季节顺序：春(1) → 夏(2) → 秋(3) → 冬(4) → 春(1)
     * getSeasonBefore(春) = 冬，表示如果产品适合春季，当前是冬季则用户在提前购买
     * </p>
     *
     * @param seasonId 季节 ID（1=春, 2=夏, 3=秋, 4=冬）
     * @return 上一个季节 ID，非标准季节返回 null
     * @author IhaveBB
     * @date 2026/03/29
     */
    private Long getSeasonBefore(Long seasonId) {
        if (seasonId == null) {
            return null;
        }
        switch (seasonId.intValue()) {
            case 1: return 4L; // 春的前一个季节是冬
            case 2: return 1L; // 夏的前一个季节是春
            case 3: return 2L; // 秋的前一个季节是夏
            case 4: return 3L; // 冬的前一个季节是秋
            default: return null;
        }
    }

    /**
     * 获取当前季节ID
     *
     * @author IhaveBB
     * @date 2026/03/29
     */
    private Long getCurrentSeasonId() {
        String seasonName = getCurrentSeason();
        if (seasonName == null) {
            return null;
        }
        LambdaQueryWrapper<Season> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Season::getName, seasonName);
        Season season = seasonMapper.selectOne(wrapper);
        return season != null ? season.getId() : null;
    }

    /**
     * 计算适用作物匹配得分（农药、肥料类商品）
     * <p>
     * 二元打分：有交集 → 1.0，无交集 → 0.0，信息不全 → 0.5
     * </p>
     *
     * @author IhaveBB
     * @date 2026/03/29
     */
    private double computeCropMatchScore(UserProfileDTO userProfile, ProductProfileDTO productProfile) {
        List<Long> userCrops = userProfile.getPreferredCropIds();
        List<Long> productCrops = productProfile.getCropIds();

        if (userCrops == null || userCrops.isEmpty()) {
            return 0.5; // 用户无作物偏好，中性分
        }
        if (productCrops == null || productCrops.isEmpty()) {
            return 0.5; // 商品无适用作物信息，中性分
        }

        // 有任何交集 → 1.0，无交集 → 0.0
        boolean hasMatch = userCrops.stream().anyMatch(productCrops::contains);
        return hasMatch ? 1.0 : 0.0;
    }

    /**
     * 计算适用动物匹配得分（饲料、兽药类商品）
     * <p>
     * 二元打分：有交集 → 1.0，无交集 → 0.0，信息不全 → 0.5
     * </p>
     *
     * @author IhaveBB
     * @date 2026/03/29
     */
    private double computeAnimalMatchScore(UserProfileDTO userProfile, ProductProfileDTO productProfile) {
        List<Long> userAnimals = userProfile.getPreferredAnimalIds();
        List<Long> productAnimals = productProfile.getAnimalIds();

        if (userAnimals == null || userAnimals.isEmpty()) {
            return 0.5; // 用户无动物偏好，中性分
        }
        if (productAnimals == null || productAnimals.isEmpty()) {
            return 0.5; // 商品无适用动物信息，中性分
        }

        // 有任何交集 → 1.0，无交集 → 0.0
        boolean hasMatch = userAnimals.stream().anyMatch(productAnimals::contains);
        return hasMatch ? 1.0 : 0.0;
    }

    /**
     * 获取当前季节名称
     * <p>
     * BUG FIX：统一返回"春季"格式，与数据库 Season 表存储格式及 NewProductRecommendationStrategy 保持一致
     * </p>
     *
     * @return 季节名称（春季/夏季/秋季/冬季）
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
            dto.setSalesCount(product.getSalesCount() != null ? product.getSalesCount() : 0);

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
        double highThreshold = recommendationConfig.getCfHighScoreThreshold();
        double midThreshold = recommendationConfig.getCfMediumScoreThreshold();

        if (item.getCfScore() > highThreshold) {
            return "与您浏览/购买的商品相似";
        } else if (item.getProfileScore() > highThreshold) {
            return "符合您的地域和作物偏好";
        } else if (item.getProfileScore() > midThreshold) {
            return "您可能感兴趣";
        } else {
            return "热门推荐";
        }
    }

    /**
     * 生成匹配标签
     */
    private List<String> generateMatchTags(RecommendedItem item) {
        double highThreshold = recommendationConfig.getCfHighScoreThreshold();
        double midThreshold = recommendationConfig.getCfMediumScoreThreshold();
        List<String> tags = new ArrayList<>();

        if (item.getCfScore() > midThreshold) {
            tags.add("相似商品");
        }
        if (item.getProfileScore() > highThreshold) {
            tags.add("偏好匹配");
        } else if (item.getProfileScore() > midThreshold) {
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
            dto.setSalesCount(product.getSalesCount() != null ? product.getSalesCount() : 0);
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
