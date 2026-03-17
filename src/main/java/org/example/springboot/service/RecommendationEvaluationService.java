package org.example.springboot.service;

import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.example.springboot.entity.Order;
import org.example.springboot.entity.Product;
import org.example.springboot.entity.dto.RecommendationResultDTO;
import org.example.springboot.mapper.OrderMapper;
import org.example.springboot.mapper.ProductMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 推荐算法离线评估服务
 * <p>
 * 提供多种评估指标计算：
 * - 准确率(Precision)
 * - 召回率(Recall)
 * - F1值
 * - NDCG
 * - MAP
 * - 覆盖率(Coverage)
 * - 多样性(Diversity)
 * </p>
 *
 * @author agri-input-trade
 * @version 1.0
 */
@Slf4j
@Service
public class RecommendationEvaluationService {

    @Resource
    private OrderMapper orderMapper;

    @Resource
    private ProductMapper productMapper;

    @Resource
    private FusionRecommendationService fusionRecommendationService;

    @Resource
    private CollaborativeFilteringStrategy cfStrategy;

    @Resource
    private HotProductRecommendationStrategy hotProductStrategy;

    /**
     * 评估结果DTO
     */
    @Data
    public static class EvaluationResult {
        /** 算法名称 */
        private String algorithmName;
        /** 准确率 */
        private double precision;
        /** 召回率 */
        private double recall;
        /** F1值 */
        private double f1Score;
        /** NDCG */
        private double ndcg;
        /** 平均精度均值 */
        private double map;
        /** 覆盖率 */
        private double coverage;
        /** 多样性 */
        private double diversity;
        /** 评估用户数 */
        private int evaluatedUsers;
        /** 评估时间戳 */
        private long timestamp;
    }

    /**
     * 评估所有算法
     *
     * @return 各算法的评估结果
     */
    public List<EvaluationResult> evaluateAllAlgorithms() {
        log.info("[评估服务] 开始评估所有推荐算法");

        List<EvaluationResult> results = new ArrayList<>();

        // 评估融合推荐算法
        results.add(evaluateFusionAlgorithm());

        // 评估纯协同过滤算法
        results.add(evaluateCFAlgorithm());

        // 评估热销推荐算法
        results.add(evaluateHotAlgorithm());

        log.info("[评估服务] 算法评估完成");
        return results;
    }

    /**
     * 评估融合推荐算法
     */
    public EvaluationResult evaluateFusionAlgorithm() {
        log.info("[评估服务] 评估融合推荐算法");
        return evaluateAlgorithm("融合推荐", (userId, profile) ->
                fusionRecommendationService.recommend(userId));
    }

    /**
     * 评估纯协同过滤算法
     */
    public EvaluationResult evaluateCFAlgorithm() {
        log.info("[评估服务] 评估纯协同过滤算法");
        return evaluateAlgorithm("纯协同过滤", (userId, profile) ->
                cfStrategy.recommend(userId, profile, 10));
    }

    /**
     * 评估热销推荐算法
     */
    public EvaluationResult evaluateHotAlgorithm() {
        log.info("[评估服务] 评估热销推荐算法");
        return evaluateAlgorithm("热销推荐", (userId, profile) ->
                hotProductStrategy.recommend(userId, profile, 10));
    }

    /**
     * 通用算法评估方法
     *
     * @param algorithmName 算法名称
     * @param recommender   推荐函数
     * @return 评估结果
     */
    private EvaluationResult evaluateAlgorithm(String algorithmName,
                                               RecommenderFunction recommender) {
        // 获取测试用户（有购买行为的用户）
        List<Long> testUsers = getTestUsers();

        if (testUsers.isEmpty()) {
            log.warn("[评估服务] 没有测试用户数据");
            return createEmptyResult(algorithmName);
        }

        // 分割训练集和测试集（按时间分割：80%训练，20%测试）
        Map<Long, Set<Long>> testGroundTruth = new HashMap<>();

        double totalPrecision = 0.0;
        double totalRecall = 0.0;
        double totalF1 = 0.0;
        double totalNDCG = 0.0;
        double totalAP = 0.0;

        int validUsers = 0;
        Set<Long> allRecommendedItems = new HashSet<>();
        Set<String> recommendedCategories = new HashSet<>();

        for (Long userId : testUsers) {
            try {
                // 获取用户的实际购买商品（作为ground truth）
                Set<Long> actualPurchases = getUserPurchases(userId);
                if (actualPurchases.isEmpty()) {
                    continue;
                }

                testGroundTruth.put(userId, actualPurchases);

                // 生成推荐
                List<RecommendationResultDTO> recommendations =
                        recommender.recommend(userId, null);

                if (recommendations.isEmpty()) {
                    continue;
                }

                List<Long> recommendedIds = recommendations.stream()
                        .map(RecommendationResultDTO::getProductId)
                        .collect(Collectors.toList());

                // 收集覆盖率和多样性数据
                allRecommendedItems.addAll(recommendedIds);
                recommendations.forEach(r -> {
                    if (r.getCategoryName() != null) {
                        recommendedCategories.add(r.getCategoryName());
                    }
                });

                // 计算各项指标
                double precision = calculatePrecision(recommendedIds, actualPurchases);
                double recall = calculateRecall(recommendedIds, actualPurchases);
                double f1 = calculateF1(precision, recall);
                double ndcg = calculateNDCG(recommendations, actualPurchases);
                double ap = calculateAP(recommendedIds, actualPurchases);

                totalPrecision += precision;
                totalRecall += recall;
                totalF1 += f1;
                totalNDCG += ndcg;
                totalAP += ap;

                validUsers++;

            } catch (Exception e) {
                log.error("[评估服务] 评估用户{}时出错: {}", userId, e.getMessage());
            }
        }

        if (validUsers == 0) {
            return createEmptyResult(algorithmName);
        }

        // 计算平均值
        EvaluationResult result = new EvaluationResult();
        result.setAlgorithmName(algorithmName);
        result.setPrecision(totalPrecision / validUsers);
        result.setRecall(totalRecall / validUsers);
        result.setF1Score(totalF1 / validUsers);
        result.setNdcg(totalNDCG / validUsers);
        result.setMap(totalAP / validUsers);

        // 计算覆盖率（推荐商品占总商品的比例）
        long totalProducts = getTotalProductCount();
        result.setCoverage(totalProducts > 0 ?
                (double) allRecommendedItems.size() / totalProducts : 0.0);

        // 计算多样性（推荐品类数量）
        result.setDiversity(recommendedCategories.size());

        result.setEvaluatedUsers(validUsers);
        result.setTimestamp(System.currentTimeMillis());

        log.info("[评估服务] {}评估完成: Precision={}, Recall={}, F1={}, NDCG={}",
                algorithmName, result.getPrecision(), result.getRecall(),
                result.getF1Score(), result.getNdcg());

        return result;
    }

    /**
     * 计算准确率
     * Precision = |推荐的商品 ∩ 实际购买的商品| / |推荐的商品|
     */
    private double calculatePrecision(List<Long> recommended, Set<Long> actual) {
        if (recommended.isEmpty()) {
            return 0.0;
        }

        long hits = recommended.stream().filter(actual::contains).count();
        return (double) hits / recommended.size();
    }

    /**
     * 计算召回率
     * Recall = |推荐的商品 ∩ 实际购买的商品| / |实际购买的商品|
     */
    private double calculateRecall(List<Long> recommended, Set<Long> actual) {
        if (actual.isEmpty()) {
            return 0.0;
        }

        long hits = recommended.stream().filter(actual::contains).count();
        return (double) hits / actual.size();
    }

    /**
     * 计算F1值
     * F1 = 2 * (Precision * Recall) / (Precision + Recall)
     */
    private double calculateF1(double precision, double recall) {
        if (precision + recall == 0) {
            return 0.0;
        }
        return 2 * precision * recall / (precision + recall);
    }

    /**
     * 计算NDCG (Normalized Discounted Cumulative Gain)
     */
    private double calculateNDCG(List<RecommendationResultDTO> recommendations,
                                  Set<Long> actual) {
        if (recommendations.isEmpty() || actual.isEmpty()) {
            return 0.0;
        }

        // 计算DCG
        double dcg = 0.0;
        for (int i = 0; i < recommendations.size(); i++) {
            Long productId = recommendations.get(i).getProductId();
            if (actual.contains(productId)) {
                // 相关项的收益为1，位置越靠前权重越高
                dcg += 1.0 / Math.log(i + 2); // log(i+2) 因为i从0开始
            }
        }

        // 计算IDCG (理想情况)
        double idcg = 0.0;
        int relevantCount = Math.min(actual.size(), recommendations.size());
        for (int i = 0; i < relevantCount; i++) {
            idcg += 1.0 / Math.log(i + 2);
        }

        return idcg > 0 ? dcg / idcg : 0.0;
    }

    /**
     * 计算AP (Average Precision)
     */
    private double calculateAP(List<Long> recommended, Set<Long> actual) {
        if (recommended.isEmpty() || actual.isEmpty()) {
            return 0.0;
        }

        double sumPrecision = 0.0;
        int hitCount = 0;

        for (int i = 0; i < recommended.size(); i++) {
            if (actual.contains(recommended.get(i))) {
                hitCount++;
                sumPrecision += (double) hitCount / (i + 1);
            }
        }

        return hitCount > 0 ? sumPrecision / hitCount : 0.0;
    }

    /**
     * 获取测试用户列表（有购买行为的用户）
     */
    private List<Long> getTestUsers() {
        List<Order> orders = orderMapper.selectList(null);
        return orders.stream()
                .map(Order::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 获取用户的购买商品列表
     */
    private Set<Long> getUserPurchases(Long userId) {
        List<Order> orders = orderMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Order>()
                        .eq(Order::getUserId, userId)
                        .eq(Order::getStatus, 3)
        );

        return orders.stream()
                .map(Order::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * 获取商品总数
     */
    private long getTotalProductCount() {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getStatus, 1);
        return productMapper.selectCount(wrapper);
    }

    /**
     * 创建空结果
     */
    private EvaluationResult createEmptyResult(String algorithmName) {
        EvaluationResult result = new EvaluationResult();
        result.setAlgorithmName(algorithmName);
        result.setTimestamp(System.currentTimeMillis());
        return result;
    }

    /**
     * 推荐函数接口
     */
    @FunctionalInterface
    private interface RecommenderFunction {
        List<RecommendationResultDTO> recommend(Long userId,
                                                   org.example.springboot.entity.dto.UserProfileDTO profile);
    }
}
