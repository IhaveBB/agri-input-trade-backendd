package org.example.springboot.entity.dto.statistics;

import lombok.Data;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 智能优化建议DTO
 */
@Data
public class RecommendOptimizationDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 健康度评分 */
    private String healthScore;

    /** 健康等级 */
    private String healthLevel;

    /** 建议列表 */
    private List<Map<String, String>> suggestions;

    /** 分析时间 */
    private String analyzedAt;
}
