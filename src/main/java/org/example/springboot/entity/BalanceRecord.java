package org.example.springboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * 余额变动记录实体
 * 记录用户余额的每一笔变动（充值、消费、退款等）
 *
 * @author IhaveBB
 * @date 2026/03/24
 */
@Data
@Schema(description = "余额变动记录")
@TableName("balance_record")
public class BalanceRecord {

    @TableId(type = IdType.AUTO)
    @Schema(description = "记录ID")
    private Long id;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "变动金额（正数表示增加，负数表示减少）")
    private BigDecimal amount;

    @Schema(description = "变动前余额")
    private BigDecimal balanceBefore;

    @Schema(description = "变动后余额")
    private BigDecimal balanceAfter;

    @Schema(description = "变动类型：1-充值，2-消费，3-退款")
    private Integer type;

    @Schema(description = "关联订单ID（消费/退款时关联）")
    private Long orderId;

    @Schema(description = "备注说明")
    private String remark;

    @Schema(description = "创建时间")
    private Timestamp createdAt;
}
