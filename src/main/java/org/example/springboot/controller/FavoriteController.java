package org.example.springboot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.springboot.annotation.RequiresRole;
import org.example.springboot.common.Result;
import org.example.springboot.entity.Favorite;
import org.example.springboot.enumClass.UserRole;
import org.example.springboot.enums.ErrorCodeEnum;
import org.example.springboot.exception.BusinessException;
import org.example.springboot.service.FavoriteService;
import org.example.springboot.util.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 收藏管理控制器
 * 提供用户收藏的增删改查功能，包括权限验证
 *
 * @author IhaveBB
 * @date 2026/03/19
 */
@Tag(name = "收藏管理接口")
@RestController
@RequestMapping("/favorite")
public class FavoriteController {
    private static final Logger LOGGER = LoggerFactory.getLogger(FavoriteController.class);

    @Autowired
    private FavoriteService favoriteService;

    /**
     * 创建收藏
     * 权限：需要登录，强制使用当前用户ID
     *
     * @param favorite 收藏实体（包含商品ID）
     * @return 创建后的收藏记录
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "创建收藏")
    @RequiresRole
    @PostMapping
    public Result<?> createFavorite(@RequestBody Favorite favorite) {
        Long currentUserId = UserContext.getUserId();
        // 强制使用当前登录用户ID，防止伪造
        favorite.setUserId(currentUserId);
        return Result.success(favoriteService.createFavorite(favorite));
    }

    /**
     * 更新收藏状态
     * 权限：需要登录，只能修改自己的收藏
     *
     * @param userId    用户ID
     * @param productId 商品ID
     * @param status    状态
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "更新收藏状态")
    @RequiresRole
    @PutMapping("/{userId}/{productId}/status")
    public Result<?> updateFavoriteStatus(@PathVariable Long userId, @PathVariable Long productId, @RequestParam Integer status) {
        Long currentUserId = UserContext.getUserId();
        String role = UserContext.getRole();

        // 普通用户只能修改自己的收藏
        if (!UserRole.isAdmin(role) && !currentUserId.equals(userId)) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权限修改他人收藏");
        }

        favoriteService.updateFavoriteStatus(userId, productId, status);
        return Result.success();
    }

    /**
     * 删除收藏
     * 权限：需要登录，只能删除自己的收藏
     *
     * @param id 收藏ID
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "删除收藏")
    @RequiresRole
    @DeleteMapping("/{id}")
    public Result<?> deleteFavorite(@PathVariable Long id) {
        Long currentUserId = UserContext.getUserId();
        String role = UserContext.getRole();

        // 验证收藏是否属于当前用户（管理员可以删除任意收藏）
        Favorite favorite = favoriteService.getFavoriteById(id);
        if (favorite != null && !UserRole.isAdmin(role) && !favorite.getUserId().equals(currentUserId)) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权限删除他人收藏");
        }

        favoriteService.deleteFavorite(id);
        return Result.success();
    }

    /**
     * 根据ID获取收藏详情
     * 权限：需要登录，只能查看自己的收藏
     *
     * @param id 收藏ID
     * @return 收藏详情
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "根据ID获取收藏详情")
    @RequiresRole
    @GetMapping("/{id}")
    public Result<?> getFavoriteById(@PathVariable Long id) {
        Long currentUserId = UserContext.getUserId();
        String role = UserContext.getRole();

        // 验证收藏是否属于当前用户（管理员可以查看任意收藏）
        Favorite favorite = favoriteService.getFavoriteById(id);
        if (favorite != null && !UserRole.isAdmin(role) && !favorite.getUserId().equals(currentUserId)) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权限查看他人收藏");
        }

        return Result.success(favorite);
    }

    /**
     * 根据用户ID获取收藏列表
     * 权限：需要登录，普通用户只能查看自己的收藏
     *
     * @param userId 用户ID
     * @return 收藏列表
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "根据用户ID获取收藏列表")
    @RequiresRole
    @GetMapping("/user/{userId}")
    public Result<?> getFavoritesByUserId(@PathVariable Long userId) {
        Long currentUserId = UserContext.getUserId();
        String role = UserContext.getRole();

        // 普通用户只能查看自己的收藏
        if (!UserRole.isAdmin(role) && !currentUserId.equals(userId)) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权限查看他人收藏");
        }

        return Result.success(favoriteService.getFavoritesByUserId(userId));
    }

    /**
     * 分页查询收藏列表
     * 权限：需要登录，普通用户只能查看自己的收藏
     *
     * @param userId      用户ID（可选）
     * @param currentPage 当前页
     * @param size        每页大小
     * @return 分页收藏列表
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "分页查询收藏列表")
    @RequiresRole
    @GetMapping("/page")
    public Result<?> getFavoritesByPage(
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "10") Integer size) {
        Long currentUserId = UserContext.getUserId();
        String role = UserContext.getRole();

        // 普通用户强制只能查看自己的收藏
        if (UserRole.isUser(role)) {
            userId = currentUserId;
        }
        // 商户和管理员可以查看指定用户的收藏

        return Result.success(favoriteService.getFavoritesByPage(userId, currentPage, size));
    }

    /**
     * 批量删除收藏
     * 权限：需要登录，只能删除自己的收藏
     *
     * @param ids 收藏ID列表
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "批量删除收藏")
    @RequiresRole
    @DeleteMapping("/batch")
    public Result<?> deleteBatch(@RequestParam List<Long> ids) {
        Long currentUserId = UserContext.getUserId();
        String role = UserContext.getRole();

        // 验证所有收藏是否属于当前用户（管理员可以删除任意收藏）
        if (!UserRole.isAdmin(role)) {
            for (Long id : ids) {
                Favorite favorite = favoriteService.getFavoriteById(id);
                if (favorite != null && !favorite.getUserId().equals(currentUserId)) {
                    throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权限删除他人收藏");
                }
            }
        }

        favoriteService.deleteBatch(ids);
        return Result.success();
    }
}
