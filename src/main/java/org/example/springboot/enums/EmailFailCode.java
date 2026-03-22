package org.example.springboot.enums;

/**
 * 邮件发送失败错误码枚举
 *
 * @author IhaveBB
 * @date 2026/03/22
 */
public enum EmailFailCode {

    /**
     * 认证失败（密钥错误、账号被禁等）- 不可重试
     */
    AUTH_FAILED("AUTH_FAILED", "认证失败", false),

    /**
     * 收件人无效（邮箱不存在、格式错误）- 不可重试
     */
    INVALID_RECIPIENT("INVALID_RECIPIENT", "收件人无效", false),

    /**
     * 网络错误（连接超时、SMTP不可达）- 可重试
     */
    NETWORK_ERROR("NETWORK_ERROR", "网络错误", true),

    /**
     * 发送频率限制 - 可重试
     */
    RATE_LIMITED("RATE_LIMITED", "发送频率限制", true),

    /**
     * 未知错误 - 可重试
     */
    UNKNOWN("UNKNOWN", "未知错误", true);

    private final String code;
    private final String description;
    private final boolean retryable;

    EmailFailCode(String code, String description, boolean retryable) {
        this.code = code;
        this.description = description;
        this.retryable = retryable;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 是否可重试
     *
     * @return true-可重试，false-不可重试
     * @author IhaveBB
     * @date 2026/03/22
     */
    public boolean isRetryable() {
        return retryable;
    }

    /**
     * 根据异常信息判断错误码
     *
     * @param errorMessage 异常信息
     * @return 错误码
     * @author IhaveBB
     * @date 2026/03/22
     */
    public static EmailFailCode fromErrorMessage(String errorMessage) {
        if (errorMessage == null) {
            return UNKNOWN;
        }

        String msg = errorMessage.toLowerCase();

        if (msg.contains("authentication") || msg.contains("auth") ||
            msg.contains("login") || msg.contains("credential") ||
            msg.contains("535") || msg.contains("username") || msg.contains("password")) {
            return AUTH_FAILED;
        }

        if (msg.contains("recipient") || msg.contains("address") ||
            msg.contains("user not found") || msg.contains("does not exist") ||
            msg.contains("550") || msg.contains("551") || msg.contains("553")) {
            return INVALID_RECIPIENT;
        }

        if (msg.contains("timeout") || msg.contains("connection") ||
            msg.contains("network") || msg.contains("unreachable") ||
            msg.contains("refused") || msg.contains("socket")) {
            return NETWORK_ERROR;
        }

        if (msg.contains("rate") || msg.contains("limit") ||
            msg.contains("too many") || msg.contains("throttl")) {
            return RATE_LIMITED;
        }

        return UNKNOWN;
    }
}
