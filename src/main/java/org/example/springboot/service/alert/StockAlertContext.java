package org.example.springboot.service.alert;

import lombok.Data;

/**
 * 库存预警评估上下文
 * 包含评估预警所需的所有数据
 *
 * @author IhaveBB
 * @date 2026/03/22
 */
@Data
public class StockAlertContext {

    /**
     * 商品ID
     */
    private Long productId;

    /**
     * 商品名称
     */
    private String productName;

    /**
     * 当前库存
     */
    private Integer currentStock;

    /**
     * 昨日销量
     */
    private Integer yesterdaySales;

    /**
     * 近3日平均销量
     */
    private Double avgSales3Days;

    /**
     * 近7日平均销量
     */
    private Double avgSales7Days;

    /**
     * 商户ID
     */
    private Long merchantId;

    /**
     * 商户名称
     */
    private String merchantName;

    /**
     * 商户邮箱
     */
    private String merchantEmail;

    /**
     * 获取预计可售天数（基于近3日平均销量）
     *
     * @return 预计可售天数，如果销量为0返回null
     * @author IhaveBB
     * @date 2026/03/22
     */
    public Integer getDaysRemaining() {
        if (currentStock == null || currentStock <= 0) {
            return 0;
        }
        if (avgSales3Days == null || avgSales3Days <= 0) {
            return null; // 无销量数据，无法预测
        }
        return (int) Math.floor(currentStock / avgSales3Days);
    }
}
