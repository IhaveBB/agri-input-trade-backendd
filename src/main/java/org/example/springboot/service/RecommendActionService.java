package org.example.springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.example.springboot.entity.Product;
import org.example.springboot.entity.RecommendAction;
import org.example.springboot.entity.dto.statistics.*;
import org.example.springboot.mapper.ProductMapper;
import org.example.springboot.mapper.RecommendActionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 推荐行为记录服务
 * 负责埋点记录和效果统计分析
 */
@Service
public class RecommendActionService {

    // ==================== 阈值配置常量 ====================

    /** 多样性等级阈值配置 */
    static class DiversityThresholds {
        static final double HIGH = 0.8;        // 高多样性阈值
        static final double MEDIUM = 0.5;      // 中等多样性阈值
        static final double LOW = 0.3;         // 较低多样性阈值
    }

    /** 健康度评分等级阈值 */
    static class HealthScoreThresholds {
        static final double EXCELLENT = 80;
        static final double GOOD = 60;
        static final double NORMAL = 40;
    }

    /** 预测置信度阈值 */
    static class ConfidenceThresholds {
        static final double HIGH = 0.7;
        static final double MEDIUM = 0.4;
    }

    /** 默认参数配置 */
    static class DefaultParams {
        static final int TREND_DEFAULT_DAYS = 30;
        static final int MIN_DATA_POINTS = 3;
        static final long MIN_COVERED_USERS = 100;
        static final long GOOD_COVERED_USERS = 1000;
    }

    /** 评分因子配置 */
    static class ScoreConfig {
        // CVR评分：优秀(>=2%)得20分，良好(>=1%)得10分
        static final double CVR_EXCELLENT = 2.0;
        static final double CVR_GOOD = 1.0;
        static final double CVR_EXCELLENT_SCORE = 20.0;
        static final double CVR_GOOD_SCORE = 10.0;

        // 基础分
        static final double BASE_SCORE = 50.0;

        // 多样性评分：不集中得15分
        static final double DIVERSITY_SCORE = 15.0;

        // 数据量评分：>=1000得15分
        static final double DATA_VOLUME_SCORE = 15.0;
    }

    /** 规则阈值配置 */
    static class RuleThresholds {
        // 转化率阈值
        static final double CVR_WARNING = 1.0;
        static final double CVR_OPTIMIZE = 2.0;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(RecommendActionService.class);

    @Resource
    private ProductMapper productMapper;

    /**
     * 获取商户的商品ID集合
     *
     * @param merchantId 商户ID（可为null）
     * @return 商品ID集合， merchantId 为 null 时返回 null 表示不筛选
     * @author IhaveBB
     * @date 2026/03/29
     */
    private Set<Long> getMerchantProductIds(Long merchantId) {
        if (merchantId == null) {
            return null;
        }
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getStatus, 1)
                .eq(Product::getMerchantId, merchantId)
                .select(Product::getId);
        List<Product> products = productMapper.selectList(wrapper);
        return products.stream()
                .map(Product::getId)
                .collect(Collectors.toSet());
    }
    @Resource
    private RecommendActionMapper recommendActionMapper;

    /**
     * 记录推荐行为（异步执行，不影响主流程）
     * 推荐商品曝光时调用
     */
    @Async("recommendActionExecutor")
    public void recordExposure(Long userId, Long productId, Long categoryId,
                               String source, String scene, Integer position,
                               List<Long> contextProductIds) {
        recordAction(userId, productId, categoryId, "EXPOSURE", source, scene, position, contextProductIds);
    }

    /**
     * 记录推荐商品点击
     */
    @Async("recommendActionExecutor")
    public void recordClick(Long userId, Long productId, Long categoryId,
                            String source, String scene, Integer position,
                            List<Long> contextProductIds) {
        recordAction(userId, productId, categoryId, "CLICK", source, scene, position, contextProductIds);
    }

    /**
     * 记录推荐商品收藏
     */
    @Async("recommendActionExecutor")
    public void recordCollect(Long userId, Long productId, Long categoryId,
                              String source, String scene, Integer position,
                              List<Long> contextProductIds) {
        recordAction(userId, productId, categoryId, "COLLECT", source, scene, position, contextProductIds);
    }

    /**
     * 记录推荐商品加入购物车
     */
    @Async("recommendActionExecutor")
    public void recordCart(Long userId, Long productId, Long categoryId,
                           String source, String scene, Integer position,
                           List<Long> contextProductIds) {
        recordAction(userId, productId, categoryId, "CART", source, scene, position, contextProductIds);
    }

    /**
     * 记录推荐商品购买
     */
    @Async("recommendActionExecutor")
    public void recordBuy(Long userId, Long productId, Long categoryId,
                          String source, String scene, Integer position,
                          List<Long> contextProductIds) {
        recordAction(userId, productId, categoryId, "BUY", source, scene, position, contextProductIds);
    }

    /**
     * 上报商品停留时长
     * <p>
     * 前端在用户离开商品详情页时调用，更新最近一条CLICK记录的duration字段。
     * 用于推荐算法中过滤无效浏览（停留过短的点击不计入交互矩阵）。
     * </p>
     *
     * @param userId    用户ID
     * @param productId 商品ID
     * @param duration  停留时长（秒）
     * @author IhaveBB
     * @date 2026/03/29
     */
    @Async("recommendActionExecutor")
    public void reportDwellDuration(Long userId, Long productId, Integer duration) {
        try {
            if (userId == null || productId == null || duration == null || duration <= 0) {
                return;
            }
            // 查询该用户对该商品最近的一条CLICK记录
            LambdaQueryWrapper<RecommendAction> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(RecommendAction::getUserId, userId)
                    .eq(RecommendAction::getProductId, productId)
                    .eq(RecommendAction::getActionType, "CLICK")
                    .orderByDesc(RecommendAction::getCreatedAt)
                    .last("LIMIT 1");
            RecommendAction lastClick = recommendActionMapper.selectOne(wrapper);
            if (lastClick != null) {
                lastClick.setDuration(duration);
                recommendActionMapper.updateById(lastClick);
                LOGGER.info("[推荐埋点] 用户{}在商品{}停留{}秒", userId, productId, duration);
            }
        } catch (Exception e) {
            LOGGER.error("[推荐埋点] 上报停留时长失败: {}", e.getMessage());
        }
    }

    /**
     * 统一记录行为
     */
    private void recordAction(Long userId, Long productId, Long categoryId,
                              String actionType, String source, String scene,
                              Integer position, List<Long> contextProductIds) {
        try {
            RecommendAction action = new RecommendAction();
            action.setUserId(userId);
            action.setProductId(productId);
            action.setCategoryId(categoryId);
            action.setActionType(actionType);
            action.setSource(source);
            action.setScene(scene);
            action.setPosition(position);
            action.setCreatedAt(LocalDateTime.now());

            // 将上下文商品ID列表转为字符串存储
            if (contextProductIds != null && !contextProductIds.isEmpty()) {
                action.setContextProductIds(contextProductIds.toString());
            }

            recommendActionMapper.insert(action);
            LOGGER.info("[推荐埋点] 用户{} 对商品{} 执行了{}行为，来源:{}, 场景:{}",
                    userId, productId, actionType, source, scene);
        } catch (Exception e) {
            LOGGER.error("[推荐埋点] 记录失败: {}", e.getMessage());
        }
    }

    /**
     * 获取推荐系统效果概览
     * 支持按商户维度筛选
     *
     * @param merchantId 商户ID（可为null，表示查询全平台）
     * @return 推荐效果概览
     * @author IhaveBB
     * @date 2026/03/29
     */
    public RecommendOverviewResponse getRecommendOverview(Long merchantId) {
        RecommendOverviewResponse response = new RecommendOverviewResponse();
        Set<Long> productIds = getMerchantProductIds(merchantId);

        // 如果商户没有商品， 直接返回空数据
        if (productIds != null && productIds.isEmpty()) {
            response.setExposureCount(0L);
            response.setClickCount(0L);
            response.setBuyCount(0L);
            response.setCtr("0.00%");
            response.setCvr("0.00%");
            response.setExposureGrowth("0.00%");
            response.setClickGrowth("0.00%");
            response.setBuyGrowth("0.00%");
            response.setCoveredUsers(0L);
            return response;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime thisMonthStart = now.withDayOfMonth(1).toLocalDate().atStartOfDay();

        // 本月统计
        long monthExposureCount = countActionByType(thisMonthStart, now, "EXPOSURE", productIds);
        long monthClickCount = countActionByType(thisMonthStart, now, "CLICK", productIds);
        long monthBuyCount = countActionByType(thisMonthStart, now, "BUY", productIds);

        // 上月同期统计
        LocalDateTime lastMonthStart = now.minusMonths(1).withDayOfMonth(1).toLocalDate().atStartOfDay();
        LocalDateTime lastMonthSameDay = now.minusMonths(1).toLocalDate().atStartOfDay();
        long lastMonthExposureCount = countActionByType(lastMonthStart, lastMonthSameDay, "EXPOSURE", productIds);
        long lastMonthClickCount = countActionByType(lastMonthStart, lastMonthSameDay, "CLICK", productIds);
        long lastMonthBuyCount = countActionByType(lastMonthStart, lastMonthSameDay, "BUY", productIds);

        // 计算关键指标
        double ctr = monthExposureCount > 0 ? (double) monthClickCount / monthExposureCount * 100 : 0;
        double cvr = monthExposureCount > 0 ? (double) monthBuyCount / monthExposureCount * 100 : 0;

        // 计算环比增长率
        double exposureGrowth = calculateGrowth(monthExposureCount, lastMonthExposureCount);
        double clickGrowth = calculateGrowth(monthClickCount, lastMonthClickCount);
        double buyGrowth = calculateGrowth(monthBuyCount, lastMonthBuyCount);

        response.setExposureCount(monthExposureCount);
        response.setClickCount(monthClickCount);
        response.setBuyCount(monthBuyCount);
        response.setCtr(String.format("%.2f", ctr) + "%");
        response.setCvr(String.format("%.2f", cvr) + "%");
        response.setExposureGrowth(String.format("%.2f", exposureGrowth) + "%");
        response.setClickGrowth(String.format("%.2f", clickGrowth) + "%");
        response.setBuyGrowth(String.format("%.2f", buyGrowth) + "%");

        // 覆盖用户数
        List<Long> productIdList = productIds != null ? new ArrayList<>(productIds) : null;
        Long coveredUsers = recommendActionMapper.countDistinctUsers(thisMonthStart, now, null, productIdList);
        response.setCoveredUsers(coveredUsers != null ? coveredUsers : 0L);

        return response;
    }

    /**
     * 获取推荐来源效果对比
     * 作用：对比不同推荐来源（协同过滤、热门推荐、地域推荐等）的效果差异，计算各来源的曝光、点击、购买及CTR/CVR
     * 用于：大屏图表-推荐来源效果对比（柱状图/多系列数据）
     * 注：已删除，目前只有单一推荐来源
     */

    /**
     * 获取推荐效果趋势
     * 支持按商户维度筛选
     *
     * @param days       统计天数
     * @param merchantId 商户ID（可为null，表示查询全平台）
     * @return 推荐效果趋势
     * @author IhaveBB
     * @date 2026/03/29
     */
    public RecommendTrendResponse getRecommendTrend(Integer days, Long merchantId) {
        RecommendTrendResponse response = new RecommendTrendResponse();

        if (days == null || days <= 0) {
            days = DefaultParams.TREND_DEFAULT_DAYS;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = now.minusDays(days).withHour(0).withMinute(0).withSecond(0);

        Set<Long> productIds = getMerchantProductIds(merchantId);
        List<Long> productIdList = productIds != null ? new ArrayList<>(productIds) : null;

        List<RecommendTrendDTO> trendList = recommendActionMapper.selectTrendByDay(startTime, now, null, productIdList);

        // 填充缺失的日期
        Map<String, RecommendTrendDTO> trendMap = trendList.stream()
                .collect(java.util.stream.Collectors.toMap(
                        RecommendTrendDTO::getDate,
                        d -> d,
                        (a, b) -> a
                ));

        List<RecommendTrendDTO> filledTrend = new ArrayList<>();
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (int i = 0; i < days; i++) {
            LocalDateTime day = now.minusDays(days - 1 - i);
            String dayKey = day.format(formatter);
            RecommendTrendDTO dto = trendMap.get(dayKey);
            if (dto == null) {
                dto = new RecommendTrendDTO();
                dto.setDate(dayKey);
                dto.setExposureCount(0L);
                dto.setClickCount(0L);
                dto.setBuyCount(0L);
            }
            filledTrend.add(dto);
        }

        long totalExposure = filledTrend.stream().mapToLong(d -> d.getExposureCount() != null ? d.getExposureCount() : 0L).sum();
        long totalClick = filledTrend.stream().mapToLong(d -> d.getClickCount() != null ? d.getClickCount() : 0L).sum();
        long totalBuy = filledTrend.stream().mapToLong(d -> d.getBuyCount() != null ? d.getBuyCount() : 0L).sum();

        response.setTrend(filledTrend);
        response.setDays(days);
        response.setTotalExposure(totalExposure);
        response.setTotalClick(totalClick);
        response.setTotalBuy(totalBuy);

        return response;
    }

    /**
     * 获取分类推荐效果
     * 支持按商户维度筛选
     *
     * @param merchantId 商户ID（可为null，表示查询全平台）
     * @return 分类推荐效果
     * @author IhaveBB
     * @date 2026/03/29
     */
    public RecommendCategoryEffectResponse getCategoryEffect(Long merchantId) {
        RecommendCategoryEffectResponse response = new RecommendCategoryEffectResponse();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = now.minusMonths(1).withDayOfMonth(1).toLocalDate().atStartOfDay();

        Set<Long> productIds = getMerchantProductIds(merchantId);
        List<Long> productIdList = productIds != null ? new ArrayList<>(productIds) : null;

        List<RecommendCategoryEffectDTO> categoryList = recommendActionMapper.selectCategoryEffect(startTime, now, productIdList);

        // 计算各类别的转化率
        for (RecommendCategoryEffectDTO dto : categoryList) {
            long exposure = dto.getExposureCount() != null ? dto.getExposureCount() : 0L;
            long click = dto.getClickCount() != null ? dto.getClickCount() : 0L;
            long buy = dto.getBuyCount() != null ? dto.getBuyCount() : 0L;

            dto.setCtr(exposure > 0 ? String.format("%.2f", (double) click / exposure * 100) + "%" : "0%");
            dto.setCvr(exposure > 0 ? String.format("%.2f", (double) buy / exposure * 100) + "%" : "0%");
        }

        response.setCategoryEffect(categoryList);
        response.setTotalCategories(categoryList.size());

        return response;
    }

    /**
     * 获取推荐算法构成
     * 支持按商户维度筛选
     *
     * @param merchantId 商户ID（可为null，表示查询全平台）
     * @return 推荐算法构成
     * @author IhaveBB
     * @date 2026/03/29
     */
    public RecommendAlgorithmResponse getAlgorithmComposition(Long merchantId) {
        RecommendAlgorithmResponse response = new RecommendAlgorithmResponse();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = now.minusMonths(1).withDayOfMonth(1).toLocalDate().atStartOfDay();

        Set<Long> productIds = getMerchantProductIds(merchantId);
        List<Long> productIdList = productIds != null ? new ArrayList<>(productIds) : null;

        List<Map<String, Object>> sourceStats = recommendActionMapper.countBySourceAndAction(startTime, now, productIdList);

        // 按来源分组
        Map<String, Long> sourceTotal = new HashMap<>();
        long total = 0;

        for (Map<String, Object> stat : sourceStats) {
            String source = (String) stat.get("source");
            if (!"NATURAL".equals(source)) {
                Long count = ((Number) stat.get("count")).longValue();
                sourceTotal.put(source, sourceTotal.getOrDefault(source, 0L) + count);
                total += count;
            }
        }

        // 计算占比
        Map<String, String> composition = new HashMap<>();
        for (Map.Entry<String, Long> entry : sourceTotal.entrySet()) {
            double percentage = total > 0 ? (double) entry.getValue() / total * 100 : 0;
            composition.put(entry.getKey(), String.format("%.1f", percentage) + "%");
        }

        response.setComposition(composition);
        response.setTotal(total);

        return response;
    }

    /**
     * 获取推荐多样性指标（信息熵）
     * 支持按商户维度筛选
     *
     * @param merchantId 商户ID（可为null，表示查询全平台）
     * @return 推荐多样性指标
     * @author IhaveBB
     * @date 2026/03/29
     */
    public RecommendDiversityDTO getRecommendationDiversity(Long merchantId) {
        RecommendDiversityDTO response = new RecommendDiversityDTO();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = now.minusMonths(1).withDayOfMonth(1).toLocalDate().atStartOfDay();

        Set<Long> productIds = getMerchantProductIds(merchantId);
        List<Long> productIdList = productIds != null ? new ArrayList<>(productIds) : null;

        List<Map<String, Object>> categoryData = recommendActionMapper.countByCategory(startTime, now, productIdList);

        if (categoryData == null || categoryData.isEmpty()) {
            response.setEntropy("0.00");
            response.setDiversityLevel("无数据");
            response.setCategoryDistribution(new HashMap<>());
            return response;
        }

        // 计算信息熵 H = -Σ(p * log2(p))
        double entropy = 0.0;
        long totalExposure = 0;
        Map<String, Long> distribution = new HashMap<>();

        for (Map<String, Object> category : categoryData) {
            long exposure = ((Number) category.get("exposure_count")).longValue();
            String categoryName = (String) category.get("category_name");
            if (categoryName == null) {
                categoryName = "未知";
            }
            totalExposure += exposure;
            distribution.put(categoryName, exposure);
        }

        for (Map<String, Object> category : categoryData) {
            long exposure = ((Number) category.get("exposure_count")).longValue();
            if (exposure > 0) {
                double p = (double) exposure / totalExposure;
                entropy -= p * (Math.log(p) / Math.log(2));
            }
        }

        // 计算最大熵（均匀分布时的熵）
        double maxEntropy = Math.log(distribution.size()) / Math.log(2);
        // 归一化的多样性指标（0-1之间）
        double normalizedDiversity = maxEntropy > 0 ? entropy / maxEntropy : 0;

        String diversityLevel;
        if (normalizedDiversity > DiversityThresholds.HIGH) {
            diversityLevel = "高多样性";
        } else if (normalizedDiversity > DiversityThresholds.MEDIUM) {
            diversityLevel = "中等多样性";
        } else if (normalizedDiversity > DiversityThresholds.LOW) {
            diversityLevel = "较低多样性";
        } else {
            diversityLevel = "推荐过于集中";
        }

        response.setEntropy(String.format("%.2f", entropy));
        response.setMaxEntropy(String.format("%.2f", maxEntropy));
        response.setNormalizedDiversity(String.format("%.2f", normalizedDiversity * 100) + "%");
        response.setDiversityLevel(diversityLevel);
        response.setCategoryCount(distribution.size());
        response.setTotalExposure(totalExposure);

        // 分类分布百分比
        Map<String, String> distributionPercent = new HashMap<>();
        for (Map.Entry<String, Long> entry : distribution.entrySet()) {
            double percent = totalExposure > 0 ? (double) entry.getValue() / totalExposure * 100 : 0;
            distributionPercent.put(entry.getKey(), String.format("%.1f", percent) + "%");
        }
        response.setCategoryDistribution(distributionPercent);

        LOGGER.info("[推荐多样性] 熵: {}, 多样性等级: {}", entropy, diversityLevel);
        return response;
    }

    /**
     * 获取用户行为相似度分布
     * 支持按商户维度筛选
     *
     * @param merchantId 商户ID（可为null，表示查询全平台）
     * @return 用户行为相似度分布
     * @author IhaveBB
     * @date 2026/03/29
     */
    public RecommendUserSimilarityDTO getUserSimilarityDistribution(Long merchantId) {
        RecommendUserSimilarityDTO result = new RecommendUserSimilarityDTO();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = now.minusMonths(1).withDayOfMonth(1).toLocalDate().atStartOfDay();

        Set<Long> productIds = getMerchantProductIds(merchantId);
        List<Long> productIdList = productIds != null ? new ArrayList<>(productIds) : null;

        Long usersWithClick = recommendActionMapper.countDistinctUsers(startTime, now, "CLICK", productIdList);
        Long usersWithBuy = recommendActionMapper.countDistinctUsers(startTime, now, "BUY", productIdList);
        Long totalUsers = recommendActionMapper.countDistinctUsers(startTime, now, null, productIdList);

        // 计算转化漏斗
        double clickRate = totalUsers > 0 ? (double) usersWithClick / totalUsers * 100 : 0;
        double buyRate = totalUsers > 0 ? (double) usersWithBuy / totalUsers * 100 : 0;

        // 构建漏斗DTO
        RecommendUserSimilarityDTO.FunnelDTO funnel = new RecommendUserSimilarityDTO.FunnelDTO();
        funnel.setTotalUsers(totalUsers != null ? totalUsers : 0L);
        funnel.setUsersWithClick(usersWithClick != null ? usersWithClick : 0L);
        funnel.setUsersWithBuy(usersWithBuy != null ? usersWithBuy : 0L);
        funnel.setClickRate(String.format("%.2f", clickRate) + "%");
        funnel.setBuyRate(String.format("%.2f", buyRate) + "%");
        result.setFunnel(funnel);

        // 行为深度分析
        RecommendUserSimilarityDTO.DepthAnalysisDTO depthAnalysis = analyzeBehaviorDepthDTO(startTime, now, productIds);
        result.setDepthAnalysis(depthAnalysis);

        return result;
    }

    /**
     * 分析用户行为深度（返回DTO），支持按商品集合过滤
     *
     * @param startTime  开始时间
     * @param endTime    结束时间
     * @param productIds 商品ID集合（可为null）
     * @return 行为深度分析DTO
     * @author IhaveBB
     * @date 2026/03/29
     */
    private RecommendUserSimilarityDTO.DepthAnalysisDTO analyzeBehaviorDepthDTO(LocalDateTime startTime, LocalDateTime endTime, Set<Long> productIds) {
        RecommendUserSimilarityDTO.DepthAnalysisDTO result = new RecommendUserSimilarityDTO.DepthAnalysisDTO();

        LambdaQueryWrapper<RecommendAction> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(RecommendAction::getCreatedAt, startTime)
               .lt(RecommendAction::getCreatedAt, endTime)
               .isNotNull(RecommendAction::getUserId);
        if (productIds != null && !productIds.isEmpty()) {
            wrapper.in(RecommendAction::getProductId, productIds);
        }

        List<RecommendAction> actions = recommendActionMapper.selectList(wrapper);

        // 按用户分组统计行为数量
        Map<Long, Map<String, Integer>> userBehaviorCounts = new HashMap<>();
        for (RecommendAction action : actions) {
            Long userId = action.getUserId();
            userBehaviorCounts.computeIfAbsent(userId, k -> new HashMap<>())
                    .merge(action.getActionType(), 1, Integer::sum);
        }

        // 统计行为深度分布
        int depth1 = 0; // 只有曝光
        int depth2 = 0; // 曝光+点击
        int depth3 = 0; // 曝光+点击+收藏/加购
        int depth4 = 0; // 完整转化（曝光+点击+收藏/加购+购买）

        for (Map<String, Integer> behaviors : userBehaviorCounts.values()) {
            boolean hasExposure = behaviors.containsKey("EXPOSURE");
            boolean hasClick = behaviors.containsKey("CLICK");
            boolean hasCollectOrCart = behaviors.containsKey("COLLECT") || behaviors.containsKey("CART");
            boolean hasBuy = behaviors.containsKey("BUY");

            if (hasBuy && hasCollectOrCart && hasClick && hasExposure) {
                depth4++;
            } else if (hasCollectOrCart && hasClick && hasExposure) {
                depth3++;
            } else if (hasClick && hasExposure) {
                depth2++;
            } else if (hasExposure) {
                depth1++;
            }
        }

        int total = userBehaviorCounts.size();
        Map<String, String> depthDistribution = new HashMap<>();
        depthDistribution.put("深度4(完整转化)", total > 0 ? String.format("%.1f", (double) depth4 / total * 100) + "%" : "0%");
        depthDistribution.put("深度3(加购/收藏)", total > 0 ? String.format("%.1f", (double) depth3 / total * 100) + "%" : "0%");
        depthDistribution.put("深度2(点击)", total > 0 ? String.format("%.1f", (double) depth2 / total * 100) + "%" : "0%");
        depthDistribution.put("深度1(仅曝光)", total > 0 ? String.format("%.1f", (double) depth1 / total * 100) + "%" : "0%");

        result.setTotalUsers(total);
        result.setDepthDistribution(depthDistribution);

        // 计算平均行为数
        double avgActions = total > 0 ? (double) actions.size() / total : 0;
        result.setAvgActionsPerUser(String.format("%.2f", avgActions));

        return result;
    }

    /**
     * 生成智能优化建议， 支持按商户维度筛选
     *
     * @param merchantId 商户ID（可为null，表示查询全平台）
     * @return 优化建议DTO
     * @author IhaveBB
     * @date 2026/03/29
     */
    public RecommendOptimizationDTO getOptimizationSuggestions(Long merchantId) {
        // 初始化规则引擎
        List<SuggestionRule> rules = buildRules();
        List<ScoreFactor> scoreFactors = buildScoreFactors();

        // 收集指标数据
        Map<String, Object> metrics = collectMetrics(merchantId);

        // 执行规则生成建议
        List<Map<String, String>> suggestions = executeRules(rules, metrics);

        // 计算健康度评分
        double healthScore = calculateHealthScore(scoreFactors, metrics);
        String healthLevel = getHealthLevel(healthScore);

        // 构建返回DTO
        RecommendOptimizationDTO result = new RecommendOptimizationDTO();
        result.setHealthScore(String.format("%.0f", healthScore));
        result.setHealthLevel(healthLevel);
        result.setSuggestions(suggestions);
        result.setAnalyzedAt(LocalDateTime.now().toString());

        LOGGER.info("[优化建议] 健康度评分: {}, 建议数: {}", healthScore, suggestions.size());
        return result;
    }

    /**
     * 构建建议规则列表（可扩展）
     */
    private List<SuggestionRule> buildRules() {
        List<SuggestionRule> rules = new ArrayList<>();

        // 转化率规则
        rules.add(new CvrRule(new RuleConfig(RuleThresholds.CVR_WARNING, "转化率预警", "高",
                "推荐转化率低于1%，建议优化推荐算法或调整推荐商品排序",
                "增加热门商品权重或优化相似用户匹配算法")));
        rules.add(new CvrRule(new RuleConfig(RuleThresholds.CVR_OPTIMIZE, "转化率优化", "中",
                "推荐转化率处于中等水平，有提升空间",
                "可以尝试增加地域权重或热门商品推荐比例")));

        // 多样性规则
        rules.add(new DiversityRule(new RuleConfig(0, "多样性不足", "高",
                "推荐商品类目过于集中，用户可选范围有限",
                "增加推荐结果的多样性，引入更多品类商品"), "推荐过于集中"));

        // 数据量规则
        rules.add(new DataVolumeRule(new RuleConfig(0, "数据量不足", "中",
                "推荐覆盖用户数较少，可能影响推荐效果",
                "增加用户引导，完善用户画像数据"), DefaultParams.MIN_COVERED_USERS));

        return rules;
    }

    /**
     * 构建评分因子列表（可扩展）
     */
    private List<ScoreFactor> buildScoreFactors() {
        List<ScoreFactor> factors = new ArrayList<>();

        // CVR评分因子：>=CVR_EXCELLENT得CVR_EXCELLENT_SCORE分，>=CVR_GOOD得CVR_GOOD_SCORE分
        factors.add(new CvrScoreFactor(ScoreConfig.CVR_EXCELLENT, ScoreConfig.CVR_GOOD,
                ScoreConfig.CVR_EXCELLENT_SCORE, ScoreConfig.CVR_GOOD_SCORE));

        // 多样性评分因子：不集中则得DIVERSITY_SCORE分
        factors.add(new DiversityScoreFactor("推荐过于集中", ScoreConfig.DIVERSITY_SCORE));

        // 数据量评分因子：>=GOOD_COVERED_USERS则得DATA_VOLUME_SCORE分
        factors.add(new DataVolumeScoreFactor(DefaultParams.GOOD_COVERED_USERS, ScoreConfig.DATA_VOLUME_SCORE));

        return factors;
    }

    /**
     * 收集指标数据， 支持按商户维度
     *
     * @param merchantId 商户ID（可为null）
     * @return 指标Map
     * @author IhaveBB
     * @date 2026/03/29
     */
    private Map<String, Object> collectMetrics(Long merchantId) {
        Map<String, Object> metrics = new HashMap<>();

        // 转化率
        RecommendOverviewResponse overview = getRecommendOverview(merchantId);
        double cvr = 0;
        if (overview.getCvr() != null) {
            try {
                cvr = Double.parseDouble(overview.getCvr().replace("%", ""));
            } catch (Exception e) {
                cvr = 0;
            }
        }
        metrics.put("cvrValue", cvr);

        // 多样性
        RecommendDiversityDTO diversity = getRecommendationDiversity(merchantId);
        metrics.put("diversityLevel", diversity.getDiversityLevel());

        // 覆盖用户数
        metrics.put("coveredUsers", overview.getCoveredUsers());

        return metrics;
    }

    /**
     * 执行规则生成建议
     */
    private List<Map<String, String>> executeRules(List<SuggestionRule> rules, Map<String, Object> metrics) {
        List<Map<String, String>> suggestions = new ArrayList<>();
        for (SuggestionRule rule : rules) {
            if (rule.isMatch(metrics)) {
                Map<String, String> suggestion = new HashMap<>();
                suggestion.put("type", rule.getType());
                suggestion.put("level", rule.getLevel());
                suggestion.put("message", rule.getMessage());
                suggestion.put("action", rule.getAction());
                suggestions.add(suggestion);
            }
        }
        // 按优先级排序：高 > 中 > 低
        suggestions.sort(Comparator.comparing(s -> {
            String level = s.get("level");
            if ("高".equals(level)) return 0;
            if ("中".equals(level)) return 1;
            return 2;
        }));
        return suggestions;
    }

    /**
     * 计算健康度评分
     */
    private double calculateHealthScore(List<ScoreFactor> factors, Map<String, Object> metrics) {
        double baseScore = ScoreConfig.BASE_SCORE; // 基础分
        for (ScoreFactor factor : factors) {
            baseScore += factor.calculateScore(metrics);
        }
        return Math.min(100, baseScore); // 最高100分
    }

    /**
     * 根据评分获取健康等级
     */
    private String getHealthLevel(double score) {
        if (score >= HealthScoreThresholds.EXCELLENT) return "优秀";
        if (score >= HealthScoreThresholds.GOOD) return "良好";
        if (score >= HealthScoreThresholds.NORMAL) return "一般";
        return "需要优化";
    }

    /**
     * 预测推荐效果（简单线性回归），支持按商户维度筛选
     *
     * @param merchantId 商户ID（可为null， 表示查询全平台）
     * @return 推荐效果预测
     * @author IhaveBB
     * @date 2026/03/29
     */
    public RecommendPredictionDTO predictNextPeriodEffect(Long merchantId) {
        RecommendPredictionDTO result = new RecommendPredictionDTO();

        LocalDateTime now = LocalDateTime.now();

        Set<Long> productIds = getMerchantProductIds(merchantId);
        List<Long> productIdList = productIds != null ? new ArrayList<>(productIds) : null;

        // 获取近7天数据
        List<Map<String, Object>> weeklyData = recommendActionMapper.countTrendByDay(
                now.minusDays(7).withHour(0).withMinute(0),
                now,
                null,
                productIdList
        );

        if (weeklyData == null || weeklyData.size() < DefaultParams.MIN_DATA_POINTS) {
            result.setPredictedCVR("数据不足，无法预测");
            result.setConfidence("低");
            result.setWeeklyData(weeklyData);
            return result;
        }

        // 简单线性回归：y = ax + b
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        int n = weeklyData.size();

        for (int i = 0; i < n; i++) {
            Map<String, Object> dayData = weeklyData.get(i);
            long exposure = ((Number) dayData.get("exposure_count")).longValue();
            long buy = ((Number) dayData.get("buy_count")).longValue();
            double cvr = exposure > 0 ? (double) buy / exposure * 100 : 0;

            sumX += i;
            sumY += cvr;
            sumXY += i * cvr;
            sumX2 += i * i;
        }

        double a = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
        double b = (sumY - a * sumX) / n;

        // 预测下期（第n+1天）
        double predictedCVR = a * n + b;
        predictedCVR = Math.max(0, Math.min(100, predictedCVR)); // 限制在0-100之间

        // 计算R²评估拟合度
        double avgY = sumY / n;
        double ssTot = 0, ssRes = 0;
        for (int i = 0; i < n; i++) {
            Map<String, Object> dayData = weeklyData.get(i);
            long exposure = ((Number) dayData.get("exposure_count")).longValue();
            long buy = ((Number) dayData.get("buy_count")).longValue();
            double actualCVR = exposure > 0 ? (double) buy / exposure * 100 : 0;
            double predicted = a * i + b;

            ssTot += Math.pow(actualCVR - avgY, 2);
            ssRes += Math.pow(actualCVR - predicted, 2);
        }
        double r2 = ssTot > 0 ? 1 - ssRes / ssTot : 0;

        String confidence;
        if (r2 > ConfidenceThresholds.HIGH) {
            confidence = "高";
        } else if (r2 > ConfidenceThresholds.MEDIUM) {
            confidence = "中";
        } else {
            confidence = "低";
        }

        result.setPredictedCVR(String.format("%.2f", predictedCVR) + "%");
        result.setConfidence(confidence);
        result.setRSquared(String.format("%.2f", r2));
        result.setTrend(a > 0.1 ? "上升" : (a < -0.1 ? "下降" : "平稳"));
        result.setWeeklyData(weeklyData);

        LOGGER.info("[效果预测] 预测转化率: {}%, 置信度: {}, 趋势: {}", predictedCVR, confidence, a > 0.1 ? "上升" : "下降");
        return result;
    }

    private long countActionByType(LocalDateTime startTime, LocalDateTime endTime, String actionType, Set<Long> productIds) {
        LambdaQueryWrapper<RecommendAction> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(RecommendAction::getCreatedAt, startTime)
               .lt(RecommendAction::getCreatedAt, endTime)
               .eq(RecommendAction::getActionType, actionType);
        if (productIds != null && !productIds.isEmpty()) {
            wrapper.in(RecommendAction::getProductId, productIds);
        }
        return recommendActionMapper.selectCount(wrapper);
    }

    private double calculateGrowth(long current, long last) {
        if (last == 0) {
            return current > 0 ? 100.0 : 0.0;
        }
        return ((current - last) / (double) last) * 100;
    }

    // ==================== 规则引擎相关类 ====================

    /**
     * 建议规则接口
     */
    interface SuggestionRule {
        String getType();
        String getLevel();
        String getMessage();
        String getAction();
        boolean isMatch(Map<String, Object> metrics);
    }

    /**
     * 评分因子接口
     */
    interface ScoreFactor {
        String getName();
        double calculateScore(Map<String, Object> metrics);
    }

    /**
     * 规则配置类
     */
    static class RuleConfig {
        double threshold;
        String type;
        String level;
        String message;
        String action;

        RuleConfig(double threshold, String type, String level, String message, String action) {
            this.threshold = threshold;
            this.type = type;
            this.level = level;
            this.message = message;
            this.action = action;
        }
    }

    /**
     * 转化率规则
     */
    static class CvrRule implements SuggestionRule {
        private final RuleConfig config;

        CvrRule(RuleConfig config) {
            this.config = config;
        }

        @Override
        public String getType() { return config.type; }

        @Override
        public String getLevel() { return config.level; }

        @Override
        public String getMessage() { return config.message; }

        @Override
        public String getAction() { return config.action; }

        @Override
        public boolean isMatch(Map<String, Object> metrics) {
            Object cvrObj = metrics.get("cvrValue");
            if (cvrObj == null) return false;
            double cvr = (double) cvrObj;
            return cvr < config.threshold;
        }
    }

    /**
     * 多样性规则
     */
    static class DiversityRule implements SuggestionRule {
        private final RuleConfig config;
        private final String targetLevel;

        DiversityRule(RuleConfig config, String targetLevel) {
            this.config = config;
            this.targetLevel = targetLevel;
        }

        @Override
        public String getType() { return config.type; }

        @Override
        public String getLevel() { return config.level; }

        @Override
        public String getMessage() { return config.message; }

        @Override
        public String getAction() { return config.action; }

        @Override
        public boolean isMatch(Map<String, Object> metrics) {
            Object levelObj = metrics.get("diversityLevel");
            return targetLevel.equals(levelObj);
        }
    }

    /**
     * 数据量规则
     */
    static class DataVolumeRule implements SuggestionRule {
        private final RuleConfig config;
        private final double maxThreshold;

        DataVolumeRule(RuleConfig config, double maxThreshold) {
            this.config = config;
            this.maxThreshold = maxThreshold;
        }

        @Override
        public String getType() { return config.type; }

        @Override
        public String getLevel() { return config.level; }

        @Override
        public String getMessage() { return config.message; }

        @Override
        public String getAction() { return config.action; }

        @Override
        public boolean isMatch(Map<String, Object> metrics) {
            Object usersObj = metrics.get("coveredUsers");
            if (usersObj == null) return false;
            long users = ((Number) usersObj).longValue();
            return users < maxThreshold;
        }
    }

    /**
     * CVR评分因子
     */
    static class CvrScoreFactor implements ScoreFactor {
        private final double excellentThreshold;
        private final double goodThreshold;
        private final double excellentScore;
        private final double goodScore;

        CvrScoreFactor(double excellentThreshold, double goodThreshold, double excellentScore, double goodScore) {
            this.excellentThreshold = excellentThreshold;
            this.goodThreshold = goodThreshold;
            this.excellentScore = excellentScore;
            this.goodScore = goodScore;
        }

        @Override
        public String getName() { return "CVR"; }

        @Override
        public double calculateScore(Map<String, Object> metrics) {
            Object cvrObj = metrics.get("cvrValue");
            if (cvrObj == null) return 0;
            double cvr = (double) cvrObj;
            if (cvr >= excellentThreshold) return excellentScore;
            if (cvr >= goodThreshold) return goodScore;
            return 0;
        }
    }

    /**
     * 多样性评分因子
     */
    static class DiversityScoreFactor implements ScoreFactor {
        private final String badLevel;
        private final double score;

        DiversityScoreFactor(String badLevel, double score) {
            this.badLevel = badLevel;
            this.score = score;
        }

        @Override
        public String getName() { return "多样性"; }

        @Override
        public double calculateScore(Map<String, Object> metrics) {
            Object levelObj = metrics.get("diversityLevel");
            if (levelObj == null) return 0;
            return badLevel.equals(levelObj) ? 0 : score;
        }
    }

    /**
     * 数据量评分因子
     */
    static class DataVolumeScoreFactor implements ScoreFactor {
        private final long threshold;
        private final double score;

        DataVolumeScoreFactor(long threshold, double score) {
            this.threshold = threshold;
            this.score = score;
        }

        @Override
        public String getName() { return "数据量"; }

        @Override
        public double calculateScore(Map<String, Object> metrics) {
            Object usersObj = metrics.get("coveredUsers");
            if (usersObj == null) return 0;
            long users = ((Number) usersObj).longValue();
            return users >= threshold ? score : 0;
        }
    }
}
