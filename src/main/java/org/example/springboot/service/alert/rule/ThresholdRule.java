package org.example.springboot.service.alert.rule;

import lombok.Data;
import org.example.springboot.enums.StockAlertRuleType;
import org.example.springboot.service.alert.StockAlertContext;
import org.example.springboot.service.alert.StockAlertRule;

/**
 * 阈值规则 - 库存低于阈值时触发
 *
 * @author IhaveBB
 * @date 2026/03/22
 */
@Data
public class ThresholdRule implements StockAlertRule {

    /**
     * 库存阈值
     */
    private int thresholdValue = 10;

    public ThresholdRule() {
    }

    public ThresholdRule(int thresholdValue) {
        this.thresholdValue = thresholdValue;
    }

    @Override
    public boolean evaluate(StockAlertContext context) {
        if (context.getCurrentStock() == null) {
            return false;
        }
        // 库存低于阈值时触发（包括库存为0的情况）
        return context.getCurrentStock() < thresholdValue;
    }

    @Override
    public String getRuleType() {
        return StockAlertRuleType.THRESHOLD.getCode();
    }

    @Override
    public String getRuleName() {
        return StockAlertRuleType.THRESHOLD.getName();
    }

    @Override
    public String getDescription() {
        return StockAlertRuleType.THRESHOLD.getDescription() + "（阈值：" + thresholdValue + "）";
    }

    @Override
    public String getAlertMessage(StockAlertContext context) {
        return String.format("商品「%s」库存不足，当前库存 %d 件，低于预设阈值 %d 件",
                context.getProductName(),
                context.getCurrentStock(),
                thresholdValue);
    }

    @Override
    public String getSuggestion(StockAlertContext context) {
        return "建议及时补货，确保库存充足";
    }
}
