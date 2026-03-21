package org.example.springboot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.springboot.annotation.RequiresRole;
import org.example.springboot.common.Result;
import org.example.springboot.entity.Region;
import org.example.springboot.service.RegionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 区域管理控制器
 * 提供区域的增删改查功能
 *
 * @author IhaveBB
 * @date 2026/03/19
 */
@Tag(name = "区域管理接口")
@RestController
@RequestMapping("/region")
public class RegionController {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegionController.class);

    @Autowired
    private RegionService regionService;

    /**
     * 创建区域
     * 权限：只有管理员
     *
     * @param region 区域实体
     * @return 创建结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "创建区域")
    @RequiresRole("ADMIN")
    @PostMapping
    public Result<?> createRegion(@RequestBody Region region) {
        return Result.success(regionService.createRegion(region));
    }

    /**
     * 更新区域
     * 权限：只有管理员
     *
     * @param id     区域ID
     * @param region 区域实体
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "更新区域")
    @RequiresRole("ADMIN")
    @PutMapping("/{id}")
    public Result<?> updateRegion(@PathVariable Long id, @RequestBody Region region) {
        regionService.updateRegion(id, region);
        return Result.success();
    }

    /**
     * 删除区域
     * 权限：只有管理员
     *
     * @param id 区域ID
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "删除区域")
    @RequiresRole("ADMIN")
    @DeleteMapping("/{id}")
    public Result<?> deleteRegion(@PathVariable Long id) {
        regionService.deleteRegion(id);
        return Result.success();
    }

    /**
     * 获取区域详情
     * 无需权限验证
     *
     * @param id 区域ID
     * @return 区域详情
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "获取区域详情")
    @GetMapping("/{id}")
    public Result<?> getRegionById(@PathVariable Long id) {
        return Result.success(regionService.getRegionById(id));
    }

    /**
     * 获取所有区域（启用状态）
     * 无需权限验证
     *
     * @return 区域列表
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "获取所有区域（启用状态）")
    @GetMapping("/all")
    public Result<?> getAllRegions() {
        return Result.success(regionService.getAllRegions());
    }

    /**
     * 分页查询区域
     * 无需权限验证
     *
     * @param name        区域名称
     * @param currentPage 当前页
     * @param size        每页大小
     * @return 分页区域列表
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "分页查询区域")
    @GetMapping("/page")
    public Result<?> getRegionsByPage(
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(regionService.getRegionsByPage(name, currentPage, size));
    }
}
