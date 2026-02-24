package org.example.springboot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 推荐系统配置类
 */
@Data
@Component
@ConfigurationProperties(prefix = "recommend.location-weight")
public class RecommendLocationConfig {

    /**
     * 同市权重
     */
    private double sameCity = 1.5;

    /**
     * 同省权重
     */
    private double sameProvince = 1.2;

    /**
     * 同区域权重
     */
    private double sameRegion = 1.1;

    /**
     * 默认权重
     */
    private double defaultWeight = 1.0;
}
