package org.example.springboot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.springboot.annotation.RequiresRole;
import org.example.springboot.common.Result;
import org.example.springboot.entity.Article;
import org.example.springboot.service.ArticleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 资讯管理控制器
 * 提供资讯的增删改查功能
 *
 * @author IhaveBB
 * @date 2026/03/19
 */
@Tag(name = "资讯管理接口")
@RestController
@RequestMapping("/article")
public class ArticleController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArticleController.class);

    @Autowired
    private ArticleService articleService;

    /**
     * 创建资讯
     * 权限：只有管理员
     *
     * @param article 资讯实体
     * @return 创建结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "创建资讯")
    @RequiresRole("ADMIN")
    @PostMapping
    public Result<?> createArticle(@RequestBody Article article) {
        return Result.success(articleService.createArticle(article));
    }

    /**
     * 更新资讯
     * 权限：只有管理员
     *
     * @param id      资讯ID
     * @param article 资讯实体
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "更新资讯")
    @RequiresRole("ADMIN")
    @PutMapping("/{id}")
    public Result<?> updateArticle(@PathVariable Long id, @RequestBody Article article) {
        return Result.success(articleService.updateArticle(id, article));
    }

    /**
     * 删除资讯
     * 权限：只有管理员
     *
     * @param id 资讯ID
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "删除资讯")
    @RequiresRole("ADMIN")
    @DeleteMapping("/{id}")
    public Result<?> deleteArticle(@PathVariable Long id) {
        articleService.deleteArticle(id);
        return Result.success();
    }

    /**
     * 根据ID获取资讯详情
     * 无需权限验证
     *
     * @param id 资讯ID
     * @return 资讯详情
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "根据ID获取资讯详情")
    @GetMapping("/{id}")
    public Result<?> getArticleById(@PathVariable Long id) {
        return Result.success(articleService.getArticleById(id));
    }

    /**
     * 分页查询资讯列表
     * 无需权限验证
     *
     * @param title       标题
     * @param status      状态
     * @param currentPage 当前页
     * @param size        每页大小
     * @return 分页资讯列表
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "分页查询资讯列表")
    @GetMapping("/page")
    public Result<?> getArticlesByPage(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(articleService.getArticlesByPage(title, status, currentPage, size));
    }

    /**
     * 更新资讯状态
     * 权限：只有管理员
     *
     * @param id     资讯ID
     * @param status 状态
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "更新资讯状态")
    @RequiresRole("ADMIN")
    @PutMapping("/{id}/status")
    public Result<?> updateArticleStatus(@PathVariable Long id, @RequestParam Integer status) {
        articleService.updateArticleStatus(id, status);
        return Result.success();
    }

    /**
     * 批量删除资讯
     * 权限：只有管理员
     *
     * @param ids 资讯ID列表
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "批量删除资讯")
    @RequiresRole("ADMIN")
    @DeleteMapping("/batch")
    public Result<?> deleteBatch(@RequestParam List<Long> ids) {
        articleService.deleteBatch(ids);
        return Result.success();
    }
} 