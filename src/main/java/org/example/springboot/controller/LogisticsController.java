package org.example.springboot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.springboot.common.Result;
import org.example.springboot.entity.Logistics;
import org.example.springboot.enumClass.UserRole;
import org.example.springboot.service.LogisticsService;
import org.example.springboot.util.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "物流管理接口")
@RestController
@RequestMapping("/logistics")
public class LogisticsController {
    private static final Logger LOGGER = LoggerFactory.getLogger(LogisticsController.class);

    @Autowired
    private LogisticsService logisticsService;

    @Operation(summary = "创建物流信息")
    @PostMapping
    public Result<?> createLogistics(@RequestBody Logistics logistics) {
        Long userId = UserContext.getUserId();
        String role = UserContext.getRole();

        if (userId == null) {
            return Result.error("-1", "用户未登录");
        }
        // 只有商户和管理员可以创建物流信息
        if (!UserRole.isMerchant(role) && !UserRole.isAdmin(role)) {
            return Result.error("-1", "无权限创建物流信息");
        }
        return Result.success(logisticsService.createLogistics(logistics));
    }

    @Operation(summary = "更新物流状态")
    @PutMapping("/{id}/status")
    public Result<?> updateLogisticsStatus(@PathVariable Long id, @RequestParam Integer status) {
        String role = UserContext.getRole();

        if (role == null) {
            return Result.error("-1", "用户未登录");
        }
        // 只有商户和管理员可以更新物流状态
        if (!UserRole.isMerchant(role) && !UserRole.isAdmin(role)) {
            return Result.error("-1", "无权限更新物流状态");
        }
        logisticsService.updateLogisticsStatus(id, status);
        return Result.success();
    }

    @Operation(summary = "删除物流信息")
    @DeleteMapping("/{id}")
    public Result<?> deleteLogistics(@PathVariable Long id) {
        String role = UserContext.getRole();

        if (role == null) {
            return Result.error("-1", "用户未登录");
        }
        // 只有管理员可以删除物流信息
        if (!UserRole.isAdmin(role)) {
            return Result.error("-1", "无权限删除物流信息");
        }
        logisticsService.deleteLogistics(id);
        return Result.success();
    }

    @Operation(summary = "根据ID获取物流详情")
    @GetMapping("/{id}")
    public Result<?> getLogisticsById(@PathVariable Long id) {
        return Result.success(logisticsService.getLogisticsById(id));
    }

    @Operation(summary = "根据订单ID获取物流信息")
    @GetMapping("/order/{orderId}")
    public Result<?> getLogisticsByOrderId(@PathVariable Long orderId) {
        return Result.success(logisticsService.getLogisticsByOrderId(orderId));
    }

    @Operation(summary = "分页查询物流信息")
    @GetMapping("/page")
    public Result<?> getLogisticsByPage(
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "10") Integer size) {

        Long userId = UserContext.getUserId();
        String role = UserContext.getRole();
        Long merchantId = null;

        if (userId != null && UserRole.isMerchant(role)) {
            // 商户只能查看自己店铺的物流信息
            merchantId = userId;
        }

        return Result.success(logisticsService.getLogisticsByPage(orderId, merchantId, status, currentPage, size));
    }

    @Operation(summary = "批量删除物流信息")
    @DeleteMapping("/batch")
    public Result<?> deleteBatch(@RequestParam List<Long> ids) {
        String role = UserContext.getRole();

        if (role == null) {
            return Result.error("-1", "用户未登录");
        }
        // 只有管理员可以批量删除
        if (!UserRole.isAdmin(role)) {
            return Result.error("-1", "无权限批量删除物流信息");
        }
        logisticsService.deleteBatch(ids);
        return Result.success();
    }

    @Operation(summary = "确认签收")
    @PutMapping("/{id}/sign")
    public Result<?> signLogistics(@PathVariable Long id) {
        Long userId = UserContext.getUserId();

        if (userId == null) {
            return Result.error("-1", "用户未登录");
        }
        // 确认签收应该由订单所有者操作
        logisticsService.signLogistics(id, userId);
        return Result.success();
    }
}
