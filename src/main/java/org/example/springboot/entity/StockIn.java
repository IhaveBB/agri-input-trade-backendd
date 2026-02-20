package org.example.springboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Date;

@Data
@Schema(description = "入库记录实体")
public class StockIn {
    @TableId(type = IdType.AUTO)
    @Schema(description = "入库ID")
    private Long id;

    @NotNull(message = "商品ID不能为空")
    @Schema(description = "商品ID")
    private Long productId;

    @NotNull(message = "入库数量不能为空")
    @Positive(message = "入库数量必须大于0")
    @Schema(description = "入库数量")
    private Integer quantity;

    @NotNull(message = "单价不能为空")
    @Positive(message = "单价必须大于0")
    @Schema(description = "单价")
    private BigDecimal unitPrice;

    @Schema(description = "总价")
    private BigDecimal totalPrice;

    @Schema(description = "供应商")
    private String supplier;

    @NotNull(message = "入库日期不能为空")
    @Schema(description = "入库日期")
    private Date stockDate;

    @NotNull(message = "操作人ID不能为空")
    @Schema(description = "操作人ID")
    private Long operatorId;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "状态:0-作废,1-正常")
    private Integer status = 1;

    @Schema(description = "创建时间")
    private Timestamp createdAt;

    @Schema(description = "更新时间")
    private Timestamp updatedAt;

    @TableField(exist = false)
    @Schema(description = "商品信息")
    private Product product;

    @TableField(exist = false)
    @Schema(description = "操作人信息")
    private User operator;
} 