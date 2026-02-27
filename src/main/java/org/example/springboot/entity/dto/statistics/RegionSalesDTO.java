package org.example.springboot.entity.dto.statistics;

import lombok.Data;
import java.io.Serializable;

/**
 * 地区销售统计DTO
 */
@Data
public class RegionSalesDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 地区/省份 */
    private String region;

    /** 订单数量 */
    private Integer orderCount;

    /** 销售金额 */
    private Double salesAmount;

    /** 商品数量 */
    private Integer productCount;

    /** 用户数量 */
    private Integer userCount;

    /** 占比 */
    private String percentage;
}
