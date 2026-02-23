package org.example.springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.springboot.common.Result;
import org.example.springboot.entity.Region;
import org.example.springboot.mapper.RegionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RegionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RegionService.class);

    @Autowired
    private RegionMapper regionMapper;

    public Result<?> createRegion(Region region) {
        try {
            if (region.getSortOrder() == null) {
                region.setSortOrder(0);
            }
            if (region.getStatus() == null) {
                region.setStatus(1);
            }
            int result = regionMapper.insert(region);
            if (result > 0) {
                return Result.success(region);
            }
            return Result.error("-1", "创建区域失败");
        } catch (Exception e) {
            LOGGER.error("创建区域失败：{}", e.getMessage());
            return Result.error("-1", "创建区域失败：" + e.getMessage());
        }
    }

    public Result<?> updateRegion(Long id, Region region) {
        region.setId(id);
        try {
            int result = regionMapper.updateById(region);
            if (result > 0) {
                return Result.success(regionMapper.selectById(id));
            }
            return Result.error("-1", "更新区域失败");
        } catch (Exception e) {
            LOGGER.error("更新区域失败：{}", e.getMessage());
            return Result.error("-1", "更新区域失败：" + e.getMessage());
        }
    }

    public Result<?> deleteRegion(Long id) {
        try {
            int result = regionMapper.deleteById(id);
            if (result > 0) {
                return Result.success();
            }
            return Result.error("-1", "删除区域失败");
        } catch (Exception e) {
            LOGGER.error("删除区域失败：{}", e.getMessage());
            return Result.error("-1", "删除区域失败：" + e.getMessage());
        }
    }

    public Result<?> getRegionById(Long id) {
        Region region = regionMapper.selectById(id);
        if (region != null) {
            return Result.success(region);
        }
        return Result.error("-1", "未找到区域");
    }

    public Result<?> getAllRegions() {
        LambdaQueryWrapper<Region> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Region::getStatus, 1);
        queryWrapper.orderByAsc(Region::getSortOrder);
        List<Region> regions = regionMapper.selectList(queryWrapper);
        return Result.success(regions);
    }

    public Result<?> getRegionsByPage(String name, Integer currentPage, Integer size) {
        LambdaQueryWrapper<Region> queryWrapper = new LambdaQueryWrapper<>();
        if (name != null && !name.isEmpty()) {
            queryWrapper.like(Region::getName, name);
        }
        queryWrapper.orderByAsc(Region::getSortOrder);

        Page<Region> page = new Page<>(currentPage, size);
        Page<Region> result = regionMapper.selectPage(page, queryWrapper);
        return Result.success(result);
    }
}
