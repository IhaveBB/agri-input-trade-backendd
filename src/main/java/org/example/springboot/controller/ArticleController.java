package org.example.springboot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.springboot.common.Result;
import org.example.springboot.entity.Article;
import org.example.springboot.service.ArticleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "资讯管理接口")
@RestController
@RequestMapping("/article")
public class ArticleController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArticleController.class);

    @Autowired
    private ArticleService articleService;

    @Operation(summary = "创建资讯")
    @PostMapping
    public Result<?> createArticle(@RequestBody Article article) {
        return articleService.createArticle(article);
    }

    @Operation(summary = "更新资讯")
    @PutMapping("/{id}")
    public Result<?> updateArticle(@PathVariable Long id, @RequestBody Article article) {
        return articleService.updateArticle(id, article);
    }

    @Operation(summary = "删除资讯")
    @DeleteMapping("/{id}")
    public Result<?> deleteArticle(@PathVariable Long id) {
        return articleService.deleteArticle(id);
    }

    @Operation(summary = "获取资讯详情")
    @GetMapping("/{id}")
    public Result<?> getArticleById(@PathVariable Long id) {
        return articleService.getArticleById(id);
    }

    @Operation(summary = "分页查询资讯列表")
    @GetMapping("/page")
    public Result<?> getArticlesByPage(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(articleService.getArticlesByPage(title, status, currentPage, size));
    }

    @Operation(summary = "更新资讯状态")
    @PutMapping("/{id}/status")
    public Result<?> updateArticleStatus(@PathVariable Long id, @RequestParam Integer status) {
        return articleService.updateArticleStatus(id, status);
    }

    @Operation(summary = "批量删除资讯")
    @DeleteMapping("/batch")
    public Result<?> deleteBatch(@RequestParam List<Long> ids) {
        return articleService.deleteBatch(ids);
    }
} 