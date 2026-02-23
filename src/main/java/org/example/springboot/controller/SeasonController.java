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
        return seasonService.createSeason(season);
    }

    @Operation(summary = "更新季节")
    @PutMapping("/{id}")
    public Result<?> updateSeason(@PathVariable Long id, @RequestBody Season season) {
        return seasonService.updateSeason(id, season);
    }

    @Operation(summary = "删除季节")
    @DeleteMapping("/{id}")
    public Result<?> deleteSeason(@PathVariable Long id) {
        return seasonService.deleteSeason(id);
    }

    @Operation(summary = "获取季节详情")
    @GetMapping("/{id}")
    public Result<?> getSeasonById(@PathVariable Long id) {
        return seasonService.getSeasonById(id);
    }

    @Operation(summary = "获取所有季节（启用状态）")
    @GetMapping("/all")
    public Result<?> getAllSeasons() {
        return seasonService.getAllSeasons();
    }

    @Operation(summary = "分页查询季节")
    @GetMapping("/page")
    public Result<?> getSeasonsByPage(
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "10") Integer size) {
        return seasonService.getSeasonsByPage(name, currentPage, size);
    }
}
