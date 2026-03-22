package org.example.springboot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.springboot.annotation.RequiresRole;
import org.example.springboot.common.Result;
import org.example.springboot.entity.dto.ReviewCreateDTO;
import org.example.springboot.enumClass.UserRole;
import org.example.springboot.enums.ErrorCodeEnum;
import org.example.springboot.exception.BusinessException;
import org.example.springboot.service.ReviewService;
import org.example.springboot.util.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name = "评价管理接口")
@RestController
@RequestMapping("/review")
public class ReviewController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReviewController.class);

    @Autowired
    private ReviewService reviewService;

    /**
     * 创建评价
     * 权限：需要登录，userId 从上下文获取
     *
     * @param dto 评价创建DTO
     * @return 创建结果
     * @author IhaveBB
     * @date 2026/03/21
     */
    @Operation(summary = "创建评价")
    @RequiresRole
    @PostMapping
    public Result<?> createReview(@Valid @RequestBody ReviewCreateDTO dto) {
        Long currentUserId = UserContext.getUserId();
        return Result.success(reviewService.createReview(currentUserId, dto));
    }

    /**
     * 更新评价状态
     * 权限：只有管理员
     *
     * @param id     评价ID
     * @param status 状态
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "更新评价状态")
    @RequiresRole("ADMIN")
    @PutMapping("/{id}/status")
    public Result<?> updateReviewStatus(@PathVariable Long id, @RequestParam Integer status) {
        reviewService.updateReviewStatus(id, status);
        return Result.success();
    }

    /**
     * 删除评价
     * 权限：管理员可删除任何评价，普通用户只能删除自己的评价
     *
     * @param id 评价ID
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "删除评价")
    @RequiresRole
    @DeleteMapping("/{id}")
    public Result<?> deleteReview(@PathVariable Long id) {
        Long currentUserId = UserContext.getUserId();
        String role = UserContext.getRole();

        // 管理员可以删除任何评价，普通用户只能删除自己的评价
        if (!UserRole.isAdmin(role)) {
            var review = reviewService.getReviewById(id);
            if (review != null && !review.getUserId().equals(currentUserId)) {
                throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权限删除他人评价");
            }
        }

        reviewService.deleteReview(id);
        return Result.success();
    }

    @Operation(summary = "根据ID获取评价详情")
    @GetMapping("/{id}")
    public Result<?> getReviewById(@PathVariable Long id) {
        return Result.success(reviewService.getReviewById(id));
    }


    @Operation(summary = "根据商品ID获取评价列表")
    @GetMapping("/product/{productId}")
    public Result<?> getReviewsByProductId(@PathVariable Long productId, @RequestParam(required = false) Integer status) {
        return Result.success(reviewService.getReviewsByProductId(productId, status));
    }

    @Operation(summary = "分页查询评价列表")
    @GetMapping("/page")
    public Result<?> getReviewsByPage(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Long merchantId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(reviewService.getReviewsByPage(productId, productName,userId,username,merchantId, status, currentPage, size));
    }
}
