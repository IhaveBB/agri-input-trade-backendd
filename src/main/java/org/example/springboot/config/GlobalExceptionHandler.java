package org.example.springboot.config;

import org.example.springboot.common.Result;
import org.example.springboot.util.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * 统一处理用户未登录、权限不足等异常
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理用户未登录异常
     */
    @ExceptionHandler(UserContext.UserNotLoginException.class)
    public Result<?> handleUserNotLogin(UserContext.UserNotLoginException e) {
        logger.warn("用户未登录: {}", e.getMessage());
        return Result.error("-1", "用户未登录，请先登录");
    }

    /**
     * 处理权限不足异常
     */
    @ExceptionHandler(UserContext.PermissionDeniedException.class)
    public Result<?> handlePermissionDenied(UserContext.PermissionDeniedException e) {
        logger.warn("权限不足: {}", e.getMessage());
        return Result.error("-1", e.getMessage());
    }

    /**
     * 处理其他未知异常
     */
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        logger.error("系统异常: ", e);
        return Result.error("-1", "系统异常: " + e.getMessage());
    }
}
