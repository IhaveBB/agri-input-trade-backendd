package org.example.springboot.service.alert.rule;

import org.example.springboot.enums.StockAlertRuleType;
import org.example.springboot.service.alert.StockAlertContext;
import org.example.springboot.service.alert.StockAlertRule;

/**
 * 销量比率规则 - 库存小于昨日销量时触发
 *
 * @author IhaveBB
 * @date 2026/03/22
 */
public class SalesRatioRule implements StockAlertRule {

    @Override
    public boolean evaluate(StockAlertContext context) {
        if (context.getCurrentStock() == null || context.getYesterdaySales() == null) {
            return false;
        }
        // 库存大于0且小于昨日销量时触发
        return context.getCurrentStock() > 0 &&
               context.getCurrentStock() < context.getYesterdaySales();
    }

    @Override
    public String getRuleType() {
        return StockAlertRuleType.SALES_RATIO.getCode();
    }

    @Override
    public String getRuleName() {
        return StockAlertRuleType.SALES_RATIO.getName();
    }

    @Override
    public String getDescription() {
        return StockAlertRuleType.SALES_RATIO.getDescription();
    }

    @Override
    public String getAlertMessage(StockAlertContext context) {
        return String.format("商品「%s」库存不足，当前库存 %d 件，小于昨日销量 %d 件",
                context.getProductName(),
                context.getCurrentStock(),
                context.getYesterdaySales());
    }

    @Override
    public String getSuggestion(StockAlertContext context) {
        return "建议及时补货，确保库存能够满足日常销售需求";
    }
}
