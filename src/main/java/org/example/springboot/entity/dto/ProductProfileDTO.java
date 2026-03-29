package org.example.springboot.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 商品画像 DTO
 *
 * @author IhaveBB
 * @date 2026/03/21
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

    @Schema(description = "是否为种子类商品")
    private Boolean isSeed;

    @Schema(description = "适用作物 ID 列表")
    private java.util.List<Long> cropIds;

    @Schema(description = "适用作物名称列表")
    private java.util.List<String> cropNames;

    @Schema(description = "适用动物 ID 列表")
    private java.util.List<Long> animalIds;

    @Schema(description = "适用动物名称列表")
    private java.util.List<String> animalNames;

    @Schema(description = "一级分类 ID")
    private Long topCategoryId;

    @Schema(description = "区域-季节配对列表（保持 product_region_season 的对应关系）")
    private java.util.List<RegionSeasonPair> regionSeasonPairs;

    /**
     * 区域-季节配对，保持商家上架时配置的对应关系
     * <p>
     * 例如：华北→春季, 华南→夏季 表示该种子在华北适合春季种植、在华南适合夏季种植。
     * 推荐时先匹配区域，再在该区域对应的季节中匹配当前/上一季节，避免区域和季节独立判断。
     * </p>
     *
     * @author IhaveBB
     * @date 2026/03/29
     */
    @Data
    @Schema(description = "区域-季节配对")
    public static class RegionSeasonPair {

        @Schema(description = "区域 ID")
        private Long regionId;

        @Schema(description = "季节 ID")
        private Long seasonId;

        public RegionSeasonPair() {}

        public RegionSeasonPair(Long regionId, Long seasonId) {
            this.regionId = regionId;
            this.seasonId = seasonId;
        }
    }
}
