package org.example.springboot.entity.dto.statistics;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

/**
 * 分类推荐效果响应DTO
 */
@Data
public class RecommendCategoryEffectResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 分类效果列表 */
    private List<RecommendCategoryEffectDTO> categoryEffect;

    /** 总分类数 */
    private Integer totalCategories;
}
