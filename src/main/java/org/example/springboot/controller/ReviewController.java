package org.example.springboot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.springboot.common.Result;
import org.example.springboot.entity.Review;
import org.example.springboot.service.ReviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "评价管理接口")
@RestController
@RequestMapping("/review")
public class ReviewController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReviewController.class);

    @Autowired
    private ReviewService reviewService;

    @Operation(summary = "创建评价")
    @PostMapping
    public Result<?> createReview(@RequestBody Review review) {
        return Result.success(reviewService.createReview(review));
    }

    @Operation(summary = "更新评价状态")
    @PutMapping("/{id}/status")
    public Result<?> updateReviewStatus(@PathVariable Long id, @RequestParam Integer status) {
        reviewService.updateReviewStatus(id, status);
        return Result.success();
    }

    @Operation(summary = "删除评价")
    @DeleteMapping("/{id}")
    public Result<?> deleteReview(@PathVariable Long id) {
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
            @RequestParam(required = false) Integer userId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Long merchantId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(reviewService.getReviewsByPage(productId, productName,userId,username,merchantId, status, currentPage, size));
    }
}

 