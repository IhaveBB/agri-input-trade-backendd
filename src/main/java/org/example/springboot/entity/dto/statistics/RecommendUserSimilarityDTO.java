package org.example.springboot.entity.dto.statistics;

import lombok.Data;
import java.io.Serializable;
import java.util.Map;

/**
 * 用户行为相似度分布DTO
 */
@Data
public class RecommendUserSimilarityDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 转化漏斗 */
    private FunnelDTO funnel;

    /** 行为深度分析 */
    private DepthAnalysisDTO depthAnalysis;

    /**
     * 转化漏斗内部DTO
     */
    @Data
    public static class FunnelDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 总用户数 */
        private Long totalUsers;

        /**有点击行为的用户数 */
        private Long usersWithClick;

        /** 有购买行为的用户数 */
        private Long usersWithBuy;

        /** 点击率 */
        private String clickRate;

        /** 购买率 */
        private String buyRate;
    }

    /**
     * 行为深度分析内部DTO
     */
    @Data
    public static class DepthAnalysisDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 总用户数 */
        private Integer totalUsers;

        /** 深度分布 */
        private Map<String, String> depthDistribution;

        /** 人均行为数 */
        private String avgActionsPerUser;
    }
}
