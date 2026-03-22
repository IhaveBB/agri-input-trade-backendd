package org.example.springboot.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 创建订单 DTO
 *
 * @author IhaveBB
 * @date 2026/03/21
 */
@Data
@Schema(description = "创建订单请求")
public class OrderCreateDTO {

    @NotNull(message = "商品ID不能为空")
    @Schema(description = "商品ID")
    private Long productId;

    @NotNull(message = "购买数量不能为空")
    @Min(value = 1, message = "购买数量必须大于0")
    @Schema(description = "购买数量")
    private Integer quantity;

    @NotNull(message = "商品单价不能为空")
    @DecimalMin(value = "0.01", message = "商品单价必须大于0")
    @Schema(description = "商品单价")
    private BigDecimal price;

    @Schema(description = "订单备注")
    private String remark;

    @Schema(description = "收货人姓名")
    private String recvName;

    @Schema(description = "收货电话")
    private String recvPhone;

    @Schema(description = "收货地址")
    private String recvAddress;
}
