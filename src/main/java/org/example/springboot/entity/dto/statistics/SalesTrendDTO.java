package org.example.springboot.entity.dto.statistics;

import lombok.Data;
import java.io.Serializable;

/**
 * 销售趋势统计DTO
 */
@Data
public class SalesTrendDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 日期（格式：MM/dd） */
    private String date;

    /** 订单数量 */
    private Integer orderCount;

    /** 销售金额 */
    private Double salesAmount;

    /** 商品数量 */
    private Integer productCount;
}
