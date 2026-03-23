package org.example.springboot.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.sql.Timestamp;
import java.util.List;

@Data
@Schema(description = "商品分类实体")
public class Category {
    @TableId(type = IdType.AUTO)
    @Schema(description = "分类ID")
    private Long id;

    @NotBlank(message = "分类名称不能为空")
    @Schema(description = "分类名称")
    private String name;
    @Schema(description = "分类图标")
    private String icon;

    @Schema(description = "分类描述")
    private String description;

    @Schema(description = "父分类ID，顶级分类为0或null")
    private Long parentId;

    @Schema(description = "分类层级：1-一级分类，2-二级分类，3-三级分类，4-四级分类")
    private Integer level;

    @Schema(description = "排序字段，数字越小越靠前")
    private Integer sortOrder;

    @Schema(description = "分类状态：0-禁用，1-启用")
    private Integer status;

    @Schema(description = "是否为商家自定义分类：0-系统预置，1-商家自定义")
    private Integer isCustom;

    @Schema(description = "创建用户ID")
    private Long createUserId;

    @Schema(description = "审核状态（仅自定义分类有效）：0-待审核, 1-已通过, 2-已拒绝")
    private Integer auditStatus;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    @Schema(description = "审核备注（管理员拒绝时填写原因）")
    private String auditRemark;

    @Schema(description = "创建时间")
    private Timestamp createdAt;

    @Schema(description = "更新时间")
    private Timestamp updatedAt;

    @TableField(exist = false)
    @Schema(description = "商品数量")
    private Integer productCount;

    @TableField(exist = false)
    @Schema(description = "子分类列表")
    private List<Category> children;
} 