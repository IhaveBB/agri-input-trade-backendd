package org.example.springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.springboot.entity.Product;
import org.example.springboot.entity.StockAlertConfig;
import org.example.springboot.enums.StockAlertConfigType;
import org.example.springboot.mapper.OrderMapper;
import org.example.springboot.mapper.ProductMapper;
import org.example.springboot.mapper.StockAlertConfigMapper;
import org.example.springboot.service.alert.StockAlertContext;
import org.example.springboot.service.alert.StockAlertEvaluator;
import org.example.springboot.service.alert.StockAlertRule;
import org.example.springboot.service.alert.StockAlertRuleFactory;
import org.example.springboot.util.RedisUtil;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 库存预警定时任务调度器
 *
 * @author IhaveBB
 * @date 2026/03/22
 */
@Slf4j
@Component
public class StockAlertScheduler {

    @Resource
    private ProductMapper productMapper;

    @Resource
    private StockAlertConfigMapper configMapper;

    @Resource
    private StockAlertConfigService configService;

    @Resource
    private EmailRecordService emailRecordService;

    @Resource
    private StockAlertEvaluator evaluator;

    @Resource
    private StockAlertRuleFactory ruleFactory;

    @Resource
    private RedisUtil redisUtil;

    @Resource
    private OrderMapper orderMapper;

    /**
     * 分布式锁 Key 前缀
     */
    private static final String LOCK_PREFIX = "stock:alert:lock:";

    /**
     * 扫描库存预警（每5分钟）
     * 检查所有启用预警的商品，发送预警邮件
     *
     * @author IhaveBB
     * @date 2026/03/22
     */
    @Scheduled(cron = "0 */5 * * * ?")
    public void scanStockAlert() {
        String lockKey = LOCK_PREFIX + "scan";
        // 尝试获取分布式锁，防止多实例重复执行
        if (!tryLock(lockKey, 10)) {
            log.debug("未获取到扫描锁，跳过本次执行");
            return;
        }

        try {
            log.info("[库存预警] 开始扫描库存预警...");

            int alertCount = 0;

            // 1. 获取所有启用了全局配置的商户
            List<StockAlertConfig> merchantConfigs = configMapper.selectList(
                    new LambdaQueryWrapper<StockAlertConfig>()
                            .eq(StockAlertConfig::getConfigType, StockAlertConfigType.MERCHANT.getCode())
                            .eq(StockAlertConfig::getEnabled, true)
            );

            // 2. 遍历每个商户的所有商品
            for (StockAlertConfig merchantConfig : merchantConfigs) {
                Long merchantId = merchantConfig.getMerchantId();

                // 获取该商户的所有商品
                List<Product> products = productMapper.selectList(
                        new LambdaQueryWrapper<Product>()
                                .eq(Product::getMerchantId, merchantId)
                                .eq(Product::getStatus, 1) // 只检查上架商品
                );

                for (Product product : products) {
                    try {
                        // 获取有效配置（优先商品配置，其次全局配置）
                        StockAlertConfig effectiveConfig = configService.getEffectiveConfig(product.getId(), merchantId);
                        if (effectiveConfig == null || !Boolean.TRUE.equals(effectiveConfig.getEnabled())) {
                            continue;
                        }

                        if (shouldSendAlert(product, effectiveConfig)) {
                            StockAlertEvaluator.AlertResult result = evaluator.evaluate(product.getId(), effectiveConfig);
                            if (result != null) {
                                emailRecordService.sendStockAlertEmailAsync(result);
                                alertCount++;
                            }
                        }
                    } catch (Exception e) {
                        log.error("处理商品预警失败：productId={}", product.getId(), e);
                    }
                }
            }

            log.info("[库存预警] 扫描完成，共发送 {} 条预警邮件", alertCount);

        } finally {
            releaseLock(lockKey);
        }
    }

    /**
     * 判断是否应该发送预警
     *
     * @param product         商品
     * @param effectiveConfig 有效配置
     * @return true-应该发送，false-不应该发送
     * @author IhaveBB
     * @date 2026/03/22
     */
    private boolean shouldSendAlert(Product product, StockAlertConfig effectiveConfig) {
        if (product == null || effectiveConfig == null) {
            return false;
        }

        // 1. 检查预警间隔
        if (effectiveConfig.getLastAlertTime() != null) {
            int intervalHours = effectiveConfig.getAlertIntervalHours() != null ? effectiveConfig.getAlertIntervalHours() : 24;
            LocalDateTime nextAlertTime = effectiveConfig.getLastAlertTime().plusHours(intervalHours);

            if (LocalDateTime.now().isBefore(nextAlertTime)) {
                // 未到预警时间
                return false;
            }
        }

        // 2. 检查重复提醒
        // 如果重复提醒关闭，且库存未恢复（库存 <= 上次预警时的库存），则不再发送
        if (!Boolean.TRUE.equals(effectiveConfig.getRepeatAlertEnabled()) && effectiveConfig.getLastStockValue() != null) {
            if (product.getStock() != null && product.getStock() <= effectiveConfig.getLastStockValue()) {
                // 库存未恢复，且不开启重复提醒，跳过
                return false;
            }
        }

        // 3. 评估规则
        StockAlertRule rule = ruleFactory.createRule(effectiveConfig.getRuleConfig());
        StockAlertContext context = buildContext(product);

        return rule.evaluate(context);
    }

    /**
     * 构建评估上下文
     *
     * @param product 商品
     * @return 评估上下文
     * @author IhaveBB
     * @date 2026/03/22
     */
    private StockAlertContext buildContext(Product product) {
        StockAlertContext context = new StockAlertContext();
        context.setProductId(product.getId());
        context.setProductName(product.getName());
        context.setCurrentStock(product.getStock() != null ? product.getStock() : 0);
        context.setMerchantId(product.getMerchantId());

        // 从订单统计中获取销量数据
        try {
            // 昨日销量
            Integer yesterdaySales = orderMapper.getYesterdaySales(product.getId());
            context.setYesterdaySales(yesterdaySales != null ? yesterdaySales : 0);

            // 3天平均销量
            Integer sales3Days = orderMapper.getProductSalesInDays(product.getId(), 3);
            context.setAvgSales3Days(sales3Days != null ? sales3Days / 3.0 : 0.0);

            // 7天平均销量
            Integer sales7Days = orderMapper.getProductSalesInDays(product.getId(), 7);
            context.setAvgSales7Days(sales7Days != null ? sales7Days / 7.0 : 0.0);
        } catch (Exception e) {
            log.warn("获取商品销量数据失败：productId={}", product.getId(), e);
            context.setYesterdaySales(0);
            context.setAvgSales3Days(0.0);
            context.setAvgSales7Days(0.0);
        }

        return context;
    }

    /**
     * 重试发送失败的邮件（每1分钟）
     *
     * @author IhaveBB
     * @date 2026/03/22
     */
    @Scheduled(cron = "0 */1 * * * ?")
    public void retryFailedEmails() {
        String lockKey = LOCK_PREFIX + "retry";
        if (!tryLock(lockKey, 5)) {
            return;
        }

        try {
            int retryCount = emailRecordService.retryFailedEmails();
            if (retryCount > 0) {
                log.info("[邮件重试] 成功重试 {} 封邮件", retryCount);
            }
        } catch (Exception e) {
            log.error("[邮件重试] 重试失败", e);
        } finally {
            releaseLock(lockKey);
        }
    }

    /**
     * 尝试获取分布式锁
     *
     * @param lockKey 锁 Key
     * @param expireSeconds 过期时间（秒）
     * @return 是否获取成功
     * @author IhaveBB
     * @date 2026/03/22
     */
    private boolean tryLock(String lockKey, int expireSeconds) {
        return redisUtil.tryLock(lockKey, "1", expireSeconds);
    }

    /**
     * 释放分布式锁
     *
     * @param lockKey 锁 Key
     * @author IhaveBB
     * @date 2026/03/22
     */
    private void releaseLock(String lockKey) {
        redisUtil.releaseLock(lockKey, "1");
    }
}
