package org.example.springboot.annotation;

import java.lang.annotation.*;

/**
 * 角色权限注解，用于方法级别的权限控制
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresRole {
    /**
     * 允许访问的角色
     */
    String[] value() default {"ADMIN"};

    /**
     * 提示信息
     */
    String message() default "无权限访问";
}
