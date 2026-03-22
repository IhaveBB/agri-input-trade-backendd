package org.example.springboot.enums;

/**
 * 邮件类型枚举
 *
 * @author IhaveBB
 * @date 2026/03/22
 */
public enum EmailType {

    /**
     * 库存预警邮件
     */
    STOCK_ALERT("STOCK_ALERT", "库存预警"),

    /**
     * 验证码邮件
     */
    VERIFICATION("VERIFICATION", "验证码"),

    /**
     * 其他类型
     */
    OTHER("OTHER", "其他");

    private final String code;
    private final String description;

    EmailType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
