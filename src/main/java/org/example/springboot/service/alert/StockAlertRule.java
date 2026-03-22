package org.example.springboot.service.alert;

/**
 * 库存预警规则接口
 * 使用策略模式，支持多种预警规则
 *
 * @author IhaveBB
 * @date 2026/03/22
 */
public interface StockAlertRule {

    /**
     * 评估是否触发预警
     *
     * @param context 评估上下文，包含库存、销量等信息
     * @return true-触发预警，false-不触发
     * @author IhaveBB
     * @date 2026/03/22
     */
    boolean evaluate(StockAlertContext context);

    /**
     * 获取规则类型代码
     *
     * @return 规则类型
     * @author IhaveBB
     * @date 2026/03/22
     */
    String getRuleType();

    /**
     * 获取规则名称（用于前端展示）
     *
     * @return 规则名称
     * @author IhaveBB
     * @date 2026/03/22
     */
    String getRuleName();

    /**
     * 获取规则描述
     *
     * @return 规则描述
     * @author IhaveBB
     * @date 2026/03/22
     */
    String getDescription();

    /**
     * 获取预警消息
     *
     * @param context 评估上下文
     * @return 预警消息
     * @author IhaveBB
     * @date 2026/03/22
     */
    String getAlertMessage(StockAlertContext context);

    /**
     * 获取建议操作
     *
     * @param context 评估上下文
     * @return 建议操作
     * @author IhaveBB
     * @date 2026/03/22
     */
    String getSuggestion(StockAlertContext context);
}
