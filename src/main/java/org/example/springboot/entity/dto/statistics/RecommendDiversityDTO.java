package org.example.springboot.entity.dto.statistics;

import lombok.Data;
import java.io.Serializable;
import java.util.Map;

/**
 * 推荐多样性指标DTO
 */
@Data
public class RecommendDiversityDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 信息熵 */
    private String entropy;

    /** 最大熵 */
    private String maxEntropy;

    /** 归一化多样性 */
    private String normalizedDiversity;

    /** 多样性等级 */
    private String diversityLevel;

    /** 分类数量 */
    private Integer categoryCount;

    /** 总曝光数 */
    private Long totalExposure;

    /** 分类分布 */
    private Map<String, String> categoryDistribution;
}
