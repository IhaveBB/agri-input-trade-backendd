package org.example.springboot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.springboot.common.Result;
import org.example.springboot.entity.Logistics;
import org.example.springboot.entity.Order;
import org.example.springboot.entity.OrderBatchRequest;
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

    /**
     * 创建订单
     *
     * @param order 订单实体
     * @return 创建的订单
     * @author IhaveBB
     * @date 2026/03/18
     */
    @Operation(summary = "创建订单")
    @PostMapping
    public Result<Order> createOrder(@RequestBody Order order) {
        return Result.success(orderService.createOrder(order));
    }

    /**
     * 更新订单状态
     *
     * @param id     订单ID
     * @param status 新状态
     * @return 更新后的订单
     * @author IhaveBB
     * @date 2026/03/18
     */
    @Operation(summary = "更新订单状态")
    @PutMapping("/{id}/status")
    public Result<Order> updateOrderStatus(@PathVariable Long id, @RequestParam Integer status) {
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

        return Result.success(orderService.updateOrderStatus(id, status));
    }


    /**
     * 支付订单
     *
     * @param id 订单ID
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/18
     */
    @Operation(summary = "支付订单")
    @PutMapping("/{id}/pay")
    public Result<Void> pay(@PathVariable Long id) {
        orderService.payOrder(id);
        return Result.success();
    }

    /**
     * 删除订单
     *
     * @param id 订单ID
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/18
     */
    @Operation(summary = "删除订单")
    @DeleteMapping("/{id}")
    public Result<Void> deleteOrder(@PathVariable Long id) {
        String role = UserContext.getRole();

        if (role == null) {
            return Result.error("-1", "用户未登录");
        }

        // 只有管理员可以删除订单
        if (!UserRole.isAdmin(role)) {
            return Result.error("-1", "无权限删除订单，只有管理员可以删除");
        }

        orderService.deleteOrder(id);
        return Result.success();
    }

    /**
     * 根据ID获取订单详情
     *
     * @param id 订单ID
     * @return 订单详情
     * @author IhaveBB
     * @date 2026/03/18
     */
    @Operation(summary = "根据ID获取订单详情")
    @GetMapping("/{id}")
    public Result<Order> getOrderById(@PathVariable Long id) {
        return Result.success(orderService.getOrderById(id));
    }

    /**
     * 根据用户ID获取订单列表
     *
     * @param userId 用户ID
     * @return 订单列表
     * @author IhaveBB
     * @date 2026/03/18
     */
    @Operation(summary = "根据用户ID获取订单列表")
    @GetMapping("/user/{userId}")
    public Result<List<Order>> getOrdersByUserId(@PathVariable Long userId) {
        Long currentUserId = UserContext.getUserId();
        String role = UserContext.getRole();

        if (currentUserId == null) {
            return Result.error("-1", "用户未登录");
        }

        // 权限检查：只有管理员可以查看任意用户的订单
        if (UserRole.isUser(role) && !currentUserId.equals(userId)) {
            return Result.error("-1", "无权限查看其他用户的订单");
        }

        return Result.success(orderService.getOrdersByUserId(userId));
    }

    /**
     * 分页查询订单列表
     *
     * @param id          订单ID
     * @param status      订单状态
     * @param currentPage 当前页
     * @param size        每页大小
     * @return 分页订单列表
     * @author IhaveBB
     * @date 2026/03/18
     */
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

        return Result.success(orderService.getOrdersByPage(queryUserId, id, status, merchantId, currentPage, size));
    }

    /**
     * 申请退款
     *
     * @param id     订单ID
     * @param reason 退款原因
     * @return 更新后的订单
     * @author IhaveBB
     * @date 2026/03/18
     */
    @Operation(summary = "申请退款")
    @PostMapping("/{id}/refund")
    public Result<Order> refundOrder(@PathVariable Long id, @RequestParam String reason) {
        return Result.success(orderService.refundOrder(id, reason));
    }

    /**
     * 批量删除订单
     *
     * @param ids 订单ID列表
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/18
     */
    @Operation(summary = "批量删除订单")
    @DeleteMapping("/batch")
    public Result<Void> deleteBatch(@RequestParam List<Long> ids) {
        String role = UserContext.getRole();

        if (role == null) {
            return Result.error("-1", "用户未登录");
        }

        // 只有管理员可以批量删除订单
        if (!UserRole.isAdmin(role)) {
            return Result.error("-1", "无权限批量删除订单，只有管理员可以删除");
        }

        orderService.deleteBatch(ids);
        return Result.success();
    }

    /**
     * 更新订单收货信息
     *
     * @param id      订单ID
     * @param name    收货人姓名
     * @param address 收货地址
     * @param phone   联系电话
     * @return 更新后的订单
     * @author IhaveBB
     * @date 2026/03/18
     */
    @Operation(summary = "更新订单收货信息")
    @PutMapping("/{id}/address")
    public Result<Order> updateOrderAddress(
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

        return Result.success(orderService.updateOrderAddress(name, id, address, phone));
    }

    /**
     * 更新订单信息
     *
     * @param id    订单ID
     * @param order 订单实体
     * @return 更新后的订单
     * @author IhaveBB
     * @date 2026/03/18
     */
    @Operation(summary = "更新订单信息")
    @PutMapping("/{id}")
    public Result<Order> updateOrder(@PathVariable Long id, @RequestBody Order order) {
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

        return Result.success(orderService.updateOrder(id, order));
    }

    /**
     * 获取订单物流信息
     *
     * @param id 订单ID
     * @return 物流信息
     * @author IhaveBB
     * @date 2026/03/18
     */
    @Operation(summary = "获取订单物流信息")
    @GetMapping("/{id}/logistics")
    public Result<Logistics> getOrderLogistics(@PathVariable Long id) {
        return Result.success(orderService.getOrderLogistics(id));
    }

    /**
     * 处理退款申请
     *
     * @param id     订单ID
     * @param status 退款状态
     * @param remark 备注
     * @return 更新后的订单
     * @author IhaveBB
     * @date 2026/03/18
     */
    @Operation(summary = "处理退款申请")
    @PutMapping("/{id}/handle-refund")
    public Result<Order> handleRefund(
            @PathVariable Long id,
            @RequestParam Integer status,
            @RequestParam String remark) {
        return Result.success(orderService.handleRefund(id, status, remark));
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

    /**
     * 批量创建订单（从购物车下单）
     *
     * @param request 批量创建订单请求
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/18
     */
    @Operation(summary = "批量创建订单（从购物车下单）")
    @PostMapping("/batch")
    public Result<Void> batchCreateOrders(@RequestBody OrderBatchRequest request) {
        Long currentUserId = UserContext.getUserId();
        if (currentUserId == null) {
            return Result.error("-1", "用户未登录");
        }

        // 验证用户 ID 是否匹配
        if (!currentUserId.equals(request.getUserId())) {
            return Result.error("-1", "用户 ID 不匹配");
        }

        orderService.batchCreateOrders(request);
        return Result.success();
    }
} 