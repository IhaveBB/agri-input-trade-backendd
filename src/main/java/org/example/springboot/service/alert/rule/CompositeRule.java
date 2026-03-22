package org.example.springboot.service.alert.rule;

import org.example.springboot.enums.StockAlertRuleType;
import org.example.springboot.service.alert.StockAlertContext;
import org.example.springboot.service.alert.StockAlertRule;

import java.util.ArrayList;
import java.util.List;

/**
 * 组合规则 - 支持多个规则的 AND/OR 组合
 *
 * @author IhaveBB
 * @date 2026/03/22
 */
public class CompositeRule implements StockAlertRule {

    /**
     * 逻辑操作符：AND 或 OR
     */
    private String operator = "OR";

    /**
     * 子规则列表
     */
    private List<StockAlertRule> rules = new ArrayList<>();

    public CompositeRule() {
    }

    public CompositeRule(String operator) {
        this.operator = operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public void addRule(StockAlertRule rule) {
        this.rules.add(rule);
    }

    public void setRules(List<StockAlertRule> rules) {
        this.rules = rules;
    }

    @Override
    public boolean evaluate(StockAlertContext context) {
        if (rules == null || rules.isEmpty()) {
            return false;
        }

        if ("AND".equalsIgnoreCase(operator)) {
            // 所有规则都满足才触发
            for (StockAlertRule rule : rules) {
                if (!rule.evaluate(context)) {
                    return false;
                }
            }
            return true;
        } else {
            // 任一规则满足就触发（默认 OR）
            for (StockAlertRule rule : rules) {
                if (rule.evaluate(context)) {
                    return true;
                }
            }
            return false;
        }
    }

    @Override
    public String getRuleType() {
        return StockAlertRuleType.COMPOSITE.getCode();
    }

    @Override
    public String getRuleName() {
        return StockAlertRuleType.COMPOSITE.getName();
    }

    @Override
    public String getDescription() {
        StringBuilder sb = new StringBuilder("组合规则（");
        sb.append(operator);
        sb.append("）：");
        for (int i = 0; i < rules.size(); i++) {
            if (i > 0) {
                sb.append(" ").append(operator).append(" ");
            }
            sb.append(rules.get(i).getRuleName());
        }
        return sb.toString();
    }

    @Override
    public String getAlertMessage(StockAlertContext context) {
        // 找到触发的规则，返回其消息
        for (StockAlertRule rule : rules) {
            if (rule.evaluate(context)) {
                return rule.getAlertMessage(context);
            }
        }
        return "库存预警";
    }

    @Override
    public String getSuggestion(StockAlertContext context) {
        // 找到触发的规则，返回其建议
        for (StockAlertRule rule : rules) {
            if (rule.evaluate(context)) {
                return rule.getSuggestion(context);
            }
        }
        return "建议检查库存并及时补货";
    }
}
