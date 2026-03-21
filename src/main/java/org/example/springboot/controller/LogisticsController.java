package org.example.springboot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.springboot.annotation.RequiresRole;
import org.example.springboot.common.Result;
import org.example.springboot.entity.Logistics;
import org.example.springboot.enumClass.UserRole;
import org.example.springboot.enums.ErrorCodeEnum;
import org.example.springboot.exception.BusinessException;
import org.example.springboot.service.LogisticsService;
import org.example.springboot.util.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 物流管理控制器
 * 提供物流的增删改查功能
 *
 * @author IhaveBB
 * @date 2026/03/19
 */
@Tag(name = "物流管理接口")
@RestController
@RequestMapping("/logistics")
public class LogisticsController {
    private static final Logger LOGGER = LoggerFactory.getLogger(LogisticsController.class);

    @Autowired
    private LogisticsService logisticsService;

    /**
     * 创建物流信息
     * 权限：商户或管理员
     *
     * @param logistics 物流实体
     * @return 创建结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "创建物流信息")
    @RequiresRole({"MERCHANT", "ADMIN"})
    @PostMapping
    public Result<?> createLogistics(@RequestBody Logistics logistics) {
        return Result.success(logisticsService.createLogistics(logistics));
    }

    /**
     * 更新物流状态
     * 权限：商户或管理员
     *
     * @param id     物流ID
     * @param status 状态
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "更新物流状态")
    @RequiresRole({"MERCHANT", "ADMIN"})
    @PutMapping("/{id}/status")
    public Result<?> updateLogisticsStatus(@PathVariable Long id, @RequestParam Integer status) {
        logisticsService.updateLogisticsStatus(id, status);
        return Result.success();
    }

    /**
     * 删除物流信息
     * 权限：只有管理员
     *
     * @param id 物流ID
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "删除物流信息")
    @RequiresRole("ADMIN")
    @DeleteMapping("/{id}")
    public Result<?> deleteLogistics(@PathVariable Long id) {
        logisticsService.deleteLogistics(id);
        return Result.success();
    }

    /**
     * 根据ID获取物流详情
     * 无需权限验证
     *
     * @param id 物流ID
     * @return 物流详情
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "根据ID获取物流详情")
    @GetMapping("/{id}")
    public Result<?> getLogisticsById(@PathVariable Long id) {
        return Result.success(logisticsService.getLogisticsById(id));
    }

    /**
     * 根据订单ID获取物流信息
     * 无需权限验证
     *
     * @param orderId 订单ID
     * @return 物流信息
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "根据订单ID获取物流信息")
    @GetMapping("/order/{orderId}")
    public Result<?> getLogisticsByOrderId(@PathVariable Long orderId) {
        return Result.success(logisticsService.getLogisticsByOrderId(orderId));
    }

    /**
     * 分页查询物流信息
     * 权限：需要登录，商户只能查看自己店铺的物流
     *
     * @param orderId     订单ID
     * @param status      状态
     * @param currentPage 当前页
     * @param size        每页大小
     * @return 分页物流列表
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "分页查询物流信息")
    @RequiresRole
    @GetMapping("/page")
    public Result<?> getLogisticsByPage(
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "10") Integer size) {

        Long userId = UserContext.getUserId();
        String role = UserContext.getRole();
        Long merchantId = null;

        if (UserRole.isMerchant(role)) {
            // 商户只能查看自己店铺的物流信息
            merchantId = userId;
        }

        return Result.success(logisticsService.getLogisticsByPage(orderId, merchantId, status, currentPage, size));
    }

    /**
     * 批量删除物流信息
     * 权限：只有管理员
     *
     * @param ids 物流ID列表
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "批量删除物流信息")
    @RequiresRole("ADMIN")
    @DeleteMapping("/batch")
    public Result<?> deleteBatch(@RequestParam List<Long> ids) {
        logisticsService.deleteBatch(ids);
        return Result.success();
    }

    /**
     * 确认签收
     * 权限：需要登录
     *
     * @param id 物流ID
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "确认签收")
    @RequiresRole
    @PutMapping("/{id}/sign")
    public Result<?> signLogistics(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        // 确认签收应该由订单所有者操作
        return Result.success(logisticsService.signLogistics(id, userId));
    }
}
