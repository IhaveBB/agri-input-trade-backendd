package org.example.springboot.service.alert.rule;

import org.example.springboot.enums.StockAlertRuleType;
import org.example.springboot.service.alert.StockAlertContext;
import org.example.springboot.service.alert.StockAlertRule;

/**
 * 预计可售天数规则 - 预计可售天数小于设定值时触发
 *
 * @author IhaveBB
 * @date 2026/03/22
 */
public class DaysRemainingRule implements StockAlertRule {

    /**
     * 最小可售天数阈值
     */
    private int days = 7;

    public DaysRemainingRule() {
    }

    public DaysRemainingRule(int days) {
        this.days = days;
    }

    @Override
    public boolean evaluate(StockAlertContext context) {
        if (context.getCurrentStock() == null || context.getCurrentStock() <= 0) {
            return false;
        }

        Integer daysRemaining = context.getDaysRemaining();
        // 无法计算（无销量数据）时不触发
        if (daysRemaining == null) {
            return false;
        }

        return daysRemaining < days;
    }

    @Override
    public String getRuleType() {
        return StockAlertRuleType.DAYS_REMAINING.getCode();
    }

    @Override
    public String getRuleName() {
        return StockAlertRuleType.DAYS_REMAINING.getName();
    }

    @Override
    public String getDescription() {
        return StockAlertRuleType.DAYS_REMAINING.getDescription() + "（" + days + "天）";
    }

    @Override
    public String getAlertMessage(StockAlertContext context) {
        Integer daysRemaining = context.getDaysRemaining();
        return String.format("商品「%s」库存预警，当前库存 %d 件，预计可售 %d 天，低于设定阈值 %d 天",
                context.getProductName(),
                context.getCurrentStock(),
                daysRemaining != null ? daysRemaining : 0,
                days);
    }

    @Override
    public String getSuggestion(StockAlertContext context) {
        return "建议根据销售趋势及时补货，避免缺货";
    }
}
