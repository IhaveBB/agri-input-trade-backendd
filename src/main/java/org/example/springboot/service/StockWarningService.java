package org.example.springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.example.springboot.entity.Product;
import org.example.springboot.mapper.ProductMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 库存预警服务
 * <p>
 * 提供库存监控和预警功能，包括库存不足、库存积压、缺货三类预警。
 * 预警阈值通过 {@link StockThresholdStrategy} 策略接口统一管理，
 * 支持运行期动态切换阈值策略，避免硬编码。
 * </p>
 *
 * @author IhaveBB
 * @date 2026/03/22
 */
@Slf4j
@Service
public class StockWarningService {

    @Resource
    private ProductMapper productMapper;

    /**
     * 阈值策略，默认使用读取配置文件的 {@link DefaultStockThresholdStrategy}
     */
    private StockThresholdStrategy thresholdStrategy;

    /**
     * 通过构造注入默认策略（利用 Spring @Value 在构造后完成注入，此处手动初始化）
     *
     * @param lowThreshold  配置文件中的低库存阈值，默认 10
     * @param highThreshold 配置文件中的高库存阈值，默认 1000
     */
    public StockWarningService(
            @Value("${stock.warning.low-threshold:10}") int lowThreshold,
            @Value("${stock.warning.high-threshold:1000}") int highThreshold) {
        this.thresholdStrategy = new DefaultStockThresholdStrategy(lowThreshold, highThreshold);
    }

    /**
     * 动态替换阈值策略（支持测试场景和运营配置热更新）
     *
     * @param strategy 新的阈值策略实现
     * @author IhaveBB
     * @date 2026/03/22
     */
    public void setThresholdStrategy(StockThresholdStrategy strategy) {
        this.thresholdStrategy = strategy;
        log.info("[库存预警] 阈值策略已切换：低阈值={}，高阈值={}",
                strategy.getLowStockThreshold(), strategy.getHighStockThreshold());
    }

    // ==================== 策略接口与默认实现 ====================

    /**
     * 库存预警阈值策略接口
     * <p>
     * 实现该接口可自定义低库存与高库存判定边界，
     * 通过 {@link #setThresholdStrategy} 注入到服务中生效。
     * </p>
     *
     * @author IhaveBB
     * @date 2026/03/22
     */
    public interface StockThresholdStrategy {
        /**
         * 低库存警戒线：库存低于此值（不含0）触发 LOW_STOCK 预警
         *
         * @return 低库存阈值
         */
        int getLowStockThreshold();

        /**
         * 高库存警戒线：库存高于此值触发 HIGH_STOCK（积压）预警
         *
         * @return 高库存阈值
         */
        int getHighStockThreshold();
    }

    /**
     * 默认阈值策略：从 application.properties 读取配置
     * <p>
     * 对应配置项：
     * <pre>
     * stock.warning.low-threshold=10
     * stock.warning.high-threshold=1000
     * </pre>
     * </p>
     *
     * @author IhaveBB
     * @date 2026/03/22
     */
    public static class DefaultStockThresholdStrategy implements StockThresholdStrategy {

        private final int lowThreshold;
        private final int highThreshold;

        public DefaultStockThresholdStrategy(int lowThreshold, int highThreshold) {
            this.lowThreshold = lowThreshold;
            this.highThreshold = highThreshold;
        }

        @Override
        public int getLowStockThreshold() {
            return lowThreshold;
        }

        @Override
        public int getHighStockThreshold() {
            return highThreshold;
        }
    }

    // ==================== 核心预警逻辑 ====================

    /**
     * 获取所有库存预警信息（排除正常库存）
     *
     * @return 预警商品列表，按库存量升序排列
     * @author IhaveBB
     * @date 2026/03/22
     */
    public List<StockWarningDTO> getAllStockWarnings() {
        log.info("[库存预警] 查询所有库存预警信息");

        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getStatus, 1)
                .orderByAsc(Product::getStock);

        List<Product> products = productMapper.selectList(wrapper);

        return products.stream()
                .map(this::analyzeStockStatus)
                .filter(dto -> dto.getWarningType() != WarningType.SAFE)
                .collect(Collectors.toList());
    }

    /**
     * 获取指定商户的库存预警信息
     *
     * @param merchantId 商户ID
     * @return 该商户下的预警商品列表
     * @author IhaveBB
     * @date 2026/03/22
     */
    public List<StockWarningDTO> getMerchantStockWarnings(Long merchantId) {
        log.info("[库存预警] 查询商户{}的库存预警", merchantId);

        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getStatus, 1)
                .eq(Product::getMerchantId, merchantId)
                .orderByAsc(Product::getStock);

        List<Product> products = productMapper.selectList(wrapper);

        return products.stream()
                .map(this::analyzeStockStatus)
                .filter(dto -> dto.getWarningType() != WarningType.SAFE)
                .collect(Collectors.toList());
    }

    /**
     * 检查指定商品的库存状态
     *
     * @param productId 商品ID
     * @return 预警信息，商品不存在时返回 null
     * @author IhaveBB
     * @date 2026/03/22
     */
    public StockWarningDTO checkProductStock(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            return null;
        }
        return analyzeStockStatus(product);
    }

    /**
     * 获取库存预警数量（用于前端角标提示）
     * <p>
     * 直接通过数据库 COUNT 查询计算，避免加载全部商品数据。
     * </p>
     *
     * @return 当前处于预警状态（低库存 + 缺货 + 积压）的商品总数
     * @author IhaveBB
     * @date 2026/03/22
     */
    public int getWarningCount() {
        int lowThreshold = thresholdStrategy.getLowStockThreshold();
        int highThreshold = thresholdStrategy.getHighStockThreshold();

        Long count = productMapper.selectCount(
                new LambdaQueryWrapper<Product>()
                        .eq(Product::getStatus, 1)
                        .and(w -> w.lt(Product::getStock, lowThreshold)
                                   .or()
                                   .gt(Product::getStock, highThreshold))
        );
        return count == null ? 0 : count.intValue();
    }

    /**
     * 获取库存统计概览（各状态商品数量汇总）
     *
     * @return 概览DTO，包含总数、缺货数、低库存数、积压数、正常数
     * @author IhaveBB
     * @date 2026/03/22
     */
    public StockWarningOverview getStockOverview() {
        StockWarningOverview overview = new StockWarningOverview();

        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getStatus, 1);
        List<Product> products = productMapper.selectList(wrapper);

        int lowThreshold = thresholdStrategy.getLowStockThreshold();
        int highThreshold = thresholdStrategy.getHighStockThreshold();

        int outOfStock = 0;
        int lowStock = 0;
        int highStock = 0;
        int safeStock = 0;

        for (Product product : products) {
            int stock = product.getStock() != null ? product.getStock() : 0;
            if (stock == 0) {
                outOfStock++;
            } else if (stock < lowThreshold) {
                lowStock++;
            } else if (stock > highThreshold) {
                highStock++;
            } else {
                safeStock++;
            }
        }

        overview.setTotalProducts(products.size());
        overview.setOutOfStockCount(outOfStock);
        overview.setLowStockCount(lowStock);
        overview.setHighStockCount(highStock);
        overview.setSafeStockCount(safeStock);

        return overview;
    }

    /**
     * 分析单个商品的库存状态（委托给当前阈值策略）
     *
     * @param product 商品实体
     * @return 预警信息DTO
     */
    private StockWarningDTO analyzeStockStatus(Product product) {
        int lowThreshold = thresholdStrategy.getLowStockThreshold();
        int highThreshold = thresholdStrategy.getHighStockThreshold();

        StockWarningDTO dto = new StockWarningDTO();
        dto.setProductId(product.getId());
        dto.setProductName(product.getName());
        dto.setMerchantId(product.getMerchantId());
        dto.setCurrentStock(product.getStock());

        int stock = product.getStock() != null ? product.getStock() : 0;

        if (stock == 0) {
            dto.setWarningType(WarningType.OUT_OF_STOCK);
            dto.setThreshold(0);
            dto.setWarningMessage("商品已售罄，库存为0");
            dto.setSuggestion(WarningType.OUT_OF_STOCK.getSuggestion());
        } else if (stock < lowThreshold) {
            dto.setWarningType(WarningType.LOW_STOCK);
            dto.setThreshold(lowThreshold);
            dto.setWarningMessage(String.format("库存不足，当前库存%d，低于阈值%d", stock, lowThreshold));
            dto.setSuggestion(WarningType.LOW_STOCK.getSuggestion());
        } else if (stock > highThreshold) {
            dto.setWarningType(WarningType.HIGH_STOCK);
            dto.setThreshold(highThreshold);
            dto.setWarningMessage(String.format("库存积压，当前库存%d，高于阈值%d", stock, highThreshold));
            dto.setSuggestion(WarningType.HIGH_STOCK.getSuggestion());
        } else {
            dto.setWarningType(WarningType.SAFE);
            dto.setThreshold(lowThreshold);
            dto.setWarningMessage("库存正常");
            dto.setSuggestion(WarningType.SAFE.getSuggestion());
        }

        return dto;
    }

    // ==================== 内部 DTO ====================

    /**
     * 库存预警信息DTO
     *
     * @author IhaveBB
     * @date 2026/03/22
     */
    @Data
    public static class StockWarningDTO {
        /** 商品ID */
        private Long productId;
        /** 商品名称 */
        private String productName;
        /** 商户ID */
        private Long merchantId;
        /** 当前库存 */
        private Integer currentStock;
        /** 预警类型 */
        private WarningType warningType;
        /** 预警描述 */
        private String warningMessage;
        /** 建议操作 */
        private String suggestion;
        /** 判定阈值 */
        private Integer threshold;
    }

    /**
     * 预警类型枚举
     *
     * @author IhaveBB
     * @date 2026/03/22
     */
    public enum WarningType {
        /** 库存不足 */
        LOW_STOCK("库存不足", "建议及时补货"),
        /** 缺货 */
        OUT_OF_STOCK("已缺货", "请立即补货"),
        /** 库存积压 */
        HIGH_STOCK("库存积压", "建议促销清仓"),
        /** 安全库存 */
        SAFE("库存正常", "无需操作");

        private final String label;
        private final String suggestion;

        WarningType(String label, String suggestion) {
            this.label = label;
            this.suggestion = suggestion;
        }

        public String getLabel() {
            return label;
        }

        public String getSuggestion() {
            return suggestion;
        }
    }

    /**
     * 库存预警概览DTO
     *
     * @author IhaveBB
     * @date 2026/03/22
     */
    @Data
    public static class StockWarningOverview {
        /** 商品总数 */
        private int totalProducts;
        /** 缺货数量 */
        private int outOfStockCount;
        /** 库存不足数量 */
        private int lowStockCount;
        /** 库存积压数量 */
        private int highStockCount;
        /** 库存正常数量 */
        private int safeStockCount;
    }
}
