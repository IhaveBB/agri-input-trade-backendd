package org.example.springboot.controller.email;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.example.springboot.common.Result;
import org.example.springboot.service.EmailService;
import org.springframework.web.bind.annotation.*;

/**
 * 邮件发送控制器
 * 提供注册验证码和找回密码验证码的发送接口
 *
 * @author IhaveBB
 * @date 2026/03/19
 */
@RestController
@CrossOrigin(origins = "*", maxAge = 3600)
@RequestMapping("/email")
@Tag(name = "邮件服务", description = "验证码邮件发送相关接口")
public class SendEmailController {

    @Resource
    private EmailService emailService;

    /**
     * 发送注册验证码
     * 验证码将发送到指定邮箱，有效期5分钟
     *
     * @param email 目标邮箱
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/22
     */
    @Operation(summary = "发送注册验证码")
    @GetMapping("/sendEmail/{email}")
    public Result<?> sendRegisterCode(
            @Parameter(description = "邮箱地址") @PathVariable String email) {
        emailService.sendRegisterEmail(email);
        return Result.success("验证码已发送");
    }

    /**
     * 发送找回密码验证码
     * 验证码将发送到指定邮箱，有效期5分钟
     *
     * @param email 目标邮箱
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/22
     */
    @Operation(summary = "发送找回密码验证码")
    @GetMapping("/findByEmail/{email}")
    public Result<?> sendResetCode(
            @Parameter(description = "邮箱地址") @PathVariable String email) {
        emailService.sendFindPasswordEmail(email);
        return Result.success("验证码已发送");
    }
}
