package com.nicebao.springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nicebao.springboot.entity.*;
import com.nicebao.springboot.mapper.*;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import com.nicebao.springboot.config.RecommendationConfig;
import com.nicebao.springboot.entity.*;
import com.nicebao.springboot.entity.dto.RecommendationResultDTO;
import com.nicebao.springboot.entity.dto.UserProfileDTO;
import com.nicebao.springboot.mapper.*;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 纯协同过滤推荐策略
 * <p>
 * 作为对比算法使用，仅基于Item-CF，不包含画像约束
 * 用于论文实验中与融合推荐算法进行对比
 * </p>
 *
 * @author IhaveBB
 * @date 2026/03/21
 */
@Slf4j
@Component
public class CollaborativeFilteringStrategy implements RecommendationStrategy {

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
    private RecommendActionMapper recommendActionMapper;

    @Resource
    private ReviewMapper reviewMapper;

    @Resource
    private RecommendationAlgorithmSupport algorithmSupport;

    // ==================== 缓存结构 ====================

    /**
     * 交互矩阵重建锁
     */
    private final Object matrixLock = new Object();

    /**
     * 交互矩阵最后重建时间
     */
    private volatile long matrixLastRefreshTime = 0;

    /**
     * 用户 - 商品交互强度矩阵缓存
     */
    private final Map<Long, Map<Long, Double>> userInteractionMatrix = new ConcurrentHashMap<>();

    /**
     * 物品相似度矩阵缓存
     */
    private final Map<Long, Map<Long, Double>> itemSimilarityMatrix = new ConcurrentHashMap<>();

    @Override
    public String getStrategyName() {
        return "COLLABORATIVE_FILTERING";
    }

    @Override
    public boolean supportsColdStart() {
        return false;
    }

    @Override
    public double getPriorityScore(Long userId) {
        return 0.7;
    }

    /**
     * 执行纯协同过滤推荐
     *
     * @param userId      用户ID
     * @param userProfile 用户画像（不使用）
     * @param limit       推荐数量限制
     * @return 推荐结果列表
     */
    @Override
    public List<RecommendationResultDTO> recommend(Long userId, UserProfileDTO userProfile, int limit) {
        log.info("[纯CF推荐] 开始为用户{}生成纯协同过滤推荐", userId);

        try {
            // 1. 构建交互矩阵
            refreshInteractionMatrix();

            // 2. 计算物品相似度
            computeItemSimilarityIfNecessary();

            // 3. 获取用户已交互商品
            Set<Long> interactedProducts = userInteractionMatrix.getOrDefault(userId, new HashMap<>()).keySet();

            // 4. 计算候选商品的CF得分
            Map<Long, Double> cfScores = computeCFScores(userId, interactedProducts);

            // 5. 归一化得分
            Map<Long, Double> normalizedScores = algorithmSupport.normalizeScores(cfScores);

            // 6. 排序并截取Top-N
            List<Map.Entry<Long, Double>> topItems = normalizedScores.entrySet().stream()
                    .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                    .limit(limit)
                    .collect(Collectors.toList());

            // 7. 转换为DTO
            List<RecommendationResultDTO> results = convertToDTOs(topItems);

            log.info("[纯CF推荐] 为用户{}生成{}条推荐", userId, results.size());
            return results;

        } catch (Exception e) {
            log.error("[纯CF推荐] 为用户{}生成推荐失败：{}", userId, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 刷新交互矩阵（带并发保护）
     */
    private void refreshInteractionMatrix() {
        synchronized (matrixLock) {
            // 缓存未过期则跳过重建（默认1小时）
            long cacheExpireMs = recommendationConfig.getSimilarityCacheExpireSeconds() * 1000L;
            if (!userInteractionMatrix.isEmpty() && (System.currentTimeMillis() - matrixLastRefreshTime) < cacheExpireMs) {
                return;
            }
            doRefreshInteractionMatrix();
            matrixLastRefreshTime = System.currentTimeMillis();
        }
    }

    /**
     * 实际执行交互矩阵重建（已在 synchronized 块内）
     */
    private void doRefreshInteractionMatrix() {
        userInteractionMatrix.clear();
        // 交互矩阵重建后相似度矩阵必须同步清空，确保下一步重新计算
        itemSimilarityMatrix.clear();

        // 加载购买行为
        LambdaQueryWrapper<Order> orderWrapper = new LambdaQueryWrapper<>();
        orderWrapper.eq(Order::getStatus, 3);
        List<Order> orders = orderMapper.selectList(orderWrapper);

        for (Order order : orders) {
            if (order.getUserId() == null || order.getProductId() == null) {
                continue;
            }
            algorithmSupport.addInteraction(userInteractionMatrix, order.getUserId(), order.getProductId(),
                    recommendationConfig.getPurchaseWeight());
        }

        // 加载收藏行为
        LambdaQueryWrapper<Favorite> favoriteWrapper = new LambdaQueryWrapper<>();
        favoriteWrapper.eq(Favorite::getStatus, 1);
        List<Favorite> favorites = favoriteMapper.selectList(favoriteWrapper);

        for (Favorite favorite : favorites) {
            if (favorite.getUserId() == null || favorite.getProductId() == null) {
                continue;
            }
            algorithmSupport.addInteraction(userInteractionMatrix, favorite.getUserId(), favorite.getProductId(),
                    recommendationConfig.getFavoriteWeight());
        }

        // 加载购物车行为
        LambdaQueryWrapper<Cart> cartWrapper = new LambdaQueryWrapper<>();
        List<Cart> carts = cartMapper.selectList(cartWrapper);

        for (Cart cart : carts) {
            if (cart.getUserId() == null || cart.getProductId() == null) {
                continue;
            }
            algorithmSupport.addInteraction(userInteractionMatrix, cart.getUserId(), cart.getProductId(),
                    recommendationConfig.getCartWeight());
        }

        // 加载浏览（点击）行为（去重 + 停留时长过滤 + 封顶）
        LambdaQueryWrapper<RecommendAction> clickWrapper = new LambdaQueryWrapper<>();
        clickWrapper.eq(RecommendAction::getActionType, "CLICK")
                .orderByAsc(RecommendAction::getCreatedAt);
        List<RecommendAction> clicks = recommendActionMapper.selectList(clickWrapper);

        Map<String, LocalDateTime> lastClickTimeMap = new HashMap<>();
        Map<String, Double> clickWeightMap = new HashMap<>();
        int clickWeightCap = recommendationConfig.getClickWeight() * 3;

        for (RecommendAction click : clicks) {
            if (click.getUserId() == null || click.getProductId() == null) {
                continue;
            }
            // 停留时长过滤：duration < 5秒视为无效浏览
            if (!algorithmSupport.isEffectiveClick(click.getDuration())) {
                continue;
            }
            // 1分钟内重复点击去重
            String dedupKey = click.getUserId() + "_" + click.getProductId();
            LocalDateTime lastTime = lastClickTimeMap.get(dedupKey);
            if (algorithmSupport.isDuplicateClickWithinOneMinute(lastTime, click.getCreatedAt())) {
                continue;
            }
            if (click.getCreatedAt() != null) {
                lastClickTimeMap.put(dedupKey, click.getCreatedAt());
            }
            // 封顶
            double currentWeight = clickWeightMap.getOrDefault(dedupKey, 0.0);
            if (currentWeight >= clickWeightCap) {
                continue;
            }
            double addWeight = algorithmSupport.cappedClickWeight(
                    currentWeight, recommendationConfig.getClickWeight(), clickWeightCap);
            if (addWeight <= 0) {
                continue;
            }
            clickWeightMap.merge(dedupKey, addWeight, Double::sum);
            algorithmSupport.addInteraction(userInteractionMatrix, click.getUserId(), click.getProductId(), addWeight);
        }

        // 加载评分行为（双向信号：高分正向，低分负向）
        // 公式：reviewWeight = (rating - 3) × reviewWeightBase
        //   5星 → +2×base, 4星 → +1×base, 3星 → 0, 2星 → -1×base, 1星 → -2×base
        // 购买后低分(≤2星)额外惩罚：回扣购买权重50%
        LambdaQueryWrapper<Review> reviewWrapper = new LambdaQueryWrapper<>();
        reviewWrapper.isNotNull(Review::getRating).eq(Review::getStatus, 1);
        List<Review> reviews = reviewMapper.selectList(reviewWrapper);

        for (Review review : reviews) {
            if (review.getUserId() == null || review.getProductId() == null
                    || review.getRating() == null || review.getRating() <= 0) {
                continue;
            }
            double reviewInteractionWeight = algorithmSupport.reviewInteractionWeight(
                    review.getRating(), recommendationConfig.getReviewWeight());
            algorithmSupport.addInteraction(userInteractionMatrix, review.getUserId(), review.getProductId(),
                    reviewInteractionWeight);
            double penalty = algorithmSupport.lowRatingPurchasePenalty(
                    review.getRating(), recommendationConfig.getPurchaseWeight());
            if (penalty < 0) {
                algorithmSupport.addInteraction(userInteractionMatrix, review.getUserId(), review.getProductId(), penalty);
            }
        }
    }

    /**
     * 计算物品相似度矩阵
     */
    private void computeItemSimilarityIfNecessary() {
        if (!itemSimilarityMatrix.isEmpty()) {
            return;
        }

        itemSimilarityMatrix.clear();
        itemSimilarityMatrix.putAll(algorithmSupport.computeItemSimilarityMatrix(
                userInteractionMatrix,
                recommendationConfig.getSimilarityThreshold(),
                recommendationConfig.getTopK()));
    }

    /**
     * 计算CF得分
     */
    private Map<Long, Double> computeCFScores(Long userId, Set<Long> interactedProducts) {
        Map<Long, Double> scores = new HashMap<>();
        Map<Long, Double> userInteractions = userInteractionMatrix.getOrDefault(userId, new HashMap<>());

        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getStatus, 1)
                .gt(Product::getStock, 0);
        List<Product> allProducts = productMapper.selectList(wrapper);

        for (Product product : allProducts) {
            if (interactedProducts.contains(product.getId())) {
                continue;
            }

            double score = algorithmSupport.computeCfScore(product.getId(), userInteractions, itemSimilarityMatrix);
            scores.put(product.getId(), score);
        }

        return scores;
    }

    /**
     * 将 (productId, score) 列表批量转换为推荐结果DTO
     * <p>
     * 使用 selectBatchIds 批量查询商品和分类，避免 N+1 查询问题。
     * </p>
     *
     * @param items productId → 推荐分数的有序条目列表
     * @return 推荐结果DTO列表
     * @author IhaveBB
     * @date 2026/03/21
     */
    private List<RecommendationResultDTO> convertToDTOs(List<Map.Entry<Long, Double>> items) {
        if (items.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量查询商品（1次查询）
        List<Long> productIds = items.stream().map(Map.Entry::getKey).collect(Collectors.toList());
        List<Product> productList = productMapper.selectBatchIds(productIds);
        Map<Long, Product> productMap = productList.stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        // 批量查询分类（1次查询）
        Set<Long> categoryIds = productList.stream()
                .filter(p -> p.getCategoryId() != null)
                .map(Product::getCategoryId)
                .collect(Collectors.toSet());
        Map<Long, Category> categoryMap = categoryIds.isEmpty() ? Collections.emptyMap()
                : categoryMapper.selectBatchIds(new ArrayList<>(categoryIds)).stream()
                        .collect(Collectors.toMap(Category::getId, c -> c));

        List<RecommendationResultDTO> results = new ArrayList<>();
        for (Map.Entry<Long, Double> entry : items) {
            Product product = productMap.get(entry.getKey());
            if (product == null) {
                continue;
            }

            RecommendationResultDTO dto = new RecommendationResultDTO();
            dto.setProductId(product.getId());
            dto.setProductName(product.getName());
            dto.setPrice(product.getPrice() != null ? product.getPrice().doubleValue() : 0.0);
            dto.setStock(product.getStock() != null ? product.getStock() : 0);
            dto.setImageUrl(product.getImageUrl());
            dto.setCategoryId(product.getCategoryId());

            Category category = categoryMap.get(product.getCategoryId());
            if (category != null) {
                dto.setCategoryName(category.getName());
            }

            dto.setScore(entry.getValue());
            dto.setCfScore(entry.getValue());
            dto.setProfileScore(0.0);
            dto.setReason("协同过滤推荐");
            dto.setMatchTags(Collections.singletonList("相似商品"));

            results.add(dto);
        }

        return results;
    }
}
