package org.example.springboot.service;

import org.example.springboot.entity.dto.RecommendationResultDTO;
import org.example.springboot.entity.dto.UserProfileDTO;

import java.util.List;

/**
 * 推荐策略接口
 * <p>
 * 定义推荐算法的统一契约，支持策略模式扩展不同的推荐算法
 * 实现类包括：融合推荐、新品推荐、热销推荐等
 * </p>
 *
 * @author IhaveBB
 * @date 2026/03/21
 */
public interface RecommendationStrategy {

    /**
     * 获取策略名称
     *
     * @return 策略标识名称
     */
    String getStrategyName();

    /**
     * 执行推荐
     *
     * @param userId      用户ID
     * @param userProfile 用户画像
     * @param limit       推荐数量限制
     * @return 推荐结果列表
     */
    List<RecommendationResultDTO> recommend(Long userId, UserProfileDTO userProfile, int limit);

    /**
     * 是否支持冷启动
     *
     * @return true表示该策略可以处理新用户/新商品场景
     */
    default boolean supportsColdStart() {
        return false;
    }

    /**
     * 计算推荐优先级得分
     * 用于多策略融合时的权重计算
     *
     * @param userId 用户ID
     * @return 优先级得分（0-1之间）
     */
    default double getPriorityScore(Long userId) {
        return 0.5;
    }
}
