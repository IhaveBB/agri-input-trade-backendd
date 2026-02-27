package org.example.springboot.entity.dto.statistics;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

/**
 * 品类销售统计响应 DTO
 */
@Data
public class CategorySalesStatisticsResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 品类统计列表 */
    private List<CategoryStatsDTO> categoryStats;

    /** 品类总数 */
    private Integer total;

    /** 总销量 */
    private Integer totalSales;

    /** 总销售额 */
    private Double totalSalesAmount;

    /**
     * 品类统计 DTO
     */
    @Data
    public static class CategoryStatsDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 品类 ID */
        private Long categoryId;

        /** 品类名称 */
        private String categoryName;

        /** 销量 */
        private Integer salesCount;

        /** 销售额 */
        private Double salesAmount;

        /** 销量占比 */
        private String countPercentage;

        /** 销售额占比 */
        private String amountPercentage;
    }
}
