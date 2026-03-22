package org.example.springboot.service.alert;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.springboot.enums.StockAlertRuleType;
import org.example.springboot.service.alert.rule.CompositeRule;
import org.example.springboot.service.alert.rule.DaysRemainingRule;
import org.example.springboot.service.alert.rule.OutOfStockRule;
import org.example.springboot.service.alert.rule.SalesRatioRule;
import org.example.springboot.service.alert.rule.ThresholdRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 库存预警规则工厂
 * 根据规则配置 JSON 创建规则实例
 *
 * @author IhaveBB
 * @date 2026/03/22
 */
@Component
public class StockAlertRuleFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(StockAlertRuleFactory.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 根据规则配置 JSON 创建规则实例
     *
     * @param ruleConfigJson 规则配置 JSON 字符串
     * @return 规则实例，解析失败返回 null
     * @author IhaveBB
     * @date 2026/03/22
     */
    public StockAlertRule createRule(String ruleConfigJson) {
        if (ruleConfigJson == null || ruleConfigJson.isBlank()) {
            LOGGER.warn("规则配置为空，使用默认阈值规则");
            return new ThresholdRule(10);
        }

        try {
            JsonNode root = objectMapper.readTree(ruleConfigJson);
            return createRuleFromJson(root);
        } catch (Exception e) {
            LOGGER.error("解析规则配置失败：{}", e.getMessage());
            return new ThresholdRule(10);
        }
    }

    /**
     * 从 JSON 节点创建规则
     *
     * @param node JSON 节点
     * @return 规则实例
     * @author IhaveBB
     * @date 2026/03/22
     */
    private StockAlertRule createRuleFromJson(JsonNode node) {
        String ruleType = node.has("ruleType") ? node.get("ruleType").asText() : "THRESHOLD";

        return switch (ruleType) {
            case "THRESHOLD" -> createThresholdRule(node);
            case "SALES_RATIO" -> new SalesRatioRule();
            case "DAYS_REMAINING" -> createDaysRemainingRule(node);
            case "OUT_OF_STOCK" -> new OutOfStockRule();
            case "COMPOSITE" -> createCompositeRule(node);
            default -> {
                LOGGER.warn("未知的规则类型：{}，使用默认阈值规则", ruleType);
                yield new ThresholdRule(10);
            }
        };
    }

    /**
     * 创建阈值规则
     *
     * @param node JSON 节点
     * @return 阈值规则实例
     * @author IhaveBB
     * @date 2026/03/22
     */
    private ThresholdRule createThresholdRule(JsonNode node) {
        int thresholdValue = node.has("thresholdValue") ? node.get("thresholdValue").asInt() : 10;
        return new ThresholdRule(thresholdValue);
    }

    /**
     * 创建可售天数规则
     *
     * @param node JSON 节点
     * @return 可售天数规则实例
     * @author IhaveBB
     * @date 2026/03/22
     */
    private DaysRemainingRule createDaysRemainingRule(JsonNode node) {
        int days = node.has("days") ? node.get("days").asInt() : 7;
        return new DaysRemainingRule(days);
    }

    /**
     * 创建组合规则
     *
     * @param node JSON 节点
     * @return 组合规则实例
     * @author IhaveBB
     * @date 2026/03/22
     */
    private CompositeRule createCompositeRule(JsonNode node) {
        String operator = node.has("operator") ? node.get("operator").asText() : "OR";
        CompositeRule compositeRule = new CompositeRule(operator);

        if (node.has("rules") && node.get("rules").isArray()) {
            for (JsonNode ruleNode : node.get("rules")) {
                StockAlertRule rule = createRuleFromJson(ruleNode);
                compositeRule.addRule(rule);
            }
        }

        return compositeRule;
    }

    /**
     * 获取规则类型的默认配置 JSON
     *
     * @param ruleType 规则类型
     * @return 默认配置 JSON
     * @author IhaveBB
     * @date 2026/03/22
     */
    public String getDefaultConfig(String ruleType) {
        return switch (ruleType) {
            case "THRESHOLD" -> "{\"ruleType\":\"THRESHOLD\",\"thresholdValue\":10}";
            case "SALES_RATIO" -> "{\"ruleType\":\"SALES_RATIO\"}";
            case "DAYS_REMAINING" -> "{\"ruleType\":\"DAYS_REMAINING\",\"days\":7}";
            case "OUT_OF_STOCK" -> "{\"ruleType\":\"OUT_OF_STOCK\"}";
            case "COMPOSITE" ->
                    "{\"ruleType\":\"COMPOSITE\",\"operator\":\"OR\",\"rules\":[{\"ruleType\":\"THRESHOLD\",\"thresholdValue\":10}]}";
            default -> "{\"ruleType\":\"THRESHOLD\",\"thresholdValue\":10}";
        };
    }
}
