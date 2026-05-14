package com.nicebao.springboot.config;


import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;

import jakarta.annotation.Resource;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.nicebao.springboot.entity.User;
import com.nicebao.springboot.service.UserService;
import com.nicebao.springboot.util.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;




@Component
public class JwtInterceptor implements HandlerInterceptor {
    public static final Logger LOGGER = LoggerFactory.getLogger(HandlerInterceptor.class);
    @Resource
    private UserService userService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        UserContext.clear();

        String token = request.getHeader("token");
        if (StringUtils.isBlank(token)) {
            token = request.getParameter("token");
        }
        boolean anonymousAllowed = isAnonymousAllowed(request);
        if (StringUtils.isBlank(token)) {
            if (anonymousAllowed) {
                return true;
            }
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401状态码
            response.getWriter().print("Token缺失"); // 返回错误信息
            return false;
        }

        User user = null;
        try {
            String userId = JWT.decode(token).getAudience().get(0);
            user = userService.getUserById(Integer.parseInt(userId));
        } catch (Exception e) {
            String errMsg = "token失效，重新登录！";
            LOGGER.error(errMsg + " ,token=" + token, e);
            if (anonymousAllowed) {
                return true;
            }
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().print(errMsg); // 返回错误信息
            return false;
        }
        if (user == null) {
            if (anonymousAllowed) {
                return true;
            }
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().print("User not found");
            return false;
        }
        try {
            JWTVerifier jwtVerifier = JWT.require(Algorithm.HMAC256(user.getPassword())).build();
            jwtVerifier.verify(token);
        } catch (JWTVerificationException e) {
            if (anonymousAllowed) {
                return true;
            }
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().print("token认证失败，重新登录！");
            return false;
        }
        LOGGER.info("验证成功，允许放行。{}",user);

        // 将当前用户信息存入ThreadLocal，供后续Service层使用
        UserContext.setUser(user);

        return HandlerInterceptor.super.preHandle(request, response, handler);
    }

    /**
     * 部分推荐接口既要支持未登录用户访问，也要在登录时识别当前用户。
     * 这些接口走可选认证：有合法 token 时写入 UserContext，没有 token 时按匿名用户处理。
     */
    private boolean isAnonymousAllowed(HttpServletRequest request) {
        String method = request.getMethod();
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }

        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (StringUtils.isNotBlank(contextPath) && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }

        if ("GET".equalsIgnoreCase(method)) {
            return "/api/recommendation/smart".equals(path)
                    || "/api/recommendation/new".equals(path)
                    || "/api/recommendation/hot".equals(path)
                    || path.matches("/api/recommendation/product/\\d+/profile");
        }

        return "POST".equalsIgnoreCase(method)
                && "/api/recommendation/dwell".equals(path);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 请求结束后清除ThreadLocal，避免内存泄漏
        UserContext.clear();
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }
}
