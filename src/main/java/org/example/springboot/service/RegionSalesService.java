package org.example.springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.example.springboot.entity.Order;
import org.example.springboot.entity.Product;
import org.example.springboot.entity.User;
import org.example.springboot.mapper.OrderMapper;
import org.example.springboot.mapper.ProductMapper;
import org.example.springboot.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 地域销售分析服务
 * <p>
 * 提供地域销售数据统计和热力图数据支持。
 * 支持按商户维度过滤数据：merchantId 为 null 时查询全平台，不为 null 时查询指定商户。
 * </p>
 *
 * @author IhaveBB
 * @date 2026/03/21
 */
@Slf4j
@Service
public class RegionSalesService {

    @Resource
    private OrderMapper orderMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private ProductMapper productMapper;

    /**
     * 中国省份列表（用于地图）
     */
    private static final List<String> CHINA_PROVINCES = Arrays.asList(
            "北京", "天津", "上海", "重庆",
            "河北", "山西", "辽宁", "吉林", "黑龙江",
            "江苏", "浙江", "安徽", "福建", "江西", "山东",
            "河南", "湖北", "湖南", "广东", "海南",
            "四川", "贵州", "云南", "陕西", "甘肃", "青海", "台湾",
            "内蒙古", "广西", "西藏", "宁夏", "新疆",
            "香港", "澳门"
    );

    /**
     * 地域销售数据DTO
     */
    @Data
    public static class RegionSalesDTO {
        /** 地区名称 */
        private String regionName;
        /** 销售额 */
        private BigDecimal salesAmount;
        /** 订单数量 */
        private Integer orderCount;
        /** 商品数量 */
        private Integer productCount;
        /** 热度值（用于地图，0-100） */
        private Double heatValue;
    }

    /**
     * 省份销售数据DTO
     */
    @Data
    public static class ProvinceSalesDTO {
        /** 省份名称 */
        private String provinceName;
        /** 销售额 */
        private BigDecimal salesAmount;
        /** 订单数 */
        private Integer orderCount;
        /** 用户数 */
        private Integer userCount;
        /** 热力值 */
        private Double heatValue;
    }

    /**
     * 地域销售概览DTO
     */
    @Data
    public static class RegionSalesOverview {
        /** 总销售额 */
        private BigDecimal totalSales;
        /** 覆盖省份数 */
        private Integer coveredProvinces;
        /** 最热销售地区 */
        private String topRegion;
        /** 地区销售分布 */
        private List<RegionSalesDTO> regionDistribution;
    }

    /**
     * 获取指定商户的商品ID集合
     *
     * @param merchantId 商户ID
     * @return 该商户所有商品的ID集合，merchantId 为 null 时返回 null
     * @author IhaveBB
     * @date 2026/03/29
     */
    private Set<Long> getMerchantProductIds(Long merchantId) {
        if (merchantId == null) {
            return null;
        }
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getStatus, 1)
               .eq(Product::getMerchantId, merchantId)
               .select(Product::getId);
        List<Product> products = productMapper.selectList(wrapper);
        return products.stream()
                .map(Product::getId)
                .collect(Collectors.toSet());
    }

    /**
     * 获取地域销售热力图数据
     *
     * @param merchantId 商户ID（可为null，表示查询全平台）
     * @return 省份销售数据列表
     * @author IhaveBB
     * @date 2026/03/21
     */
    public List<ProvinceSalesDTO> getProvinceSalesHeatmap(Long merchantId) {
        log.info("[地域销售] 生成省份销售热力图数据，merchantId: {}", merchantId);

        // 获取所有已完成订单
        LambdaQueryWrapper<Order> orderWrapper = new LambdaQueryWrapper<>();
        orderWrapper.eq(Order::getStatus, 3);

        // 按商户过滤：通过订单的 productId 关联商品表
        Set<Long> merchantProductIds = getMerchantProductIds(merchantId);
        if (merchantProductIds != null && !merchantProductIds.isEmpty()) {
            orderWrapper.in(Order::getProductId, merchantProductIds);
        } else if (merchantProductIds != null) {
            // 商户没有商品，直接返回空数据
            return buildEmptyProvinceData();
        }

        List<Order> orders = orderMapper.selectList(orderWrapper);

        // 按省份聚合数据
        Map<String, ProvinceData> provinceDataMap = new HashMap<>();

        for (Order order : orders) {
            if (order.getUserId() == null) {
                continue;
            }

            // 获取用户省份
            User user = userMapper.selectById(order.getUserId());
            if (user == null || user.getLocation() == null) {
                continue;
            }

            String province = extractProvince(user.getLocation());
            if (province == null) {
                continue;
            }

            ProvinceData data = provinceDataMap.computeIfAbsent(province, k -> new ProvinceData());
            data.salesAmount = data.salesAmount.add(order.getTotalPrice() != null ? order.getTotalPrice() : BigDecimal.ZERO);
            data.orderCount++;
            data.userIds.add(order.getUserId());
        }

        // 找出最大值用于归一化
        BigDecimal maxSales = provinceDataMap.values().stream()
                .map(d -> d.salesAmount)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ONE);

        // 转换为DTO
        List<ProvinceSalesDTO> result = new ArrayList<>();
        for (String province : CHINA_PROVINCES) {
            ProvinceData data = provinceDataMap.getOrDefault(province, new ProvinceData());

            ProvinceSalesDTO dto = new ProvinceSalesDTO();
            dto.setProvinceName(province);
            dto.setSalesAmount(data.salesAmount);
            dto.setOrderCount(data.orderCount);
            dto.setUserCount(data.userIds.size());

            // 计算热力值（0-100）
            double heatValue = maxSales.compareTo(BigDecimal.ZERO) > 0
                    ? data.salesAmount.doubleValue() / maxSales.doubleValue() * 100
                    : 0.0;
            dto.setHeatValue(Math.round(heatValue * 100.0) / 100.0);

            result.add(dto);
        }

        log.info("[地域销售] 生成{}个省份的销售数据", result.size());
        return result;
    }

    /**
     * 获取大区销售分布
     *
     * @param merchantId 商户ID（可为null，表示查询全平台）
     * @return 大区销售数据
     * @author IhaveBB
     * @date 2026/03/21
     */
    public List<RegionSalesDTO> getRegionSalesDistribution(Long merchantId) {
        log.info("[地域销售] 生成大区销售分布，merchantId: {}", merchantId);

        // 获取所有已完成订单
        LambdaQueryWrapper<Order> orderWrapper = new LambdaQueryWrapper<>();
        orderWrapper.eq(Order::getStatus, 3);

        // 按商户过滤
        Set<Long> merchantProductIds = getMerchantProductIds(merchantId);
        if (merchantProductIds != null && !merchantProductIds.isEmpty()) {
            orderWrapper.in(Order::getProductId, merchantProductIds);
        } else if (merchantProductIds != null) {
            return new ArrayList<>();
        }

        List<Order> orders = orderMapper.selectList(orderWrapper);

        // 大区映射
        Map<String, String> provinceToRegion = buildProvinceToRegionMap();

        // 按大区聚合
        Map<String, RegionData> regionDataMap = new HashMap<>();

        for (Order order : orders) {
            if (order.getUserId() == null) {
                continue;
            }

            User user = userMapper.selectById(order.getUserId());
            if (user == null || user.getLocation() == null) {
                continue;
            }

            String province = extractProvince(user.getLocation());
            String region = provinceToRegion.getOrDefault(province, "其他");

            RegionData data = regionDataMap.computeIfAbsent(region, k -> new RegionData());
            data.salesAmount = data.salesAmount.add(order.getTotalPrice() != null ? order.getTotalPrice() : BigDecimal.ZERO);
            data.orderCount++;
        }

        // 找出最大值用于归一化
        BigDecimal maxSales = regionDataMap.values().stream()
                .map(d -> d.salesAmount)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ONE);

        // 转换为DTO
        List<RegionSalesDTO> result = new ArrayList<>();
        for (Map.Entry<String, RegionData> entry : regionDataMap.entrySet()) {
            RegionData data = entry.getValue();

            RegionSalesDTO dto = new RegionSalesDTO();
            dto.setRegionName(entry.getKey());
            dto.setSalesAmount(data.salesAmount);
            dto.setOrderCount(data.orderCount);

            double heatValue = maxSales.compareTo(BigDecimal.ZERO) > 0
                    ? data.salesAmount.doubleValue() / maxSales.doubleValue() * 100
                    : 0.0;
            dto.setHeatValue(Math.round(heatValue * 100.0) / 100.0);

            result.add(dto);
        }

        // 按销售额排序
        result.sort((a, b) -> b.getSalesAmount().compareTo(a.getSalesAmount()));

        return result;
    }

    /**
     * 获取地域销售概览
     *
     * @param merchantId 商户ID（可为null，表示查询全平台）
     * @return 销售概览
     * @author IhaveBB
     * @date 2026/03/21
     */
    public RegionSalesOverview getRegionSalesOverview(Long merchantId) {
        RegionSalesOverview overview = new RegionSalesOverview();

        List<ProvinceSalesDTO> provinceData = getProvinceSalesHeatmap(merchantId);
        List<RegionSalesDTO> regionData = getRegionSalesDistribution(merchantId);

        // 计算总销售额
        BigDecimal totalSales = provinceData.stream()
                .map(ProvinceSalesDTO::getSalesAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        overview.setTotalSales(totalSales);

        // 计算覆盖省份数（有销售的省份）
        long coveredProvinces = provinceData.stream()
                .filter(p -> p.getSalesAmount().compareTo(BigDecimal.ZERO) > 0)
                .count();
        overview.setCoveredProvinces((int) coveredProvinces);

        // 最热销售地区
        if (!regionData.isEmpty()) {
            overview.setTopRegion(regionData.get(0).getRegionName());
        }

        overview.setRegionDistribution(regionData);

        return overview;
    }

    /**
     * 从地址中提取省份
     *
     * @param location 地址
     * @return 省份名称
     * @author IhaveBB
     * @date 2026/03/21
     */
    private String extractProvince(String location) {
        if (location == null || location.isEmpty()) {
            return null;
        }

        // 去除空格
        location = location.trim();

        // 尝试直接匹配省份
        for (String province : CHINA_PROVINCES) {
            if (location.contains(province)) {
                return province;
            }
        }

        // 处理特殊前缀
        if (location.startsWith("中国")) {
            location = location.substring(2);
        }

        // 再次尝试匹配
        for (String province : CHINA_PROVINCES) {
            if (location.contains(province)) {
                return province;
            }
        }

        // 返回地址的第一部分（假设是省份）
        String[] parts = location.split("[-省市区县]");
        if (parts.length > 0 && !parts[0].isEmpty()) {
            return parts[0];
        }

        return null;
    }

    /**
     * 构建省份到大区的映射
     *
     * @return 省份到大区的映射Map
     * @author IhaveBB
     * @date 2026/03/29
     */
    private Map<String, String> buildProvinceToRegionMap() {
        Map<String, String> map = new HashMap<>();
        map.put("北京", "华北");
        map.put("天津", "华北");
        map.put("河北", "华北");
        map.put("山西", "华北");
        map.put("内蒙古", "华北");
        map.put("辽宁", "东北");
        map.put("吉林", "东北");
        map.put("黑龙江", "东北");
        map.put("上海", "华东");
        map.put("江苏", "华东");
        map.put("浙江", "华东");
        map.put("安徽", "华东");
        map.put("福建", "华东");
        map.put("江西", "华东");
        map.put("山东", "华东");
        map.put("河南", "华中");
        map.put("湖北", "华中");
        map.put("湖南", "华中");
        map.put("广东", "华南");
        map.put("广西", "华南");
        map.put("海南", "华南");
        map.put("重庆", "西南");
        map.put("四川", "西南");
        map.put("贵州", "西南");
        map.put("云南", "西南");
        map.put("西藏", "西南");
        map.put("陕西", "西北");
        map.put("甘肃", "西北");
        map.put("青海", "西北");
        map.put("宁夏", "西北");
        map.put("新疆", "西北");
        return map;
    }

    /**
     * 构建空的省份数据（商户无商品时使用）
     *
     * @return 空的省份销售数据列表
     * @author IhaveBB
     * @date 2026/03/29
     */
    private List<ProvinceSalesDTO> buildEmptyProvinceData() {
        List<ProvinceSalesDTO> result = new ArrayList<>();
        for (String province : CHINA_PROVINCES) {
            ProvinceSalesDTO dto = new ProvinceSalesDTO();
            dto.setProvinceName(province);
            dto.setSalesAmount(BigDecimal.ZERO);
            dto.setOrderCount(0);
            dto.setUserCount(0);
            dto.setHeatValue(0.0);
            result.add(dto);
        }
        return result;
    }

    /**
     * 每日销售趋势DTO
     */
    @Data
    public static class DailySalesTrendDTO {
        /** 日期 */
        private String date;
        /** 销售额 */
        private BigDecimal salesAmount;
        /** 订单数 */
        private Integer orderCount;
    }

    /**
     * 获取近N天的每日销售趋势数据
     *
     * @param days       天数（默认7天）
     * @param merchantId 商户ID（可为null，表示查询全平台）
     * @return 每日销售趋势列表
     * @author IhaveBB
     * @date 2026/03/29
     */
    public List<DailySalesTrendDTO> getSalesTrend(Integer days, Long merchantId) {
        if (days == null || days <= 0) {
            days = 7;
        }
        log.info("[地域销售] 获取近{}天销售趋势，merchantId: {}", days, merchantId);

        // 计算日期范围
        java.time.LocalDate endDate = java.time.LocalDate.now();
        java.time.LocalDate startDate = endDate.minusDays(days - 1);

        // 获取已完成订单
        LambdaQueryWrapper<Order> orderWrapper = new LambdaQueryWrapper<>();
        orderWrapper.eq(Order::getStatus, 3);

        Set<Long> merchantProductIds = getMerchantProductIds(merchantId);
        if (merchantProductIds != null && !merchantProductIds.isEmpty()) {
            orderWrapper.in(Order::getProductId, merchantProductIds);
        } else if (merchantProductIds != null) {
            return buildEmptyTrend(startDate, endDate);
        }

        orderWrapper.ge(Order::getCreatedAt, java.sql.Timestamp.valueOf(startDate.atStartOfDay()));
        orderWrapper.lt(Order::getCreatedAt, java.sql.Timestamp.valueOf(endDate.plusDays(1).atStartOfDay()));
        List<Order> orders = orderMapper.selectList(orderWrapper);

        // 按日期聚合
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");
        Map<String, TrendAccumulator> trendMap = new java.util.LinkedHashMap<>();

        // 初始化所有日期
        for (int i = 0; i < days; i++) {
            java.time.LocalDate day = startDate.plusDays(i);
            trendMap.put(day.format(formatter), new TrendAccumulator());
        }

        // 累加订单数据
        for (Order order : orders) {
            if (order.getCreatedAt() == null) {
                continue;
            }
            String dayKey = order.getCreatedAt().toLocalDateTime().toLocalDate().format(formatter);
            TrendAccumulator acc = trendMap.get(dayKey);
            if (acc != null) {
                acc.salesAmount = acc.salesAmount.add(order.getTotalPrice() != null ? order.getTotalPrice() : BigDecimal.ZERO);
                acc.orderCount++;
            }
        }

        // 转换为DTO
        List<DailySalesTrendDTO> result = new ArrayList<>();
        for (Map.Entry<String, TrendAccumulator> entry : trendMap.entrySet()) {
            DailySalesTrendDTO dto = new DailySalesTrendDTO();
            dto.setDate(entry.getKey());
            dto.setSalesAmount(entry.getValue().salesAmount);
            dto.setOrderCount(entry.getValue().orderCount);
            result.add(dto);
        }

        return result;
    }

    /**
     * 构建空的每日趋势数据
     *
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 空的趋势列表
     * @author IhaveBB
     * @date 2026/03/29
     */
    private List<DailySalesTrendDTO> buildEmptyTrend(java.time.LocalDate startDate, java.time.LocalDate endDate) {
        List<DailySalesTrendDTO> result = new ArrayList<>();
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");
        long days = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;
        for (int i = 0; i < days; i++) {
            DailySalesTrendDTO dto = new DailySalesTrendDTO();
            dto.setDate(startDate.plusDays(i).format(formatter));
            dto.setSalesAmount(BigDecimal.ZERO);
            dto.setOrderCount(0);
            result.add(dto);
        }
        return result;
    }

    /**
     * 趋势累加器内部类
     */
    private static class TrendAccumulator {
        BigDecimal salesAmount = BigDecimal.ZERO;
        int orderCount = 0;
    }

    /**
     * 省份数据内部类
     */
    private static class ProvinceData {
        BigDecimal salesAmount = BigDecimal.ZERO;
        int orderCount = 0;
        Set<Long> userIds = new HashSet<>();
    }

    /**
     * 大区数据内部类
     */
    private static class RegionData {
        BigDecimal salesAmount = BigDecimal.ZERO;
        int orderCount = 0;
    }
}
