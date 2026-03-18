package org.example.springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.springboot.entity.Season;
import org.example.springboot.enums.ErrorCodeEnum;
import org.example.springboot.exception.BusinessException;
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

    public Season createSeason(Season season) {
        if (season.getSortOrder() == null) {
            season.setSortOrder(0);
        }
        if (season.getStatus() == null) {
            season.setStatus(1);
        }
        int result = seasonMapper.insert(season);
        if (result > 0) {
            return season;
        }
        throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR, "创建季节失败");
    }

    public Season updateSeason(Long id, Season season) {
        season.setId(id);
        int result = seasonMapper.updateById(season);
        if (result > 0) {
            return seasonMapper.selectById(id);
        }
        throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR, "更新季节失败");
    }

    public void deleteSeason(Long id) {
        int result = seasonMapper.deleteById(id);
        if (result > 0) {
            return;
        }
        throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR, "删除季节失败");
    }

    public Season getSeasonById(Long id) {
        Season season = seasonMapper.selectById(id);
        if (season != null) {
            return season;
        }
        throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "未找到季节");
    }

    public List<Season> getAllSeasons() {
        LambdaQueryWrapper<Season> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Season::getStatus, 1);
        queryWrapper.orderByAsc(Season::getSortOrder);
        return seasonMapper.selectList(queryWrapper);
    }

    public Page<Season> getSeasonsByPage(String name, Integer currentPage, Integer size) {
        LambdaQueryWrapper<Season> queryWrapper = new LambdaQueryWrapper<>();
        if (name != null && !name.isEmpty()) {
            queryWrapper.like(Season::getName, name);
        }
        queryWrapper.orderByAsc(Season::getSortOrder);

        Page<Season> page = new Page<>(currentPage, size);
        return seasonMapper.selectPage(page, queryWrapper);
    }
}
