package org.example.springboot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.springboot.common.Result;
import org.example.springboot.entity.dto.statistics.*;
import org.example.springboot.enumClass.UserRole;
import org.example.springboot.enums.ErrorCodeEnum;
import org.example.springboot.exception.BusinessException;
import org.example.springboot.service.RecommendActionService;
import org.example.springboot.service.StatisticsService;
import org.example.springboot.util.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 统计分析控制器
 * 提供销售统计、用户统计、推荐系统效果评估等数据查询接口
 *
 * @author IhaveBB
 * @date 2026/03/22
 */
@Tag(name = "统计分析接口")
@RestController
@RequestMapping("/statistics")
public class StatisticsController {
    private static final Logger LOGGER = LoggerFactory.getLogger(StatisticsController.class);

    @Autowired
    private StatisticsService statisticsService;

    @Autowired
    private RecommendActionService recommendActionService;

    // ==================== 销售统计接口 ====================

    /**
     * 获取本月订单统计
     *
     * @param merchantId 商户ID（可为null，管理员场景）
     * @return 本月订单统计数据
     * @author IhaveBB
     * @date 2026/03/22
     */
    @Operation(summary = "获取本月订单统计")
    @GetMapping("/orders/monthly")
    public Result<OrderStatisticsDTO> getMonthlyOrderStatistics(@RequestParam(required = false) Long merchantId) {
        Long finalMerchantId = UserContext.getMerchantId(merchantId);
        LOGGER.info("获取本月订单统计，merchantId: {}", finalMerchantId);
        OrderStatisticsDTO statistics = statisticsService.getMonthlyOrderStatistics(finalMerchantId);
        return Result.success(statistics);
    }

    /**
     * 获取本月销售额统计
     *
     * @param merchantId 商户ID（可为null，管理员场景）
     * @return 本月销售额统计数据
     * @author IhaveBB
     * @date 2026/03/22
     */
    @Operation(summary = "获取本月销售额统计")
    @GetMapping("/sales/monthly")
    public Result<SalesStatisticsDTO> getMonthlySalesStatistics(@RequestParam(required = false) Long merchantId) {
        Long finalMerchantId = UserContext.getMerchantId(merchantId);
        LOGGER.info("获取本月销售额统计，merchantId: {}", finalMerchantId);
        SalesStatisticsDTO statistics = statisticsService.getMonthlySalesStatistics(finalMerchantId);
        return Result.success(statistics);
    }

    /**
     * 获取用户订单统计
     * <p>
     * 普通用户只能查看自己的订单统计，管理员可以查看任意用户。
     * </p>
     *
     * @param userId 用户ID（可为null，取登录用户ID）
     * @return 用户订单统计数据
     * @author IhaveBB
     * @date 2026/03/22
     */
    @Operation(summary = "获取用户订单统计")
    @GetMapping("/user/orders")
    public Result<UserOrderStatisticsDTO> getUserOrderStatistics(@RequestParam(required = false) Long userId) {
        if (userId == null) {
            userId = UserContext.requireUserId();
        } else {
            if (UserContext.isUser() && !userId.equals(UserContext.getUserId())) {
                throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权限查看他人的订单统计");
            }
        }

        LOGGER.info("获取用户订单统计，userId: {}", userId);
        UserOrderStatisticsDTO statistics = statisticsService.getUserOrderStatistics(userId);
        return Result.success(statistics);
    }

    /**
     * 获取当前登录用户的消费统计
     *
     * @return 用户消费统计数据
     * @author IhaveBB
     * @date 2026/03/22
     */
    @Operation(summary = "获取用户消费统计")
    @GetMapping("/user/spending")
    public Result<UserSpendingStatisticsDTO> getUserSpendingStatistics() {
        Long userId = UserContext.requireUserId();
        LOGGER.info("获取用户消费统计，userId: {}", userId);
        UserSpendingStatisticsDTO statistics = statisticsService.getUserSpendingStatistics(userId);
        return Result.success(statistics);
    }

    /**
     * 获取年度用户总数统计
     *
     * @return 年度用户统计数据
     * @author IhaveBB
     * @date 2026/03/22
     */
    @Operation(summary = "获取用户总数统计")
    @GetMapping("/users/yearly")
    public Result<YearlyUserStatisticsDTO> getYearlyUserStatistics() {
        LOGGER.info("获取年度用户统计");
        YearlyUserStatisticsDTO statistics = statisticsService.getYearlyUserStatistics();
        return Result.success(statistics);
    }

    /**
     * 获取热销商品 Top5
     *
     * @param merchantId 商户ID（可为null，管理员场景）
     * @return 热销商品 Top5 统计数据
     * @author IhaveBB
     * @date 2026/03/22
     */
    @Operation(summary = "获取热销商品 Top5")
    @GetMapping("/products/top5")
    public Result<TopProductsStatisticsDTO> getTopSellingProducts(@RequestParam(required = false) Long merchantId) {
        Long finalMerchantId = UserContext.getMerchantId(merchantId);
        LOGGER.info("获取热销商品 Top5 统计，merchantId: {}", finalMerchantId);
        TopProductsStatisticsDTO statistics = statisticsService.getTopSellingProducts(finalMerchantId);
        return Result.success(statistics);
    }

    /**
     * 获取品类销售占比统计
     *
     * @param merchantId 商户ID（可为null，管理员场景）
     * @return 品类销售占比数据
     * @author IhaveBB
     * @date 2026/03/22
     */
    @Operation(summary = "获取品类销售占比")
    @GetMapping("/category/sales")
    public Result<CategorySalesStatisticsResponse> getCategorySalesStatistics(@RequestParam(required = false) Long merchantId) {
        Long finalMerchantId = UserContext.getMerchantId(merchantId);
        LOGGER.info("获取品类销售占比统计，merchantId: {}", finalMerchantId);
        CategorySalesStatisticsResponse statistics = statisticsService.getCategorySalesStatistics(finalMerchantId);
        return Result.success(statistics);
    }

    // ==================== 新增统计接口 ====================

    /**
     * 获取销售趋势数据
     *
     * @param days       统计天数，默认30天
     * @param merchantId 商户ID（可为null，管理员场景）
     * @return 销售趋势数据
     * @author IhaveBB
     * @date 2026/03/22
     */
    @Operation(summary = "获取销售趋势")
    @GetMapping("/sales/trend")
    public Result<SalesTrendResponse> getSalesTrend(
            @RequestParam(defaultValue = "30") Integer days,
            @RequestParam(required = false) Long merchantId) {
        Long finalMerchantId = UserContext.getMerchantId(merchantId);
        LOGGER.info("获取销售趋势，days: {}, merchantId: {}", days, finalMerchantId);
        SalesTrendResponse response = statisticsService.getSalesTrend(days, finalMerchantId);
        return Result.success(response);
    }

    /**
     * 获取季节性销售统计
     *
     * @param merchantId 商户ID（可为null，管理员场景）
     * @return 季节性销售统计数据
     * @author IhaveBB
     * @date 2026/03/22
     */
    @Operation(summary = "获取季节性销售统计")
    @GetMapping("/sales/seasonal")
    public Result<SeasonalSalesResponse> getSeasonalStatistics(@RequestParam(required = false) Long merchantId) {
        Long finalMerchantId = UserContext.getMerchantId(merchantId);
        LOGGER.info("获取季节性销售统计，merchantId: {}", finalMerchantId);
        SeasonalSalesResponse response = statisticsService.getSeasonalStatistics(finalMerchantId);
        return Result.success(response);
    }

    /**
     * 获取地区销售统计
     *
     * @param merchantId 商户ID（可为null，管理员场景）
     * @return 地区销售统计数据
     * @author IhaveBB
     * @date 2026/03/22
     */
    @Operation(summary = "获取地区销售统计")
    @GetMapping("/sales/region")
    public Result<RegionSalesResponse> getRegionStatistics(@RequestParam(required = false) Long merchantId) {
        Long finalMerchantId = UserContext.getMerchantId(merchantId);
        LOGGER.info("获取地区销售统计，merchantId: {}", finalMerchantId);
        RegionSalesResponse response = statisticsService.getRegionStatistics(finalMerchantId);
        return Result.success(response);
    }

    /**
     * 获取商户列表（仅管理员）
     *
     * @return 商户列表
     * @author IhaveBB
     * @date 2026/03/22
     */
    @Operation(summary = "获取商户列表")
    @GetMapping("/merchants")
    public Result<List<MerchantDTO>> getMerchantList() {
        UserContext.checkAdmin();
        LOGGER.info("获取商户列表");
        List<MerchantDTO> merchants = statisticsService.getMerchantList();
        return Result.success(merchants);
    }

    // ==================== 推荐系统效果评估接口 ====================

    /**
     * 获取推荐系统效果概览
     *
     * @return 推荐系统效果概览数据
     * @author IhaveBB
     * @date 2026/03/22
     */
    @Operation(summary = "获取推荐系统效果概览")
    @GetMapping("/recommend/overview")
    public Result<RecommendOverviewResponse> getRecommendOverview() {
        LOGGER.info("获取推荐系统效果概览");
        RecommendOverviewResponse response = recommendActionService.getRecommendOverview();
        return Result.success(response);
    }

    /**
     * 获取推荐效果趋势
     *
     * @param days 统计天数，默认30天
     * @return 推荐效果趋势数据
     * @author IhaveBB
     * @date 2026/03/22
     */
    @Operation(summary = "获取推荐效果趋势")
    @GetMapping("/recommend/trend")
    public Result<RecommendTrendResponse> getRecommendTrend(@RequestParam(defaultValue = "30") Integer days) {
        LOGGER.info("获取推荐效果趋势，days: {}", days);
        RecommendTrendResponse response = recommendActionService.getRecommendTrend(days);
        return Result.success(response);
    }

    /**
     * 获取分类推荐效果
     *
     * @return 分类推荐效果数据
     * @author IhaveBB
     * @date 2026/03/22
     */
    @Operation(summary = "获取分类推荐效果")
    @GetMapping("/recommend/category-effect")
    public Result<RecommendCategoryEffectResponse> getCategoryEffect() {
        LOGGER.info("获取分类推荐效果");
        RecommendCategoryEffectResponse response = recommendActionService.getCategoryEffect();
        return Result.success(response);
    }

    /**
     * 获取推荐算法构成分析
     *
     * @return 推荐算法构成数据
     * @author IhaveBB
     * @date 2026/03/22
     */
    @Operation(summary = "获取推荐算法构成")
    @GetMapping("/recommend/algorithm-composition")
    public Result<RecommendAlgorithmResponse> getAlgorithmComposition() {
        LOGGER.info("获取推荐算法构成");
        RecommendAlgorithmResponse response = recommendActionService.getAlgorithmComposition();
        return Result.success(response);
    }

    /**
     * 获取推荐多样性指标（信息熵）
     *
     * @return 推荐多样性指标数据
     * @author IhaveBB
     * @date 2026/03/22
     */
    @Operation(summary = "获取推荐多样性指标（信息熵）")
    @GetMapping("/recommend/diversity")
    public Result<RecommendDiversityDTO> getRecommendationDiversity() {
        LOGGER.info("获取推荐多样性指标");
        RecommendDiversityDTO response = recommendActionService.getRecommendationDiversity();
        return Result.success(response);
    }

    /**
     * 获取用户行为相似度分布
     *
     * @return 用户行为相似度分布数据
     * @author IhaveBB
     * @date 2026/03/22
     */
    @Operation(summary = "获取用户行为相似度分布")
    @GetMapping("/recommend/user-similarity")
    public Result<RecommendUserSimilarityDTO> getUserSimilarityDistribution() {
        LOGGER.info("获取用户行为相似度分布");
        RecommendUserSimilarityDTO response = recommendActionService.getUserSimilarityDistribution();
        return Result.success(response);
    }

    /**
     * 获取智能优化建议
     *
     * @return 推荐系统智能优化建议
     * @author IhaveBB
     * @date 2026/03/22
     */
    @Operation(summary = "获取智能优化建议")
    @GetMapping("/recommend/suggestions")
    public Result<RecommendOptimizationDTO> getOptimizationSuggestions() {
        LOGGER.info("获取智能优化建议");
        RecommendOptimizationDTO response = recommendActionService.getOptimizationSuggestions();
        return Result.success(response);
    }

    /**
     * 预测下期推荐效果
     *
     * @return 下期推荐效果预测数据
     * @author IhaveBB
     * @date 2026/03/22
     */
    @Operation(summary = "预测下期推荐效果")
    @GetMapping("/recommend/prediction")
    public Result<RecommendPredictionDTO> predictNextPeriodEffect() {
        LOGGER.info("预测下期推荐效果");
        RecommendPredictionDTO response = recommendActionService.predictNextPeriodEffect();
        return Result.success(response);
    }
}
