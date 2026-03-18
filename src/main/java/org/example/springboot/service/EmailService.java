package org.example.springboot.service;

import jakarta.annotation.Resource;
import org.example.springboot.entity.User;
import org.example.springboot.enums.ErrorCodeEnum;
import org.example.springboot.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Random;

@Service
public class EmailService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EmailService.class);

    @Resource
    private JavaMailSender javaMailSender;

    @Value("${user.fromEmail}")
    private String FROM_EMAIL;

    @Resource
    private UserService userService;

    public int sendRegisterEmail(String email) {
        if (userService.getByEmail(email) != null) {
            throw new BusinessException(ErrorCodeEnum.ALREADY_EXISTS, "邮箱已被注册");
        }

        Random random = new Random();
        int code = random.nextInt(899999) + 100000;

        SimpleMailMessage simpleMailMessage = new SimpleMailMessage();
        simpleMailMessage.setFrom(FROM_EMAIL);
        simpleMailMessage.setTo(email);
        simpleMailMessage.setSubject("注册验证码");
        simpleMailMessage.setText("邮箱验证码为：" + code + ",请勿转发给他人");

        try {
            javaMailSender.send(simpleMailMessage);
            LOGGER.info("邮件已发送：" + simpleMailMessage.getText());
            return code;
        } catch (Exception e) {
            LOGGER.error("邮件发送异常。" + e.getMessage());
            throw new BusinessException(ErrorCodeEnum.OPERATION_FAILED, "验证码发送异常，请联系管理员。");
        }
    }

    public int sendFindPasswordEmail(String email) {
        User user = userService.getByEmail(email);
        if (user == null) {
            throw new BusinessException(ErrorCodeEnum.USER_NOT_FOUND, "邮箱不存在");
        }

        Random random = new Random();
        int code = random.nextInt(899999) + 100000;

        SimpleMailMessage simpleMailMessage = new SimpleMailMessage();
        simpleMailMessage.setFrom(FROM_EMAIL);
        simpleMailMessage.setTo(email);
        simpleMailMessage.setSubject("找回密码验证码");
        simpleMailMessage.setText("您的找回密码验证码为：" + code + "，有效期为5分钟，请勿泄露给他人。");

        try {
            javaMailSender.send(simpleMailMessage);
            LOGGER.info("找回密码邮件已发送：" + simpleMailMessage.getText());
            return code;
        } catch (Exception e) {
            LOGGER.error("找回密码邮件发送异常：" + e.getMessage());
            throw new BusinessException(ErrorCodeEnum.OPERATION_FAILED, "邮件发送异常，请联系管理员。");
        }
    }
}
