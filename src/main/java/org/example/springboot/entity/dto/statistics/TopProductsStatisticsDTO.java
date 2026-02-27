package org.example.springboot.entity.dto.statistics;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

/**
 * 热销商品统计 DTO
 */
@Data
public class TopProductsStatisticsDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 热销商品列表 */
    private List<TopProductDTO> topProducts;

    /** 商品总数 */
    private Integer total;

    /** 总销售额 */
    private Double totalSalesAmount;

    /**
     * 单个商品信息
     */
    @Data
    public static class TopProductDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 商品 ID */
        private Long id;

        /** 商品名称 */
        private String name;

        /** 销量 */
        private Integer salesCount;

        /** 销售额 */
        private Double salesAmount;
    }
}
