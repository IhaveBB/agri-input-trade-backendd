package org.example.springboot.entity.dto.statistics;

import lombok.Data;
import java.io.Serializable;

/**
 * 季节性销售统计DTO
 */
@Data
public class SeasonalSalesDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 季节名称（春季/夏季/秋季/冬季） */
    private String season;

    /** 月份（1-12） */
    private Integer month;

    /** 月份名称 */
    private String monthName;

    /** 订单数量 */
    private Integer orderCount;

    /** 销售金额 */
    private Double salesAmount;

    /** 商品数量 */
    private Integer productCount;
}
