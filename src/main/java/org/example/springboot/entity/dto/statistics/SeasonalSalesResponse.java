package org.example.springboot.entity.dto.statistics;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

/**
 * 季节性销售统计响应DTO
 */
@Data
public class SeasonalSalesResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 季节统计数据列表 */
    private List<SeasonalSalesDTO> seasonStats;

    /** 月度统计数据列表 */
    private List<SeasonalSalesDTO> monthStats;

    /** 总订单数 */
    private Integer totalOrders;

    /** 总销售额 */
    private Double totalSales;
}
