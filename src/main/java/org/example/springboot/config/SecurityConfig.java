package org.example.springboot.config;

import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security安全配置类
 * 用于配置系统的安全认证、授权等功能
 * 包括：
 * - 密码加密方式
 * - 安全过滤器链
 * - 请求授权规则
 * - CSRF防护配置
 *
 * @author IhaveBB
 * @date 2026/03/19
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Resource
    private UserDetailsService userDetailsService;

    /**
     * 密码编码器配置
     * 使用BCrypt加密算法对密码进行加密
     * BCrypt是一种安全的密码哈希函数，自动包含随机盐值
     *
     * @return PasswordEncoder BCrypt密码编码器实例
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    /**
     * 安全过滤器链配置
     * 配置系统的安全规则，包括：
     * 1. 请求授权规则
     *    - /user/login 允许匿名访问
     *    - 其他请求暂时允许所有访问（开发环境配置）
     * 2. CSRF防护
     *    - 当前已禁用（仅适用于开发环境）
     *
     * @param http HttpSecurity配置对象
     * @return SecurityFilterChain 配置好的安全过滤器链
     * @throws Exception 配置过程中可能发生的异常
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/user/login").permitAll()
                        .anyRequest().permitAll()
                )
                .csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }
}