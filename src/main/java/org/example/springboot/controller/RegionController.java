package org.example.springboot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.springboot.common.Result;
import org.example.springboot.entity.Region;
import org.example.springboot.service.RegionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name = "区域管理接口")
@RestController
@RequestMapping("/region")
public class RegionController {

    @Autowired
    private RegionService regionService;

    @Operation(summary = "创建区域")
    @PostMapping
    public Result<?> createRegion(@RequestBody Region region) {
        return Result.success(regionService.createRegion(region));
    }

    @Operation(summary = "更新区域")
    @PutMapping("/{id}")
    public Result<?> updateRegion(@PathVariable Long id, @RequestBody Region region) {
        regionService.updateRegion(id, region);
        return Result.success();
    }

    @Operation(summary = "删除区域")
    @DeleteMapping("/{id}")
    public Result<?> deleteRegion(@PathVariable Long id) {
        regionService.deleteRegion(id);
        return Result.success();
    }

    @Operation(summary = "获取区域详情")
    @GetMapping("/{id}")
    public Result<?> getRegionById(@PathVariable Long id) {
        return Result.success(regionService.getRegionById(id));
    }

    @Operation(summary = "获取所有区域（启用状态）")
    @GetMapping("/all")
    public Result<?> getAllRegions() {
        return Result.success(regionService.getAllRegions());
    }

    @Operation(summary = "分页查询区域")
    @GetMapping("/page")
    public Result<?> getRegionsByPage(
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(regionService.getRegionsByPage(name, currentPage, size));
    }
}
