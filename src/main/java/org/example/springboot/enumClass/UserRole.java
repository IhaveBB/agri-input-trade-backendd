package org.example.springboot.enumClass;

/**
 * 用户角色枚举
 */
public enum UserRole {
    USER("USER", "农户"),
    MERCHANT("MERCHANT", "商户"),
    ADMIN("ADMIN", "管理员");

    private final String code;
    private final String description;

    UserRole(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static UserRole fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return USER; // 默认角色
        }
        for (UserRole role : values()) {
            if (role.code.equalsIgnoreCase(code)) {
                return role;
            }
        }
        throw new IllegalArgumentException("未知角色: " + code);
    }

    /**
     * 判断是否为管理员
     */
    public static boolean isAdmin(String role) {
        return ADMIN.code.equalsIgnoreCase(role);
    }

    /**
     * 判断是否为商户
     */
    public static boolean isMerchant(String role) {
        return MERCHANT.code.equalsIgnoreCase(role);
    }

    /**
     * 判断是否为普通用户
     *
     * @param role 角色代码
     * @return 是否为普通用户
     * @author IhaveBB
     * @date 2026/03/19
     */
    public static boolean isUser(String role) {
        return USER.code.equalsIgnoreCase(role);
    }

    /**
     * 判断角色是否有效（是否为系统定义的角色）
     *
     * @param role 角色代码
     * @return 是否为有效角色
     * @author IhaveBB
     * @date 2026/03/19
     */
    public static boolean isValidRole(String role) {
        if (role == null || role.isEmpty()) {
            return false;
        }
        for (UserRole userRole : values()) {
            if (userRole.code.equalsIgnoreCase(role)) {
                return true;
            }
        }
        return false;
    }
}
