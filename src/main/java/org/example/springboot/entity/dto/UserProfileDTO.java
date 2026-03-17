package org.example.springboot.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 用户画像 DTO
 *
 * @author agri-input-trade
 * @version 1.0
 */
@Data
@Schema(description = "用户画像")
public class UserProfileDTO {

    @Schema(description = "用户 ID")
    private Long userId;

    @Schema(description = "消费能力等级：LOW/MEDIUM/HIGH")
    private String consumptionLevel;

    @Schema(description = "偏好品类分布 Map<categoryId, weight>")
    private List<CategoryPreferenceDTO> categoryPreferences;

    @Schema(description = "用户所在地区 ID")
    private Long regionId;

    @Schema(description = "用户所在地区名称")
    private String regionName;

    @Schema(description = "用户所在省份")
    private String province;

    @Schema(description = "用户所在城市")
    private String city;

    @Schema(description = "价格敏感度：LOW/MEDIUM/HIGH")
    private String priceSensitivity;

    @Schema(description = "平均订单金额")
    private Double avgOrderAmount;

    @Schema(description = "总购买次数")
    private Integer totalPurchases;

    @Schema(description = "偏好作物 ID 列表（基于购买历史统计）")
    private List<Long> preferredCropIds;

    @Schema(description = "偏好作物名称列表")
    private List<String> preferredCropNames;
}

