package org.example.springboot.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 推荐结果 DTO
 *
 * @author IhaveBB
 * @date 2026/03/21
 */
@Data
@Schema(description = "推荐结果")
public class RecommendationResultDTO {

    @Schema(description = "商品 ID")
    private Long productId;

    @Schema(description = "商品名称")
    private String productName;

    @Schema(description = "商品价格")
    private Double price;

    @Schema(description = "商品图片 URL")
    private String imageUrl;

    @Schema(description = "分类 ID")
    private Long categoryId;

    @Schema(description = "分类名称")
    private String categoryName;

    @Schema(description = "推荐得分")
    private Double score;

    @Schema(description = "协同过滤得分")
    private Double cfScore;

    @Schema(description = "画像匹配得分")
    private Double profileScore;

    @Schema(description = "推荐原因")
    private String reason;

    @Schema(description = "匹配标签列表")
    private List<String> matchTags;

    @Schema(description = "销量")
    private Integer salesCount;
}
