package org.example.springboot.entity.dto.statistics;

import lombok.Data;
import java.io.Serializable;

/**
 * 年度用户统计 DTO
 */
@Data
public class YearlyUserStatisticsDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 当年新增用户数 */
    private Long currentYearUsers;

    /** 上年新增用户数 */
    private Long lastYearUsers;

    /** 增长率 */
    private String growthRate;
}
