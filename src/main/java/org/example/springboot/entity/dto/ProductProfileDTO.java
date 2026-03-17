package org.example.springboot.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 商品画像 DTO
 *
 * @author agri-input-trade
 * @version 1.0
 */
@Data
@Schema(description = "商品画像")
public class ProductProfileDTO {

    @Schema(description = "商品 ID")
    private Long productId;

    @Schema(description = "商品名称")
    private String productName;

    @Schema(description = "分类 ID")
    private Long categoryId;

    @Schema(description = "分类名称")
    private String categoryName;

    @Schema(description = "价格区间：LOW/MEDIUM/HIGH")
    private String priceRange;

    @Schema(description = "实际价格")
    private Double price;

    @Schema(description = "适用地区 ID 列表")
    private java.util.List<Long> regionIds;

    @Schema(description = "适用地区名称列表")
    private java.util.List<String> regionNames;

    @Schema(description = "适用季节 ID 列表")
    private java.util.List<Long> seasonIds;

    @Schema(description = "适用季节名称列表")
    private java.util.List<String> seasonNames;

    @Schema(description = "销量")
    private Integer salesCount;

    @Schema(description = "是否热销：true/false")
    private Boolean isHot;

    @Schema(description = "适用作物 ID 列表")
    private java.util.List<Long> cropIds;

    @Schema(description = "适用作物名称列表")
    private java.util.List<String> cropNames;
}
