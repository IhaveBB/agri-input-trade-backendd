package org.example.springboot.service.alert;

import org.example.springboot.entity.Product;
import org.example.springboot.entity.StockAlertConfig;
import org.example.springboot.entity.User;
import org.example.springboot.mapper.ProductMapper;
import org.example.springboot.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * 库存预警评估器
 * 用于评估商品是否触发预警
 *
 * @author IhaveBB
 * @date 2026/03/22
 */
@Component
public class StockAlertEvaluator {

    private static final Logger LOGGER = LoggerFactory.getLogger(StockAlertEvaluator.class);

    @Resource
    private ProductMapper productMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private StockAlertRuleFactory ruleFactory;

    /**
     * 评估商品是否触发预警
     *
     * @param productId 商品ID
     * @param config    预警配置
     * @return 预警评估结果，null 表示不触发预警
     * @author IhaveBB
     * @date 2026/03/22
     */
    public AlertResult evaluate(Long productId, StockAlertConfig config) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            LOGGER.warn("商品不存在：{}", productId);
            return null;
        }

        return evaluate(product, config);
    }

    /**
     * 评估商品是否触发预警
     *
     * @param product 商品实体
     * @param config  预警配置
     * @return 预警评估结果，null 表示不触发预警
     * @author IhaveBB
     * @date 2026/03/22
     */
    public AlertResult evaluate(Product product, StockAlertConfig config) {

        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            return null;
        }

        // 构建评估上下文
        StockAlertContext context = buildContext(product);

        // 创建规则并评估
        StockAlertRule rule = ruleFactory.createRule(config.getRuleConfig());
        boolean triggered = rule.evaluate(context);

        if (!triggered) {
            return null;
        }

        // 构建评估结果
        AlertResult result = new AlertResult();
        result.setProductId(product.getId());
        result.setProductName(product.getName());
        result.setMerchantId(product.getMerchantId());
        result.setMerchantName(context.getMerchantName());
        result.setMerchantEmail(context.getMerchantEmail());
        result.setCurrentStock(context.getCurrentStock());
        result.setAlertMessage(rule.getAlertMessage(context));
        result.setSuggestion(rule.getSuggestion(context));
        result.setRuleName(rule.getRuleName());
        result.setRuleDescription(rule.getDescription());

        return result;
    }

    /**
     * 构建评估上下文
     *
     * @param product 商品实体
     * @return 评估上下文
     * @author IhaveBB
     * @date 2026/03/22
     */
    private StockAlertContext buildContext(Product product) {
        StockAlertContext context = new StockAlertContext();
        context.setProductId(product.getId());
        context.setProductName(product.getName());
        context.setCurrentStock(product.getStock() != null ? product.getStock() : 0);
        context.setMerchantId(product.getMerchantId());

        // 获取商户信息
        if (product.getMerchantId() != null) {
            User merchant = userMapper.selectById(product.getMerchantId());
            if (merchant != null) {
                context.setMerchantName(merchant.getName());
                context.setMerchantEmail(merchant.getEmail());
            }
        }

        // TODO: 从订单统计中获取销量数据，暂时设为默认值
        context.setYesterdaySales(0);
        context.setAvgSales3Days(0.0);
        context.setAvgSales7Days(0.0);

        return context;
    }

    /**
     * 预警评估结果
     */
    public static class AlertResult {
        private Long productId;
        private String productName;
        private Long merchantId;
        private String merchantName;
        private String merchantEmail;
        private Integer currentStock;
        private String alertMessage;
        private String suggestion;
        private String ruleName;
        private String ruleDescription;

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

        public Long getMerchantId() {
            return merchantId;
        }

        public void setMerchantId(Long merchantId) {
            this.merchantId = merchantId;
        }

        public String getMerchantName() {
            return merchantName;
        }

        public void setMerchantName(String merchantName) {
            this.merchantName = merchantName;
        }

        public String getMerchantEmail() {
            return merchantEmail;
        }

        public void setMerchantEmail(String merchantEmail) {
            this.merchantEmail = merchantEmail;
        }

        public Integer getCurrentStock() {
            return currentStock;
        }

        public void setCurrentStock(Integer currentStock) {
            this.currentStock = currentStock;
        }

        public String getAlertMessage() {
            return alertMessage;
        }

        public void setAlertMessage(String alertMessage) {
            this.alertMessage = alertMessage;
        }

        public String getSuggestion() {
            return suggestion;
        }

        public void setSuggestion(String suggestion) {
            this.suggestion = suggestion;
        }

        public String getRuleName() {
            return ruleName;
        }

        public void setRuleName(String ruleName) {
            this.ruleName = ruleName;
        }

        public String getRuleDescription() {
            return ruleDescription;
        }

        public void setRuleDescription(String ruleDescription) {
            this.ruleDescription = ruleDescription;
        }
    }
}
