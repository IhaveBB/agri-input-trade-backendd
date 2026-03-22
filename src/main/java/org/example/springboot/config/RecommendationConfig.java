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

    // ==================== 消费能力分档阈值 ====================

    /**
     * 高消费判定阈值（avgAmount >= 该值 → HIGH）
     */
    private Integer highConsumptionThreshold = 500;

    /**
     * 中消费判定阈值（avgAmount >= 该值 → MEDIUM）
     */
    private Integer mediumConsumptionThreshold = 200;

    // ==================== 价格区间分档阈值 ====================

    /**
     * 高价格区间阈值（price >= 该值 → HIGH）
     */
    private Integer highPriceThreshold = 300;

    /**
     * 中价格区间阈值（price >= 该值 → MEDIUM）
     */
    private Integer mediumPriceThreshold = 100;

    /**
     * 热销商品销量阈值（salesCount >= 该值 → isHot=true）
     */
    private Integer hotSalesThreshold = 100;

    // ==================== 画像匹配维度权重 ====================

    /**
     * 品类偏好匹配权重（0-1，三维权重合计需为1.0）
     */
    private Double categoryWeight = 0.4;

    /**
     * 价格区间匹配权重
     */
    private Double priceWeight = 0.3;

    /**
     * 适用作物匹配权重（农资电商特有维度）
     */
    private Double cropWeight = 0.3;

    // ==================== 价格匹配分数 ====================

    /**
     * 价格邻档匹配得分（HIGH→MEDIUM 或 MEDIUM→LOW 视为近似匹配）
     */
    private Double priceNearMatchScore = 0.7;

    /**
     * 价格跨档匹配得分（LOW→MEDIUM 或 MEDIUM→HIGH）
     */
    private Double priceFarMatchScore = 0.3;

    /**
     * 价格极端不匹配得分（HIGH→LOW 或 LOW→HIGH）
     */
    private Double priceNoMatchScore = 0.1;

    // ==================== 作物匹配分数 ====================

    /**
     * 作物完全不匹配时的得分
     */
    private Double cropNoMatchScore = 0.1;

    /**
     * 作物匹配公式基础分（finalCropScore = cropBaseScore + matchRatio * cropMatchMultiplier）
     */
    private Double cropBaseScore = 0.3;

    /**
     * 作物匹配比例乘数
     */
    private Double cropMatchMultiplier = 0.7;

    // ==================== 推荐文案生成阈值 ====================

    /**
     * CF/画像得分的高分阈值（>= 该值生成强推荐文案）
     */
    private Double cfHighScoreThreshold = 0.7;

    /**
     * CF/画像得分的中分阈值（>= 该值生成普通推荐文案）
     */
    private Double cfMediumScoreThreshold = 0.5;
}
