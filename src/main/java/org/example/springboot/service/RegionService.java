package org.example.springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.springboot.entity.Region;
import org.example.springboot.enums.ErrorCodeEnum;
import org.example.springboot.exception.BusinessException;
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

    public Region createRegion(Region region) {
        if (region.getSortOrder() == null) {
            region.setSortOrder(0);
        }
        if (region.getStatus() == null) {
            region.setStatus(1);
        }
        int result = regionMapper.insert(region);
        if (result > 0) {
            return region;
        }
        throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR, "创建区域失败");
    }

    public Region updateRegion(Long id, Region region) {
        region.setId(id);
        int result = regionMapper.updateById(region);
        if (result > 0) {
            return regionMapper.selectById(id);
        }
        throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR, "更新区域失败");
    }

    public void deleteRegion(Long id) {
        int result = regionMapper.deleteById(id);
        if (result > 0) {
            return;
        }
        throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR, "删除区域失败");
    }

    public Region getRegionById(Long id) {
        Region region = regionMapper.selectById(id);
        if (region != null) {
            return region;
        }
        throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "未找到区域");
    }

    public List<Region> getAllRegions() {
        LambdaQueryWrapper<Region> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Region::getStatus, 1);
        queryWrapper.orderByAsc(Region::getSortOrder);
        return regionMapper.selectList(queryWrapper);
    }

    public Page<Region> getRegionsByPage(String name, Integer currentPage, Integer size) {
        LambdaQueryWrapper<Region> queryWrapper = new LambdaQueryWrapper<>();
        if (name != null && !name.isEmpty()) {
            queryWrapper.like(Region::getName, name);
        }
        queryWrapper.orderByAsc(Region::getSortOrder);

        Page<Region> page = new Page<>(currentPage, size);
        return regionMapper.selectPage(page, queryWrapper);
    }
}
