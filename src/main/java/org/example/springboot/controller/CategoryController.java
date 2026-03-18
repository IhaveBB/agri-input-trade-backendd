package org.example.springboot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "分类管理接口")
@RestController
@RequestMapping("/category")
public class CategoryController {
    private static final Logger LOGGER = LoggerFactory.getLogger(CategoryController.class);

    @Autowired
    private CategoryService categoryService;

    @Operation(summary = "创建分类（管理员）")
    @PostMapping
    public Result<?> createCategory(@RequestBody Category category) {
        return Result.success(categoryService.createCategory(category, null));
    }

    @Operation(summary = "商家申请新增自定义分类")
    @PostMapping("/custom")
    public Result<?> applyCustomCategory(@RequestBody Category category) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCodeEnum.UNAUTHORIZED);
        }
        return Result.success(categoryService.applyCustomCategory(category, userId));
    }

    @Operation(summary = "更新分类信息")
    @PutMapping("/{id}")
    public Result<?> updateCategory(@PathVariable Long id, @RequestBody Category category) {
        categoryService.updateCategory(id, category);
        return Result.success();
    }

    @Operation(summary = "删除分类")
    @DeleteMapping("/{id}")
    public Result<?> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return Result.success();
    }

    @Operation(summary = "根据ID获取分类详情")
    @GetMapping("/{id}")
    public Result<?> getCategoryById(@PathVariable Long id) {
        return Result.success(categoryService.getCategoryById(id));
    }

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

    @Operation(summary = "获取所有分类（平铺列表）")
    @GetMapping("/all")
    public Result<?> getAllCategories() {
        return Result.success(categoryService.getAllCategories());
    }

    @Operation(summary = "获取分类树形结构")
    @GetMapping("/tree")
    public Result<?> getCategoryTree() {
        return Result.success(categoryService.getCategoryTree());
    }

    @Operation(summary = "根据父分类ID获取子分类")
    @GetMapping("/children")
    public Result<?> getCategoriesByParentId(@RequestParam Long parentId) {
        return Result.success(categoryService.getCategoriesByParentId(parentId));
    }

    @Operation(summary = "获取一级分类列表（首页展示）")
    @GetMapping("/top")
    public Result<?> getTopCategories() {
        return Result.success(categoryService.getTopCategories());
    }

    @Operation(summary = "批量删除分类")
    @DeleteMapping("/batch")
    public Result<?> deleteBatch(@RequestParam List<Long> ids) {
        categoryService.deleteBatch(ids);
        return Result.success();
    }
}
