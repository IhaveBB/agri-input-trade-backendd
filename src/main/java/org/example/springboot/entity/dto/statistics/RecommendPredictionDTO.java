package org.example.springboot.entity.dto.statistics;

import lombok.Data;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 推荐效果预测DTO
 */
@Data
public class RecommendPredictionDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 预测转化率 */
    private String predictedCVR;

    /** 置信度 */
    private String confidence;

    /** R²值 */
    private String rSquared;

    /** 趋势（上升/下降/平稳） */
    private String trend;

    /** 近7天数据 */
    private List<Map<String, Object>> weeklyData;
}
