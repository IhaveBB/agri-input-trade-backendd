package org.example.springboot.enumClass;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 推荐算法行为类型枚举
 * <p>
 * 用于定义用户与商品交互的行为类型及其权重
 * 权重设计依据：行为强度越强，权重越高
 * </p>
 *
 * @author IhaveBB
 * @date 2026/03/21
 */
@Getter
@RequiredArgsConstructor
public enum RecommendationActionType {

    /**
     * 点击/浏览行为 - 基础兴趣
     */
    CLICK("CLICK", 1, "点击/浏览"),

    /**
     * 收藏行为 - 明确兴趣
     */
    FAVORITE("FAVORITE", 2, "收藏"),

    /**
     * 加入购物车行为 - 强购买意向
     */
    CART("CART", 3, "加入购物车"),

    /**
     * 购买行为 - 最强认可
     */
    PURCHASE("PURCHASE", 5, "购买");

    // ==================== 推荐来源常量 ====================

    /** 混合推荐来源 */
    public static final String SOURCE_HYBRID = "HYBRID";

    /** 协同过滤推荐来源 */
    public static final String SOURCE_COLLABORATIVE = "COLLABORATIVE";

    /** 地域推荐来源 */
    public static final String SOURCE_LOCATION = "LOCATION";

    /** 热门商品推荐来源 */
    public static final String SOURCE_HOT = "HOT";

    /** 新品推荐来源 */
    public static final String SOURCE_NEW = "NEW";

    /** 自然流量来源 */
    public static final String SOURCE_NATURAL = "NATURAL";

    // ==================== 推荐场景常量 ====================

    /** 首页推荐场景 */
    public static final String SCENE_HOME_PAGE = "HOME_PAGE";

    /** 商品详情页推荐场景 */
    public static final String SCENE_PRODUCT_DETAIL = "PRODUCT_DETAIL";

    /** 购物车推荐场景 */
    public static final String SCENE_CART = "CART";

    /**
     * 行为类型代码
     */
    private final String code;

    /**
     * 行为权重值
     */
    private final int weight;

    /**
     * 行为描述
     */
    private final String description;

    /**
     * 根据行为代码获取枚举
     *
     * @param code 行为代码
     * @return 对应的枚举值，不存在返回 null
     */
    public static RecommendationActionType fromCode(String code) {
        for (RecommendationActionType type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 获取行为权重
     *
     * @param code 行为代码
     * @return 对应的权重值，不存在返回 0
     */
    public static int getWeight(String code) {
        RecommendationActionType type = fromCode(code);
        return type != null ? type.getWeight() : 0;
    }
}
