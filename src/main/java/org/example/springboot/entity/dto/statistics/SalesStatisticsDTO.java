package org.example.springboot.entity.dto.statistics;

import lombok.Data;
import java.io.Serializable;

/**
 * 销售统计 DTO
 */
@Data
public class SalesStatisticsDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 当月销售额 */
    private Double currentMonthSales;

    /** 上月销售额 */
    private Double lastMonthSales;

    /** 增长率 */
    private String growthRate;
}
