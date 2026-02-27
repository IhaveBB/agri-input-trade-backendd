package org.example.springboot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.springboot.entity.Order;
import org.example.springboot.entity.dto.statistics.RegionSalesDTO;
import org.example.springboot.entity.dto.statistics.SalesTrendDTO;
import org.example.springboot.entity.dto.statistics.SeasonalSalesDTO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 统计数据Mapper
 */
@Mapper
public interface StatisticsMapper extends BaseMapper<Order> {

    /**
     * 查询销售趋势（按天分组）
     */
    List<SalesTrendDTO> selectSalesTrend(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("merchantId") Long merchantId
    );

    /**
     * 按季节查询销售统计
     */
    List<SeasonalSalesDTO> selectSeasonalSales(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("merchantId") Long merchantId
    );

    /**
     * 按月份查询销售统计
     */
    List<SeasonalSalesDTO> selectMonthlySales(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("merchantId") Long merchantId
    );

    /**
     * 按地区查询销售统计
     */
    List<RegionSalesDTO> selectRegionSales(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("merchantId") Long merchantId
    );
}
