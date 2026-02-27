package org.example.springboot.entity.dto.statistics;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

/**
 * 地区销售统计响应DTO
 */
@Data
public class RegionSalesResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 地区统计数据列表 */
    private List<RegionSalesDTO> regionStats;

    /** 地区数量 */
    private Integer totalRegions;

    /** 总订单数 */
    private Integer totalOrders;

    /** 总销售额 */
    private Double totalSales;
}
