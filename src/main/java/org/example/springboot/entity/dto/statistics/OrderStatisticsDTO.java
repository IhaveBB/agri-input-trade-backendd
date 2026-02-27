package org.example.springboot.entity.dto.statistics;

import lombok.Data;
import java.io.Serializable;

/**
 * 订单统计 DTO
 */
@Data
public class OrderStatisticsDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 当月订单数 */
    private Long currentMonthOrders;

    /** 上月订单数 */
    private Long lastMonthOrders;

    /** 增长率 */
    private String growthRate;
}
