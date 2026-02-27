package org.example.springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.example.springboot.entity.Order;
import org.example.springboot.entity.Product;
import org.example.springboot.entity.User;
import org.example.springboot.entity.Category;
import org.example.springboot.entity.dto.statistics.*;
import org.example.springboot.mapper.OrderMapper;
import org.example.springboot.mapper.ProductMapper;
import org.example.springboot.mapper.UserMapper;
import org.example.springboot.mapper.CategoryMapper;
import org.example.springboot.mapper.StatisticsMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 统计服务
 */
@Service
public class StatisticsService {
    private static final Logger LOGGER = LoggerFactory.getLogger(StatisticsService.class);

    @Resource
    private OrderMapper orderMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private ProductMapper productMapper;

    @Resource
    private CategoryMapper categoryMapper;

    @Resource
    private StatisticsMapper statisticsMapper;

    // ==================== 月度统计 ====================

    public OrderStatisticsDTO getMonthlyOrderStatistics(Long merchantId) {
        OrderStatisticsDTO dto = new OrderStatisticsDTO();

        YearMonth currentMonth = YearMonth.now();
        YearMonth lastMonth = currentMonth.minusMonths(1);

        long currentMonthOrders = countOrders(currentMonth, merchantId);
        long lastMonthOrders = countOrders(lastMonth, merchantId);
        double growthRate = calculateGrowthRate(currentMonthOrders, lastMonthOrders);

        dto.setCurrentMonthOrders(currentMonthOrders);
        dto.setLastMonthOrders(lastMonthOrders);
        dto.setGrowthRate(String.format("%.2f", growthRate) + "%");

        LOGGER.info("月度订单统计结果：{}", dto);
        return dto;
    }

    public SalesStatisticsDTO getMonthlySalesStatistics(Long merchantId) {
        SalesStatisticsDTO dto = new SalesStatisticsDTO();

        YearMonth currentMonth = YearMonth.now();
        YearMonth lastMonth = currentMonth.minusMonths(1);

        double currentMonthSales = sumSalesAmount(currentMonth, merchantId);
        double lastMonthSales = sumSalesAmount(lastMonth, merchantId);
        double growthRate = calculateGrowthRate(currentMonthSales, lastMonthSales);

        dto.setCurrentMonthSales(currentMonthSales);
        dto.setLastMonthSales(lastMonthSales);
        dto.setGrowthRate(String.format("%.2f", growthRate) + "%");

        LOGGER.info("月度销售额统计结果：{}", dto);
        return dto;
    }

    // ==================== 用户统计 ====================

    public UserOrderStatisticsDTO getUserOrderStatistics(Long userId) {
        UserOrderStatisticsDTO dto = new UserOrderStatisticsDTO();

        YearMonth currentMonth = YearMonth.now();
        YearMonth lastMonth = currentMonth.minusMonths(1);

        long currentMonthOrders = countUserOrders(currentMonth, userId);
        long lastMonthOrders = countUserOrders(lastMonth, userId);
        long totalOrders = totalUserOrders(userId);
        double growthRate = calculateGrowthRate(currentMonthOrders, lastMonthOrders);

        dto.setCurrentMonthOrders(currentMonthOrders);
        dto.setLastMonthOrders(lastMonthOrders);
        dto.setTotalOrders(totalOrders);
        dto.setGrowthRate(String.format("%.2f", growthRate) + "%");

        LOGGER.info("用户订单统计结果：{}", dto);
        return dto;
    }

    public UserSpendingStatisticsDTO getUserSpendingStatistics(Long userId) {
        UserSpendingStatisticsDTO dto = new UserSpendingStatisticsDTO();

        YearMonth currentMonth = YearMonth.now();
        YearMonth lastMonth = currentMonth.minusMonths(1);

        double currentMonthSpending = sumUserSpending(currentMonth, userId);
        double lastMonthSpending = sumUserSpending(lastMonth, userId);
        double totalSpending = totalUserSpending(userId);
        double growthRate = calculateGrowthRate(currentMonthSpending, lastMonthSpending);

        dto.setCurrentMonthSpending(currentMonthSpending);
        dto.setLastMonthSpending(lastMonthSpending);
        dto.setTotalSpending(totalSpending);
        dto.setGrowthRate(String.format("%.2f", growthRate) + "%");

        LOGGER.info("用户消费统计结果：{}", dto);
        return dto;
    }

    public YearlyUserStatisticsDTO getYearlyUserStatistics() {
        YearlyUserStatisticsDTO dto = new YearlyUserStatisticsDTO();

        int currentYear = LocalDateTime.now().getYear();
        long currentYearUsers = countUsers(currentYear);
        long lastYearUsers = countUsers(currentYear - 1);
        double growthRate = calculateGrowthRate(currentYearUsers, lastYearUsers);

        dto.setCurrentYearUsers(currentYearUsers);
        dto.setLastYearUsers(lastYearUsers);
        dto.setGrowthRate(String.format("%.2f", growthRate) + "%");

        LOGGER.info("年度用户统计结果：{}", dto);
        return dto;
    }

    // ==================== 商品统计 ====================

    public TopProductsStatisticsDTO getTopSellingProducts(Long merchantId) {
        TopProductsStatisticsDTO result = new TopProductsStatisticsDTO();

        try {
            LambdaQueryWrapper<Product> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Product::getStatus, 1);

            if (merchantId != null) {
                queryWrapper.eq(Product::getMerchantId, merchantId);
            }

            queryWrapper.select(Product::getId, Product::getName, Product::getSalesCount, Product::getMerchantId)
                    .orderByDesc(Product::getSalesCount)
                    .last("LIMIT 5");

            List<Product> products = productMapper.selectList(queryWrapper);

            List<TopProductsStatisticsDTO.TopProductDTO> topProductStats = new ArrayList<>();
            double totalSalesAmount = 0;

            for (Product product : products) {
                TopProductsStatisticsDTO.TopProductDTO stat = new TopProductsStatisticsDTO.TopProductDTO();
                stat.setId(product.getId());
                stat.setName(product.getName());
                stat.setSalesCount(product.getSalesCount());

                double salesAmount = sumProductSalesAmount(product.getId());
                stat.setSalesAmount(salesAmount);
                totalSalesAmount += salesAmount;

                topProductStats.add(stat);
            }

            result.setTopProducts(topProductStats);
            result.setTotal(topProductStats.size());
            result.setTotalSalesAmount(totalSalesAmount);

            LOGGER.info("获取热销商品 Top5 成功，共{}条记录", topProductStats.size());

        } catch (Exception e) {
            LOGGER.error("获取热销商品 Top5 失败：{}", e.getMessage());
        }

        return result;
    }

    public CategorySalesStatisticsResponse getCategorySalesStatistics(Long merchantId) {
        CategorySalesStatisticsResponse result = new CategorySalesStatisticsResponse();

        try {
            List<Category> categories = categoryMapper.selectList(null);
            List<CategorySalesStatisticsResponse.CategoryStatsDTO> categoryStats = new ArrayList<>();
            int totalSales = 0;
            double totalSalesAmount = 0;

            for (Category category : categories) {
                int categorySalesCount = sumCategorySalesCount(category.getId(), merchantId);
                double categorySalesAmount = sumCategorySalesAmount(category.getId(), merchantId);

                if (categorySalesCount > 0 || categorySalesAmount > 0) {
                    CategorySalesStatisticsResponse.CategoryStatsDTO stat = new CategorySalesStatisticsResponse.CategoryStatsDTO();
                    stat.setCategoryId(category.getId());
                    stat.setCategoryName(category.getName());
                    stat.setSalesCount(categorySalesCount);
                    stat.setSalesAmount(categorySalesAmount);
                    categoryStats.add(stat);

                    totalSales += categorySalesCount;
                    totalSalesAmount += categorySalesAmount;
                }
            }

            // 添加销售占比
            for (CategorySalesStatisticsResponse.CategoryStatsDTO stat : categoryStats) {
                int salesCount = stat.getSalesCount();
                double salesAmount = stat.getSalesAmount();
                double countPercentage = totalSales > 0 ? (double) salesCount / totalSales * 100 : 0;
                double amountPercentage = totalSalesAmount > 0 ? salesAmount / totalSalesAmount * 100 : 0;
                stat.setCountPercentage(String.format("%.2f", countPercentage) + "%");
                stat.setAmountPercentage(String.format("%.2f", amountPercentage) + "%");
            }

            // 按销量降序排序
            categoryStats.sort((a, b) -> Integer.compare(b.getSalesCount(), a.getSalesCount()));

            result.setCategoryStats(categoryStats);
            result.setTotal(categoryStats.size());
            result.setTotalSales(totalSales);
            result.setTotalSalesAmount(totalSalesAmount);

            LOGGER.info("获取品类销售占比统计成功，共{}个品类", categoryStats.size());

        } catch (Exception e) {
            LOGGER.error("获取品类销售占比统计失败：{}", e.getMessage());
        }

        return result;
    }

    // ==================== 趋势和分布统计 ====================

    /**
     * 获取销售趋势
     */
    public SalesTrendResponse getSalesTrend(int days, Long merchantId) {
        SalesTrendResponse response = new SalesTrendResponse();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = now.minusDays(days).withHour(0).withMinute(0);

        List<SalesTrendDTO> trendList = statisticsMapper.selectSalesTrend(startTime, now, merchantId);

        // 填充缺失的日期
        Map<String, SalesTrendDTO> trendMap = trendList.stream()
                .collect(Collectors.toMap(SalesTrendDTO::getDate, d -> d, (a, b) -> a));

        List<SalesTrendDTO> filledTrend = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd");
        for (int i = 0; i < days; i++) {
            LocalDateTime day = now.minusDays(days - 1 - i);
            String dayKey = day.format(formatter);
            SalesTrendDTO dto = trendMap.getOrDefault(dayKey, createEmptyTrend(dayKey));
            filledTrend.add(dto);
        }

        double totalSales = filledTrend.stream()
                .mapToDouble(d -> d.getSalesAmount() != null ? d.getSalesAmount() : 0)
                .sum();
        int totalOrders = filledTrend.stream()
                .mapToInt(d -> d.getOrderCount() != null ? d.getOrderCount() : 0)
                .sum();

        response.setTrend(filledTrend);
        response.setDays(days);
        response.setTotalOrders(totalOrders);
        response.setTotalSales(totalSales);

        return response;
    }

    /**
     * 获取季节性销售统计
     */
    public SeasonalSalesResponse getSeasonalStatistics(Long merchantId) {
        SeasonalSalesResponse response = new SeasonalSalesResponse();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfYear = now.withDayOfYear(1).withHour(0);

        // 使用 Mapper 查询季节统计
        List<SeasonalSalesDTO> seasonList = statisticsMapper.selectSeasonalSales(startOfYear, now, merchantId);

        // 使用 Mapper 查询月度统计
        List<SeasonalSalesDTO> monthList = statisticsMapper.selectMonthlySales(startOfYear, now, merchantId);

        // 填充缺失的月份
        Map<Integer, SeasonalSalesDTO> monthMap = monthList.stream()
                .collect(Collectors.toMap(SeasonalSalesDTO::getMonth, d -> d, (a, b) -> a));

        List<SeasonalSalesDTO> filledMonths = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            SeasonalSalesDTO dto = monthMap.get(month);
            if (dto == null) {
                dto = new SeasonalSalesDTO();
                dto.setMonth(month);
                dto.setMonthName(month + "月");
                dto.setOrderCount(0);
                dto.setSalesAmount(0.0);
                dto.setProductCount(0);
            }
            filledMonths.add(dto);
        }

        double totalSales = seasonList.stream()
                .mapToDouble(d -> d.getSalesAmount() != null ? d.getSalesAmount() : 0)
                .sum();
        int totalOrders = seasonList.stream()
                .mapToInt(d -> d.getOrderCount() != null ? d.getOrderCount() : 0)
                .sum();

        response.setSeasonStats(seasonList);
        response.setMonthStats(filledMonths);
        response.setTotalOrders(totalOrders);
        response.setTotalSales(totalSales);

        return response;
    }

    /**
     * 获取地区销售统计
     */
    public RegionSalesResponse getRegionStatistics(Long merchantId) {
        RegionSalesResponse response = new RegionSalesResponse();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfYear = now.withDayOfYear(1).withHour(0);

        // 使用 Mapper 查询
        List<RegionSalesDTO> regionList = statisticsMapper.selectRegionSales(startOfYear, now, merchantId);

        // 计算总销售额
        double totalSales = regionList.stream()
                .mapToDouble(d -> d.getSalesAmount() != null ? d.getSalesAmount() : 0)
                .sum();

        // 计算占比
        for (RegionSalesDTO dto : regionList) {
            double salesAmount = dto.getSalesAmount() != null ? dto.getSalesAmount() : 0;
            double percentage = totalSales > 0 ? salesAmount / totalSales * 100 : 0;
            dto.setPercentage(String.format("%.2f", percentage) + "%");
        }

        // 按销售额降序排序
        regionList.sort((a, b) -> Double.compare(
                b.getSalesAmount() != null ? b.getSalesAmount() : 0,
                a.getSalesAmount() != null ? a.getSalesAmount() : 0
        ));

        response.setRegionStats(regionList);
        response.setTotalRegions(regionList.size());
        response.setTotalSales(totalSales);

        return response;
    }

    // ==================== 商户相关 ====================

    /**
     * 获取商户列表（管理员用）
     */
    public List<MerchantDTO> getMerchantList() {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getRole, "MERCHANT");

        List<User> merchants = userMapper.selectList(wrapper);
        return merchants.stream()
                .map(this::convertToMerchantDTO)
                .collect(Collectors.toList());
    }

    private MerchantDTO convertToMerchantDTO(User user) {
        MerchantDTO dto = new MerchantDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setName(user.getName());
        return dto;
    }

    // ==================== 辅助方法 ====================

    private long countOrders(YearMonth yearMonth, Long merchantId) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(Order::getCreatedAt, yearMonth.atDay(1).atStartOfDay())
               .lt(Order::getCreatedAt, yearMonth.plusMonths(1).atDay(1).atStartOfDay())
               .eq(Order::getStatus, 3); // 已完成的订单

        if (merchantId != null) {
            wrapper.inSql(Order::getProductId,
                    "SELECT id FROM product WHERE merchant_id = " + merchantId);
        }

        return orderMapper.selectCount(wrapper);
    }

    private double sumSalesAmount(YearMonth yearMonth, Long merchantId) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(Order::getCreatedAt, yearMonth.atDay(1).atStartOfDay())
               .lt(Order::getCreatedAt, yearMonth.plusMonths(1).atDay(1).atStartOfDay())
               .eq(Order::getStatus, 3); // 已完成的订单

        if (merchantId != null) {
            wrapper.inSql(Order::getProductId,
                "SELECT id FROM product WHERE merchant_id = " + merchantId);
        }

        List<Order> orders = orderMapper.selectList(wrapper);
        return orders.stream()
                    .mapToDouble(order -> order.getTotalPrice().doubleValue())
                    .sum();
    }

    private long countUserOrders(YearMonth yearMonth, Long userId) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(Order::getCreatedAt, yearMonth.atDay(1).atStartOfDay())
               .lt(Order::getCreatedAt, yearMonth.plusMonths(1).atDay(1).atStartOfDay())
               .eq(Order::getStatus, 3) // 已完成的订单
               .eq(Order::getUserId, userId);

        return orderMapper.selectCount(wrapper);
    }

    private long totalUserOrders(Long userId) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getStatus, 3) // 已完成的订单
               .eq(Order::getUserId, userId);

        return orderMapper.selectCount(wrapper);
    }

    private double sumUserSpending(YearMonth yearMonth, Long userId) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(Order::getCreatedAt, yearMonth.atDay(1).atStartOfDay())
               .lt(Order::getCreatedAt, yearMonth.plusMonths(1).atDay(1).atStartOfDay())
               .eq(Order::getStatus, 3) // 已完成的订单
               .eq(Order::getUserId, userId);

        List<Order> orders = orderMapper.selectList(wrapper);
        return orders.stream()
                    .mapToDouble(order -> order.getTotalPrice().doubleValue())
                    .sum();
    }

    private double totalUserSpending(Long userId) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getStatus, 3) // 已完成的订单
               .eq(Order::getUserId, userId);

        List<Order> orders = orderMapper.selectList(wrapper);
        return orders.stream()
                    .mapToDouble(order -> order.getTotalPrice().doubleValue())
                    .sum();
    }

    private long countUsers(int year) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(User::getCreatedAt, LocalDateTime.of(year, 1, 1, 0, 0))
               .lt(User::getCreatedAt, LocalDateTime.of(year + 1, 1, 1, 0, 0))
               .eq(User::getStatus, 1); // 只统计启用状态的用户

        return userMapper.selectCount(wrapper);
    }

    private int sumCategorySalesCount(Long categoryId, Long merchantId) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getCategoryId, categoryId)
               .eq(Product::getStatus, 1); // 只统计上架商品

        if (merchantId != null) {
            wrapper.eq(Product::getMerchantId, merchantId);
        }

        List<Product> products = productMapper.selectList(wrapper);
        return products.stream()
                    .mapToInt(Product::getSalesCount)
                    .sum();
    }

    private double sumCategorySalesAmount(Long categoryId, Long merchantId) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getStatus, 3); // 已完成的订单

        if (merchantId != null) {
            wrapper.inSql(Order::getProductId,
                "SELECT id FROM product WHERE merchant_id = " + merchantId + " AND category_id = " + categoryId);
        } else {
            wrapper.inSql(Order::getProductId,
                "SELECT id FROM product WHERE category_id = " + categoryId);
        }

        List<Order> orders = orderMapper.selectList(wrapper);
        return orders.stream()
                    .mapToDouble(order -> order.getTotalPrice().doubleValue())
                    .sum();
    }

    private double sumProductSalesAmount(Long productId) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getProductId, productId)
               .eq(Order::getStatus, 3); // 已完成的订单

        List<Order> orders = orderMapper.selectList(wrapper);
        return orders.stream()
                    .mapToDouble(order -> order.getTotalPrice().doubleValue())
                    .sum();
    }

    private double calculateGrowthRate(double current, double last) {
        if (last == 0) {
            return current > 0 ? 100.0 : 0.0;
        }
        return ((current - last) / last) * 100;
    }

    private double calculateGrowthRate(long current, long last) {
        if (last == 0) {
            return current > 0 ? 100.0 : 0.0;
        }
        return ((double)(current - last) / last) * 100;
    }

    private SalesTrendDTO createEmptyTrend(String date) {
        SalesTrendDTO dto = new SalesTrendDTO();
        dto.setDate(date);
        dto.setSalesAmount(0.0);
        dto.setOrderCount(0);
        return dto;
    }
}
