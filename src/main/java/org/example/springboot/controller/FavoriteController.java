package org.example.springboot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.springboot.common.Result;
import org.example.springboot.entity.Favorite;
import org.example.springboot.service.FavoriteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "收藏管理接口")
@RestController
@RequestMapping("/favorite")
public class FavoriteController {
    private static final Logger LOGGER = LoggerFactory.getLogger(FavoriteController.class);

    @Autowired
    private FavoriteService favoriteService;

    @Operation(summary = "创建收藏")
    @PostMapping
    public Result<?> createFavorite(@RequestBody Favorite favorite) {
        return Result.success(favoriteService.createFavorite(favorite));
    }

    @Operation(summary = "更新收藏状态")
    @PutMapping("/{userId}/{productId}/status")
    public Result<?> updateFavoriteStatus(@PathVariable Long userId, @PathVariable Long productId, @RequestParam Integer status) {
        favoriteService.updateFavoriteStatus(userId, productId, status);
        return Result.success();
    }

    @Operation(summary = "删除收藏")
    @DeleteMapping("/{id}")
    public Result<?> deleteFavorite(@PathVariable Long id) {
        favoriteService.deleteFavorite(id);
        return Result.success();
    }

    @Operation(summary = "根据ID获取收藏详情")
    @GetMapping("/{id}")
    public Result<?> getFavoriteById(@PathVariable Long id) {
        return Result.success(favoriteService.getFavoriteById(id));
    }

    @Operation(summary = "根据用户ID获取收藏列表")
    @GetMapping("/user/{userId}")
    public Result<?> getFavoritesByUserId(@PathVariable Long userId) {
        return Result.success(favoriteService.getFavoritesByUserId(userId));
    }

    @Operation(summary = "分页查询收藏列表")
    @GetMapping("/page")
    public Result<?> getFavoritesByPage(
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(favoriteService.getFavoritesByPage(userId, currentPage, size));
    }

    @Operation(summary = "批量删除收藏")
    @DeleteMapping("/batch")
    public Result<?> deleteBatch(@RequestParam List<Long> ids) {
        favoriteService.deleteBatch(ids);
        return Result.success();
    }
} 