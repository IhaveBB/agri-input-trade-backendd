package org.example.springboot.enumClass;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 推荐系统常量类
 */
public final class RecommendConstants {

    private RecommendConstants() {
        throw new IllegalStateException("常量类不允许实例化");
    }

    /**
     * 省份到大区的映射
     */
    public static final Map<String, String> PROVINCE_REGION_MAP;

    static {
        Map<String, String> map = new HashMap<>(40);
        // 华北
        map.put("北京", "华北");
        map.put("天津", "华北");
        map.put("河北", "华北");
        map.put("山西", "华北");
        map.put("内蒙古", "华北");
        // 东北
        map.put("辽宁", "东北");
        map.put("吉林", "东北");
        map.put("黑龙江", "东北");
        // 华东
        map.put("上海", "华东");
        map.put("江苏", "华东");
        map.put("浙江", "华东");
        map.put("安徽", "华东");
        map.put("福建", "华东");
        map.put("江西", "华东");
        map.put("山东", "华东");
        // 华中
        map.put("河南", "华中");
        map.put("湖北", "华中");
        map.put("湖南", "华中");
        // 华南
        map.put("广东", "华南");
        map.put("广西", "华南");
        map.put("海南", "华南");
        // 西南
        map.put("重庆", "西南");
        map.put("四川", "西南");
        map.put("贵州", "西南");
        map.put("云南", "西南");
        map.put("西藏", "西南");
        // 西北
        map.put("陕西", "西北");
        map.put("甘肃", "西北");
        map.put("青海", "西北");
        map.put("宁夏", "西北");
        map.put("新疆", "西北");
        // 港澳台
        map.put("香港", "港澳台");
        map.put("澳门", "港澳台");
        map.put("台湾", "港澳台");
        PROVINCE_REGION_MAP = Collections.unmodifiableMap(map);
    }

    /**
     * 位置匹配级别
     */
    public enum LocationLevel {
        /**
         * 同市
         */
        SAME_CITY,
        /**
         * 同省
         */
        SAME_PROVINCE,
        /**
         * 同区域
         */
        SAME_REGION,
        /**
         * 不同区域
         */
        DIFFERENT_REGION
    }

    /**
     * 相似度阈值常量
     */
    public static final class SimilarityThreshold {
        private SimilarityThreshold() {
            throw new IllegalStateException("常量类不允许实例化");
        }

        /**
         * 新用户阈值
         */
        public static final double NEW_USER = 0.2;

        /**
         * 活跃用户阈值
         */
        public static final double ACTIVE_USER = 0.4;

        /**
         * 普通用户阈值
         */
        public static final double NORMAL_USER = 0.3;

        /**
         * 最小共同商品数阈值（皮尔逊相关系数需要）
         * 共同商品少于该值，相似度不可靠，返回0
         */
        public static final int MIN_COMMON_ITEMS = 2;
    }

    /**
     * 行为权重常量
     */
    public static final class BehaviorWeight {
        private BehaviorWeight() {
            throw new IllegalStateException("常量类不允许实例化");
        }

        /**
         * 购买行为权重
         */
        public static final double PURCHASE = 2.0;

        /**
         * 收藏行为权重
         */
        public static final double FAVORITE = 1.0;
    }

    /**
     * 推荐数量限制
     */
    public static final class RecommendLimit {
        private RecommendLimit() {
            throw new IllegalStateException("常量类不允许实例化");
        }

        /**
         * 最大推荐商品数量
         */
        public static final int MAX_RECOMMEND_COUNT = 12;

        /**
         * 最大相似用户数量
         */
        public static final int MAX_SIMILAR_USERS = 10;
    }
}
