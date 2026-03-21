package org.example.springboot.service;

import jakarta.annotation.Resource;
import org.example.springboot.entity.User;
import org.example.springboot.enums.ErrorCodeEnum;
import org.example.springboot.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Random;

/**
 * 邮件服务类
 * 提供发送验证码邮件的功能，包括注册验证码和找回密码验证码
 * 使用缓存存储验证码，有效期5分钟
 *
 * @author IhaveBB
 * @date 2026/03/19
 */
@Service
public class EmailService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EmailService.class);

    private static final String VERIFICATION_CODE_CACHE = "verificationCode";
    private static final int CODE_EXPIRE_MINUTES = 5;

    @Resource
    private JavaMailSender javaMailSender;

    @Value("${user.fromEmail}")
    private String FROM_EMAIL;

    @Resource
    private UserService userService;

    @Resource
    private CacheManager cacheManager;

    /**
     * 发送注册验证码邮件
     * 验证码存储到缓存，有效期5分钟
     *
     * @param email 目标邮箱
     * @return 验证码
     * @author IhaveBB
     * @date 2026/03/19
     */
    public int sendRegisterEmail(String email) {
        if (userService.getByEmail(email) != null) {
            throw new BusinessException(ErrorCodeEnum.ALREADY_EXISTS, "邮箱已被注册");
        }

        int code = generateCode();

        SimpleMailMessage simpleMailMessage = new SimpleMailMessage();
        simpleMailMessage.setFrom(FROM_EMAIL);
        simpleMailMessage.setTo(email);
        simpleMailMessage.setSubject("注册验证码");
        simpleMailMessage.setText("邮箱验证码为：" + code + "，有效期为" + CODE_EXPIRE_MINUTES + "分钟，请勿转发给他人");

        try {
            javaMailSender.send(simpleMailMessage);
            LOGGER.info("注册验证码邮件已发送至：{}", email);
            // 存储验证码到缓存
            storeVerificationCode(email, code);
            return code;
        } catch (Exception e) {
            LOGGER.error("邮件发送异常：" + e.getMessage());
            throw new BusinessException(ErrorCodeEnum.OPERATION_FAILED, "验证码发送异常，请联系管理员。");
        }
    }

    /**
     * 发送找回密码验证码邮件
     * 验证码存储到缓存，有效期5分钟
     *
     * @param email 目标邮箱
     * @return 验证码
     * @author IhaveBB
     * @date 2026/03/19
     */
    public int sendFindPasswordEmail(String email) {
        User user = userService.getByEmail(email);
        if (user == null) {
            throw new BusinessException(ErrorCodeEnum.USER_NOT_FOUND, "邮箱不存在");
        }

        int code = generateCode();

        SimpleMailMessage simpleMailMessage = new SimpleMailMessage();
        simpleMailMessage.setFrom(FROM_EMAIL);
        simpleMailMessage.setTo(email);
        simpleMailMessage.setSubject("找回密码验证码");
        simpleMailMessage.setText("您的找回密码验证码为：" + code + "，有效期为" + CODE_EXPIRE_MINUTES + "分钟，请勿泄露给他人。");

        try {
            javaMailSender.send(simpleMailMessage);
            LOGGER.info("找回密码验证码邮件已发送至：{}", email);
            // 存储验证码到缓存
            storeVerificationCode(email, code);
            return code;
        } catch (Exception e) {
            LOGGER.error("找回密码邮件发送异常：" + e.getMessage());
            throw new BusinessException(ErrorCodeEnum.OPERATION_FAILED, "邮件发送异常，请联系管理员。");
        }
    }

    /**
     * 验证验证码是否正确
     * 验证成功后会自动删除缓存中的验证码（一次性使用）
     *
     * @param email 邮箱
     * @param code  用户输入的验证码
     * @return 验证是否通过
     * @author IhaveBB
     * @date 2026/03/19
     */
    public boolean verifyCode(String email, String code) {
        Cache cache = cacheManager.getCache(VERIFICATION_CODE_CACHE);
        if (cache == null) {
            LOGGER.error("验证码缓存不可用");
            return false;
        }

        String cacheKey = "pwd:" + email;
        Cache.ValueWrapper wrapper = cache.get(cacheKey);
        if (wrapper == null || wrapper.get() == null) {
            LOGGER.warn("验证码不存在或已过期，邮箱：{}", email);
            return false;
        }

        String storedCode = wrapper.get().toString();
        boolean valid = storedCode.equals(code);

        if (valid) {
            // 验证成功后删除验证码（一次性使用）
            cache.evict(cacheKey);
            LOGGER.info("验证码验证成功，邮箱：{}", email);
        } else {
            LOGGER.warn("验证码验证失败，邮箱：{}", email);
        }

        return valid;
    }

    /**
     * 生成6位数字验证码
     *
     * @return 验证码
     * @author IhaveBB
     * @date 2026/03/19
     */
    private int generateCode() {
        Random random = new Random();
        return random.nextInt(899999) + 100000;
    }

    /**
     * 存储验证码到缓存
     *
     * @param email 邮箱
     * @param code  验证码
     * @author IhaveBB
     * @date 2026/03/19
     */
    private void storeVerificationCode(String email, int code) {
        Cache cache = cacheManager.getCache(VERIFICATION_CODE_CACHE);
        if (cache != null) {
            String cacheKey = "pwd:" + email;
            cache.put(cacheKey, String.valueOf(code));
            LOGGER.debug("验证码已存储到缓存，邮箱：{}", email);
        }
    }
}
