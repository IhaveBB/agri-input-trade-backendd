package org.example.springboot.enums;

/**
 * 关联类型枚举（用于邮件记录关联用户或商户）
 *
 * @author IhaveBB
 * @date 2026/03/22
 */
public enum RelatedType {

    /**
     * 用户
     */
    USER("USER", "用户"),

    /**
     * 商户
     */
    MERCHANT("MERCHANT", "商户");

    private final String code;
    private final String description;

    RelatedType(String code, String description) {
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
