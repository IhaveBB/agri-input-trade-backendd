package org.example.springboot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.springboot.common.Result;
import org.example.springboot.enumClass.UserRole;
import org.example.springboot.service.RecommendActionService;
import org.example.springboot.service.StatisticsService;
import org.example.springboot.util.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "统计分析接口")
@RestController
@RequestMapping("/statistics")
public class StatisticsController {
    private static final Logger LOGGER = LoggerFactory.getLogger(StatisticsController.class);

    @Autowired
    private StatisticsService statisticsService;

    @Autowired
    private RecommendActionService recommendActionService;

    @Operation(summary = "获取本月订单统计")
    @GetMapping("/orders/monthly")
    public Result<?> getMonthlyOrderStatistics() {
        Long userId = UserContext.getUserId();
        String role = UserContext.getRole();

        if (userId == null) {
            return Result.error("-1", "用户未登录");
        }

        Long merchantId = null;
        // 商户只能查看自己店铺的统计
        if (UserRole.isMerchant(role)) {
            merchantId = userId;
        }
        // 管理员可以查看全部

        LOGGER.info("获取本月订单统计, merchantId: {}", merchantId);
        Map<String, Object> statistics = statisticsService.getMonthlyOrderStatistics(merchantId);
        return Result.success(statistics);
    }

    @Operation(summary = "获取本月销售额统计")
    @GetMapping("/sales/monthly")
    public Result<?> getMonthlySalesStatistics() {
        Long userId = UserContext.getUserId();
        String role = UserContext.getRole();

        if (userId == null) {
            return Result.error("-1", "用户未登录");
        }

        Long merchantId = null;
        // 商户只能查看自己店铺的统计
        if (UserRole.isMerchant(role)) {
            merchantId = userId;
        }
        // 管理员可以查看全部

        LOGGER.info("获取本月销售额统计, merchantId: {}", merchantId);
        Map<String, Object> statistics = statisticsService.getMonthlySalesStatistics(merchantId);
        return Result.success(statistics);
    }

    @Operation(summary = "获取用户订单统计")
    @GetMapping("/user/orders")
    public Result<?> getUserOrderStatistics(@RequestParam(required = false) Long userId) {
        Long currentUserId = UserContext.getUserId();
        String role = UserContext.getRole();

        if (currentUserId == null) {
            return Result.error("-1", "用户未登录");
        }

        // 如果没有传userId，使用当前用户ID
        if (userId == null) {
            userId = currentUserId;
        }

        // 普通用户只能查看自己的订单统计
        if (UserRole.isUser(role) && !currentUserId.equals(userId)) {
            return Result.error("-1", "无权限查看他人的订单统计");
        }

        LOGGER.info("获取用户订单统计, userId: {}", userId);
        Map<String, Object> statistics = statisticsService.getUserOrderStatistics(userId);
        return Result.success(statistics);
    }

    @Operation(summary = "获取用户消费统计")
    @GetMapping("/user/spending")
    public Result<?> getUserSpendingStatistics() {
        Long userId = UserContext.getUserId();
        String role = UserContext.getRole();

        if (userId == null) {
            return Result.error("-1", "用户未登录");
        }

        // 普通用户只能查看自己的消费统计
        if (UserRole.isUser(role)) {
            LOGGER.info("获取用户消费统计, userId: {}", userId);
            Map<String, Object> statistics = statisticsService.getUserSpendingStatistics(userId);
            return Result.success(statistics);
        }

        // 管理员可以查看所有用户的消费统计（需要传userId参数）
        LOGGER.info("获取用户消费统计, userId: {}", userId);
        Map<String, Object> statistics = statisticsService.getUserSpendingStatistics(userId);
        return Result.success(statistics);
    }

    @Operation(summary = "获取用户总数统计")
    @GetMapping("/users/yearly")
    public Result<?> getYearlyUserStatistics() {
        LOGGER.info("获取年度用户统计");
        Map<String, Object> statistics = statisticsService.getYearlyUserStatistics();
        return Result.success(statistics);
    }

    @Operation(summary = "获取热销商品Top5")
    @GetMapping("/products/top5")
    public Result<?> getTopSellingProducts() {
        LOGGER.info("获取热销商品Top5统计");
        Map<String, Object> statistics = statisticsService.getTopSellingProducts();
        return Result.success(statistics);
    }

    @Operation(summary = "获取品类销售占比")
    @GetMapping("/category/sales")
    public Result<?> getCategorySalesStatistics() {
        LOGGER.info("获取品类销售占比统计");
        Map<String, Object> statistics = statisticsService.getCategorySalesStatistics();
        return Result.success(statistics);
    }

    // ==================== 推荐系统效果评估接口 ====================

    @Operation(summary = "获取推荐系统效果概览")
    @GetMapping("/recommend/overview")
    public Result<?> getRecommendOverview() {
        LOGGER.info("获取推荐系统效果概览");
        Map<String, Object> overview = recommendActionService.getRecommendOverview();
        return Result.success(overview);
    }

    @Operation(summary = "获取推荐效果趋势")
    @GetMapping("/recommend/trend")
    public Result<?> getRecommendTrend(@RequestParam(defaultValue = "30") Integer days) {
        LOGGER.info("获取推荐效果趋势, days: {}", days);
        Map<String, Object> trend = recommendActionService.getRecommendTrend(days);
        return Result.success(trend);
    }

    @Operation(summary = "获取分类推荐效果")
    @GetMapping("/recommend/category-effect")
    public Result<?> getCategoryEffect() {
        LOGGER.info("获取分类推荐效果");
        Map<String, Object> categoryEffect = recommendActionService.getCategoryEffect();
        return Result.success(categoryEffect);
    }

    @Operation(summary = "获取推荐算法构成")
    @GetMapping("/recommend/algorithm-composition")
    public Result<?> getAlgorithmComposition() {
        LOGGER.info("获取推荐算法构成");
        Map<String, Object> composition = recommendActionService.getAlgorithmComposition();
        return Result.success(composition);
    }

    @Operation(summary = "获取推荐多样性指标（信息熵）")
    @GetMapping("/recommend/diversity")
    public Result<?> getRecommendationDiversity() {
        LOGGER.info("获取推荐多样性指标");
        Map<String, Object> diversity = recommendActionService.getRecommendationDiversity();
        return Result.success(diversity);
    }

    @Operation(summary = "获取用户行为相似度分布")
    @GetMapping("/recommend/user-similarity")
    public Result<?> getUserSimilarityDistribution() {
        LOGGER.info("获取用户行为相似度分布");
        Map<String, Object> similarity = recommendActionService.getUserSimilarityDistribution();
        return Result.success(similarity);
    }

    @Operation(summary = "获取智能优化建议")
    @GetMapping("/recommend/suggestions")
    public Result<?> getOptimizationSuggestions() {
        LOGGER.info("获取智能优化建议");
        Map<String, Object> suggestions = recommendActionService.getOptimizationSuggestions();
        return Result.success(suggestions);
    }

    @Operation(summary = "预测下期推荐效果")
    @GetMapping("/recommend/prediction")
    public Result<?> predictNextPeriodEffect() {
        LOGGER.info("预测下期推荐效果");
        Map<String, Object> prediction = recommendActionService.predictNextPeriodEffect();
        return Result.success(prediction);
    }
}
