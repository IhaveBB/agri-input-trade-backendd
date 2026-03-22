package org.example.springboot.enums;

/**
 * 库存预警配置类型枚举
 *
 * @author IhaveBB
 * @date 2026/03/22
 */
public enum StockAlertConfigType {

    /**
     * 商户全局配置
     */
    MERCHANT("MERCHANT", "商户全局配置"),

    /**
     * 商品配置
     */
    PRODUCT("PRODUCT", "商品配置");

    private final String code;
    private final String description;

    StockAlertConfigType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
