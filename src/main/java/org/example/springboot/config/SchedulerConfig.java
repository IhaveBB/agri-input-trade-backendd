package org.example.springboot.config;

import org.example.springboot.service.FusionRecommendationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 定时任务配置
 * <p>
 * 定时刷新推荐数据，与开题报告描述的离线预计算一致
 * </p>
 */
@Configuration
@EnableScheduling
public class SchedulerConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(SchedulerConfig.class);

    @Autowired
    private FusionRecommendationService fusionRecommendationService;

    /**
     * 每天凌晨2点刷新所有用户推荐
     * 基于物品的协同过滤相似度矩阵预计算
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduleRecommendUpdate() {
        LOGGER.info("[定时任务] 开始执行基于物品协同过滤的推荐刷新任务");
        fusionRecommendationService.refreshAllRecommendations();
        LOGGER.info("[定时任务] 推荐刷新任务完成");
    }
} 