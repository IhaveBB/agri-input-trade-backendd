package org.example.springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.springboot.common.Result;
import org.example.springboot.entity.Season;
import org.example.springboot.mapper.SeasonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SeasonService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SeasonService.class);

    @Autowired
    private SeasonMapper seasonMapper;

    public Result<?> createSeason(Season season) {
        try {
            if (season.getSortOrder() == null) {
                season.setSortOrder(0);
            }
            if (season.getStatus() == null) {
                season.setStatus(1);
            }
            int result = seasonMapper.insert(season);
            if (result > 0) {
                return Result.success(season);
            }
            return Result.error("-1", "创建季节失败");
        } catch (Exception e) {
            LOGGER.error("创建季节失败：{}", e.getMessage());
            return Result.error("-1", "创建季节失败：" + e.getMessage());
        }
    }

    public Result<?> updateSeason(Long id, Season season) {
        season.setId(id);
        try {
            int result = seasonMapper.updateById(season);
            if (result > 0) {
                return Result.success(seasonMapper.selectById(id));
            }
            return Result.error("-1", "更新季节失败");
        } catch (Exception e) {
            LOGGER.error("更新季节失败：{}", e.getMessage());
            return Result.error("-1", "更新季节失败：" + e.getMessage());
        }
    }

    public Result<?> deleteSeason(Long id) {
        try {
            int result = seasonMapper.deleteById(id);
            if (result > 0) {
                return Result.success();
            }
            return Result.error("-1", "删除季节失败");
        } catch (Exception e) {
            LOGGER.error("删除季节失败：{}", e.getMessage());
            return Result.error("-1", "删除季节失败：" + e.getMessage());
        }
    }

    public Result<?> getSeasonById(Long id) {
        Season season = seasonMapper.selectById(id);
        if (season != null) {
            return Result.success(season);
        }
        return Result.error("-1", "未找到季节");
    }

    public Result<?> getAllSeasons() {
        LambdaQueryWrapper<Season> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Season::getStatus, 1);
        queryWrapper.orderByAsc(Season::getSortOrder);
        List<Season> seasons = seasonMapper.selectList(queryWrapper);
        return Result.success(seasons);
    }

    public Result<?> getSeasonsByPage(String name, Integer currentPage, Integer size) {
        LambdaQueryWrapper<Season> queryWrapper = new LambdaQueryWrapper<>();
        if (name != null && !name.isEmpty()) {
            queryWrapper.like(Season::getName, name);
        }
        queryWrapper.orderByAsc(Season::getSortOrder);

        Page<Season> page = new Page<>(currentPage, size);
        Page<Season> result = seasonMapper.selectPage(page, queryWrapper);
        return Result.success(result);
    }
}
