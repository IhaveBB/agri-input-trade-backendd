package org.example.springboot.config;

import org.example.springboot.common.Result;
import org.example.springboot.enums.ErrorCodeEnum;
import org.example.springboot.exception.BusinessException;
import org.example.springboot.util.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

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
     * 处理业务异常
     *
     * @param e 业务异常
     * @return 错误结果
     */
    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e) {
        logger.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理参数校验失败异常（@Valid）
     *
     * @param e 参数校验异常
     * @return 错误结果
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        logger.warn("参数校验失败: {}", message);
        return Result.error(ErrorCodeEnum.PARAM_ERROR.getCode(), message);
    }

    /**
     * 处理非法参数异常
     *
     * @param e 非法参数异常
     * @return 错误结果
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Result<?> handleIllegalArgumentException(IllegalArgumentException e) {
        logger.warn("非法参数: {}", e.getMessage());
        return Result.error(ErrorCodeEnum.PARAM_ERROR.getCode(), e.getMessage());
    }

    /**
     * 处理其他未知异常
     */
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        logger.error("系统异常: ", e);
        return Result.error(ErrorCodeEnum.INTERNAL_ERROR.getCode(), "系统异常: " + e.getMessage());
    }
}
