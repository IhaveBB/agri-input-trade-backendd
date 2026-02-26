package org.example.springboot.enumClass;

/**
 * 推荐行为类型枚举
 */
public class RecommendActionType {

    /**
     * 行为类型
     */
    public static final String EXPOSURE = "EXPOSURE";
    public static final String CLICK = "CLICK";
    public static final String COLLECT = "COLLECT";
    public static final String CART = "CART";
    public static final String BUY = "BUY";

    /**
     * 推荐来源
     */
    public static final String SOURCE_COLLABORATIVE = "COLLABORATIVE";
    public static final String SOURCE_LOCATION = "LOCATION";
    public static final String SOURCE_HOT = "HOT";
    public static final String SOURCE_HYBRID = "HYBRID";
    public static final String SOURCE_NATURAL = "NATURAL";

    /**
     * 推荐场景
     */
    public static final String SCENE_HOME_PAGE = "HOME_PAGE";
    public static final String SCENE_PRODUCT_DETAIL = "PRODUCT_DETAIL";
    public static final String SCENE_CART_PAGE = "CART_PAGE";
    public static final String SCENE_SEARCH_RESULT = "SEARCH_RESULT";
}
