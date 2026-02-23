package org.example.springboot.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Schema(description = "商品创建/更新DTO")
public class ProductCreateDTO {

    // ==================== 基础信息 ====================
    @NotBlank(message = "商品名称不能为空")
    @Schema(description = "商品名称")
    private String name;

    @Schema(description = "商品描述")
    private String description;

    @NotNull(message = "价格不能为空")
    @PositiveOrZero(message = "价格不能为负数")
    @Schema(description = "商品价格")
    private BigDecimal price;

    @NotNull(message = "库存不能为空")
    @PositiveOrZero(message = "库存不能为负数")
    @Schema(description = "库存数量")
    private Integer stock;

    @Schema(description = "是否开启折扣：0-否，1-是")
    private Integer isDiscount;

    @Schema(description = "折扣价格")
    private BigDecimal discountPrice;

    @NotNull(message = "分类ID不能为空")
    @Schema(description = "分类ID")
    private Long categoryId;

    @Schema(description = "商品图片URL")
    private String imageUrl;

    @Schema(description = "产地")
    private String placeOfOrigin;

    // ==================== 扩展属性（Map格式） ====================
    @Schema(description = "扩展属性Map")
    private Map<String, Object> extraAttributes;

    // ==================== 推荐相关字段 ====================
    @Schema(description = "适用作物分类ID列表（农药/肥料用，对应category表的种子分类）")
    private List<Long> categoryIds;

    @Schema(description = "区域-季节配置列表（种子用）")
    private List<RegionSeasonConfigDTO> regionSeasonConfigs;

    @Data
    @Schema(description = "区域-季节配置")
    public static class RegionSeasonConfigDTO {
        @Schema(description = "区域ID")
        private Long regionId;

        @Schema(description = "季节ID列表")
        private List<Long> seasonIds;
    }
}
