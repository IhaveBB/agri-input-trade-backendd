package org.example.springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.springboot.entity.Product;
import org.example.springboot.entity.StockAlertConfig;
import org.example.springboot.enums.StockAlertConfigType;
import org.example.springboot.mapper.ProductMapper;
import org.example.springboot.mapper.StockAlertConfigMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 库存预警配置服务
 *
 * @author IhaveBB
 * @date 2026/03/22
 */
@Slf4j
@Service
public class StockAlertConfigService {

    @Resource
    private StockAlertConfigMapper configMapper;

    @Resource
    private ProductMapper productMapper;

    /**
     * 获取商户的全局配置
     *
     * @param merchantId 商户ID
     * @return 全局配置，不存在则返回null
     * @author IhaveBB
     * @date 2026/03/22
     */
    public StockAlertConfig getMerchantConfig(Long merchantId) {
        return configMapper.selectOne(new LambdaQueryWrapper<StockAlertConfig>()
                .eq(StockAlertConfig::getConfigType, StockAlertConfigType.MERCHANT.getCode())
                .eq(StockAlertConfig::getMerchantId, merchantId));
    }

    /**
     * 获取或创建商户的全局配置（不存在则创建默认配置）
     *
     * @param merchantId 商户ID
     * @return 全局配置
     * @author IhaveBB
     * @date 2026/03/22
     */
    public StockAlertConfig getOrCreateMerchantConfig(Long merchantId) {
        StockAlertConfig config = getMerchantConfig(merchantId);
        if (config == null) {
            config = createDefaultMerchantConfig(merchantId);
        }
        return config;
    }

    /**
     * 创建商户默认全局配置
     *
     * @param merchantId 商户ID
     * @return 创建的配置
     * @author IhaveBB
     * @date 2026/03/22
     */
    private StockAlertConfig createDefaultMerchantConfig(Long merchantId) {
        StockAlertConfig config = new StockAlertConfig();
        config.setConfigType(StockAlertConfigType.MERCHANT.getCode());
        config.setMerchantId(merchantId);
        config.setEnabled(true);
        config.setRuleConfig("{\"ruleType\":\"THRESHOLD\",\"thresholdValue\":10}");
        config.setAlertIntervalHours(24);
        config.setRepeatAlertEnabled(true);
        configMapper.insert(config);
        return config;
    }

    /**
     * 更新商户全局配置
     *
     * @param merchantId 商户ID
     * @param config     配置信息
     * @return 更新后的配置
     * @author IhaveBB
     * @date 2026/03/22
     */
    @Transactional
    public StockAlertConfig updateMerchantConfig(Long merchantId, StockAlertConfig config) {
        StockAlertConfig existing = getOrCreateMerchantConfig(merchantId);

        if (config.getEnabled() != null) {
            existing.setEnabled(config.getEnabled());
        }
        if (config.getRuleConfig() != null) {
            existing.setRuleConfig(config.getRuleConfig());
        }
        if (config.getAlertIntervalHours() != null) {
            existing.setAlertIntervalHours(config.getAlertIntervalHours());
        }
        if (config.getRepeatAlertEnabled() != null) {
            existing.setRepeatAlertEnabled(config.getRepeatAlertEnabled());
        }

        configMapper.updateById(existing);
        return existing;
    }

    /**
     * 获取商品的预警配置
     *
     * @param productId 商品ID
     * @return 商品配置，不存在则返回null
     * @author IhaveBB
     * @date 2026/03/22
     */
    public StockAlertConfig getProductConfig(Long productId) {
        return configMapper.selectOne(new LambdaQueryWrapper<StockAlertConfig>()
                .eq(StockAlertConfig::getConfigType, StockAlertConfigType.PRODUCT.getCode())
                .eq(StockAlertConfig::getProductId, productId));
    }

    /**
     * 获取商品的有效配置（优先商品配置，其次全局配置）
     *
     * @param productId  商品ID
     * @param merchantId 商户ID
     * @return 有效配置，可能为null
     * @author IhaveBB
     * @date 2026/03/22
     */
    public StockAlertConfig getEffectiveConfig(Long productId, Long merchantId) {
        // 1. 查找商品配置
        StockAlertConfig productConfig = getProductConfig(productId);

        // 商品配置存在且启用，使用商品配置
        if (productConfig != null && Boolean.TRUE.equals(productConfig.getEnabled())) {
            return productConfig;
        }

        // 2. 使用商户全局配置
        StockAlertConfig merchantConfig = getMerchantConfig(merchantId);

        // 全局配置启用才返回
        if (merchantConfig != null && Boolean.TRUE.equals(merchantConfig.getEnabled())) {
            return merchantConfig;
        }

        return null;
    }

    /**
     * 检查商品是否需要发送预警（基于配置和时间间隔）
     *
     * @param productId  商品ID
     * @param merchantId 商户ID
     * @return true-需要发送，false-不需要
     * @author IhaveBB
     * @date 2026/03/22
     */
    public boolean shouldSendAlert(Long productId, Long merchantId) {
        StockAlertConfig config = getEffectiveConfig(productId, merchantId);
        if (config == null) {
            return false;
        }

        // 检查预警间隔
        if (config.getLastAlertTime() != null) {
            int intervalHours = config.getAlertIntervalHours() != null ? config.getAlertIntervalHours() : 24;
            LocalDateTime nextAlertTime = config.getLastAlertTime().plusHours(intervalHours);

            if (LocalDateTime.now().isBefore(nextAlertTime)) {
                return false;
            }
        }

        return true;
    }

    /**
     * 更新商品配置
     *
     * @param productId  商品ID
     * @param merchantId 商户ID
     * @param config     配置信息
     * @return 更新后的配置
     * @author IhaveBB
     * @date 2026/03/22
     */
    @Transactional
    public StockAlertConfig updateProductConfig(Long productId, Long merchantId, StockAlertConfig config) {
        StockAlertConfig existing = getProductConfig(productId);

        if (existing == null) {
            existing = new StockAlertConfig();
            existing.setConfigType(StockAlertConfigType.PRODUCT.getCode());
            existing.setMerchantId(merchantId);
            existing.setProductId(productId);
            existing.setEnabled(config.getEnabled() != null ? config.getEnabled() : true);
            existing.setRuleConfig(config.getRuleConfig());
            existing.setAlertIntervalHours(config.getAlertIntervalHours());
            existing.setRepeatAlertEnabled(config.getRepeatAlertEnabled());
            configMapper.insert(existing);
        } else {
            if (config.getEnabled() != null) {
                existing.setEnabled(config.getEnabled());
            }
            if (config.getRuleConfig() != null) {
                existing.setRuleConfig(config.getRuleConfig());
            }
            if (config.getAlertIntervalHours() != null) {
                existing.setAlertIntervalHours(config.getAlertIntervalHours());
            }
            if (config.getRepeatAlertEnabled() != null) {
                existing.setRepeatAlertEnabled(config.getRepeatAlertEnabled());
            }
            configMapper.updateById(existing);
        }

        return existing;
    }

    /**
     * 批量更新商品配置
     *
     * @param merchantId 商户ID
     * @param productIds 商品ID列表
     * @param enabled    是否启用
     * @param ruleConfig 规则配置（可选）
     * @author IhaveBB
     * @date 2026/03/22
     */
    @Transactional
    public void batchUpdateProductConfig(Long merchantId, List<Long> productIds, Boolean enabled, String ruleConfig) {
        for (Long productId : productIds) {
            StockAlertConfig config = new StockAlertConfig();
            config.setEnabled(enabled);
            if (ruleConfig != null) {
                config.setRuleConfig(ruleConfig);
            }
            updateProductConfig(productId, merchantId, config);
        }
    }

    /**
     * 分页获取商户的商品配置列表
     *
     * @param merchantId 商户ID
     * @param productName 商品名称（模糊搜索，可选）
     * @param pageNum    页码
     * @param pageSize   每页大小
     * @return 商品配置分页列表
     * @author IhaveBB
     * @date 2026/03/22
     */
    public Page<ProductConfigVO> getProductConfigPage(Long merchantId, String productName, int pageNum, int pageSize) {
        // 查询商户的商品
        LambdaQueryWrapper<Product> productWrapper = new LambdaQueryWrapper<>();
        productWrapper.eq(Product::getMerchantId, merchantId)
                .eq(Product::getStatus, 1);

        if (productName != null && !productName.isBlank()) {
            productWrapper.like(Product::getName, productName);
        }

        Page<Product> productPage = productMapper.selectPage(new Page<>(pageNum, pageSize), productWrapper);

        // 转换为配置VO
        Page<ProductConfigVO> resultPage = new Page<>(pageNum, pageSize, productPage.getTotal());
        List<ProductConfigVO> voList = new ArrayList<>();

        for (Product product : productPage.getRecords()) {
            ProductConfigVO vo = new ProductConfigVO();
            vo.setProductId(product.getId());
            vo.setProductName(product.getName());
            vo.setCurrentStock(product.getStock());
            vo.setMerchantId(merchantId);

            // 查找商品配置
            StockAlertConfig config = getProductConfig(product.getId());
            if (config != null) {
                vo.setConfigId(config.getId());
                vo.setEnabled(config.getEnabled());
                vo.setRuleConfig(config.getRuleConfig());
                vo.setAlertIntervalHours(config.getAlertIntervalHours());
                vo.setRepeatAlertEnabled(config.getRepeatAlertEnabled());
            } else {
                // 使用全局配置
                vo.setEnabled(false);
            }

            voList.add(vo);
        }

        resultPage.setRecords(voList);
        return resultPage;
    }

    /**
     * 更新商品的上次预警时间
     * 优先更新商品配置，如果没有则更新商户全局配置
     *
     * @param productId 商品ID
     * @param stockValue 当前库存值
     * @author IhaveBB
     * @date 2026/03/22
     */
    public void updateLastAlertTime(Long productId, Integer stockValue) {
        // 先查找商品配置
        StockAlertConfig config = getProductConfig(productId);

        // 如果没有商品配置，查找商品所属商户的全局配置
        if (config == null) {
            Product product = productMapper.selectById(productId);
            if (product != null && product.getMerchantId() != null) {
                config = getMerchantConfig(product.getMerchantId());
            }
        }

        if (config != null) {
            config.setLastAlertTime(LocalDateTime.now());
            config.setLastStockValue(stockValue);
            configMapper.updateById(config);
        }
    }

    /**
     * 商品配置VO
     */
    public static class ProductConfigVO {
        private Long configId;
        private Long productId;
        private String productName;
        private Integer currentStock;
        private Long merchantId;
        private Boolean enabled;
        private String ruleConfig;
        private Integer alertIntervalHours;
        private Boolean repeatAlertEnabled;

        public Long getConfigId() {
            return configId;
        }

        public void setConfigId(Long configId) {
            this.configId = configId;
        }

        public Long getProductId() {
            return productId;
        }

        public void setProductId(Long productId) {
            this.productId = productId;
        }

        public String getProductName() {
            return productName;
        }

        public void setProductName(String productName) {
            this.productName = productName;
        }

        public Integer getCurrentStock() {
            return currentStock;
        }

        public void setCurrentStock(Integer currentStock) {
            this.currentStock = currentStock;
        }

        public Long getMerchantId() {
            return merchantId;
        }

        public void setMerchantId(Long merchantId) {
            this.merchantId = merchantId;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public String getRuleConfig() {
            return ruleConfig;
        }

        public void setRuleConfig(String ruleConfig) {
            this.ruleConfig = ruleConfig;
        }

        public Integer getAlertIntervalHours() {
            return alertIntervalHours;
        }

        public void setAlertIntervalHours(Integer alertIntervalHours) {
            this.alertIntervalHours = alertIntervalHours;
        }

        public Boolean getRepeatAlertEnabled() {
            return repeatAlertEnabled;
        }

        public void setRepeatAlertEnabled(Boolean repeatAlertEnabled) {
            this.repeatAlertEnabled = repeatAlertEnabled;
        }
    }
}
