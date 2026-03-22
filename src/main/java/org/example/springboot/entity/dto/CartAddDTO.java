package org.example.springboot.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 添加购物车 DTO
 *
 * @author IhaveBB
 * @date 2026/03/21
 */
@Data
@Schema(description = "添加购物车请求")
public class CartAddDTO {

    @NotNull(message = "商品ID不能为空")
    @Schema(description = "商品ID")
    private Long productId;

    @NotNull(message = "商品数量不能为空")
    @Min(value = 1, message = "商品数量必须大于0")
    @Schema(description = "商品数量")
    private Integer quantity = 1;
}
