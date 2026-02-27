package org.example.springboot.entity.dto.statistics;

import lombok.Data;
import java.io.Serializable;

/**
 * 用户订单统计 DTO
 */
@Data
public class UserOrderStatisticsDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 当月订单数 */
    private Long currentMonthOrders;

    /** 上月订单数 */
    private Long lastMonthOrders;

    /** 总订单数 */
    private Long totalOrders;

    /** 增长率 */
    private String growthRate;
}
