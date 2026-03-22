package org.example.springboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 邮件发送记录实体
 *
 * @author IhaveBB
 * @date 2026/03/22
 */
@Data
@TableName("email_record")
public class EmailRecord {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 收件人邮箱
     */
    private String recipientEmail;

    /**
     * 收件人名称
     */
    private String recipientName;

    /**
     * 邮件主题
     */
    private String subject;

    /**
     * 邮件内容
     */
    private String content;

    /**
     * 邮件类型：STOCK_ALERT/VERIFICATION/OTHER
     */
    private String emailType;

    /**
     * 状态：PENDING/SENDING/SUCCESS/RETRYABLE_FAIL/PERMANENT_FAIL
     */
    private String status;

    /**
     * 失败原因
     */
    private String failReason;

    /**
     * 失败错误码
     */
    private String failCode;

    /**
     * 重试次数
     */
    private Integer retryCount;

    /**
     * 最大重试次数
     */
    private Integer maxRetry;

    /**
     * 下次重试时间
     */
    private LocalDateTime nextRetryTime;

    /**
     * 关联类型：USER/MERCHANT
     */
    private String relatedType;

    /**
     * 关联ID
     */
    private Long relatedId;

    /**
     * 商品ID（库存预警时）
     */
    private Long productId;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
