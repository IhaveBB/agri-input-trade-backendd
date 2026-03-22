package org.example.springboot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.springboot.common.Result;
import org.example.springboot.entity.dto.RecommendationResultDTO;
import org.example.springboot.entity.dto.UserProfileDTO;
import org.example.springboot.service.*;
import org.example.springboot.util.UserContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 融合推荐算法控制器
 * <p>
 * 提供基于物品协同过滤与画像约束的融合推荐算法接口
 * </p>
 *
 * @author IhaveBB
 * @date 2026/03/21
 */
@Slf4j
@Tag(name = "融合推荐算法接口", description = "基于 Item-CF 与画像约束的融合推荐算法")
@RestController
@RequestMapping("/api/recommendation")
@RequiredArgsConstructor
public class FusionRecommendationController {

    private final FusionRecommendationService recommendationService;
    private final NewProductRecommendationStrategy newProductStrategy;
    private final HotProductRecommendationStrategy hotProductStrategy;
    private final CollaborativeFilteringStrategy cfStrategy;
    private final RecommendationContext recommendationContext;

    /**
     * 获取个性化推荐
     * <p>
     * 为当前登录用户生成个性化推荐列表
     * 算法：基于物品协同过滤 (Item-CF) + 画像约束融合
     * </p>
     *
     * @return 推荐商品列表
     */
    @Operation(summary = "获取个性化推荐", description = "为当前登录用户生成推荐列表，基于 Item-CF 与画像约束融合算法")
    @GetMapping("/personalized")
    public Result<List<RecommendationResultDTO>> getPersonalizedRecommendations() {
        Long userId = UserContext.requireUserId();
        log.info("[推荐接口] 用户{}请求个性化推荐", userId);

        List<RecommendationResultDTO> recommendations = recommendationService.recommend(userId);

        return Result.success(recommendations);
    }

    /**
     * 为指定用户获取推荐
     * <p>
     * 管理员可以使用此接口为任意用户生成推荐
     * </p>
     *
     * @param userId 用户 ID
     * @return 推荐商品列表
     */
    @Operation(summary = "为指定用户获取推荐", description = "为指定用户 ID 生成推荐列表（管理员可用）")
    @GetMapping("/user/{userId}")
    public Result<List<RecommendationResultDTO>> getRecommendationsForUser(@PathVariable Long userId) {
        log.info("[推荐接口] 为用户{}生成推荐", userId);

        List<RecommendationResultDTO> recommendations = recommendationService.recommend(userId);

        return Result.success(recommendations);
    }

    /**
     * 获取用户画像
     * <p>
     * 查看当前用户的画像信息，包括消费能力、偏好品类等
     * </p>
     *
     * @return 用户画像信息
     */
    @Operation(summary = "获取用户画像", description = "查看当前用户的画像信息")
    @GetMapping("/profile/me")
    public Result<UserProfileDTO> getMyProfile() {
        Long userId = UserContext.requireUserId();
        UserProfileDTO profile = recommendationService.getUserProfile(userId);
        return Result.success(profile);
    }

    /**
     * 获取指定用户的画像
     *
     * @param userId 用户 ID
     * @return 用户画像信息
     */
    @Operation(summary = "获取指定用户画像", description = "查看指定用户的画像信息（管理员可用）")
    @GetMapping("/profile/user/{userId}")
    public Result<UserProfileDTO> getUserProfile(@PathVariable Long userId) {
        UserProfileDTO profile = recommendationService.getUserProfile(userId);
        return Result.success(profile);
    }

    /**
     * 刷新所有用户推荐
     * <p>
     * 重新计算交互矩阵、物品相似度，并更新所有用户的推荐结果
     * 建议在低峰期定时执行
     * </p>
     *
     * @return 操作结果
     */
    @Operation(summary = "刷新所有用户推荐", description = "重新计算并更新所有用户的推荐结果")
    @PostMapping("/refresh/all")
    public Result<Void> refreshAllRecommendations() {
        log.info("[推荐接口] 手动触发刷新所有用户推荐");
        recommendationService.refreshAllRecommendations();
        return Result.success();
    }

    /**
     * 获取商品画像
     * <p>
     * 查看指定商品的画像信息，包括价格区间、适用地区/季节等
     * </p>
     *
     * @param productId 商品 ID
     * @return 商品画像信息
     */
    @Operation(summary = "获取商品画像", description = "查看指定商品的画像信息")
    @GetMapping("/product/{productId}/profile")
    public Result<Object> getProductProfile(@PathVariable Long productId) {
        log.info("[推荐接口] 获取商品{}画像", productId);
        Object profile = recommendationService.getProductProfile(productId);
        return Result.success(profile);
    }

    /**
     * 获取新品推荐
     * <p>
     * 基于上架时间和用户画像匹配的新品推荐
     * 适用于冷启动场景和新商品曝光
     * </p>
     *
     * @param limit 推荐数量（默认8）
     * @return 新品推荐列表
     */
    @Operation(summary = "获取新品推荐", description = "基于画像匹配的新品冷启动推荐")
    @GetMapping("/new")
    public Result<List<RecommendationResultDTO>> getNewProductRecommendations(
            @RequestParam(defaultValue = "8") int limit) {
        Long userId = UserContext.getUserId(); // 可能为null（未登录）
        log.info("[推荐接口] 用户{}请求新品推荐", userId);

        UserProfileDTO profile = userId != null ? recommendationService.getUserProfile(userId) : null;
        List<RecommendationResultDTO> recommendations = newProductStrategy.recommend(userId, profile, limit);

        return Result.success(recommendations);
    }

    /**
     * 获取热销商品推荐
     * <p>
     * 基于销量排序的热销商品推荐
     * 适用于冷启动和默认推荐场景
     * </p>
     *
     * @param limit 推荐数量（默认10）
     * @return 热销商品列表
     */
    @Operation(summary = "获取热销推荐", description = "基于销量的热销商品推荐")
    @GetMapping("/hot")
    public Result<List<RecommendationResultDTO>> getHotProductRecommendations(
            @RequestParam(defaultValue = "10") int limit) {
        Long userId = UserContext.getUserId();
        log.info("[推荐接口] 用户{}请求热销推荐", userId);

        UserProfileDTO profile = userId != null ? recommendationService.getUserProfile(userId) : null;
        List<RecommendationResultDTO> recommendations = hotProductStrategy.recommend(userId, profile, limit);

        return Result.success(recommendations);
    }

    /**
     * 智能推荐（多策略融合）
     * <p>
     * 根据用户状态自动选择最优推荐策略：
     * - 新用户：新品(60%) + 热销(40%)
     * - 正常用户：融合推荐(80%) + 新品(20%)
     * </p>
     *
     * @param limit 推荐数量（默认10）
     * @return 智能推荐列表
     */
    @Operation(summary = "智能推荐", description = "根据用户状态自动选择最优推荐策略")
    @GetMapping("/smart")
    public Result<List<RecommendationResultDTO>> getSmartRecommendations(
            @RequestParam(defaultValue = "10") int limit) {
        Long userId = UserContext.getUserId();
        boolean isNewUser = false;

        // 判断是否是新用户（无历史行为）
        if (userId != null) {
            UserProfileDTO profile = recommendationService.getUserProfile(userId);
            isNewUser = profile.getTotalPurchases() == null || profile.getTotalPurchases() == 0;
        } else {
            isNewUser = true;
        }

        log.info("[推荐接口] 用户{}请求智能推荐，是否新用户: {}", userId, isNewUser);

        UserProfileDTO profile = userId != null ? recommendationService.getUserProfile(userId) : null;
        List<RecommendationResultDTO> recommendations = recommendationContext.smartRecommend(
                userId, profile, isNewUser, limit);

        return Result.success(recommendations);
    }

    /**
     * 获取纯协同过滤推荐（对比算法）
     * <p>
     * 仅基于Item-CF，不包含画像约束
     * 用于论文实验对比
     * </p>
     *
     * @param limit 推荐数量（默认10）
     * @return 纯CF推荐列表
     */
    @Operation(summary = "纯协同过滤推荐", description = "仅基于Item-CF的对比算法（不含画像约束）")
    @GetMapping("/cf")
    public Result<List<RecommendationResultDTO>> getCFRecommendations(
            @RequestParam(defaultValue = "10") int limit) {
        Long userId = UserContext.requireUserId();
        log.info("[推荐接口] 用户{}请求纯协同过滤推荐", userId);

        UserProfileDTO profile = recommendationService.getUserProfile(userId);
        List<RecommendationResultDTO> recommendations = cfStrategy.recommend(userId, profile, limit);

        return Result.success(recommendations);
    }
}
