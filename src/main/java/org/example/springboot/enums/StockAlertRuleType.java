package org.example.springboot.enums;

/**
 * 库存预警规则类型枚举
 *
 * @author IhaveBB
 * @date 2026/03/22
 */
public enum StockAlertRuleType {

    /**
     * 库存低于阈值
     */
    THRESHOLD("THRESHOLD", "库存低于阈值", "当库存低于设定阈值时触发预警"),

    /**
     * 库存小于昨日销量
     */
    SALES_RATIO("SALES_RATIO", "库存小于昨日销量", "当当前库存小于昨日销量时触发预警"),

    /**
     * 预计可售天数不足
     */
    DAYS_REMAINING("DAYS_REMAINING", "预计可售天数不足", "根据近期销量计算，预计可售天数小于设定天数时触发"),

    /**
     * 库存为0（缺货）
     */
    OUT_OF_STOCK("OUT_OF_STOCK", "库存为0", "当库存为0时触发预警"),

    /**
     * 组合规则
     */
    COMPOSITE("COMPOSITE", "组合规则", "多个规则的组合（OR/AND）");

    private final String code;
    private final String name;
    private final String description;

    StockAlertRuleType(String code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }
}
