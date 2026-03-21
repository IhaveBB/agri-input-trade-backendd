package org.example.springboot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.springboot.annotation.RequiresRole;
import org.example.springboot.common.Result;
import org.example.springboot.entity.Cart;
import org.example.springboot.enumClass.UserRole;
import org.example.springboot.enums.ErrorCodeEnum;
import org.example.springboot.exception.BusinessException;
import org.example.springboot.service.CartService;
import org.example.springboot.util.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 购物车管理控制器
 * 提供购物车的增删改查功能，包括权限验证
 *
 * @author IhaveBB
 * @date 2026/03/19
 */
@Tag(name = "购物车管理接口")
@RestController
@RequestMapping("/cart")
public class CartController {
    private static final Logger LOGGER = LoggerFactory.getLogger(CartController.class);

    @Autowired
    private CartService cartService;

    /**
     * 添加商品到购物车
     * 权限：需要登录，强制使用当前用户ID
     *
     * @param cart 购物车实体（包含商品ID和数量）
     * @return 添加后的购物车记录
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "添加商品到购物车")
    @RequiresRole
    @PostMapping
    public Result<?> addToCart(@RequestBody Cart cart) {
        Long currentUserId = UserContext.getUserId();
        // 强制使用当前登录用户ID，防止伪造
        cart.setUserId(currentUserId);
        return Result.success(cartService.addToCart(cart));
    }

    /**
     * 更新购物车商品数量
     * 权限：需要登录，只能修改自己的购物车
     *
     * @param id       购物车项ID
     * @param quantity 新数量
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "更新购物车商品数量")
    @RequiresRole
    @PutMapping("/{id}")
    public Result<?> updateCartItem(@PathVariable Long id, @RequestParam Integer quantity) {
        Long currentUserId = UserContext.getUserId();
        String role = UserContext.getRole();

        // 验证购物车项是否属于当前用户（管理员可以操作任意购物车）
        Cart cart = cartService.getCartById(id);
        if (cart == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "购物车记录不存在");
        }
        if (!UserRole.isAdmin(role) && !cart.getUserId().equals(currentUserId)) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权限修改他人购物车");
        }

        cartService.updateCartItem(id, quantity);
        return Result.success();
    }

    /**
     * 删除购物车商品
     * 权限：需要登录，只能删除自己的购物车
     *
     * @param id 购物车项ID
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "删除购物车商品")
    @RequiresRole
    @DeleteMapping("/{id}")
    public Result<?> deleteCartItem(@PathVariable Long id) {
        Long currentUserId = UserContext.getUserId();
        String role = UserContext.getRole();

        // 验证购物车项是否属于当前用户（管理员可以操作任意购物车）
        Cart cart = cartService.getCartById(id);
        if (cart == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "购物车记录不存在");
        }
        if (!UserRole.isAdmin(role) && !cart.getUserId().equals(currentUserId)) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权限删除他人购物车");
        }

        cartService.deleteCartItem(id);
        return Result.success();
    }

    /**
     * 根据用户ID获取购物车
     * 权限：需要登录，普通用户只能查看自己的购物车
     *
     * @param userId 用户ID
     * @return 购物车列表
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "根据用户ID获取购物车")
    @RequiresRole
    @GetMapping("/user/{userId}")
    public Result<?> getCartByUserId(@PathVariable Long userId) {
        Long currentUserId = UserContext.getUserId();
        String role = UserContext.getRole();

        // 普通用户只能查看自己的购物车
        if (!UserRole.isAdmin(role) && !currentUserId.equals(userId)) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权限查看他人购物车");
        }

        return Result.success(cartService.getCartByUserId(userId));
    }

    /**
     * 清空购物车
     * 权限：需要登录，普通用户只能清空自己的购物车
     *
     * @param userId 用户ID
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "清空购物车")
    @RequiresRole
    @DeleteMapping("/clear/{userId}")
    public Result<?> clearCart(@PathVariable Long userId) {
        Long currentUserId = UserContext.getUserId();
        String role = UserContext.getRole();

        // 普通用户只能清空自己的购物车
        if (!UserRole.isAdmin(role) && !currentUserId.equals(userId)) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权限清空他人购物车");
        }

        cartService.clearCart(userId);
        return Result.success();
    }

    /**
     * 分页查询购物车
     * 权限：需要登录，普通用户只能查看自己的购物车
     *
     * @param userId      用户ID（可选）
     * @param productName 商品名称（可选）
     * @param currentPage 当前页
     * @param size        每页大小
     * @return 分页购物车列表
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "分页查询购物车")
    @RequiresRole
    @GetMapping("/page")
    public Result<?> getCartByPage(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String productName,
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "10") Integer size) {
        Long currentUserId = UserContext.getUserId();
        String role = UserContext.getRole();

        // 普通用户强制只能查看自己的购物车
        if (UserRole.isUser(role)) {
            userId = currentUserId;
        }
        // 商户和管理员可以查看指定用户的购物车

        return Result.success(cartService.getCartByPage(userId, productName, currentPage, size));
    }

    /**
     * 批量删除购物车项
     * 权限：需要登录，只能删除自己的购物车
     *
     * @param ids 购物车项ID列表
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "批量删除购物车项")
    @RequiresRole
    @DeleteMapping("/batch")
    public Result<?> deleteBatch(@RequestParam List<Long> ids) {
        Long currentUserId = UserContext.getUserId();
        String role = UserContext.getRole();

        // 验证所有购物车项是否属于当前用户（管理员可以删除任意购物车项）
        if (!UserRole.isAdmin(role)) {
            for (Long id : ids) {
                Cart cart = cartService.getCartById(id);
                if (cart != null && !cart.getUserId().equals(currentUserId)) {
                    throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权限删除他人购物车");
                }
            }
        }

        cartService.deleteBatch(ids);
        return Result.success();
    }
} 