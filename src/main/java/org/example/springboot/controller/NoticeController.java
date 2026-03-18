package org.example.springboot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.example.springboot.common.Result;
import org.example.springboot.entity.Notice;
import org.example.springboot.service.NoticeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name="通知接口")
@RequestMapping("/notice")
@RestController
public class NoticeController {
    @Resource
    private NoticeService noticeService;

    public static final Logger LOGGER = LoggerFactory.getLogger(NoticeController.class);

    @Operation(summary = "获取所有通知")
    @GetMapping
    public Result<?> getAll() {
        return Result.success(noticeService.getAll());
    }

    @GetMapping("/limit")
    public Result<?> getWithLimit(@RequestParam(defaultValue = "10") Integer count) {
        return Result.success(noticeService.getWithLimit(count));
    }

    @Operation(summary = "分页查询通知")
    @GetMapping("/page")
    public Result<?> getNoticesByPage(
            @RequestParam(defaultValue = "") String title,
            @RequestParam(defaultValue = "") Integer currentPage,
            @RequestParam(defaultValue = "") Integer size) {
        return Result.success(noticeService.getNoticesByPage(title, currentPage, size));
    }

    @Operation(summary = "根据id获取通知")
    @GetMapping("/{id}")
    public Result<?> getById(@PathVariable int id) {
        return Result.success(noticeService.getById(id));
    }

    @Operation(summary = "新增通知")
    @PostMapping
    public Result<?> add(@RequestBody Notice notice) {
        noticeService.add(notice);
        return Result.success();
    }

    @Operation(summary = "更新通知")
    @PutMapping("/{id}")
    public Result<?> update(@PathVariable int id, @RequestBody Notice notice) {
        noticeService.update(id, notice);
        return Result.success();
    }

    @Operation(summary = "批量删除通知")
    @DeleteMapping("/deleteBatch")
    public Result<?> deleteBatch(@RequestParam List<Integer> ids) {
        noticeService.deleteBatch(ids);
        return Result.success();
    }

    @Operation(summary = "根据id删除通知")
    @DeleteMapping("/{id}")
    public Result<?> deleteById(@PathVariable int id) {
        noticeService.deleteById(id);
        return Result.success();
    }
}
