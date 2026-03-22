package org.example.springboot.enums;

/**
 * 邮件发送状态枚举
 *
 * @author IhaveBB
 * @date 2026/03/22
 */
public enum EmailStatus {

    /**
     * 待发送
     */
    PENDING("PENDING", "待发送"),

    /**
     * 发送中
     */
    SENDING("SENDING", "发送中"),

    /**
     * 发送成功
     */
    SUCCESS("SUCCESS", "发送成功"),

    /**
     * 可重试失败（网络错误、限流等）
     */
    RETRYABLE_FAIL("RETRYABLE_FAIL", "发送失败（可重试）"),

    /**
     * 永久失败（认证错误、收件人不存在等）
     */
    PERMANENT_FAIL("PERMANENT_FAIL", "发送失败（不可重试）");

    private final String code;
    private final String description;

    EmailStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 判断是否可以重试
     *
     * @return true-可重试，false-不可重试
     * @author IhaveBB
     * @date 2026/03/22
     */
    public boolean isRetryable() {
        return this == RETRYABLE_FAIL;
    }

    /**
     * 判断是否为终态
     *
     * @return true-终态，false-非终态
     * @author IhaveBB
     * @date 2026/03/22
     */
    public boolean isFinalState() {
        return this == SUCCESS || this == PERMANENT_FAIL;
    }
}
