package org.example.springboot.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import lombok.Data;

/**
 * 用户更新 DTO
 * 普通用户只能修改部分信息，角色字段由后端控制
 *
 * @author IhaveBB
 * @date 2026/03/21
 */
@Data
@Schema(description = "用户更新请求")
public class UserUpdateDTO {

    @Schema(description = "真实姓名")
    private String name;

    @Email(message = "邮箱格式不正确")
    @Schema(description = "电子邮箱")
    private String email;

    @Schema(description = "位置信息")
    private String location;

    @Schema(description = "感兴趣作物/动物分类ID列表，逗号分隔")
    private String interestedCrops;

    @Schema(description = "账号状态（仅管理员可修改）")
    private Integer status;
}
