package org.example.springboot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.springboot.annotation.RequiresRole;
import org.example.springboot.common.Result;
import org.example.springboot.entity.CarouselItem;
import org.example.springboot.service.CarouselItemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 轮播图管理控制器
 * 提供轮播图的增删改查功能
 *
 * @author IhaveBB
 * @date 2026/03/19
 */
@Tag(name = "轮播图管理接口")
@RestController
@RequestMapping("/carousel")
public class CarouselItemController {
    private static final Logger LOGGER = LoggerFactory.getLogger(CarouselItemController.class);

    @Autowired
    private CarouselItemService carouselItemService;

    /**
     * 创建轮播图
     * 权限：只有管理员
     *
     * @param carouselItem 轮播图实体
     * @return 创建结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "创建轮播图")
    @RequiresRole("ADMIN")
    @PostMapping
    public Result<?> createCarouselItem(@RequestBody CarouselItem carouselItem) {
        return Result.success(carouselItemService.createCarouselItem(carouselItem));
    }

    /**
     * 更新轮播图
     * 权限：只有管理员
     *
     * @param id           轮播图ID
     * @param carouselItem 轮播图实体
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "更新轮播图")
    @RequiresRole("ADMIN")
    @PutMapping("/{id}")
    public Result<?> updateCarouselItem(@PathVariable Long id, @RequestBody CarouselItem carouselItem) {
        carouselItemService.updateCarouselItem(id, carouselItem);
        return Result.success();
    }

    /**
     * 删除轮播图
     * 权限：只有管理员
     *
     * @param id 轮播图ID
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "删除轮播图")
    @RequiresRole("ADMIN")
    @DeleteMapping("/{id}")
    public Result<?> deleteCarouselItem(@PathVariable Long id) {
        carouselItemService.deleteCarouselItem(id);
        return Result.success();
    }

    /**
     * 根据ID获取轮播图详情
     * 无需权限验证
     *
     * @param id 轮播图ID
     * @return 轮播图详情
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "根据ID获取轮播图详情")
    @GetMapping("/{id}")
    public Result<?> getCarouselItemById(@PathVariable Long id) {
        return Result.success(carouselItemService.getCarouselItemById(id));
    }

    /**
     * 获取所有启用的轮播图
     * 无需权限验证
     *
     * @return 轮播图列表
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "获取所有启用的轮播图")
    @GetMapping("/active")
    public Result<?> getActiveCarouselItems() {
        return Result.success(carouselItemService.getActiveCarouselItems());
    }

    /**
     * 分页查询轮播图列表
     * 无需权限验证
     *
     * @param currentPage 当前页
     * @param size        每页大小
     * @return 分页轮播图列表
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "分页查询轮播图列表")
    @GetMapping("/page")
    public Result<?> getCarouselItemsByPage(
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(carouselItemService.getCarouselItemsByPage(currentPage, size));
    }
} 