package org.example.springboot.entity.dto.statistics;

import lombok.Data;
import java.io.Serializable;

/**
 * 推荐效果趋势DTO
 */
@Data
public class RecommendTrendDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 日期 */
    private String date;

    /** 曝光数 */
    private Long exposureCount;

    /** 点击数 */
    private Long clickCount;

    /** 购买数 */
    private Long buyCount;
}
