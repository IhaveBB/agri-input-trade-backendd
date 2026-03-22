package org.example.springboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库存预警配置实体（统一表，支持商户全局配置和商品配置）
 *
 * @author IhaveBB
 * @date 2026/03/22
 */
@Data
@TableName("stock_alert_config")
public class StockAlertConfig {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 配置类型：MERCHANT-商户全局配置, PRODUCT-商品配置
     */
    private String configType;

    /**
     * 商户ID（必须）
     */
    private Long merchantId;

    /**
     * 商品ID（商品配置时有值，商户全局配置为NULL）
     */
    private Long productId;

    /**
     * 是否启用预警
     */
    private Boolean enabled;

    /**
     * 规则配置JSON
     * 例如：{"ruleType":"THRESHOLD","thresholdValue":10}
     */
    private String ruleConfig;

    /**
     * 预警间隔（小时），默认24小时
     */
    private Integer alertIntervalHours;

    /**
     * 是否启用重复提醒（库存未恢复时持续提醒）
     */
    private Boolean repeatAlertEnabled;

    /**
     * 上次预警时间
     */
    private LocalDateTime lastAlertTime;

    /**
     * 上次检查时的库存值（用于判断是否已补货）
     */
    private Integer lastStockValue;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
