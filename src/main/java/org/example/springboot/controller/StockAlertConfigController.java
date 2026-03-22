package org.example.springboot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.springboot.common.Result;
import org.example.springboot.entity.StockAlertConfig;
import org.example.springboot.service.StockAlertConfigService;
import org.example.springboot.service.StockAlertConfigService.ProductConfigVO;
import org.example.springboot.util.UserContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 库存预警配置控制器
 *
 * @author IhaveBB
 * @date 2026/03/22
 */
@Slf4j
@Tag(name = "库存预警配置", description = "库存预警配置管理接口")
@RestController
@RequestMapping("/stock-alert/config")
public class StockAlertConfigController {

    @Resource
    private StockAlertConfigService configService;

    /**
     * 获取商户全局配置
     *
     * @return 全局配置
     * @author IhaveBB
     * @date 2026/03/22
     */
    @Operation(summary = "获取全局配置", description = "获取当前商户的库存预警全局配置")
    @GetMapping("/global")
    public Result<StockAlertConfig> getGlobalConfig() {
        Long merchantId = UserContext.requireUserId();
        StockAlertConfig config = configService.getOrCreateMerchantConfig(merchantId);
        return Result.success(config);
    }

    /**
     * 更新商户全局配置
     *
     * @param config 配置信息
     * @return 更新后的配置
     * @author IhaveBB
     * @date 2026/03/22
     */
    @Operation(summary = "更新全局配置", description = "更新当前商户的库存预警全局配置")
    @PutMapping("/global")
    public Result<StockAlertConfig> updateGlobalConfig(@RequestBody StockAlertConfig config) {
        Long merchantId = UserContext.requireUserId();
        StockAlertConfig updated = configService.updateMerchantConfig(merchantId, config);
        return Result.success(updated);
    }

    /**
     * 获取商品配置列表（分页）
     *
     * @param productName 商品名称（模糊搜索，可选）
     * @param pageNum     页码
     * @param pageSize    每页大小
     * @return 商品配置分页列表
     * @author IhaveBB
     * @date 2026/03/22
     */
    @Operation(summary = "获取商品配置列表", description = "分页获取当前商户的商品预警配置列表")
    @GetMapping("/products")
    public Result<Page<ProductConfigVO>> getProductConfigList(
            @Parameter(description = "商品名称（模糊搜索）") @RequestParam(required = false) String productName,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int pageSize) {
        Long merchantId = UserContext.requireUserId();
        Page<ProductConfigVO> page = configService.getProductConfigPage(merchantId, productName, pageNum, pageSize);
        return Result.success(page);
    }

    /**
     * 更新单个商品配置
     *
     * @param productId 商品ID
     * @param config    配置信息
     * @return 更新后的配置
     * @author IhaveBB
     * @date 2026/03/22
     */
    @Operation(summary = "更新商品配置", description = "更新指定商品的预警配置")
    @PutMapping("/product/{productId}")
    public Result<StockAlertConfig> updateProductConfig(
            @Parameter(description = "商品ID") @PathVariable Long productId,
            @RequestBody StockAlertConfig config) {
        Long merchantId = UserContext.requireUserId();
        StockAlertConfig updated = configService.updateProductConfig(productId, merchantId, config);
        return Result.success(updated);
    }

    /**
     * 切换商品预警开关
     *
     * @param productId 商品ID
     * @param enabled   是否启用
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/22
     */
    @Operation(summary = "切换商品预警开关", description = "开启或关闭指定商品的预警")
    @PutMapping("/product/{productId}/toggle")
    public Result<Void> toggleProductAlert(
            @Parameter(description = "商品ID") @PathVariable Long productId,
            @Parameter(description = "是否启用") @RequestParam Boolean enabled) {
        Long merchantId = UserContext.requireUserId();
        StockAlertConfig config = new StockAlertConfig();
        config.setEnabled(enabled);
        configService.updateProductConfig(productId, merchantId, config);
        return Result.success();
    }

    /**
     * 批量更新商品配置
     *
     * @param productIds 商品ID列表
     * @param enabled    是否启用
     * @param ruleConfig 规则配置（可选）
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/22
     */
    @Operation(summary = "批量更新商品配置", description = "批量更新多个商品的预警配置")
    @PutMapping("/products/batch")
    public Result<Void> batchUpdateProductConfig(
            @Parameter(description = "商品ID列表") @RequestBody List<Long> productIds,
            @Parameter(description = "是否启用") @RequestParam Boolean enabled,
            @Parameter(description = "规则配置（可选）") @RequestParam(required = false) String ruleConfig) {
        Long merchantId = UserContext.requireUserId();
        configService.batchUpdateProductConfig(merchantId, productIds, enabled, ruleConfig);
        return Result.success();
    }

    /**
     * 批量切换商品预警开关
     *
     * @param productIds 商品ID列表
     * @param enabled    是否启用
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/22
     */
    @Operation(summary = "批量切换预警开关", description = "批量开启或关闭多个商品的预警")
    @PutMapping("/products/batch-toggle")
    public Result<Void> batchToggleProductAlert(
            @Parameter(description = "商品ID列表") @RequestBody List<Long> productIds,
            @Parameter(description = "是否启用") @RequestParam Boolean enabled) {
        Long merchantId = UserContext.requireUserId();
        configService.batchUpdateProductConfig(merchantId, productIds, enabled, null);
        return Result.success();
    }
}
