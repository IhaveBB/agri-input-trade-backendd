package org.example.springboot.entity.dto.statistics;

import lombok.Data;
import java.io.Serializable;
import java.util.Map;

/**
 * 推荐算法构成响应DTO
 */
@Data
public class RecommendAlgorithmResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 算法构成（算法名 -> 占比） */
    private Map<String, String> composition;

    /** 总行为数 */
    private Long total;
}
