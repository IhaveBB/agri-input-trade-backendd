package org.example.springboot.entity.dto.statistics;

import lombok.Data;
import java.io.Serializable;

/**
 * 用户消费统计 DTO
 */
@Data
public class UserSpendingStatisticsDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 当月消费 */
    private Double currentMonthSpending;

    /** 上月消费 */
    private Double lastMonthSpending;

    /** 总消费 */
    private Double totalSpending;

    /** 增长率 */
    private String growthRate;
}
