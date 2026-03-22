package org.example.springboot.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建评价 DTO
 *
 * @author IhaveBB
 * @date 2026/03/21
 */
@Data
@Schema(description = "创建评价请求")
public class ReviewCreateDTO {

    @NotNull(message = "商品ID不能为空")
    @Schema(description = "商品ID")
    private Long productId;

    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分最小为1")
    @Max(value = 5, message = "评分最大为5")
    @Schema(description = "评分（1-5）")
    private Integer rating;

    @Size(max = 500, message = "评价内容不能超过500字")
    @Schema(description = "评价内容")
    private String content;
}
