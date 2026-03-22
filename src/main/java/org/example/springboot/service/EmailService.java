package org.example.springboot.service;

import jakarta.annotation.Resource;
import org.example.springboot.entity.User;
import org.example.springboot.enums.ErrorCodeEnum;
import org.example.springboot.exception.BusinessException;
import org.example.springboot.util.RedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

/**
 * 邮件服务类
 * 提供发送验证码邮件的功能，包括注册验证码和找回密码验证码
 * 使用 Redis 存储验证码，有效期5分钟，60秒内不可重复发送
 *
 * @author IhaveBB
 * @date 2026/03/19
 */
@Service
public class EmailService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EmailService.class);

    /** 注册验证码 Redis Key 前缀 */
    private static final String REGISTER_CODE_PREFIX = "email:register:";
    /** 找回密码验证码 Redis Key 前缀 */
    private static final String RESET_CODE_PREFIX = "email:reset:";
    /** 发送频率限制 Redis Key 前缀 */
    private static final String RATE_LIMIT_PREFIX = "email:limit:";
    /** 验证码有效期（秒） */
    private static final int CODE_EXPIRE_SECONDS = 300;
    /** 发送频率限制时间（秒） */
    private static final int RATE_LIMIT_SECONDS = 60;

    @Resource
    private JavaMailSender javaMailSender;

    @Value("${user.fromEmail}")
    private String FROM_EMAIL;

    @Resource
    private UserService userService;

    @Resource
    private RedisUtil redisUtil;

    @Resource
    private SecureRandom secureRandom;

    /**
     * 发送注册验证码邮件
     * 验证码存储到 Redis，有效期5分钟，60秒内不可重复发送
     *
     * @param email 目标邮箱
     * @author IhaveBB
     * @date 2026/03/22
     */
    public void sendRegisterEmail(String email) {
        // 检查邮箱是否已注册
        if (userService.getByEmail(email) != null) {
            throw new BusinessException(ErrorCodeEnum.ALREADY_EXISTS, "邮箱已被注册");
        }

        // 检查发送频率限制
        checkRateLimit(email);

        // 生成验证码
        String code = generateCode();

        // 发送邮件
        SimpleMailMessage simpleMailMessage = new SimpleMailMessage();
        simpleMailMessage.setFrom(FROM_EMAIL);
        simpleMailMessage.setTo(email);
        simpleMailMessage.setSubject("注册验证码");
        simpleMailMessage.setText("邮箱验证码为：" + code + "，有效期为5分钟，请勿转发给他人");

        try {
            javaMailSender.send(simpleMailMessage);
            LOGGER.info("注册验证码邮件已发送至：{}", email);

            // 存储验证码到 Redis，5分钟过期
            String cacheKey = REGISTER_CODE_PREFIX + email;
            redisUtil.set(cacheKey, code, CODE_EXPIRE_SECONDS);

            // 设置发送频率限制
            setRateLimit(email);
        } catch (Exception e) {
            LOGGER.error("注册验证码邮件发送异常：{}", e.getMessage());
            throw new BusinessException(ErrorCodeEnum.OPERATION_FAILED, "验证码发送失败，请稍后重试");
        }
    }

    /**
     * 发送找回密码验证码邮件
     * 验证码存储到 Redis，有效期5分钟，60秒内不可重复发送
     *
     * @param email 目标邮箱
     * @author IhaveBB
     * @date 2026/03/22
     */
    public void sendFindPasswordEmail(String email) {
        // 检查邮箱是否存在
        User user = userService.getByEmail(email);
        if (user == null) {
            throw new BusinessException(ErrorCodeEnum.USER_NOT_FOUND, "邮箱不存在");
        }

        // 检查发送频率限制
        checkRateLimit(email);

        // 生成验证码
        String code = generateCode();

        // 发送邮件
        SimpleMailMessage simpleMailMessage = new SimpleMailMessage();
        simpleMailMessage.setFrom(FROM_EMAIL);
        simpleMailMessage.setTo(email);
        simpleMailMessage.setSubject("找回密码验证码");
        simpleMailMessage.setText("您的找回密码验证码为：" + code + "，有效期为5分钟，请勿泄露给他人");

        try {
            javaMailSender.send(simpleMailMessage);
            LOGGER.info("找回密码验证码邮件已发送至：{}", email);

            // 存储验证码到 Redis，5分钟过期
            String cacheKey = RESET_CODE_PREFIX + email;
            redisUtil.set(cacheKey, code, CODE_EXPIRE_SECONDS);

            // 设置发送频率限制
            setRateLimit(email);
        } catch (Exception e) {
            LOGGER.error("找回密码验证码邮件发送异常：{}", e.getMessage());
            throw new BusinessException(ErrorCodeEnum.OPERATION_FAILED, "验证码发送失败，请稍后重试");
        }
    }

    /**
     * 验证注册验证码是否正确
     * 验证成功后会自动删除 Redis 中的验证码（一次性使用）
     *
     * @param email 邮箱
     * @param code  用户输入的验证码
     * @return 验证是否通过
     * @author IhaveBB
     * @date 2026/03/22
     */
    public boolean verifyRegisterCode(String email, String code) {
        String cacheKey = REGISTER_CODE_PREFIX + email;
        return verifyCodeInternal(cacheKey, email, code);
    }

    /**
     * 验证找回密码验证码是否正确
     * 验证成功后会自动删除 Redis 中的验证码（一次性使用）
     *
     * @param email 邮箱
     * @param code  用户输入的验证码
     * @return 验证是否通过
     * @author IhaveBB
     * @date 2026/03/22
     */
    public boolean verifyResetCode(String email, String code) {
        String cacheKey = RESET_CODE_PREFIX + email;
        return verifyCodeInternal(cacheKey, email, code);
    }

    /**
     * 验证码内部校验逻辑
     *
     * @param cacheKey Redis 缓存 Key
     * @param email    邮箱（用于日志）
     * @param code     用户输入的验证码
     * @return 验证是否通过
     * @author IhaveBB
     * @date 2026/03/22
     */
    private boolean verifyCodeInternal(String cacheKey, String email, String code) {
        Object storedCode = redisUtil.get(cacheKey);
        if (storedCode == null) {
            LOGGER.warn("验证码不存在或已过期，邮箱：{}", email);
            return false;
        }

        boolean valid = storedCode.toString().equals(code);

        if (valid) {
            // 验证成功后删除验证码（一次性使用）
            redisUtil.del(cacheKey);
            LOGGER.info("验证码验证成功，邮箱：{}", email);
        } else {
            LOGGER.warn("验证码验证失败，邮箱：{}", email);
        }

        return valid;
    }

    /**
     * 生成6位数字验证码
     * 使用 SecureRandom 保证安全性
     *
     * @return 验证码字符串
     * @author IhaveBB
     * @date 2026/03/22
     */
    private String generateCode() {
        int code = secureRandom.nextInt(900000) + 100000;
        return String.valueOf(code);
    }

    /**
     * 检查发送频率限制
     *
     * @param email 邮箱
     * @author IhaveBB
     * @date 2026/03/22
     */
    private void checkRateLimit(String email) {
        String limitKey = RATE_LIMIT_PREFIX + email;
        if (redisUtil.hasKey(limitKey)) {
            long remainSeconds = redisUtil.getExpire(limitKey);
            throw new BusinessException(ErrorCodeEnum.OPERATION_FAILED,
                    "发送过于频繁，请" + remainSeconds + "秒后再试");
        }
    }

    /**
     * 设置发送频率限制
     *
     * @param email 邮箱
     * @author IhaveBB
     * @date 2026/03/22
     */
    private void setRateLimit(String email) {
        String limitKey = RATE_LIMIT_PREFIX + email;
        redisUtil.set(limitKey, "1", RATE_LIMIT_SECONDS);
    }
}
