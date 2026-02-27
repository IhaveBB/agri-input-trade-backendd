package org.example.springboot.entity.dto.statistics;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

/**
 * 销售趋势响应DTO
 */
@Data
public class SalesTrendResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 趋势数据列表 */
    private List<SalesTrendDTO> trend;

    /** 查询天数 */
    private Integer days;

    /** 总订单数 */
    private Integer totalOrders;

    /** 总销售额 */
    private Double totalSales;
}
