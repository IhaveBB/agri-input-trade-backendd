package org.example.springboot.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 更新分类 DTO
 *
 * @author IhaveBB
 * @date 2026/03/21
 */
@Data
@Schema(description = "更新分类请求")
public class CategoryUpdateDTO {

    @NotBlank(message = "分类名称不能为空")
    @Schema(description = "分类名称")
    private String name;

    @Schema(description = "分类图标")
    private String icon;

    @Schema(description = "分类描述")
    private String description;

    @Schema(description = "父分类ID")
    private Long parentId;

    @Schema(description = "分类层级")
    private Integer level;

    @Schema(description = "排序字段")
    private Integer sortOrder;

    @Schema(description = "分类状态：0-禁用，1-启用")
    private Integer status;
}
