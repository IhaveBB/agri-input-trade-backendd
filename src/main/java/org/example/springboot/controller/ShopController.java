package org.example.springboot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.springboot.common.Result;
import org.example.springboot.entity.Product;
import org.example.springboot.entity.dto.shop.ShopDTO;
import org.example.springboot.entity.dto.shop.ShopReviewDTO;
import org.example.springboot.service.ShopService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 店铺控制器
 */
@Tag(name = "店铺接口")
@RestController
@RequestMapping("/shop")
public class ShopController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShopController.class);

    @Autowired
    private ShopService shopService;

    @Operation(summary = "获取店铺信息")
    @GetMapping("/info")
    public Result<ShopDTO> getShopInfo(@RequestParam Long merchantId) {
        LOGGER.info("获取店铺信息，merchantId: {}", merchantId);
        ShopDTO shopInfo = shopService.getShopInfo(merchantId);
        if (shopInfo == null) {
            return Result.error("-1", "店铺不存在");
        }
        return Result.success(shopInfo);
    }

    @Operation(summary = "获取店铺商品列表")
    @GetMapping("/products")
    public Result<?> getShopProducts(
            @RequestParam Long merchantId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        LOGGER.info("获取店铺商品列表，merchantId: {}, pageNum: {}, pageSize: {}", merchantId, pageNum, pageSize);
        Map<String, Object> products = shopService.getShopProducts(merchantId, pageNum, pageSize);
        return Result.success(products);
    }

    @Operation(summary = "获取店铺评价列表")
    @GetMapping("/reviews")
    public Result<?> getShopReviews(
            @RequestParam Long merchantId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        LOGGER.info("获取店铺评价列表，merchantId: {}, pageNum: {}, pageSize: {}", merchantId, pageNum, pageSize);
        Map<String, Object> reviews = shopService.getShopReviews(merchantId, pageNum, pageSize);
        return Result.success(reviews);
    }

    @Operation(summary = "获取店铺统计信息")
    @GetMapping("/statistics")
    public Result<?> getShopStatistics(@RequestParam Long merchantId) {
        LOGGER.info("获取店铺统计信息，merchantId: {}", merchantId);
        Map<String, Object> statistics = shopService.getShopStatistics(merchantId);
        return Result.success(statistics);
    }
}
