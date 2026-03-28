package org.example.springboot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.springboot.annotation.RequiresRole;
import org.example.springboot.common.Result;
import org.example.springboot.entity.RechargeRecord;
import org.example.springboot.service.BalanceService;
import org.example.springboot.util.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * 余额管理控制器
 * 提供余额查询、充值、流水查询等功能
 *
 * @author IhaveBB
 * @date 2026/03/24
 */
@Tag(name = "余额管理接口")
@RestController
@RequestMapping("/balance")
public class BalanceController {
    private static final Logger LOGGER = LoggerFactory.getLogger(BalanceController.class);

    @Autowired
    private BalanceService balanceService;

    /**
     * 获取当前用户余额
     * 权限：需要登录
     *
     * @return 用户余额
     * @author IhaveBB
     * @date 2026/03/24
     */
    @Operation(summary = "获取当前用户余额")
    @RequiresRole
    @GetMapping("/my")
    public Result<BigDecimal> getMyBalance() {
        Long currentUserId = UserContext.getUserId();
        return Result.success(balanceService.getUserBalance(currentUserId));
    }

    /**
     * 获取指定用户余额（管理员专用）
     * 权限：管理员
     *
     * @param userId 用户ID
     * @return 用户余额
     * @author IhaveBB
     * @date 2026/03/24
     */
    @Operation(summary = "获取指定用户余额（管理员）")
    @RequiresRole("ADMIN")
    @GetMapping("/user/{userId}")
    public Result<BigDecimal> getUserBalance(@PathVariable Long userId) {
        return Result.success(balanceService.getUserBalance(userId));
    }

    /**
     * 管理员为用户充值余额
     * 权限：管理员
     *
     * @param userId 用户ID
     * @param amount 充值金额
     * @param remark 备注（可选）
     * @return 充值后的余额
     * @author IhaveBB
     * @date 2026/03/24
     */
    @Operation(summary = "为用户充值余额（管理员）")
    @RequiresRole("ADMIN")
    @PostMapping("/recharge/admin/{userId}")
    public Result<BigDecimal> rechargeBalance(
            @PathVariable Long userId,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String remark) {
        Long adminId = UserContext.getUserId();
        BigDecimal newBalance = balanceService.rechargeBalance(userId, amount, adminId, remark);
        return Result.success(newBalance);
    }

    /**
     * 创建余额充值订单
     * 权限：需要登录
     * 用户自助充值，创建待支付的充值记录
     *
     * @param amount 充值金额
     * @return 创建的充值记录
     * @author IhaveBB
     * @date 2026/03/24
     */
    @Operation(summary = "创建余额充值订单")
    @RequiresRole
    @PostMapping("/recharge/create")
    public Result<RechargeRecord> createRechargeOrder(@RequestParam BigDecimal amount) {
        Long currentUserId = UserContext.getUserId();
        RechargeRecord rechargeRecord = balanceService.createRechargeOrder(currentUserId, amount);
        LOGGER.info("用户创建充值订单，用户ID：{}，充值单号：{}，金额：{}",
                currentUserId, rechargeRecord.getRechargeNo(), amount);
        return Result.success(rechargeRecord);
    }

    /**
     * 获取当前用户的充值记录
     * 权限：需要登录
     *
     * @param currentPage 当前页码（默认1）
     * @param size        每页条数（默认10）
     * @return 充值记录分页列表
     * @author IhaveBB
     * @date 2026/03/24
     */
    @Operation(summary = "获取我的充值记录")
    @RequiresRole
    @GetMapping("/my/recharges")
    public Result<?> getMyRechargeRecords(
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "10") Integer size) {
        Long currentUserId = UserContext.getUserId();
        return Result.success(balanceService.getRechargeRecords(currentUserId, currentPage, size));
    }

    /**
     * 获取当前用户的余额变动记录
     * 权限：需要登录
     *
     * @param currentPage 当前页码（默认1）
     * @param size        每页条数（默认10）
     * @return 余额变动记录分页列表
     * @author IhaveBB
     * @date 2026/03/24
     */
    @Operation(summary = "获取我的余额变动记录")
    @RequiresRole
    @GetMapping("/my/records")
    public Result<?> getMyBalanceRecords(
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "10") Integer size) {
        Long currentUserId = UserContext.getUserId();
        return Result.success(balanceService.getBalanceRecords(currentUserId, currentPage, size));
    }

    /**
     * 获取当前用户的支付记录
     * 权限：需要登录
     *
     * @param currentPage 当前页码（默认1）
     * @param size        每页条数（默认10）
     * @return 支付记录分页列表
     * @author IhaveBB
     * @date 2026/03/24
     */
    @Operation(summary = "获取我的支付记录")
    @RequiresRole
    @GetMapping("/my/payments")
    public Result<?> getMyPaymentRecords(
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "10") Integer size) {
        Long currentUserId = UserContext.getUserId();
        return Result.success(balanceService.getPaymentRecords(currentUserId, currentPage, size));
    }
}
