package org.example.springboot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.example.springboot.annotation.RequiresRole;
import org.example.springboot.common.Result;
import org.example.springboot.entity.Notice;
import org.example.springboot.service.NoticeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 通知管理控制器
 * 提供通知的增删改查功能
 *
 * @author IhaveBB
 * @date 2026/03/19
 */
@Tag(name="通知接口")
@RequestMapping("/notice")
@RestController
public class NoticeController {
    @Resource
    private NoticeService noticeService;

    public static final Logger LOGGER = LoggerFactory.getLogger(NoticeController.class);

    /**
     * 获取所有通知
     * 无需权限验证
     *
     * @return 通知列表
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "获取所有通知")
    @GetMapping
    public Result<?> getAll() {
        return Result.success(noticeService.getAll());
    }

    /**
     * 获取指定数量的通知
     * 无需权限验证
     *
     * @param count 数量
     * @return 通知列表
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "获取指定数量的通知")
    @GetMapping("/limit")
    public Result<?> getWithLimit(@RequestParam(defaultValue = "10") Integer count) {
        return Result.success(noticeService.getWithLimit(count));
    }

    /**
     * 分页查询通知
     * 无需权限验证
     *
     * @param title       标题
     * @param currentPage 当前页
     * @param size        每页大小
     * @return 分页通知列表
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "分页查询通知")
    @GetMapping("/page")
    public Result<?> getNoticesByPage(
            @RequestParam(defaultValue = "") String title,
            @RequestParam(defaultValue = "") Integer currentPage,
            @RequestParam(defaultValue = "") Integer size) {
        return Result.success(noticeService.getNoticesByPage(title, currentPage, size));
    }

    /**
     * 根据id获取通知
     * 无需权限验证
     *
     * @param id 通知ID
     * @return 通知详情
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "根据id获取通知")
    @GetMapping("/{id}")
    public Result<?> getById(@PathVariable int id) {
        return Result.success(noticeService.getById(id));
    }

    /**
     * 新增通知
     * 权限：只有管理员
     *
     * @param notice 通知实体
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "新增通知")
    @RequiresRole("ADMIN")
    @PostMapping
    public Result<?> add(@RequestBody Notice notice) {
        noticeService.add(notice);
        return Result.success();
    }

    /**
     * 更新通知
     * 权限：只有管理员
     *
     * @param id     通知ID
     * @param notice 通知实体
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "更新通知")
    @RequiresRole("ADMIN")
    @PutMapping("/{id}")
    public Result<?> update(@PathVariable int id, @RequestBody Notice notice) {
        noticeService.update(id, notice);
        return Result.success();
    }

    /**
     * 批量删除通知
     * 权限：只有管理员
     *
     * @param ids 通知ID列表
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "批量删除通知")
    @RequiresRole("ADMIN")
    @DeleteMapping("/deleteBatch")
    public Result<?> deleteBatch(@RequestParam List<Integer> ids) {
        noticeService.deleteBatch(ids);
        return Result.success();
    }

    /**
     * 根据id删除通知
     * 权限：只有管理员
     *
     * @param id 通知ID
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "根据id删除通知")
    @RequiresRole("ADMIN")
    @DeleteMapping("/{id}")
    public Result<?> deleteById(@PathVariable int id) {
        noticeService.deleteById(id);
        return Result.success();
    }
}
