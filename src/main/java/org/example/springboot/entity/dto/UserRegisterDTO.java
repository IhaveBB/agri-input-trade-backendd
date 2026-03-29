package org.example.springboot.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 用户注册 DTO
 *
 * @author IhaveBB
 * @date 2026/03/21
 */
@Data
@Schema(description = "用户注册请求")
public class UserRegisterDTO {

    @NotBlank(message = "用户名不能为空")
    @Schema(description = "用户名")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Schema(description = "密码")
    private String password;

    @Schema(description = "真实姓名")
    private String name;

    @Schema(description = "用户角色")
    private String role;

    @Email(message = "邮箱格式不正确")
    @Schema(description = "电子邮箱")
    private String email;

    @Schema(description = "位置信息")
    private String location;

    @Schema(description = "感兴趣作物分类ID列表，逗号分隔")
    private String interestedCrops;

    @Schema(description = "感兴趣动物分类ID列表，逗号分隔")
    private String interestedAnimals;
}
