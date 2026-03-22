package org.example.springboot.service.alert.rule;

import org.example.springboot.enums.StockAlertRuleType;
import org.example.springboot.service.alert.StockAlertContext;
import org.example.springboot.service.alert.StockAlertRule;

/**
 * 缺货规则 - 库存为0时触发
 *
 * @author IhaveBB
 * @date 2026/03/22
 */
public class OutOfStockRule implements StockAlertRule {

    @Override
    public boolean evaluate(StockAlertContext context) {
        if (context.getCurrentStock() == null) {
            return false;
        }
        return context.getCurrentStock() == 0;
    }

    @Override
    public String getRuleType() {
        return StockAlertRuleType.OUT_OF_STOCK.getCode();
    }

    @Override
    public String getRuleName() {
        return StockAlertRuleType.OUT_OF_STOCK.getName();
    }

    @Override
    public String getDescription() {
        return StockAlertRuleType.OUT_OF_STOCK.getDescription();
    }

    @Override
    public String getAlertMessage(StockAlertContext context) {
        return String.format("商品「%s」已售罄，当前库存为 0，请立即补货",
                context.getProductName());
    }

    @Override
    public String getSuggestion(StockAlertContext context) {
        return "请立即补货，避免影响销售";
    }
}
