package org.example.springboot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.springboot.common.Result;
import org.example.springboot.service.StockWarningService;
import org.example.springboot.service.StockWarningService.StockWarningDTO;
import org.example.springboot.service.StockWarningService.StockWarningOverview;
import org.example.springboot.util.UserContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 库存预警控制器
 *
 * <p>
 * 提供库存预警查询接口
 * </p>
 *
 * @author IhaveBB
 * @date 2026/03/21
 */
@Slf4j
@Tag(name = "库存预警", description = "库存监控和预警管理")
@RestController
@RequestMapping("/stock-warning")
@RequiredArgsConstructor
public class StockWarningController {

    private final StockWarningService stockWarningService;

    /**
     * 获取所有库存预警（管理员）
     *
     * @return 预警信息列表
     */
    @Operation(summary = "获取所有库存预警", description = "管理员查看所有商品的库存预警信息")
    @GetMapping("/all")
    public Result<List<StockWarningDTO>> getAllStockWarnings() {
        log.info("[库存预警接口] 查询所有库存预警");
        List<StockWarningDTO> warnings = stockWarningService.getAllStockWarnings();
        return Result.success(warnings);
    }

    /**
     * 获取当前商户的库存预警
     *
     * @return 预警信息列表
     */
    @Operation(summary = "获取商户库存预警", description = "商户查看自己商品的库存预警信息")
    @GetMapping("/merchant")
    public Result<List<StockWarningDTO>> getMerchantStockWarnings() {
        Long merchantId = UserContext.requireUserId();
        log.info("[库存预警接口] 查询商户{}的库存预警", merchantId);
        List<StockWarningDTO> warnings = stockWarningService.getMerchantStockWarnings(merchantId);
        return Result.success(warnings);
    }

    /**
     * 检查指定商品库存
     *
     * @param productId 商品ID
     * @return 预警信息
     */
    @Operation(summary = "检查商品库存", description = "检查指定商品的库存预警状态")
    @GetMapping("/product/{productId}")
    public Result<StockWarningDTO> checkProductStock(@PathVariable Long productId) {
        log.info("[库存预警接口] 检查商品{}的库存", productId);
        StockWarningDTO warning = stockWarningService.checkProductStock(productId);
        return Result.success(warning);
    }

    /**
     * 获取库存统计概览
     *
     * @return 统计信息
     */
    @Operation(summary = "库存概览", description = "获取库存状态统计概览")
    @GetMapping("/overview")
    public Result<StockWarningOverview> getStockOverview() {
        log.info("[库存预警接口] 查询库存概览");
        StockWarningOverview overview = stockWarningService.getStockOverview();
        return Result.success(overview);
    }

    /**
     * 获取预警数量（用于前端角标）
     *
     * @return 预警数量
     */
    @Operation(summary = "预警数量", description = "获取当前用户的库存预警数量")
    @GetMapping("/count")
    public Result<Integer> getWarningCount() {
        int count;
        if (UserContext.isAdmin()) {
            count = stockWarningService.getWarningCount();
        } else if (UserContext.isMerchant()) {
            count = stockWarningService.getMerchantStockWarnings(UserContext.getUserId()).size();
        } else {
            count = 0;
        }
        return Result.success(count);
    }
}
