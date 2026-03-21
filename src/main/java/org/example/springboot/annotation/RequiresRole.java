package org.example.springboot.annotation;

import java.lang.annotation.*;

/**
 * 角色权限注解，用于方法级别的权限控制
 * <p>
 * 支持多种验证模式：
 * 1. 角色验证 - 指定允许访问的角色
 * 2. 登录验证 - 只需要登录即可访问
 * 3. 资源所有者验证 - 验证当前用户是否是资源所有者
 * </p>
 *
 * @author IhaveBB
 * @date 2026/03/19
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresRole {

    /**
     * 允许访问的角色列表
     * 如果为空数组，则只要求用户已登录
     * 多个角色之间是"或"的关系，满足任一即可
     */
    String[] value() default {};

    /**
     * 提示信息
     */
    String message() default "无权限访问";

    /**
     * 是否要求资源所有者
     * 如果为true，则管理员可以访问，或者资源所有者可以访问
     */
    boolean requireOwner() default false;

    /**
     * 资源所有者ID的SpEL表达式
     * 仅当requireOwner=true时生效
     * 示例: "#userId" 表示从方法参数中获取userId
     * 示例: "#id" 表示从方法参数中获取id
     */
    String ownerIdExpression() default "";
}
