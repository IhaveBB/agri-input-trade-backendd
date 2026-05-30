package com.nicebao.springboot.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 推荐算法公共计算组件。
 *
 * <p>该类只放无数据库副作用的“纯计算逻辑”，包括交互权重累加、点击过滤规则、
 * 评分惩罚、Item-CF 相似度、CF 预测分、归一化和融合得分。线上推荐服务和论文
 * 离线评估测试都调用这里的方法，避免同一公式在多处重复实现后出现口径不一致。</p>
 */
@Component
public class RecommendationAlgorithmSupport {

    /**
     * 将某个用户对某个商品的一条行为权重累加进用户-商品交互矩阵。
     *
     * <p>线上推荐和离线评估都使用同一规则：相同用户-商品的多种行为权重可以累加；
     * 如果低分评价等负向信号把累计权重抵消到 0 或以下，则移除该交互，避免负权重
     * 进入余弦相似度矩阵造成解释困难。</p>
     */
    public void addInteraction(Map<Long, Map<Long, Double>> matrix,
                               Long userId,
                               Long productId,
                               double weight) {
        if (matrix == null || userId == null || productId == null) {
            return;
        }
        // 交互矩阵按“用户 -> 商品 -> 交互强度”存储。
        // 同一用户对同一商品的多种行为会累加，例如收藏(2)+加购(3)=5。
        Map<Long, Double> userMap = matrix.computeIfAbsent(userId, ignored -> new HashMap<>());
        userMap.merge(productId, weight, Double::sum);
        if (userMap.getOrDefault(productId, 0.0) <= 0) {
            userMap.remove(productId);
        }
    }

    /**
     * 判断一次推荐点击是否为有效浏览。
     *
     * <p>duration 为空时保留该点击；duration 小于 5 秒时认为停留时间过短，不作为
     * 正反馈训练信号。该规则与线上交互矩阵构建逻辑保持一致。</p>
     */
    public boolean isEffectiveClick(Integer duration) {
        return duration == null || duration >= 5;
    }

    /**
     * 判断同一用户对同一商品的点击是否落在去重窗口内。
     */
    public boolean isDuplicateClickWithinOneMinute(LocalDateTime lastClickTime,
                                                   LocalDateTime currentClickTime) {
        return lastClickTime != null
                && currentClickTime != null
                && Duration.between(lastClickTime, currentClickTime).toMinutes() < 1;
    }

    /**
     * 计算点击行为在封顶规则下本次还能增加的权重。
     */
    public double cappedClickWeight(double currentWeight,
                                    int clickWeight,
                                    double clickWeightCap) {
        if (currentWeight >= clickWeightCap) {
            return 0.0;
        }
        return Math.min(clickWeight, clickWeightCap - currentWeight);
    }

    /**
     * 计算评价行为的双向反馈权重。
     *
     * <p>3 分为中性，4/5 分为正向反馈，1/2 分为负向反馈。公式为：
     * {@code (rating - 3) * reviewWeightBase}。</p>
     */
    public double reviewInteractionWeight(Integer rating, int reviewWeightBase) {
        if (rating == null || rating <= 0) {
            return 0.0;
        }
        return (rating - 3.0) * reviewWeightBase;
    }

    /**
     * 低分评价对已购买行为的额外惩罚。
     *
     * <p>用户购买后如果给出 2 分及以下评价，说明购买不代表满意，因此额外回扣
     * 50% 的购买权重。该规则和线上推荐矩阵构建逻辑一致。</p>
     */
    public double lowRatingPurchasePenalty(Integer rating, int purchaseWeight) {
        if (rating != null && rating <= 2) {
            return -purchaseWeight * 0.5;
        }
        return 0.0;
    }

    /**
     * 根据用户-商品交互矩阵计算 Item-CF 物品相似度矩阵。
     *
     * <p>相似度采用余弦相似度：
     * sim(i,j)=Σu r(u,i)r(u,j)/(||r(·,i)||×||r(·,j)||)。相似度先按阈值过滤，
     * 再对每个商品保留 TopK 个相似商品。该方法不访问数据库，因此线上推荐和离线
     * 时间截断评估可以复用同一套数学计算。</p>
     */
    public Map<Long, Map<Long, Double>> computeItemSimilarityMatrix(
            Map<Long, Map<Long, Double>> userInteractionMatrix,
            double similarityThreshold,
            int topK) {
        if (userInteractionMatrix == null || userInteractionMatrix.isEmpty()) {
            return Map.of();
        }

        Set<Long> allProductIds = userInteractionMatrix.values().stream()
                .flatMap(userInteractions -> userInteractions.keySet().stream())
                .collect(Collectors.toCollection(TreeSet::new));
        if (allProductIds.isEmpty()) {
            return Map.of();
        }

        // 水稻 = [用户A强度, 用户B强度, 用户C强度, ...]。
        Map<Long, Double> productNorms = computeProductNorms(userInteractionMatrix, allProductIds);
        ArrayList<Long> productIds = new ArrayList<>(allProductIds);
        Map<Long, Map<Long, Double>> rawSimilarities = new HashMap<>();

        for (int i = 0; i < productIds.size(); i++) {
            Long productId1 = productIds.get(i);
            for (int j = i + 1; j < productIds.size(); j++) {
                Long productId2 = productIds.get(j);
                // 对每一对商品计算余弦相似度：共同被同一批用户交互得越多，相似度越高。
                double similarity = computeCosineSimilarity(
                        productId1, productId2, productNorms, userInteractionMatrix);
                if (similarity >= similarityThreshold) {
                    rawSimilarities.computeIfAbsent(productId1, ignored -> new HashMap<>())
                            .put(productId2, similarity);
                    rawSimilarities.computeIfAbsent(productId2, ignored -> new HashMap<>())
                            .put(productId1, similarity);
                }
            }
        }

        Map<Long, Map<Long, Double>> topKSimilarities = new HashMap<>();
        for (Map.Entry<Long, Map<Long, Double>> entry : rawSimilarities.entrySet()) {
            // 每个商品只保留 TopK 个最相似商品，避免相似度矩阵过大，也减少弱相关噪声。
            Map<Long, Double> values = entry.getValue().entrySet().stream()
                    .sorted(Map.Entry.<Long, Double>comparingByValue().reversed()
                            .thenComparing(Map.Entry::getKey))
                    .limit(topK)
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (left, right) -> left,
                            LinkedHashMap::new));
            topKSimilarities.put(entry.getKey(), values);
        }
        return topKSimilarities;
    }

    private Map<Long, Double> computeProductNorms(Map<Long, Map<Long, Double>> userInteractionMatrix,
                                                  Set<Long> productIds) {
        Map<Long, Double> productNorms = new HashMap<>();
        for (Long productId : productIds) {
            double sumSquares = 0.0;
            for (Map<Long, Double> userInteractions : userInteractionMatrix.values()) {
                Double weight = userInteractions.get(productId);
                if (weight != null) {
                    sumSquares += weight * weight;
                }
            }
            // 范数用于余弦相似度分母，防止高热度商品仅因交互多就被认为相似。
            productNorms.put(productId, Math.sqrt(sumSquares));
        }
        return productNorms;
    }

    /**
     * 计算两个商品在所有用户行为上的余弦相似度。
     *
     * @param productId1 第一个商品ID，例如“牛肝菌”
     * @param productId2 第二个商品ID，例如“黑松露”
     * @param productNorms 每个商品向量的长度，key为商品ID，value为sqrt(所有用户对该商品交互强度平方和)
     * @param userInteractionMatrix 用户-商品交互矩阵，结构为：用户ID -> 商品ID -> 交互强度
     * @return 两个商品的相似度，范围通常为0~1；值越大，说明两个商品越常被同一批用户共同交互
     */
    private double computeCosineSimilarity(Long productId1,
                                           Long productId2,
                                           Map<Long, Double> productNorms,
                                           Map<Long, Map<Long, Double>> userInteractionMatrix) {
        double numerator = 0.0;
        // 遍历每个用户的交互记录，找出同时交互过 productId1 和 productId2 的用户。
        for (Map<Long, Double> userInteractions : userInteractionMatrix.values()) {
            // weight1 表示当前用户对第一个商品的交互强度，例如购买=5、加购=3。
            Double weight1 = userInteractions.get(productId1);
            // weight2 表示当前用户对第二个商品的交互强度。
            Double weight2 = userInteractions.get(productId2);
            if (weight1 != null && weight2 != null) {
                // 只有同一个用户同时交互过两个商品时，才会对这两个商品的相似度产生贡献。
                numerator += weight1 * weight2;
            }
        }

        // 分母为两个商品向量长度的乘积，用于归一化，避免热门商品只因交互多就相似度偏高。
        double denominator = productNorms.getOrDefault(productId1, 0.0)
                * productNorms.getOrDefault(productId2, 0.0);
        // 余弦相似度 = 点积 / (向量1长度 * 向量2长度)；分母为0说明缺少有效交互，返回0。
        return denominator > 0 ? numerator / denominator : 0.0;
    }

    /**
     * 根据目标商品与用户历史交互商品的相似度计算 CF 预测得分。
     */
    public double computeCfScore(Long targetProductId,
                                 Map<Long, Double> userInteractions,
                                 Map<Long, Map<Long, Double>> itemSimilarityMatrix) {
        if (targetProductId == null || userInteractions == null || userInteractions.isEmpty()) {
            return 0.0;
        }
        Map<Long, Double> similarItems = itemSimilarityMatrix.get(targetProductId);
        if (similarItems == null || similarItems.isEmpty()) {
            return 0.0;
        }

        double numerator = 0.0;
        double denominator = 0.0;
        for (Map.Entry<Long, Double> entry : similarItems.entrySet()) {
            Double interactionStrength = userInteractions.get(entry.getKey());
            if (interactionStrength != null) {
                // 候选商品与用户历史商品越相似，且用户对历史商品交互越强，CF 得分越高。
                numerator += entry.getValue() * interactionStrength;
                denominator += Math.abs(entry.getValue());
            }
        }
        return denominator > 0 ? numerator / denominator : 0.0;
    }

    /**
     * 对同一用户的候选商品得分做 Min-Max 归一化。
     *
     * <p>当所有候选商品得分相同时统一置 0，避免冷启动或无相似商品场景下产生虚假
     * 排序信号。</p>
     */
    public Map<Long, Double> normalizeScores(Map<Long, Double> scores) {
        if (scores == null || scores.isEmpty()) {
            return Map.of();
        }

        double minScore = scores.values().stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double maxScore = scores.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        double range = maxScore - minScore;

        Map<Long, Double> normalized = new HashMap<>();
        for (Map.Entry<Long, Double> entry : scores.entrySet()) {
            // 在当前用户的候选商品内部做 Min-Max 归一化，便于和 0~1 的画像得分融合。
            normalized.put(entry.getKey(), range > 0 ? (entry.getValue() - minScore) / range : 0.0);
        }
        return normalized;
    }

    /**
     * 线性融合协同过滤得分和画像匹配得分。
     */
    public double computeFusionScore(double theta,
                                     double normalizedCfScore,
                                     double profileScore) {
        // theta 默认 0.7：协同过滤负责发现潜在兴趣，画像得分负责做农资场景修正。
        return theta * normalizedCfScore + (1.0 - theta) * profileScore;
    }
}
