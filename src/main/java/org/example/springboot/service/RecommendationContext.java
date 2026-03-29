package org.example.springboot.service;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.springboot.entity.dto.RecommendationResultDTO;
import org.example.springboot.entity.dto.UserProfileDTO;
import org.example.springboot.enums.ErrorCodeEnum;
import org.example.springboot.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 推荐策略上下文
 * <p>
 * 管理所有推荐策略，支持策略的组合与切换
 * 使用策略模式实现不同推荐算法的灵活扩展
 * </p>
 *
 * @author IhaveBB
 * @date 2026/03/21
 */
@Slf4j
@Component
public class RecommendationContext {

    /**
     * 策略注册表
     */
    private final Map<String, RecommendationStrategy> strategyRegistry = new ConcurrentHashMap<>();

    @Resource
    private FusionRecommendationService fusionRecommendationService;

    @Resource
    private HotProductRecommendationStrategy hotProductStrategy;

    @Resource
    private CollaborativeFilteringStrategy cfStrategy;

    @Resource
    private NewProductRecommendationStrategy newProductStrategy;

    /**
     * 初始化策略注册表
     */
    @jakarta.annotation.PostConstruct
    public void init() {
        // 注册融合推荐策略（主策略）
        registerStrategy(fusionRecommendationService);
        // 注册热销推荐策略
        registerStrategy(hotProductStrategy);
        // 注册纯协同过滤策略（对比算法）
        registerStrategy(cfStrategy);
        // 注册新品推荐策略（独立接口使用，不混入smartRecommend）
        registerStrategy(newProductStrategy);

        log.info("[推荐策略] 已注册{}种推荐策略", strategyRegistry.size());
    }

    /**
     * 注册推荐策略
     *
     * @param strategy 策略实现
     */
    public void registerStrategy(RecommendationStrategy strategy) {
        strategyRegistry.put(strategy.getStrategyName(), strategy);
        log.info("[推荐策略] 注册策略: {}", strategy.getStrategyName());
    }

    /**
     * 获取指定策略
     *
     * @param strategyName 策略名称
     * @return 策略实现
     */
    public RecommendationStrategy getStrategy(String strategyName) {
        return strategyRegistry.get(strategyName);
    }

    /**
     * 执行指定策略推荐
     *
     * @param strategyName 策略名称
     * @param userId       用户ID
     * @param userProfile  用户画像
     * @param limit        推荐数量
     * @return 推荐结果
     */
    public List<RecommendationResultDTO> executeStrategy(String strategyName,
                                                          Long userId,
                                                          UserProfileDTO userProfile,
                                                          int limit) {
        RecommendationStrategy strategy = strategyRegistry.get(strategyName);
        if (strategy == null) {
            log.error("[推荐策略] 未找到策略: {}", strategyName);
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "未知的推荐策略: " + strategyName);
        }

        return strategy.recommend(userId, userProfile, limit);
    }

    /**
     * 智能选择策略并执行推荐
     * <p>
     * 根据用户状态智能选择最合适的推荐策略：
     * <ul>
     *   <li>新用户 + 有注册信息（地域/关注作物/关注动物）→ 纯画像推荐</li>
     *   <li>新用户 + 无注册信息 / 未登录 → 热销商品推荐（降级策略）</li>
     *   <li>正常用户 → CF + 画像融合推荐，数量不足时热销兜底</li>
     * </ul>
     * </p>
     *
     * @param userId      用户ID（未登录可为null）
     * @param userProfile 用户画像（可为null）
     * @param isNewUser   是否新用户（totalPurchases为null或0）
     * @param totalLimit  总推荐数量
     * @return 推荐结果列表
     * @author IhaveBB
     * @date 2026/03/22
     */
    public List<RecommendationResultDTO> smartRecommend(Long userId,
                                                         UserProfileDTO userProfile,
                                                         boolean isNewUser,
                                                         int totalLimit) {
        boolean hasProfile = userProfile != null && hasProfileInfo(userProfile);
        log.info("[智能推荐] 用户{}，新用户={}, 画像有效={}",
                userId, isNewUser, hasProfile);

        List<RecommendationResultDTO> results = new ArrayList<>();

        if (isNewUser) {
            if (hasProfile) {
                // 新用户有注册信息 → 纯画像推荐
                log.info("[智能推荐] 策略选择：画像冷启动（地域: {}, 偏好作物: {}, 偏好动物: {}）",
                        userProfile.getRegionName(), userProfile.getPreferredCropNames(),
                        userProfile.getPreferredAnimalNames());
                results = fusionRecommendationService.recommendByProfileOnly(userId, userProfile, totalLimit);
            } else {
                // 新用户无画像信息或未登录 → 降级为热销推荐
                log.info("[智能推荐] 策略选择：热销降级（新用户无注册信息或未登录）");
                return hotProductStrategy.recommend(userId, userProfile, totalLimit);
            }
        } else {
            // 正常用户：CF + 画像融合推荐（100%）
            log.info("[智能推荐] 策略选择：融合推荐（CF + 画像，θ=0.7）");
            List<RecommendationResultDTO> fusionResults = fusionRecommendationService.recommend(userId);
            results.addAll(fusionResults.subList(0, Math.min(totalLimit, fusionResults.size())));
            log.info("[智能推荐] 融合推荐实际获取{}条", results.size());
        }

        // 数量不足时用热销补充（兜底策略）
        if (results.size() < totalLimit) {
            log.info("[智能推荐] 结果不足{}条（当前{}条），热销兜底", totalLimit, results.size());
            List<RecommendationResultDTO> hotProducts =
                    hotProductStrategy.recommend(userId, userProfile, totalLimit);
            int addedHot = 0;
            for (RecommendationResultDTO hot : hotProducts) {
                if (results.stream().noneMatch(r -> r.getProductId().equals(hot.getProductId()))) {
                    results.add(hot);
                    addedHot++;
                    if (results.size() >= totalLimit) {
                        break;
                    }
                }
            }
            log.info("[智能推荐] 热销兜底补充{}条", addedHot);
        }

        log.info("[智能推荐] 用户{}最终返回{}条推荐", userId, results.size());
        return results;
    }

    /**
     * 判断用户画像是否包含有效的注册信息（用于冷启动策略选择）
     * <p>
     * 有效注册信息：地域ID不为空 或 偏好作物列表不为空
     * </p>
     *
     * @param userProfile 用户画像
     * @return true=有注册信息，可走画像推荐；false=无信息，降级热销
     */
    private boolean hasProfileInfo(UserProfileDTO userProfile) {
        boolean hasRegion = userProfile.getRegionId() != null;
        boolean hasCrops = userProfile.getPreferredCropIds() != null
                && !userProfile.getPreferredCropIds().isEmpty();
        boolean hasAnimals = userProfile.getPreferredAnimalIds() != null
                && !userProfile.getPreferredAnimalIds().isEmpty();
        return hasRegion || hasCrops || hasAnimals;
    }

    /**
     * 获取所有已注册的策略名称
     *
     * @return 策略名称列表
     */
    public List<String> getAllStrategyNames() {
        return new ArrayList<>(strategyRegistry.keySet());
    }
}
