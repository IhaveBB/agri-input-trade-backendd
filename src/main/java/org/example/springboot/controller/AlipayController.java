package org.example.springboot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.example.springboot.annotation.RequiresRole;
import org.example.springboot.entity.RechargeRecord;
import org.example.springboot.service.AlipayService;
import org.example.springboot.service.BalanceService;
import org.example.springboot.util.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;

@Tag(name = "支付宝支付接口")
@RestController
@RequestMapping("/alipay")
public class AlipayController {
    private static final Logger LOGGER = LoggerFactory.getLogger(AlipayController.class);

    @Autowired
    private AlipayService alipayService;

    @Autowired
    private BalanceService balanceService;

    @Operation(summary = "创建支付")
    @GetMapping("/pay/{orderId}")
    public void pay(@PathVariable Long orderId, HttpServletResponse response) throws Exception {
        LOGGER.info("收到支付创建请求，订单ID：{}", orderId);
        alipayService.pay(orderId, response);
    }

    @Operation(summary = "余额充值支付")
    @RequiresRole
    @GetMapping("/recharge/pay/{rechargeId}")
    public void rechargePay(@PathVariable Long rechargeId, HttpServletResponse response) throws Exception {
        Long currentUserId = UserContext.getUserId();
        LOGGER.info("收到余额充值支付请求，充值记录ID：{}，用户ID：{}", rechargeId, currentUserId);
        RechargeRecord rechargeRecord = balanceService.getRechargeRecordById(rechargeId, currentUserId);
        alipayService.rechargePay(rechargeRecord, response);
    }

    @Operation(summary = "支付通知回调")
    @PostMapping("/notify")
    public void paymentNotify(HttpServletRequest request) throws Exception {
        alipayService.handlePaymentNotify(request);
    }
} 