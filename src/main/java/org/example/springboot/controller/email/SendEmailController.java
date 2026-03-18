package org.example.springboot.controller.email;

import jakarta.annotation.Resource;
import org.example.springboot.common.Result;
import org.example.springboot.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.security.GeneralSecurityException;

@RestController
@CrossOrigin(origins = "*", maxAge = 3600)
@RequestMapping("/email")
public class SendEmailController {
    private static final Logger LOGGER = LoggerFactory.getLogger(SendEmailController.class);

    @Resource
    private EmailService emailService;

    @GetMapping("/sendEmail/{email}")
    public Result<?> emailRegister(@PathVariable String email) throws GeneralSecurityException {
        return Result.success(emailService.sendRegisterEmail(email));
    }

    @GetMapping("/findByEmail/{email}")
    public Result<?> findByEmail(@PathVariable String email) {
        LOGGER.info("FIND BY EMAIL:" + email);
        return Result.success(emailService.sendFindPasswordEmail(email));
    }
}
