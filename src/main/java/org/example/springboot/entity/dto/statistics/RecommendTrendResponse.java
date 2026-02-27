package org.example.springboot.entity.dto.statistics;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

/**
 * 推荐效果趋势响应DTO
 */
@Data
public class RecommendTrendResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 趋势数据列表 */
    private List<RecommendTrendDTO> trend;

    /** 查询天数 */
    private Integer days;

    /** 总曝光数 */
    private Long totalExposure;

    /** 总点击数 */
    private Long totalClick;

    /** 总购买数 */
    private Long totalBuy;
}
