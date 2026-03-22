package org.example.springboot.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建收藏 DTO
 *
 * @author IhaveBB
 * @date 2026/03/21
 */
@Data
@Schema(description = "创建收藏请求")
public class FavoriteCreateDTO {

    @NotNull(message = "商品ID不能为空")
    @Schema(description = "商品ID")
    private Long productId;
}
