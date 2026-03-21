package org.example.springboot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.springboot.annotation.RequiresRole;
import org.example.springboot.common.Result;
import org.example.springboot.entity.Season;
import org.example.springboot.service.SeasonService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 季节管理控制器
 * 提供季节的增删改查功能
 *
 * @author IhaveBB
 * @date 2026/03/19
 */
@Tag(name = "季节管理接口")
@RestController
@RequestMapping("/season")
public class SeasonController {

    private static final Logger LOGGER = LoggerFactory.getLogger(SeasonController.class);

    @Autowired
    private SeasonService seasonService;

    /**
     * 创建季节
     * 权限：只有管理员
     *
     * @param season 季节实体
     * @return 创建结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "创建季节")
    @RequiresRole("ADMIN")
    @PostMapping
    public Result<?> createSeason(@RequestBody Season season) {
        return Result.success(seasonService.createSeason(season));
    }

    /**
     * 更新季节
     * 权限：只有管理员
     *
     * @param id     季节ID
     * @param season 季节实体
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "更新季节")
    @RequiresRole("ADMIN")
    @PutMapping("/{id}")
    public Result<?> updateSeason(@PathVariable Long id, @RequestBody Season season) {
        seasonService.updateSeason(id, season);
        return Result.success();
    }

    /**
     * 删除季节
     * 权限：只有管理员
     *
     * @param id 季节ID
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "删除季节")
    @RequiresRole("ADMIN")
    @DeleteMapping("/{id}")
    public Result<?> deleteSeason(@PathVariable Long id) {
        seasonService.deleteSeason(id);
        return Result.success();
    }

    /**
     * 获取季节详情
     * 无需权限验证
     *
     * @param id 季节ID
     * @return 季节详情
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "获取季节详情")
    @GetMapping("/{id}")
    public Result<?> getSeasonById(@PathVariable Long id) {
        return Result.success(seasonService.getSeasonById(id));
    }

    /**
     * 获取所有季节（启用状态）
     * 无需权限验证
     *
     * @return 季节列表
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "获取所有季节（启用状态）")
    @GetMapping("/all")
    public Result<?> getAllSeasons() {
        return Result.success(seasonService.getAllSeasons());
    }

    /**
     * 分页查询季节
     * 无需权限验证
     *
     * @param name        季节名称
     * @param currentPage 当前页
     * @param size        每页大小
     * @return 分页季节列表
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "分页查询季节")
    @GetMapping("/page")
    public Result<?> getSeasonsByPage(
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(seasonService.getSeasonsByPage(name, currentPage, size));
    }
}
