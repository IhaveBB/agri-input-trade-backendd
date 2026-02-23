package org.example.springboot.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.springboot.entity.Region;
import org.example.springboot.entity.Season;
import org.example.springboot.mapper.RegionMapper;
import org.example.springboot.mapper.SeasonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 基础数据初始化器 - 系统启动时初始化区域、季节数据
 * 作物使用分类表中种子分类的四级分类数据
 */
@Component
public class BasicDataInitializer implements CommandLineRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(BasicDataInitializer.class);

    @Autowired
    private RegionMapper regionMapper;

    @Autowired
    private SeasonMapper seasonMapper;

    @Override
    @Transactional
    public void run(String... args) {
        initRegions();
        initSeasons();
    }

    /**
     * 初始化区域数据
     */
    private void initRegions() {
        Long count = regionMapper.selectCount(new LambdaQueryWrapper<>());
        if (count > 0) {
            LOGGER.info("区域数据已存在，跳过初始化");
            return;
        }

        LOGGER.info("开始初始化区域数据...");
        regionMapper.insert(createRegion(1L, "华北", 1));
        regionMapper.insert(createRegion(2L, "华东", 2));
        regionMapper.insert(createRegion(3L, "华南", 3));
        regionMapper.insert(createRegion(4L, "华中", 4));
        regionMapper.insert(createRegion(5L, "东北", 5));
        regionMapper.insert(createRegion(6L, "西北", 6));
        regionMapper.insert(createRegion(7L, "西南", 7));
        regionMapper.insert(createRegion(8L, "全国", 8));
        LOGGER.info("区域数据初始化完成");
    }

    /**
     * 初始化季节数据
     */
    private void initSeasons() {
        Long count = seasonMapper.selectCount(new LambdaQueryWrapper<>());
        if (count > 0) {
            LOGGER.info("季节数据已存在，跳过初始化");
            return;
        }

        LOGGER.info("开始初始化季节数据...");
        seasonMapper.insert(createSeason(1L, "春", 1));
        seasonMapper.insert(createSeason(2L, "夏", 2));
        seasonMapper.insert(createSeason(3L, "秋", 3));
        seasonMapper.insert(createSeason(4L, "冬", 4));
        seasonMapper.insert(createSeason(5L, "全年", 5));
        LOGGER.info("季节数据初始化完成");
    }

    private Region createRegion(Long id, String name, Integer sortOrder) {
        Region region = new Region();
        region.setId(id);
        region.setName(name);
        region.setSortOrder(sortOrder);
        region.setStatus(1);
        return region;
    }

    private Season createSeason(Long id, String name, Integer sortOrder) {
        Season season = new Season();
        season.setId(id);
        season.setName(name);
        season.setSortOrder(sortOrder);
        season.setStatus(1);
        return season;
    }
}
