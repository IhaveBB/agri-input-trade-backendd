package org.example.springboot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.springboot.common.Result;
import org.example.springboot.entity.Order;
import org.example.springboot.enumClass.UserRole;
import org.example.springboot.mapper.OrderMapper;
import org.example.springboot.service.OrderService;
import org.example.springboot.util.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "订单管理接口")
@RestController
@RequestMapping("/order")
public class OrderController {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrderController.class);

    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderMapper orderMapper;

    @Operation(summary = "创建订单")
    @PostMapping
    public Result<?> createOrder(@RequestBody Order order) {
        return orderService.createOrder(order);
    }

    @Operation(summary = "更新订单状态")
    @PutMapping("/{id}/status")
    public Result<?> updateOrderStatus(@PathVariable Long id, @RequestParam Integer status) {
        Long userId = UserContext.getUserId();
        String role = UserContext.getRole();

        if (userId == null) {
            return Result.error("-1", "用户未登录");
        }

        // 获取订单信息进行权限验证
        Order order = orderMapper.selectById(id);
        if (order == null) {
            return Result.error("-1", "订单不存在");
        }

        // 权限检查
        if (UserRole.isUser(role)) {
            // 农户只能修改自己的订单状态，且只能是取消订单
            if (!order.getUserId().equals(userId)) {
                return Result.error("-1", "无权限修改他人订单");
            }
            // 农户只能取消订单（状态设为0或其他允许的状态）
            if (status != 0) {
                return Result.error("-1", "农户只能取消订单");
            }
        } else if (UserRole.isMerchant(role)) {
            // 商户只能修改自己店铺订单的状态
            if (!orderService.isOrderBelongToMerchant(order.getId(), userId)) {
                return Result.error("-1", "无权限修改非自己店铺的订单");
            }
        }
        // 管理员可以修改任何订单状态

        return orderService.updateOrderStatus(id, status);
    }


    @Operation(summary = "更新订单状态")
    @PutMapping("/{id}/pay")
    public Result<?> pay(@PathVariable Long id) {
        return orderService.payOrder(id);
    }

    @Operation(summary = "删除订单")
    @DeleteMapping("/{id}")
    public Result<?> deleteOrder(@PathVariable Long id) {
        String role = UserContext.getRole();

        if (role == null) {
            return Result.error("-1", "用户未登录");
        }

        // 只有管理员可以删除订单
        if (!UserRole.isAdmin(role)) {
            return Result.error("-1", "无权限删除订单，只有管理员可以删除");
        }

        return orderService.deleteOrder(id);
    }

    @Operation(summary = "根据ID获取订单详情")
    @GetMapping("/{id}")
    public Result<?> getOrderById(@PathVariable Long id) {
        return orderService.getOrderById(id);
    }

    @Operation(summary = "根据用户ID获取订单列表")
    @GetMapping("/user/{userId}")
    public Result<?> getOrdersByUserId(@PathVariable Long userId) {
        Long currentUserId = UserContext.getUserId();
        String role = UserContext.getRole();

        if (currentUserId == null) {
            return Result.error("-1", "用户未登录");
        }

        // 权限检查：只有管理员可以查看任意用户的订单
        if (UserRole.isUser(role) && !currentUserId.equals(userId)) {
            return Result.error("-1", "无权限查看其他用户的订单");
        }

        return orderService.getOrdersByUserId(userId);
    }

    @Operation(summary = "分页查询订单列表")
    @GetMapping("/page")
    public Result<?> getOrdersByPage(
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "10") Integer size) {

        Long userId = UserContext.getUserId();
        String role = UserContext.getRole();
        Long queryUserId = null;
        Long merchantId = null;

        if (userId == null) {
            return Result.error("-1", "用户未登录");
        }

        // 根据角色设置查询条件
        if (UserRole.isUser(role)) {
            // 农户只能查看自己的订单
            queryUserId = userId;
        } else if (UserRole.isMerchant(role)) {
            // 商户只能查看自己店铺的订单
            merchantId = userId;
        }
        // 管理员不设置过滤条件，可以查看全部

        return orderService.getOrdersByPage(queryUserId, id, status, merchantId, currentPage, size);
    }

    @Operation(summary = "申请退款")
    @PostMapping("/{id}/refund")
    public Result<?> refundOrder(@PathVariable Long id, @RequestParam String reason) {
        return orderService.refundOrder(id, reason);
    }

    @Operation(summary = "批量删除订单")
    @DeleteMapping("/batch")
    public Result<?> deleteBatch(@RequestParam List<Long> ids) {
        String role = UserContext.getRole();

        if (role == null) {
            return Result.error("-1", "用户未登录");
        }

        // 只有管理员可以批量删除订单
        if (!UserRole.isAdmin(role)) {
            return Result.error("-1", "无权限批量删除订单，只有管理员可以删除");
        }

        return orderService.deleteBatch(ids);
    }

    @Operation(summary = "更新订单收货信息")
    @PutMapping("/{id}/address")
    public Result<?> updateOrderAddress(
            @PathVariable Long id,
            @RequestParam String name,
            @RequestParam String address,
            @RequestParam String phone) {

        Long userId = UserContext.getUserId();
        String role = UserContext.getRole();

        if (userId == null) {
            return Result.error("-1", "用户未登录");
        }

        // 获取订单信息
        Order order = orderMapper.selectById(id);
        if (order == null) {
            return Result.error("-1", "订单不存在");
        }

        // 权限检查：只有订单所有者可以修改收货信息
        if (!order.getUserId().equals(userId) && !UserRole.isAdmin(role)) {
            return Result.error("-1", "无权限修改他人订单的收货信息");
        }

        return orderService.updateOrderAddress(name, id, address, phone);
    }

    // 更新订单信息
    @Operation(summary = "更新订单信息")
    @PutMapping("/{id}")
    public Result<?> updateOrder(@PathVariable Long id, @RequestBody Order order) {
        Long userId = UserContext.getUserId();
        String role = UserContext.getRole();

        if (userId == null) {
            return Result.error("-1", "用户未登录");
        }

        // 获取订单信息
        Order existingOrder = orderMapper.selectById(id);
        if (existingOrder == null) {
            return Result.error("-1", "订单不存在");
        }

        // 权限检查
        if (UserRole.isUser(role)) {
            if (!existingOrder.getUserId().equals(userId)) {
                return Result.error("-1", "无权限修改他人订单");
            }
        } else if (UserRole.isMerchant(role)) {
            if (!orderService.isOrderBelongToMerchant(id, userId)) {
                return Result.error("-1", "无权限修改非自己店铺的订单");
            }
        }
        // 管理员可以修改任何订单

        return orderService.updateOrder(id, order);
    }

    @Operation(summary = "获取订单物流信息")
    @GetMapping("/{id}/logistics")
    public Result<?> getOrderLogistics(@PathVariable Long id) {
        return orderService.getOrderLogistics(id);
    }

    @Operation(summary = "处理退款申请")
    @PutMapping("/{id}/handle-refund")
    public Result<?> handleRefund(
            @PathVariable Long id,
            @RequestParam Integer status,
            @RequestParam String remark) {
        return orderService.handleRefund(id, status, remark);
    }

    /**
     * 获取订单状态
     */
    @GetMapping("/{id}/status")
    @Operation(summary = "获取订单状态")
    public Result<Integer> getOrderStatus(@PathVariable Long id) {
        // 查询订单
        Order order = orderMapper.selectById(id);
        if (order == null) {
            return Result.error("-1","订单不存在");
        }
        // 返回订单状态
        return Result.success(order.getStatus());
    }
} 