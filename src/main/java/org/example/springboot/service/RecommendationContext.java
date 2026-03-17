package org.example.springboot.service;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.springboot.entity.dto.RecommendationResultDTO;
import org.example.springboot.entity.dto.UserProfileDTO;
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
 * @author agri-input-trade
 * @version 1.0
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
    private NewProductRecommendationStrategy newProductStrategy;

    @Resource
    private HotProductRecommendationStrategy hotProductStrategy;

    @Resource
    private CollaborativeFilteringStrategy cfStrategy;

    /**
     * 初始化策略注册表
     */
    @jakarta.annotation.PostConstruct
    public void init() {
        // 注册融合推荐策略（主策略）
        registerStrategy(fusionRecommendationService);
        // 注册新品推荐策略
        registerStrategy(newProductStrategy);
        // 注册热销推荐策略
        registerStrategy(hotProductStrategy);
        // 注册纯协同过滤策略（对比算法）
        registerStrategy(cfStrategy);

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
            throw new IllegalArgumentException("未知的推荐策略: " + strategyName);
        }

        return strategy.recommend(userId, userProfile, limit);
    }

    /**
     * 智能选择策略并执行推荐
     * <p>
     * 根据用户状态智能选择最合适的推荐策略：
     * - 新用户/冷启动：优先新品推荐 + 热销推荐
     * - 正常用户：融合推荐 + 补充新品
     * </p>
     *
     * @param userId          用户ID
     * @param userProfile     用户画像
     * @param isNewUser       是否新用户
     * @param totalLimit      总推荐数量
     * @return 推荐结果
     */
    public List<RecommendationResultDTO> smartRecommend(Long userId,
                                                         UserProfileDTO userProfile,
                                                         boolean isNewUser,
                                                         int totalLimit) {
        List<RecommendationResultDTO> results = new ArrayList<>();

        if (isNewUser || userProfile == null) {
            // 冷启动场景：新品(60%) + 热销(40%)
            int newProductCount = (int) (totalLimit * 0.6);
            int hotProductCount = totalLimit - newProductCount;

            List<RecommendationResultDTO> newProducts = newProductStrategy.recommend(userId, userProfile, newProductCount);
            results.addAll(newProducts);

            // 去重后补充热销商品
            List<RecommendationResultDTO> hotProducts = hotProductStrategy.recommend(userId, userProfile, hotProductCount);
            for (RecommendationResultDTO hot : hotProducts) {
                if (results.stream().noneMatch(r -> r.getProductId().equals(hot.getProductId()))) {
                    results.add(hot);
                }
            }
        } else {
            // 正常用户：融合推荐(80%) + 新品(20%)
            int fusionCount = (int) (totalLimit * 0.8);
            int newProductCount = totalLimit - fusionCount;

            List<RecommendationResultDTO> fusionResults = fusionRecommendationService.recommend(userId);
            results.addAll(fusionResults.subList(0, Math.min(fusionCount, fusionResults.size())));

            // 补充新品推荐
            List<RecommendationResultDTO> newProducts = newProductStrategy.recommend(userId, userProfile, newProductCount);
            for (RecommendationResultDTO np : newProducts) {
                if (results.stream().noneMatch(r -> r.getProductId().equals(np.getProductId()))) {
                    results.add(np);
                    if (results.size() >= totalLimit) {
                        break;
                    }
                }
            }
        }

        // 如果数量不足，用热销商品补充
        if (results.size() < totalLimit) {
            List<RecommendationResultDTO> hotProducts = hotProductStrategy.recommend(userId, userProfile, totalLimit);
            for (RecommendationResultDTO hot : hotProducts) {
                if (results.stream().noneMatch(r -> r.getProductId().equals(hot.getProductId()))) {
                    results.add(hot);
                    if (results.size() >= totalLimit) {
                        break;
                    }
                }
            }
        }

        return results;
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
