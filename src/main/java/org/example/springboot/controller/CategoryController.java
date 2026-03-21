package org.example.springboot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.springboot.annotation.RequiresRole;
import org.example.springboot.common.Result;
import org.example.springboot.entity.Category;
import org.example.springboot.enums.ErrorCodeEnum;
import org.example.springboot.exception.BusinessException;
import org.example.springboot.service.CategoryService;
import org.example.springboot.util.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 分类管理控制器
 * 提供分类的增删改查功能
 *
 * @author IhaveBB
 * @date 2026/03/19
 */
@Tag(name = "分类管理接口")
@RestController
@RequestMapping("/category")
public class CategoryController {
    private static final Logger LOGGER = LoggerFactory.getLogger(CategoryController.class);

    @Autowired
    private CategoryService categoryService;

    /**
     * 创建分类（管理员）
     * 权限：只有管理员
     *
     * @param category 分类实体
     * @return 创建后的分类
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "创建分类（管理员）")
    @RequiresRole("ADMIN")
    @PostMapping
    public Result<?> createCategory(@RequestBody Category category) {
        return Result.success(categoryService.createCategory(category, null));
    }

    /**
     * 商家申请新增自定义分类
     * 权限：需要登录
     *
     * @param category 分类实体
     * @return 申请结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "商家申请新增自定义分类")
    @RequiresRole
    @PostMapping("/custom")
    public Result<?> applyCustomCategory(@RequestBody Category category) {
        Long userId = UserContext.getUserId();
        return Result.success(categoryService.applyCustomCategory(category, userId));
    }

    /**
     * 更新分类信息
     * 权限：只有管理员
     *
     * @param id       分类ID
     * @param category 分类实体
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "更新分类信息")
    @RequiresRole("ADMIN")
    @PutMapping("/{id}")
    public Result<?> updateCategory(@PathVariable Long id, @RequestBody Category category) {
        categoryService.updateCategory(id, category);
        return Result.success();
    }

    /**
     * 删除分类
     * 权限：只有管理员
     *
     * @param id 分类ID
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "删除分类")
    @RequiresRole("ADMIN")
    @DeleteMapping("/{id}")
    public Result<?> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return Result.success();
    }

    /**
     * 根据ID获取分类详情
     * 无需权限验证
     *
     * @param id 分类ID
     * @return 分类详情
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "根据ID获取分类详情")
    @GetMapping("/{id}")
    public Result<?> getCategoryById(@PathVariable Long id) {
        return Result.success(categoryService.getCategoryById(id));
    }

    /**
     * 分页查询分类列表
     * 无需权限验证
     *
     * @param name        名称
     * @param parentId    父ID
     * @param level       层级
     * @param status      状态
     * @param currentPage 当前页
     * @param size        每页大小
     * @return 分页分类列表
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "分页查询分类列表")
    @GetMapping("/page")
    public Result<?> getCategoriesByPage(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Long parentId,
            @RequestParam(required = false) Integer level,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(categoryService.getCategoriesByPage(name, parentId, level, status, currentPage, size));
    }

    /**
     * 获取所有分类（平铺列表）
     * 无需权限验证
     *
     * @return 分类列表
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "获取所有分类（平铺列表）")
    @GetMapping("/all")
    public Result<?> getAllCategories() {
        return Result.success(categoryService.getAllCategories());
    }

    /**
     * 获取分类树形结构
     * 无需权限验证
     *
     * @return 分类树
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "获取分类树形结构")
    @GetMapping("/tree")
    public Result<?> getCategoryTree() {
        return Result.success(categoryService.getCategoryTree());
    }

    /**
     * 根据父分类ID获取子分类
     * 无需权限验证
     *
     * @param parentId 父分类ID
     * @return 子分类列表
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "根据父分类ID获取子分类")
    @GetMapping("/children")
    public Result<?> getCategoriesByParentId(@RequestParam Long parentId) {
        return Result.success(categoryService.getCategoriesByParentId(parentId));
    }

    /**
     * 获取一级分类列表（首页展示）
     * 无需权限验证
     *
     * @return 一级分类列表
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "获取一级分类列表（首页展示）")
    @GetMapping("/top")
    public Result<?> getTopCategories() {
        return Result.success(categoryService.getTopCategories());
    }

    /**
     * 批量删除分类
     * 权限：只有管理员
     *
     * @param ids 分类ID列表
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "批量删除分类")
    @RequiresRole("ADMIN")
    @DeleteMapping("/batch")
    public Result<?> deleteBatch(@RequestParam List<Long> ids) {
        categoryService.deleteBatch(ids);
        return Result.success();
    }
}
