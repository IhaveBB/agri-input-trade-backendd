package org.example.springboot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 推荐算法配置参数
 * <p>
 * 集中管理推荐算法的可配置参数，支持通过 application.yml 动态调整
 * </p>
 *
 * @author IhaveBB
 * @date 2026/03/21
 */
@Data
@Component
@ConfigurationProperties(prefix = "recommendation.algorithm")
public class RecommendationConfig {

    // ==================== 融合策略参数 ====================

    /**
     * 融合权重参数 theta (0-1)
     * theta -> 1 : 偏向协同过滤
     * theta -> 0 : 偏向画像匹配
     * 推荐值：0.7
     */
    private Double theta = 0.7;

    // ==================== 物品相似度参数 ====================

    /**
     * Top-K 相似物品数量
     * 每个商品保留的相似商品数量上限
     */
    private Integer topK = 50;

    /**
     * 相似度阈值 delta
     * 低于该阈值的相似度将被过滤
     */
    private Double similarityThreshold = 0.1;

    // ==================== 推荐结果参数 ====================

    /**
     * Top-N 推荐数量
     * 每个用户推荐的商品数量上限
     */
    private Integer topN = 10;

    // ==================== 行为权重参数 ====================

    /**
     * 浏览（点击）行为权重
     * 用户点击查看商品详情，信号强度最弱
     */
    private Integer clickWeight = 1;

    /**
     * 收藏行为权重
     */
    private Integer favoriteWeight = 2;

    /**
     * 加入购物车权重
     */
    private Integer cartWeight = 3;

    /**
     * 购买行为权重
     */
    private Integer purchaseWeight = 5;

    /**
     * 评分行为权重基数
     * 实际权重 = rating(1-5) × reviewWeight，高分评价贡献更强正向信号
     */
    private Integer reviewWeight = 1;

    // ==================== 缓存参数 ====================

    /**
     * 相似度矩阵缓存过期时间（秒）
     * 默认 1 小时
     */
    private Long similarityCacheExpireSeconds = 3600L;

    /**
     * 用户画像缓存过期时间（秒）
     * 默认 30 分钟
     */
    private Long userProfileCacheExpireSeconds = 1800L;

    // ==================== 业务规则参数 ====================

    /**
     * 是否启用地区约束
     * true: 只推荐用户地区适用的商品
     */
    private Boolean enableRegionConstraint = true;

    /**
     * 是否启用季节约束
     * true: 只推荐当前季节适用的商品
     */
    private Boolean enableSeasonConstraint = true;

    /**
     * 是否启用价格区间匹配
     * true: 根据用户消费能力推荐对应价格区间的商品
     */
    private Boolean enablePriceConstraint = false;

    // ==================== 冷启动参数 ====================

    /**
     * 新品天数阈值
     * 上架时间小于该天数的商品被视为新品
     */
    private Integer newProductDaysThreshold = 30;

    /**
     * 新品购买数量阈值
     * 销量达到该数量的商品将移出新品专区
     */
    private Integer newProductSalesThreshold = 10;

    /**
     * 新品推荐数量
     */
    private Integer newProductRecommendCount = 8;
}
