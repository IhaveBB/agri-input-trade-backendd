package org.example.springboot.entity.dto.shop;

import lombok.Data;
import java.io.Serializable;

/**
 * 店铺统计信息 DTO
 */
@Data
public class ShopStatisticsDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 店铺 ID */
    private Long shopId;

    /** 评分总数 */
    private Double totalRating;

    /** 评价总数 */
    private Long reviewCount;

    /** 好评数（4-5 星） */
    private Long goodReviewCount;

    /** 中评数（3 星） */
    private Long mediumReviewCount;

    /** 差评数（1-2 星） */
    private Long badReviewCount;

    /** 平均评分 */
    private Double averageRating;

    /** 好评率 */
    private String positiveRate;
}
