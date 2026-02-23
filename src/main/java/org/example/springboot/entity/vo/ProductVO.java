package org.example.springboot.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Schema(description = "商品详情VO")
public class ProductVO {

    @Schema(description = "商品ID")
    private Long id;

    @Schema(description = "商品名称")
    private String name;

    @Schema(description = "商品描述")
    private String description;

    @Schema(description = "商品价格")
    private BigDecimal price;

    @Schema(description = "库存数量")
    private Integer stock;

    @Schema(description = "是否开启折扣")
    private Integer isDiscount;

    @Schema(description = "折扣价格")
    private BigDecimal discountPrice;

    @Schema(description = "分类ID")
    private Long categoryId;

    @Schema(description = "分类名称")
    private String categoryName;

    @Schema(description = "商品图片URL")
    private String imageUrl;

    @Schema(description = "销量")
    private Integer salesCount;

    @Schema(description = "商户ID")
    private Long merchantId;

    @Schema(description = "商户名称")
    private String merchantName;

    @Schema(description = "商品状态")
    private Integer status;

    @Schema(description = "产地")
    private String placeOfOrigin;

    @Schema(description = "扩展属性Map")
    private Map<String, Object> extraAttributes;

    @Schema(description = "适用作物列表（对应category表的种子分类）")
    private List<CropVO> crops;

    @Schema(description = "区域-季节配置列表")
    private List<ProductRegionSeasonVO> regionSeasonList;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;

    // ==================== 嵌套VO ====================

    @Data
    @Schema(description = "作物VO")
    public static class CropVO {
        private Long id;
        private String name;
        private Long parentId;
    }

    @Data
    @Schema(description = "商品区域季节VO")
    public static class ProductRegionSeasonVO {
        private Long id;
        private Long regionId;
        private String regionName;
        private Long seasonId;
        private String seasonName;
    }
}
