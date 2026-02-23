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
        return regionService.createRegion(region);
    }

    @Operation(summary = "更新区域")
    @PutMapping("/{id}")
    public Result<?> updateRegion(@PathVariable Long id, @RequestBody Region region) {
        return regionService.updateRegion(id, region);
    }

    @Operation(summary = "删除区域")
    @DeleteMapping("/{id}")
    public Result<?> deleteRegion(@PathVariable Long id) {
        return regionService.deleteRegion(id);
    }

    @Operation(summary = "获取区域详情")
    @GetMapping("/{id}")
    public Result<?> getRegionById(@PathVariable Long id) {
        return regionService.getRegionById(id);
    }

    @Operation(summary = "获取所有区域（启用状态）")
    @GetMapping("/all")
    public Result<?> getAllRegions() {
        return regionService.getAllRegions();
    }

    @Operation(summary = "分页查询区域")
    @GetMapping("/page")
    public Result<?> getRegionsByPage(
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "10") Integer size) {
        return regionService.getRegionsByPage(name, currentPage, size);
    }
}
