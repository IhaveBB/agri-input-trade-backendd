package org.example.springboot.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 更新订单收货地址 DTO
 *
 * @author IhaveBB
 * @date 2026/03/21
 */
@Data
@Schema(description = "更新订单收货地址请求")
public class OrderAddressUpdateDTO {

    @NotBlank(message = "收货人姓名不能为空")
    @Schema(description = "收货人姓名")
    private String recvName;

    @NotBlank(message = "联系电话不能为空")
    @Schema(description = "联系电话")
    private String recvPhone;

    @NotBlank(message = "收货地址不能为空")
    @Schema(description = "收货地址")
    private String recvAddress;
}
