package org.example.springboot.entity.dto.statistics;

import lombok.Data;
import java.io.Serializable;

/**
 * 推荐效果概览DTO
 */
@Data
public class RecommendOverviewDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 曝光数 */
    private Long exposureCount;

    /** 点击数 */
    private Long clickCount;

    /** 购买数 */
    private Long buyCount;

    /** 点击率 */
    private String ctr;

    /** 转化率 */
    private String cvr;

    /** 曝光增长率 */
    private String exposureGrowth;

    /** 点击增长率 */
    private String clickGrowth;

    /** 购买增长率 */
    private String buyGrowth;

    /** 覆盖用户数 */
    private Long coveredUsers;
}
