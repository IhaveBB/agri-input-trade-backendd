package org.example.springboot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.springboot.annotation.RequiresRole;
import org.example.springboot.common.Result;
import org.example.springboot.entity.Logistics;
import org.example.springboot.entity.Order;
import org.example.springboot.entity.OrderBatchRequest;
import org.example.springboot.enumClass.UserRole;
import org.example.springboot.enums.ErrorCodeEnum;
import org.example.springboot.exception.BusinessException;
import org.example.springboot.service.OrderService;
import org.example.springboot.util.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 订单管理控制器
 * 提供订单的创建、查询、修改、删除等功能
 *
 * @author IhaveBB
 * @date 2026/03/19
 */
@Tag(name = "订单管理接口")
@RestController
@RequestMapping("/order")
public class OrderController {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrderController.class);

    @Autowired
    private OrderService orderService;

    /**
     * 创建订单
     * 权限：需要登录
     *
     * @param order 订单实体
     * @return 创建的订单
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "创建订单")
    @RequiresRole
    @PostMapping
    public Result<Order> createOrder(@RequestBody Order order) {
        return Result.success(orderService.createOrder(order));
    }

    /**
     * 更新订单状态
     * 权限：需要登录，复杂权限验证
     * - 普通用户只能取消自己的订单
     * - 商户只能修改自己店铺订单的状态
     * - 管理员可以修改任何订单
     *
     * @param id     订单ID
     * @param status 新状态
     * @return 更新后的订单
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "更新订单状态")
    @RequiresRole
    @PutMapping("/{id}/status")
    public Result<Order> updateOrderStatus(@PathVariable Long id, @RequestParam Integer status) {
        Long userId = UserContext.getUserId();
        String role = UserContext.getRole();

        Order order = orderService.getOrderById(id);

        // 权限检查
        if (UserRole.isUser(role)) {
            // 农户只能修改自己的订单
            if (!order.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权限修改他人订单");
            }
            // 农户只能：取消订单（状态0）或确认收货（状态3，且原状态必须是已发货2）
            if (status == 0) {
                // 取消订单，允许
            } else if (status == 3 && order.getStatus() == 2) {
                // 确认收货，允许
            } else {
                throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权进行此操作");
            }
        } else if (UserRole.isMerchant(role)) {
            // 商户只能修改自己店铺订单的状态
            if (!orderService.isOrderBelongToMerchant(order.getId(), userId)) {
                throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权限修改非自己店铺的订单");
            }
        }
        // 管理员可以修改任何订单状态

        return Result.success(orderService.updateOrderStatus(id, status));
    }

    /**
     * 支付订单
     * 权限：需要登录，只能支付自己的订单
     *
     * @param id 订单ID
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "支付订单")
    @RequiresRole
    @PutMapping("/{id}/pay")
    public Result<Void> pay(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        String role = UserContext.getRole();

        Order order = orderService.getOrderById(id);

        // 权限检查：只能支付自己的订单，或者是管理员
        if (!order.getUserId().equals(userId) && !UserRole.isAdmin(role)) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权限支付他人订单");
        }

        orderService.payOrder(id);
        return Result.success();
    }

    /**
     * 删除订单
     * 权限：只有管理员
     *
     * @param id 订单ID
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "删除订单")
    @RequiresRole("ADMIN")
    @DeleteMapping("/{id}")
    public Result<Void> deleteOrder(@PathVariable Long id) {
        orderService.deleteOrder(id);
        return Result.success();
    }

    /**
     * 根据ID获取订单详情
     * 权限：需要登录，只能查看自己的订单或商户查看自己店铺的订单
     *
     * @param id 订单ID
     * @return 订单详情
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "根据ID获取订单详情")
    @RequiresRole
    @GetMapping("/{id}")
    public Result<Order> getOrderById(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        String role = UserContext.getRole();

        Order order = orderService.getOrderById(id);

        // 权限检查
        if (UserRole.isUser(role)) {
            if (!order.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权限查看他人订单");
            }
        } else if (UserRole.isMerchant(role)) {
            if (!orderService.isOrderBelongToMerchant(id, userId)) {
                throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权限查看非自己店铺的订单");
            }
        }
        // 管理员可以查看所有订单

        return Result.success(order);
    }

    /**
     * 根据用户ID获取订单列表
     * 权限：需要登录，普通用户只能查看自己的订单
     *
     * @param userId 用户ID
     * @return 订单列表
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "根据用户ID获取订单列表")
    @RequiresRole
    @GetMapping("/user/{userId}")
    public Result<List<Order>> getOrdersByUserId(@PathVariable Long userId) {
        Long currentUserId = UserContext.getUserId();
        String role = UserContext.getRole();

        // 权限检查：普通用户只能查看自己的订单
        if (UserRole.isUser(role) && !currentUserId.equals(userId)) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权限查看其他用户的订单");
        }
        // 商户和管理员可以查看任意用户的订单

        return Result.success(orderService.getOrdersByUserId(userId));
    }

    /**
     * 分页查询订单列表
     * 权限：需要登录，根据角色自动过滤
     *
     * @param id          订单ID
     * @param status      订单状态
     * @param currentPage 当前页
     * @param size        每页大小
     * @return 分页订单列表
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "分页查询订单列表")
    @RequiresRole
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

        // 根据角色设置查询条件
        if (UserRole.isUser(role)) {
            // 农户只能查看自己的订单
            queryUserId = userId;
        } else if (UserRole.isMerchant(role)) {
            // 商户只能查看自己店铺的订单
            merchantId = userId;
        }
        // 管理员不设置过滤条件，可以查看全部

        return Result.success(orderService.getOrdersByPage(queryUserId, id, status, merchantId, currentPage, size));
    }

    /**
     * 申请退款
     * 权限：需要登录，只能对自己的订单申请退款
     *
     * @param id     订单ID
     * @param reason 退款原因
     * @return 更新后的订单
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "申请退款")
    @RequiresRole
    @PostMapping("/{id}/refund")
    public Result<Order> refundOrder(@PathVariable Long id, @RequestParam String reason) {
        Long userId = UserContext.getUserId();
        String role = UserContext.getRole();

        Order order = orderService.getOrderById(id);

        // 权限检查：只能对自己的订单申请退款，或者是管理员
        if (!order.getUserId().equals(userId) && !UserRole.isAdmin(role)) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权对他人订单申请退款");
        }

        return Result.success(orderService.refundOrder(id, reason));
    }

    /**
     * 批量删除订单
     * 权限：只有管理员
     *
     * @param ids 订单ID列表
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "批量删除订单")
    @RequiresRole("ADMIN")
    @DeleteMapping("/batch")
    public Result<Void> deleteBatch(@RequestParam List<Long> ids) {
        orderService.deleteBatch(ids);
        return Result.success();
    }

    /**
     * 更新订单收货信息
     * 权限：需要登录，只能修改自己订单的收货信息
     *
     * @param id      订单ID
     * @param name    收货人姓名
     * @param address 收货地址
     * @param phone   联系电话
     * @return 更新后的订单
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "更新订单收货信息")
    @RequiresRole
    @PutMapping("/{id}/address")
    public Result<Order> updateOrderAddress(
            @PathVariable Long id,
            @RequestParam String name,
            @RequestParam String address,
            @RequestParam String phone) {

        Long userId = UserContext.getUserId();
        String role = UserContext.getRole();

        Order order = orderService.getOrderById(id);

        // 权限检查：只有订单所有者可以修改收货信息
        if (!order.getUserId().equals(userId) && !UserRole.isAdmin(role)) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权限修改他人订单的收货信息");
        }

        return Result.success(orderService.updateOrderAddress(name, id, address, phone));
    }

    /**
     * 更新订单信息
     * 权限：需要登录，复杂权限验证
     *
     * @param id    订单ID
     * @param order 订单实体
     * @return 更新后的订单
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "更新订单信息")
    @RequiresRole
    @PutMapping("/{id}")
    public Result<Order> updateOrder(@PathVariable Long id, @RequestBody Order order) {
        Long userId = UserContext.getUserId();
        String role = UserContext.getRole();

        Order existingOrder = orderService.getOrderById(id);

        // 权限检查
        if (UserRole.isUser(role)) {
            if (!existingOrder.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权限修改他人订单");
            }
        } else if (UserRole.isMerchant(role)) {
            if (!orderService.isOrderBelongToMerchant(id, userId)) {
                throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权限修改非自己店铺的订单");
            }
        }
        // 管理员可以修改任何订单

        return Result.success(orderService.updateOrder(id, order));
    }

    /**
     * 获取订单物流信息
     * 权限：需要登录，只能查看自己订单的物流
     *
     * @param id 订单ID
     * @return 物流信息
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "获取订单物流信息")
    @RequiresRole
    @GetMapping("/{id}/logistics")
    public Result<Logistics> getOrderLogistics(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        String role = UserContext.getRole();

        Order order = orderService.getOrderById(id);

        // 权限检查
        if (UserRole.isUser(role)) {
            if (!order.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权限查看他人订单物流");
            }
        } else if (UserRole.isMerchant(role)) {
            if (!orderService.isOrderBelongToMerchant(id, userId)) {
                throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权限查看非自己店铺的订单物流");
            }
        }
        // 管理员可以查看所有订单物流

        return Result.success(orderService.getOrderLogistics(id));
    }

    /**
     * 处理退款申请
     * 权限：商户或管理员，商户只能处理自己店铺的退款
     *
     * @param id     订单ID
     * @param status 退款状态
     * @param remark 备注
     * @return 更新后的订单
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "处理退款申请")
    @RequiresRole({"MERCHANT", "ADMIN"})
    @PutMapping("/{id}/handle-refund")
    public Result<Order> handleRefund(
            @PathVariable Long id,
            @RequestParam Integer status,
            @RequestParam String remark) {
        Long userId = UserContext.getUserId();
        String role = UserContext.getRole();

        // 商户只能处理自己店铺订单的退款
        if (UserRole.isMerchant(role)) {
            if (!orderService.isOrderBelongToMerchant(id, userId)) {
                throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权限处理非自己店铺的退款申请");
            }
        }
        // 管理员可以处理所有退款申请

        return Result.success(orderService.handleRefund(id, status, remark));
    }

    /**
     * 获取订单状态
     * 权限：需要登录，只能查看自己订单的状态
     *
     * @param id 订单ID
     * @return 订单状态
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "获取订单状态")
    @RequiresRole
    @GetMapping("/{id}/status")
    public Result<Integer> getOrderStatus(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        String role = UserContext.getRole();

        Order order = orderService.getOrderById(id);

        // 权限检查
        if (UserRole.isUser(role)) {
            if (!order.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权限查看他人订单状态");
            }
        } else if (UserRole.isMerchant(role)) {
            if (!orderService.isOrderBelongToMerchant(id, userId)) {
                throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权限查看非自己店铺的订单状态");
            }
        }
        // 管理员可以查看所有订单状态

        return Result.success(order.getStatus());
    }

    /**
     * 批量创建订单（从购物车下单）
     * 权限：需要登录，用户ID必须匹配
     *
     * @param request 批量创建订单请求
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "批量创建订单（从购物车下单）")
    @RequiresRole
    @PostMapping("/batch")
    public Result<Void> batchCreateOrders(@RequestBody OrderBatchRequest request) {
        Long currentUserId = UserContext.getUserId();

        // 验证用户 ID 是否匹配
        if (!currentUserId.equals(request.getUserId())) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "用户 ID 不匹配");
        }

        orderService.batchCreateOrders(request);
        return Result.success();
    }
}
