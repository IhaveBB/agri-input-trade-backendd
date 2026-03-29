package org.example.springboot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.springboot.common.Result;
import org.example.springboot.entity.dto.statistics.MerchantDTO;
import org.example.springboot.service.RegionSalesService;
import org.example.springboot.service.RegionSalesService.ProvinceSalesDTO;
import org.example.springboot.service.RegionSalesService.RegionSalesDTO;
import org.example.springboot.service.RegionSalesService.RegionSalesOverview;
import org.example.springboot.service.RegionSalesService.DailySalesTrendDTO;
import org.example.springboot.service.StatisticsService;
import org.example.springboot.util.UserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 地域销售分析控制器
 *
 * <p>
 * 提供地域销售数据查询接口，支持热力图和分布分析。
 * 管理员可查看全部或指定商户的数据，商户只能查看自己的数据。
 * </p>
 *
 * @author IhaveBB
 * @date 2026/03/21
 */
@Slf4j
@Tag(name = "地域销售分析", description = "地域销售热力图和分布统计")
@RestController
@RequestMapping("/region-sales")
@RequiredArgsConstructor
public class RegionSalesController {

    private final RegionSalesService regionSalesService;
    private final StatisticsService statisticsService;

    /**
     * 获取省份销售热力图数据
     *
     * @param merchantId 商户ID（可为null，管理员场景）
     * @return 省份销售数据
     * @author IhaveBB
     * @date 2026/03/29
     */
    @Operation(summary = "省份销售热力图", description = "获取各省销售数据用于热力图展示")
    @GetMapping("/heatmap")
    public Result<List<ProvinceSalesDTO>> getProvinceSalesHeatmap(
            @RequestParam(required = false) Long merchantId) {
        Long finalMerchantId = UserContext.getMerchantId(merchantId);
        log.info("[地域销售接口] 获取省份销售热力图数据，merchantId: {}", finalMerchantId);
        List<ProvinceSalesDTO> data = regionSalesService.getProvinceSalesHeatmap(finalMerchantId);
        return Result.success(data);
    }

    /**
     * 获取大区销售分布
     *
     * @param merchantId 商户ID（可为null，管理员场景）
     * @return 大区销售数据
     * @author IhaveBB
     * @date 2026/03/29
     */
    @Operation(summary = "大区销售分布", description = "获取各大区的销售分布情况")
    @GetMapping("/distribution")
    public Result<List<RegionSalesDTO>> getRegionSalesDistribution(
            @RequestParam(required = false) Long merchantId) {
        Long finalMerchantId = UserContext.getMerchantId(merchantId);
        log.info("[地域销售接口] 获取大区销售分布，merchantId: {}", finalMerchantId);
        List<RegionSalesDTO> data = regionSalesService.getRegionSalesDistribution(finalMerchantId);
        return Result.success(data);
    }

    /**
     * 获取地域销售概览
     *
     * @param merchantId 商户ID（可为null，管理员场景）
     * @return 销售概览
     * @author IhaveBB
     * @date 2026/03/29
     */
    @Operation(summary = "销售概览", description = "获取地域销售的总体概览")
    @GetMapping("/overview")
    public Result<RegionSalesOverview> getRegionSalesOverview(
            @RequestParam(required = false) Long merchantId) {
        Long finalMerchantId = UserContext.getMerchantId(merchantId);
        log.info("[地域销售接口] 获取销售概览，merchantId: {}", finalMerchantId);
        RegionSalesOverview overview = regionSalesService.getRegionSalesOverview(finalMerchantId);
        return Result.success(overview);
    }

    /**
     * 获取地域销售趋势（按日统计）
     *
     * @param days       天数（默认7天）
     * @param merchantId 商户ID（可为null，管理员场景）
     * @return 每日销售趋势数据
     * @author IhaveBB
     * @date 2026/03/29
     */
    @Operation(summary = "销售趋势", description = "获取近N天的每日销售趋势数据")
    @GetMapping("/trend")
    public Result<List<DailySalesTrendDTO>> getSalesTrend(
            @RequestParam(required = false, defaultValue = "7") Integer days,
            @RequestParam(required = false) Long merchantId) {
        Long finalMerchantId = UserContext.getMerchantId(merchantId);
        log.info("[地域销售接口] 获取销售趋势，days: {}, merchantId: {}", days, finalMerchantId);
        List<DailySalesTrendDTO> data = regionSalesService.getSalesTrend(days, finalMerchantId);
        return Result.success(data);
    }

    /**
     * 获取商户列表（仅管理员）
     *
     * @return 商户列表
     * @author IhaveBB
     * @date 2026/03/29
     */
    @Operation(summary = "获取商户列表", description = "获取商户列表，用于地域销售分析筛选")
    @GetMapping("/merchants")
    public Result<List<MerchantDTO>> getMerchantList() {
        UserContext.checkAdmin();
        log.info("[地域销售接口] 获取商户列表");
        List<MerchantDTO> merchants = statisticsService.getMerchantList();
        return Result.success(merchants);
    }
}
