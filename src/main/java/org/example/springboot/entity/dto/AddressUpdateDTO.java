package org.example.springboot.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 更新地址 DTO
 * 只暴露用户可以修改的字段
 *
 * @author IhaveBB
 * @date 2026/03/21
 */
@Data
@Schema(description = "更新地址请求")
public class AddressUpdateDTO {

    @NotBlank(message = "收货人姓名不能为空")
    @Schema(description = "收货人姓名")
    private String receiver;

    @NotBlank(message = "联系电话不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Schema(description = "联系电话")
    private String phone;

    @NotBlank(message = "详细地址不能为空")
    @Schema(description = "详细地址")
    private String address;
}
