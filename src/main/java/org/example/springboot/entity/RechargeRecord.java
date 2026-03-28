package org.example.springboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * 充值记录实体
 * 记录用户通过支付宝充值到余额的订单
 *
 * @author IhaveBB
 * @date 2026/03/24
 */
@Data
@Schema(description = "充值记录")
@TableName("recharge_record")
public class RechargeRecord {

    @TableId(type = IdType.AUTO)
    @Schema(description = "记录ID")
    private Long id;

    @Schema(description = "充值单号（用于支付宝支付）")
    private String rechargeNo;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "充值金额")
    private BigDecimal amount;

    @Schema(description = "充值状态：0-待支付，1-已支付，2-已取消")
    private Integer status;

    @Schema(description = "支付宝交易号")
    private String tradeNo;

    @Schema(description = "创建时间")
    private Timestamp createdAt;

    @Schema(description = "支付完成时间")
    private Timestamp paidAt;
}
