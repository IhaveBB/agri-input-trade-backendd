package org.example.springboot.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.example.springboot.annotation.RequiresRole;
import org.example.springboot.enumClass.UserRole;
import org.example.springboot.enums.ErrorCodeEnum;
import org.example.springboot.exception.BusinessException;
import org.example.springboot.util.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 权限验证AOP切面
 * <p>
 * 统一处理方法级别的权限验证，支持：
 * 1. 角色验证 - 检查用户是否具有指定角色
 * 2. 登录验证 - 检查用户是否已登录
 * 3. 资源所有者验证 - 检查当前用户是否是资源所有者
 * </p>
 *
 * @author IhaveBB
 * @date 2026/03/19
 */
@Aspect
@Component
@Order(1)  // 确保在推荐埋点AOP之前执行
public class AuthorizationAspect {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizationAspect.class);

    private final ExpressionParser parser = new SpelExpressionParser();

    /**
     * 处理所有带有 @RequiresRole 注解的方法
     *
     * @param joinPoint 切点
     * @return 方法执行结果
     * @throws Throwable 异常
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Around("@annotation(org.example.springboot.annotation.RequiresRole)")
    public Object checkAuthorization(ProceedingJoinPoint joinPoint) throws Throwable {
        // 获取方法签名
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // 获取注解（切入点已确保注解存在）
        RequiresRole annotation = method.getAnnotation(RequiresRole.class);

        // 获取当前用户信息
        Long currentUserId = UserContext.getUserId();
        String currentRole = UserContext.getRole();

        // 1. 登录验证
        if (currentUserId == null) {
            LOGGER.warn("[权限AOP] 用户未登录");
            throw new BusinessException(ErrorCodeEnum.UNAUTHORIZED, "请先登录");
        }

        // 获取允许的角色列表
        String[] allowedRoles = annotation.value();
        boolean roleCheckPassed = checkRolePermission(currentRole, allowedRoles);

        // 2. 资源所有者验证（如果需要）
        if (annotation.requireOwner()) {
            // 管理员直接通过
            if (UserRole.isAdmin(currentRole)) {
                LOGGER.debug("[权限AOP] 管理员访问通过，用户ID：{}", currentUserId);
                return joinPoint.proceed();
            }

            // 如果角色验证通过，也直接通过
            if (roleCheckPassed && allowedRoles.length > 0) {
                LOGGER.debug("[权限AOP] 角色验证通过，用户ID：{}，角色：{}", currentUserId, currentRole);
                return joinPoint.proceed();
            }

            // 解析所有者ID表达式进行验证
            return verifyResourceOwner(joinPoint, annotation, currentUserId, currentRole, allowedRoles, method, signature);
        }

        // 3. 不需要所有者验证，只检查角色
        if (!roleCheckPassed && allowedRoles.length > 0) {
            LOGGER.warn("[权限AOP] 权限不足，用户ID：{}，角色：{}，需要角色：{}",
                    currentUserId, currentRole, String.join(",", allowedRoles));
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN, annotation.message());
        }

        LOGGER.debug("[权限AOP] 权限验证通过，用户ID：{}，角色：{}", currentUserId, currentRole);
        return joinPoint.proceed();
    }

    /**
     * 检查角色权限
     *
     * @param currentRole  当前用户角色
     * @param allowedRoles 允许的角色列表
     * @return 是否有权限
     * @author IhaveBB
     * @date 2026/03/19
     */
    private boolean checkRolePermission(String currentRole, String[] allowedRoles) {
        if (allowedRoles.length == 0) {
            // 没有指定角色，只需要登录即可
            return true;
        }

        // 检查当前用户角色是否在允许的角色列表中
        for (String requiredRole : allowedRoles) {
            if (isRoleMatch(currentRole, requiredRole)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 验证资源所有者
     *
     * @param joinPoint      切点
     * @param annotation     权限注解
     * @param currentUserId  当前用户ID
     * @param currentRole    当前用户角色
     * @param allowedRoles   允许的角色列表
     * @param method         方法
     * @param signature      方法签名
     * @return 方法执行结果
     * @throws Throwable 异常
     * @author IhaveBB
     * @date 2026/03/19
     */
    private Object verifyResourceOwner(ProceedingJoinPoint joinPoint, RequiresRole annotation,
                                        Long currentUserId, String currentRole, String[] allowedRoles,
                                        Method method, MethodSignature signature) throws Throwable {
        String ownerExpression = annotation.ownerIdExpression();
        if (ownerExpression != null && !ownerExpression.isEmpty()) {
            try {
                Long ownerId = evaluateOwnerIdExpression(joinPoint, ownerExpression, method, signature);
                if (ownerId != null && ownerId.equals(currentUserId)) {
                    LOGGER.debug("[权限AOP] 资源所有者验证通过，用户ID：{}", currentUserId);
                    return joinPoint.proceed();
                } else {
                    LOGGER.warn("[权限AOP] 资源所有者验证失败，当前用户：{}，资源所有者：{}", currentUserId, ownerId);
                    throw new BusinessException(ErrorCodeEnum.FORBIDDEN, annotation.message());
                }
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                LOGGER.error("[权限AOP] 解析所有者表达式失败：{}", e.getMessage());
                throw new BusinessException(ErrorCodeEnum.FORBIDDEN, annotation.message());
            }
        }

        // 没有指定所有者表达式，检查角色权限
        if (allowedRoles.length == 0) {
            // 没有指定角色也没有所有者表达式，管理员已通过，普通用户拒绝
            LOGGER.warn("[权限AOP] 非管理员无法访问，用户ID：{}，角色：{}", currentUserId, currentRole);
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN, annotation.message());
        }

        return joinPoint.proceed();
    }

    /**
     * 检查角色是否匹配
     * 支持大小写不敏感匹配
     *
     * @param currentRole  当前用户角色
     * @param requiredRole 要求的角色
     * @return 是否匹配
     * @author IhaveBB
     * @date 2026/03/19
     */
    private boolean isRoleMatch(String currentRole, String requiredRole) {
        if (currentRole == null || requiredRole == null) {
            return false;
        }
        // 忽略大小写匹配
        return currentRole.equalsIgnoreCase(requiredRole);
    }

    /**
     * 使用SpEL表达式解析所有者ID
     *
     * @param joinPoint  切点
     * @param expression SpEL表达式
     * @param method     方法
     * @param signature  方法签名
     * @return 所有者ID
     * @author IhaveBB
     * @date 2026/03/19
     */
    private Long evaluateOwnerIdExpression(ProceedingJoinPoint joinPoint, String expression,
                                            Method method, MethodSignature signature) {
        // 创建SpEL上下文
        EvaluationContext context = new StandardEvaluationContext();

        // 获取方法参数名和值
        String[] parameterNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        if (parameterNames != null && args != null) {
            for (int i = 0; i < parameterNames.length; i++) {
                context.setVariable(parameterNames[i], args[i]);
            }
        }

        // 解析表达式
        Expression exp = parser.parseExpression(expression);
        Object value = exp.getValue(context);

        // 转换为Long
        if (value instanceof Long) {
            return (Long) value;
        } else if (value instanceof Integer) {
            return ((Integer) value).longValue();
        } else if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                LOGGER.error("[权限AOP] 无法将字符串转换为Long：{}", value);
                return null;
            }
        }

        LOGGER.warn("[权限AOP] 所有者ID表达式返回了不支持的类型：{}", value != null ? value.getClass() : "null");
        return null;
    }
}
