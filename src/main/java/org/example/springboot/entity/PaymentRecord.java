package org.example.springboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * 支付记录实体
 * 记录每笔订单的支付详情（余额支付、支付宝支付）
 *
 * @author IhaveBB
 * @date 2026/03/24
 */
@Data
@Schema(description = "支付记录")
@TableName("payment_record")
public class PaymentRecord {

    @TableId(type = IdType.AUTO)
    @Schema(description = "记录ID")
    private Long id;

    @Schema(description = "订单ID")
    private Long orderId;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "支付金额")
    private BigDecimal amount;

    @Schema(description = "支付方式：1-余额支付，2-支付宝支付")
    private Integer payType;

    @Schema(description = "支付状态：1-支付成功，2-支付失败，3-待支付")
    private Integer status;

    @Schema(description = "支付宝交易号（支付宝支付时填写）")
    private String tradeNo;

    @Schema(description = "备注说明")
    private String remark;

    @Schema(description = "创建时间")
    private Timestamp createdAt;
}
