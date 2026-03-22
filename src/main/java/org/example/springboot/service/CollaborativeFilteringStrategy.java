package org.example.springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.springboot.config.RecommendationConfig;
import org.example.springboot.entity.*;
import org.example.springboot.entity.dto.RecommendationResultDTO;
import org.example.springboot.entity.dto.UserProfileDTO;
import org.example.springboot.mapper.*;
import org.springframework.stereotype.Component;

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

    // ==================== 缓存结构 ====================

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
            Map<Long, Double> normalizedScores = normalizeScores(cfScores);

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
     * 刷新交互矩阵
     */
    private void refreshInteractionMatrix() {
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
            addToInteraction(order.getUserId(), order.getProductId(),
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
            addToInteraction(favorite.getUserId(), favorite.getProductId(),
                    recommendationConfig.getFavoriteWeight());
        }

        // 加载购物车行为
        LambdaQueryWrapper<Cart> cartWrapper = new LambdaQueryWrapper<>();
        List<Cart> carts = cartMapper.selectList(cartWrapper);

        for (Cart cart : carts) {
            if (cart.getUserId() == null || cart.getProductId() == null) {
                continue;
            }
            addToInteraction(cart.getUserId(), cart.getProductId(),
                    recommendationConfig.getCartWeight());
        }

        // 加载浏览（点击）行为
        LambdaQueryWrapper<RecommendAction> clickWrapper = new LambdaQueryWrapper<>();
        clickWrapper.eq(RecommendAction::getActionType, "CLICK");
        List<RecommendAction> clicks = recommendActionMapper.selectList(clickWrapper);

        for (RecommendAction click : clicks) {
            if (click.getUserId() == null || click.getProductId() == null) {
                continue;
            }
            addToInteraction(click.getUserId(), click.getProductId(),
                    recommendationConfig.getClickWeight());
        }

        // 加载评分行为（评分值作为交互强度）
        LambdaQueryWrapper<Review> reviewWrapper = new LambdaQueryWrapper<>();
        reviewWrapper.isNotNull(Review::getRating).eq(Review::getStatus, 1);
        List<Review> reviews = reviewMapper.selectList(reviewWrapper);

        for (Review review : reviews) {
            if (review.getUserId() == null || review.getProductId() == null
                    || review.getRating() == null || review.getRating() <= 0) {
                continue;
            }
            addToInteraction(review.getUserId(), review.getProductId(),
                    review.getRating() * recommendationConfig.getReviewWeight());
        }
    }

    /**
     * 添加交互强度到矩阵
     *
     * @param userId    用户ID
     * @param productId 商品ID
     * @param weight    交互权重（支持小数，用于评分行为：rating × reviewWeight）
     */
    private void addToInteraction(Long userId, Long productId, double weight) {
        userInteractionMatrix.computeIfAbsent(userId, k -> new HashMap<>())
                .merge(productId, weight, Double::sum);
    }

    /**
     * 计算物品相似度矩阵
     */
    private void computeItemSimilarityIfNecessary() {
        if (!itemSimilarityMatrix.isEmpty()) {
            return;
        }

        itemSimilarityMatrix.clear();

        Set<Long> allProductIds = new HashSet<>();
        for (Map<Long, Double> userInteractions : userInteractionMatrix.values()) {
            allProductIds.addAll(userInteractions.keySet());
        }

        if (allProductIds.isEmpty()) {
            return;
        }

        List<Long> productIds = new ArrayList<>(allProductIds);

        // 计算商品模长
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

        // 计算余弦相似度，双向收集到临时 Map，再统一应用 TopK（保证对称矩阵两方向均受约束）
        Map<Long, Map<Long, Double>> rawSimilarities = new HashMap<>();

        for (int i = 0; i < productIds.size(); i++) {
            Long productId1 = productIds.get(i);

            for (int j = i + 1; j < productIds.size(); j++) {
                Long productId2 = productIds.get(j);
                double similarity = computeCosineSimilarity(productId1, productId2, productNorms);

                if (similarity >= recommendationConfig.getSimilarityThreshold()) {
                    rawSimilarities.computeIfAbsent(productId1, k -> new HashMap<>())
                            .put(productId2, similarity);
                    rawSimilarities.computeIfAbsent(productId2, k -> new HashMap<>())
                            .put(productId1, similarity);
                }
            }
        }

        // 统一对每个商品应用 TopK，保证两个方向均受约束
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
    }

    private double computeCosineSimilarity(Long productId1, Long productId2,
                                           Map<Long, Double> productNorms) {
        double numerator = 0.0;

        for (Map<Long, Double> interactions : userInteractionMatrix.values()) {
            Double strength1 = interactions.get(productId1);
            Double strength2 = interactions.get(productId2);
            if (strength1 != null && strength2 != null) {
                numerator += strength1 * strength2;
            }
        }

        double norm1 = productNorms.getOrDefault(productId1, 0.0);
        double norm2 = productNorms.getOrDefault(productId2, 0.0);

        if (norm1 == 0 || norm2 == 0) {
            return 0.0;
        }

        return numerator / (norm1 * norm2);
    }

    /**
     * 计算CF得分
     */
    private Map<Long, Double> computeCFScores(Long userId, Set<Long> interactedProducts) {
        Map<Long, Double> scores = new HashMap<>();
        Map<Long, Double> userInteractions = userInteractionMatrix.getOrDefault(userId, new HashMap<>());

        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getStatus, 1);
        List<Product> allProducts = productMapper.selectList(wrapper);

        for (Product product : allProducts) {
            if (interactedProducts.contains(product.getId())) {
                continue;
            }

            double score = computeCFScore(product.getId(), userInteractions);
            scores.put(product.getId(), score);
        }

        return scores;
    }

    private double computeCFScore(Long targetProductId, Map<Long, Double> userInteractions) {
        double numerator = 0.0;
        double denominator = 0.0;

        Map<Long, Double> similarItems = itemSimilarityMatrix.get(targetProductId);
        if (similarItems == null || similarItems.isEmpty()) {
            return 0.0;
        }

        for (Map.Entry<Long, Double> entry : similarItems.entrySet()) {
            Double similarity = entry.getValue();
            Double interactionStrength = userInteractions.get(entry.getKey());
            if (interactionStrength != null) {
                numerator += similarity * interactionStrength;
                denominator += Math.abs(similarity);
            }
        }

        return denominator > 0 ? numerator / denominator : 0.0;
    }

    /**
     * Min-Max归一化
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
