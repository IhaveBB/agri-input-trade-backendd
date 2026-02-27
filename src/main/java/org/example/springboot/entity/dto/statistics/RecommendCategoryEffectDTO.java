package org.example.springboot.entity.dto.statistics;

import lombok.Data;
import java.io.Serializable;

/**
 * 分类推荐效果DTO
 */
@Data
public class RecommendCategoryEffectDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 分类ID */
    private Long categoryId;

    /** 分类名称 */
    private String categoryName;

    /** 曝光数 */
    private Long exposureCount;

    /** 点击数 */
    private Long clickCount;

    /** 购买数 */
    private Long buyCount;

    /** 点击率 */
    private String ctr;

    /** 转化率 */
    private String cvr;
}
