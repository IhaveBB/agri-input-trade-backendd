package org.example.springboot.util;

import org.example.springboot.entity.User;

/**
 * 当前用户上下文工具类（使用ThreadLocal存储整个User对象）
 */
public class UserContext {

    private static final ThreadLocal<User> THREAD_LOCAL_USER = new ThreadLocal<>();

    /**
     * 设置当前用户信息
     */
    public static void setUser(User user) {
        THREAD_LOCAL_USER.set(user);
    }

    /**
     * 获取当前用户
     */
    public static User getUser() {
        return THREAD_LOCAL_USER.get();
    }

    /**
     * 获取当前用户ID
     */
    public static Long getUserId() {
        User user = THREAD_LOCAL_USER.get();
        return user != null ? user.getId() : null;
    }

    /**
     * 获取当前用户名
     */
    public static String getUsername() {
        User user = THREAD_LOCAL_USER.get();
        return user != null ? user.getUsername() : null;
    }

    /**
     * 获取当前用户角色
     */
    public static String getRole() {
        User user = THREAD_LOCAL_USER.get();
        return user != null ? user.getRole() : null;
    }

    /**
     * 清除当前用户信息
     */
    public static void clear() {
        THREAD_LOCAL_USER.remove();
    }

    /**
     * 判断是否为管理员
     */
    public static boolean isAdmin() {
        String role = getRole();
        return role != null && "ADMIN".equalsIgnoreCase(role);
    }

    /**
     * 判断是否为商户
     */
    public static boolean isMerchant() {
        String role = getRole();
        return role != null && "MERCHANT".equalsIgnoreCase(role);
    }

    /**
     * 判断是否为普通用户
     */
    public static boolean isUser() {
        String role = getRole();
        return role != null && "USER".equalsIgnoreCase(role);
    }

    /**
     * 判断当前用户是否有指定角色
     */
    public static boolean hasRole(String... roles) {
        String role = getRole();
        if (role == null) {
            return false;
        }
        for (String r : roles) {
            if (role.equalsIgnoreCase(r)) {
                return true;
            }
        }
        return false;
    }
}
