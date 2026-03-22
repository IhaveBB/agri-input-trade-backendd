package org.example.springboot.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建分类 DTO
 *
 * @author IhaveBB
 * @date 2026/03/21
 */
@Data
@Schema(description = "创建分类请求")
public class CategoryCreateDTO {

    @NotBlank(message = "分类名称不能为空")
    @Schema(description = "分类名称")
    private String name;

    @Schema(description = "分类图标")
    private String icon;

    @Schema(description = "分类描述")
    private String description;

    @Schema(description = "父分类ID，顶级分类传0或不传")
    private Long parentId;

    @Schema(description = "分类层级：1-一级分类，2-二级分类，3-三级分类")
    private Integer level;

    @Schema(description = "排序字段，数字越小越靠前")
    private Integer sortOrder;

    @Schema(description = "分类状态：0-禁用，1-启用")
    private Integer status;
}
