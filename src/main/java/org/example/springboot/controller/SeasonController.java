package org.example.springboot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.springboot.common.Result;
import org.example.springboot.entity.Season;
import org.example.springboot.service.SeasonService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name = "季节管理接口")
@RestController
@RequestMapping("/season")
public class SeasonController {

    @Autowired
    private SeasonService seasonService;

    @Operation(summary = "创建季节")
    @PostMapping
    public Result<?> createSeason(@RequestBody Season season) {
        return Result.success(seasonService.createSeason(season));
    }

    @Operation(summary = "更新季节")
    @PutMapping("/{id}")
    public Result<?> updateSeason(@PathVariable Long id, @RequestBody Season season) {
        seasonService.updateSeason(id, season);
        return Result.success();
    }

    @Operation(summary = "删除季节")
    @DeleteMapping("/{id}")
    public Result<?> deleteSeason(@PathVariable Long id) {
        seasonService.deleteSeason(id);
        return Result.success();
    }

    @Operation(summary = "获取季节详情")
    @GetMapping("/{id}")
    public Result<?> getSeasonById(@PathVariable Long id) {
        return Result.success(seasonService.getSeasonById(id));
    }

    @Operation(summary = "获取所有季节（启用状态）")
    @GetMapping("/all")
    public Result<?> getAllSeasons() {
        return Result.success(seasonService.getAllSeasons());
    }

    @Operation(summary = "分页查询季节")
    @GetMapping("/page")
    public Result<?> getSeasonsByPage(
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(seasonService.getSeasonsByPage(name, currentPage, size));
    }
}
