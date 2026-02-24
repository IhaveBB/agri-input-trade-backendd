package org.example.springboot.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 用户位置更新DTO
 */
@Data
@Schema(description = "用户位置更新请求")
public class UserLocationDTO {

    @NotBlank(message = "位置信息不能为空")
    @Schema(description = "位置信息，格式：省-市 或 省-市-区", example = "广东省-深圳市-南山区")
    private String location;
}
