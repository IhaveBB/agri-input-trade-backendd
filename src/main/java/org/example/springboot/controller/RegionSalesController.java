package org.example.springboot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.springboot.common.Result;
import org.example.springboot.service.RegionSalesService;
import org.example.springboot.service.RegionSalesService.ProvinceSalesDTO;
import org.example.springboot.service.RegionSalesService.RegionSalesDTO;
import org.example.springboot.service.RegionSalesService.RegionSalesOverview;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 地域销售分析控制器
 *
 * <p>
 * 提供地域销售数据查询接口，支持热力图和分布分析
 * </p>
 *
 * @author agri-input-trade
 * @version 1.0
 */
@Slf4j
@Tag(name = "地域销售分析", description = "地域销售热力图和分布统计")
@RestController
@RequestMapping("/api/region-sales")
@RequiredArgsConstructor
public class RegionSalesController {

    private final RegionSalesService regionSalesService;

    /**
     * 获取省份销售热力图数据
     *
     * @return 省份销售数据
     */
    @Operation(summary = "省份销售热力图", description = "获取各省销售数据用于热力图展示")
    @GetMapping("/heatmap")
    public Result<List<ProvinceSalesDTO>> getProvinceSalesHeatmap() {
        log.info("[地域销售接口] 获取省份销售热力图数据");
        List<ProvinceSalesDTO> data = regionSalesService.getProvinceSalesHeatmap();
        return Result.success(data);
    }

    /**
     * 获取大区销售分布
     *
     * @return 大区销售数据
     */
    @Operation(summary = "大区销售分布", description = "获取各大区的销售分布情况")
    @GetMapping("/distribution")
    public Result<List<RegionSalesDTO>> getRegionSalesDistribution() {
        log.info("[地域销售接口] 获取大区销售分布");
        List<RegionSalesDTO> data = regionSalesService.getRegionSalesDistribution();
        return Result.success(data);
    }

    /**
     * 获取地域销售概览
     *
     * @return 销售概览
     */
    @Operation(summary = "销售概览", description = "获取地域销售的总体概览")
    @GetMapping("/overview")
    public Result<RegionSalesOverview> getRegionSalesOverview() {
        log.info("[地域销售接口] 获取销售概览");
        RegionSalesOverview overview = regionSalesService.getRegionSalesOverview();
        return Result.success(overview);
    }
}
