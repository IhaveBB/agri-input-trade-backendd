package org.example.springboot.util;

import org.example.springboot.common.Result;
import org.example.springboot.entity.User;

/**
 * 当前用户上下文工具类（使用ThreadLocal存储整个User对象）
 * 提供统一的用户信息获取和权限判断方法
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

    // ==================== 统一的用户验证方法 ====================

    /**
     * 获取当前登录用户，如果未登录则返回错误结果
     * @return 当前用户ID，未登录返回null
     */
    public static Long requireUserId() {
        Long userId = getUserId();
        if (userId == null) {
            throw new UserNotLoginException();
        }
        return userId;
    }

    /**
     * 获取当前登录用户，如果未登录则返回错误结果
     * @return 当前用户对象，未登录返回null
     */
    public static User requireUser() {
        User user = getUser();
        if (user == null) {
            throw new UserNotLoginException();
        }
        return user;
    }

    /**
     * 获取当前用户角色，如果未登录则返回错误结果
     * @return 当前用户角色，未登录返回null
     */
    public static String requireRole() {
        String role = getRole();
        if (role == null) {
            throw new UserNotLoginException();
        }
        return role;
    }

    /**
     * 检查用户是否已登录，未登录抛出异常
     */
    public static void checkLogin() {
        if (getUserId() == null) {
            throw new UserNotLoginException();
        }
    }

    /**
     * 检查是否为管理员，未满足则抛出异常
     */
    public static void checkAdmin() {
        checkLogin();
        if (!isAdmin()) {
            throw new PermissionDeniedException("需要管理员权限");
        }
    }

    /**
     * 检查是否为商户，未满足则抛出异常
     */
    public static void checkMerchant() {
        checkLogin();
        if (!isMerchant()) {
            throw new PermissionDeniedException("需要商户权限");
        }
    }

    /**
     * 检查是否具有指定角色之一，未满足则抛出异常
     */
    public static void checkRole(String... roles) {
        checkLogin();
        if (!hasRole(roles)) {
            throw new PermissionDeniedException("权限不足");
        }
    }

    /**
     * 获取当前用户的merchantId
     * 如果是商户，返回自己的ID；如果是管理员，返回null（表示查看全部）
     * @param requestMerchantId 请求中传入的merchantId参数
     * @return 最终的merchantId
     */
    public static Long getMerchantId(Long requestMerchantId) {
        if (isMerchant()) {
            // 商户只能查看自己的数据
            return getUserId();
        }
        // 管理员可以查看全部或指定商户
        return requestMerchantId;
    }

    /**
     * 异常类：用户未登录
     */
    public static class UserNotLoginException extends RuntimeException {
        public UserNotLoginException() {
            super("用户未登录");
        }
    }

    /**
     * 异常类：权限不足
     */
    public static class PermissionDeniedException extends RuntimeException {
        public PermissionDeniedException(String message) {
            super(message);
        }
    }
}
