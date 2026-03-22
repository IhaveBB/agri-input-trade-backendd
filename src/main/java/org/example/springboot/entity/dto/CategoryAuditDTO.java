package org.example.springboot.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 分类审核请求 DTO
 * <p>
 * 管理员审核商家自定义分类申请时使用，支持通过或拒绝操作
 * </p>
 *
 * @author IhaveBB
 * @date 2026/03/22
 */
@Data
@Schema(description = "分类审核请求")
public class CategoryAuditDTO {

    /**
     * 审核结果：1-通过，2-拒绝
     */
    @NotNull(message = "审核结果不能为空")
    @Min(value = 1, message = "审核结果值无效：1-通过，2-拒绝")
    @Max(value = 2, message = "审核结果值无效：1-通过，2-拒绝")
    @Schema(description = "审核结果：1-通过，2-拒绝")
    private Integer auditStatus;

    /**
     * 审核备注（拒绝时必填）
     */
    @Schema(description = "审核备注，拒绝时必须填写拒绝原因")
    private String auditRemark;
}
