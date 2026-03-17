package org.example.springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.example.springboot.entity.Product;
import org.example.springboot.entity.StockOut;
import org.example.springboot.mapper.ProductMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 库存预警服务
 * <p>
 * 提供库存监控和预警功能：
 * - 库存不足预警
 * - 库存积压预警
 * - 缺货预警
 * - 预警阈值管理
 * </p>
 *
 * @author agri-input-trade
 * @version 1.0
 */
@Slf4j
@Service
public class StockWarningService {

    @Resource
    private ProductMapper productMapper;

    /**
     * 默认库存预警阈值
     */
    private static final int DEFAULT_LOW_STOCK_THRESHOLD = 10;

    /**
     * 默认库存积压阈值
     */
    private static final int DEFAULT_HIGH_STOCK_THRESHOLD = 1000;

    /**
     * 库存预警信息DTO
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
        /** 阈值 */
        private Integer threshold;
    }

    /**
     * 预警类型枚举
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
     * 获取所有库存预警信息
     *
     * @return 预警信息列表
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
     * 获取指定商户的库存预警
     *
     * @param merchantId 商户ID
     * @return 预警信息列表
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
     * 检查指定商品库存状态
     *
     * @param productId 商品ID
     * @return 预警信息
     */
    public StockWarningDTO checkProductStock(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            return null;
        }
        return analyzeStockStatus(product);
    }

    /**
     * 分析商品库存状态
     *
     * @param product 商品
     * @return 预警信息
     */
    private StockWarningDTO analyzeStockStatus(Product product) {
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
        } else if (stock < DEFAULT_LOW_STOCK_THRESHOLD) {
            dto.setWarningType(WarningType.LOW_STOCK);
            dto.setThreshold(DEFAULT_LOW_STOCK_THRESHOLD);
            dto.setWarningMessage(String.format("库存不足，当前库存%d，低于阈值%d",
                    stock, DEFAULT_LOW_STOCK_THRESHOLD));
            dto.setSuggestion(WarningType.LOW_STOCK.getSuggestion());
        } else if (stock > DEFAULT_HIGH_STOCK_THRESHOLD) {
            dto.setWarningType(WarningType.HIGH_STOCK);
            dto.setThreshold(DEFAULT_HIGH_STOCK_THRESHOLD);
            dto.setWarningMessage(String.format("库存积压，当前库存%d，高于阈值%d",
                    stock, DEFAULT_HIGH_STOCK_THRESHOLD));
            dto.setSuggestion(WarningType.HIGH_STOCK.getSuggestion());
        } else {
            dto.setWarningType(WarningType.SAFE);
            dto.setThreshold(DEFAULT_LOW_STOCK_THRESHOLD);
            dto.setWarningMessage("库存正常");
            dto.setSuggestion(WarningType.SAFE.getSuggestion());
        }

        return dto;
    }

    /**
     * 获取库存统计概览
     *
     * @return 统计信息
     */
    public StockWarningOverview getStockOverview() {
        StockWarningOverview overview = new StockWarningOverview();

        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getStatus, 1);
        List<Product> products = productMapper.selectList(wrapper);

        int outOfStock = 0;
        int lowStock = 0;
        int highStock = 0;
        int safeStock = 0;

        for (Product product : products) {
            int stock = product.getStock() != null ? product.getStock() : 0;
            if (stock == 0) {
                outOfStock++;
            } else if (stock < DEFAULT_LOW_STOCK_THRESHOLD) {
                lowStock++;
            } else if (stock > DEFAULT_HIGH_STOCK_THRESHOLD) {
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
     * 获取库存预警数量（用于前端角标提示）
     *
     * @return 预警数量
     */
    public int getWarningCount() {
        return getAllStockWarnings().size();
    }

    /**
     * 库存预警概览DTO
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
