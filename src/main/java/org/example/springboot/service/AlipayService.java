package org.example.springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.hutool.json.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayTradePagePayModel;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.example.springboot.config.AliPayConfig;
import org.example.springboot.entity.*;
import org.example.springboot.enums.ErrorCodeEnum;
import org.example.springboot.exception.BusinessException;
import org.example.springboot.mapper.OrderMapper;
import org.example.springboot.mapper.PaymentRecordMapper;
import org.example.springboot.mapper.ProductMapper;
import org.example.springboot.mapper.RechargeRecordMapper;
import org.example.springboot.mapper.BalanceRecordMapper;
import org.example.springboot.mapper.UserMapper;
import org.example.springboot.util.RedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.scheduling.annotation.Scheduled;

import jakarta.servlet.http.HttpServletResponse;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class AlipayService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AlipayService.class);
    private static final String GATEWAY_URL = "https://openapi-sandbox.dl.alipaydev.com/gateway.do";
    private static final String FORMAT = "JSON";
    private static final String CHARSET = "UTF-8";
    //签名方式
    private static final String SIGN_TYPE = "RSA2";
    // 分布式锁key前缀
    private static final String LOCK_PREFIX_PAY = "lock:pay:order:";
    private static final String LOCK_PREFIX_STOCK = "lock:pay:stock:";
    private static final String ORDER_PAYING_PREFIX = "order:paying:";
    private static final String RECHARGE_PAYING_PREFIX = "recharge:paying:";

    @Resource
    private AliPayConfig aliPayConfig;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private PaymentRecordMapper paymentRecordMapper;

    @Autowired
    private RechargeRecordMapper rechargeRecordMapper;

    @Autowired
    private BalanceRecordMapper balanceRecordMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RedisUtil redisUtil;



    /**
     * 发起支付宝支付，生成支付表单并写入响应
     * <p>
     * 使用 Redis 分布式锁防止并发超卖，库存预扣机制确保支付安全性。
     * </p>
     *
     * @param orderId      订单ID
     * @param httpResponse HTTP响应对象
     * @throws Exception 支付宝API调用异常
     * @author IhaveBB
     * @date 2026/03/22
     */
    public void pay(Long orderId, HttpServletResponse httpResponse) throws Exception {
        // 查询订单信息
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCodeEnum.ORDER_NOT_FOUND);
        }

        // 检查订单状态，只允许待支付订单发起支付
        if (order.getStatus() != 0) {
            throw new BusinessException(ErrorCodeEnum.ORDER_STATUS_INVALID, "订单状态不允许支付");
        }

        Product product = productMapper.selectById(order.getProductId());
        if (product == null) {
            throw new BusinessException(ErrorCodeEnum.PRODUCT_NOT_FOUND);
        }

        // 【分布式锁】防止同一订单重复支付
        String orderLockKey = LOCK_PREFIX_PAY + orderId;
        String orderLockValue = UUID.randomUUID().toString();
        boolean orderLocked = redisUtil.tryLock(orderLockKey, orderLockValue, 30);
        if (!orderLocked) {
            LOGGER.warn("支付请求过于频繁，订单正在处理中，orderId={}", orderId);
            throw new BusinessException(ErrorCodeEnum.ERROR, "支付请求过于频繁，请稍后重试");
        }

        try {
            // 【修改】库存已在创建订单时扣减，这里只检查库存是否为负（异常情况）
            product = productMapper.selectById(order.getProductId());
            if (product == null) {
                throw new BusinessException(ErrorCodeEnum.PRODUCT_NOT_FOUND);
            }
            if (product.getStock() < 0) {
                LOGGER.error("库存异常为负数，productId={}，stock={}", product.getId(), product.getStock());
                throw new BusinessException(ErrorCodeEnum.ERROR, "商品库存异常");
            }

            LOGGER.info("支付宝支付库存检查通过，productId={}，当前库存={}", product.getId(), product.getStock());

            // 设置订单支付中标记（10分钟过期，与支付宝支付超时时间一致）
            redisUtil.set(ORDER_PAYING_PREFIX + orderId, "1", 600);

            // 1. 创建Client，通用SDK提供的Client，负责调用支付宝的API
            AlipayClient alipayClient = new DefaultAlipayClient(GATEWAY_URL, aliPayConfig.getAppId(),
                    aliPayConfig.getAppPrivateKey(), FORMAT, CHARSET, aliPayConfig.getAlipayPublicKey(), SIGN_TYPE);
            LOGGER.info("alipay:" + aliPayConfig.toString());
            // 2. 创建 Request并设置Request参数
            AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();  // 发送请求的 Request类
            request.setNotifyUrl(aliPayConfig.getNotifyUrl());
            JSONObject bizContent = new JSONObject();

            BigDecimal totalAmount = order.getTotalPrice();

            // 订单的总金额
            bizContent.set("out_trade_no", order.getId());  // 我们自己生成的订单编号
            bizContent.set("total_amount", totalAmount); // 订单的总金额
            bizContent.set("subject", product.getName());   // 支付的名称
            bizContent.set("product_code", "FAST_INSTANT_TRADE_PAY");  // 固定配置
            LOGGER.info(bizContent.toString());
            request.setBizContent(bizContent.toString());
            request.setReturnUrl("http://localhost:8080/order"); // 支付完成后自动跳转到本地页面的路径
            // 执行请求，拿到响应的结果，返回给浏览器
            String form = "";

            try {
                form = alipayClient.pageExecute(request).getBody(); // 调用SDK生成表单
            } catch (AlipayApiException e) {
                LOGGER.error("支付宝接口调用失败", e);
                // 【修改】库存已在创建订单时扣减，支付失败不回滚库存，等待订单超时或取消时再回滚
                throw new BusinessException(ErrorCodeEnum.ERROR, "支付通道异常，请稍后重试");
            }
            httpResponse.setContentType("text/html;charset=" + CHARSET);
            httpResponse.getWriter().write(form);// 直接将完整的表单html输出到页面
            httpResponse.getWriter().flush();
            httpResponse.getWriter().close();

        } finally {
            redisUtil.releaseLock(orderLockKey, orderLockValue);
        }
    }

    /**
     * 发起余额充值支付（支付宝）
     * <p>
     * 用户使用支付宝充值到余额账户
     * </p>
     *
     * @param rechargeRecord 充值记录
     * @param httpResponse   HTTP响应对象
     * @throws Exception 支付宝API调用异常
     * @author IhaveBB
     * @date 2026/03/24
     */
    public void rechargePay(RechargeRecord rechargeRecord, HttpServletResponse httpResponse) throws Exception {
        // 检查充值记录状态，只允许待支付的记录发起支付
        if (rechargeRecord.getStatus() != 0) {
            LOGGER.warn("充值记录状态不允许支付，rechargeId={}，status={}", rechargeRecord.getId(), rechargeRecord.getStatus());
            throw new BusinessException(ErrorCodeEnum.ERROR, "充值订单状态异常，请刷新页面重试");
        }

        // 【分布式锁】防止重复支付
        String rechargeLockKey = LOCK_PREFIX_PAY + "recharge:" + rechargeRecord.getId();
        String rechargeLockValue = UUID.randomUUID().toString();
        boolean rechargeLocked = redisUtil.tryLock(rechargeLockKey, rechargeLockValue, 30);
        if (!rechargeLocked) {
            LOGGER.warn("充值请求过于频繁，正在处理中，rechargeId={}", rechargeRecord.getId());
            throw new BusinessException(ErrorCodeEnum.ERROR, "充值请求过于频繁，请稍后重试");
        }

        try {
            // 设置充值支付中标记（10分钟过期）
            redisUtil.set(RECHARGE_PAYING_PREFIX + rechargeRecord.getId(), "1", 600);

            // 1. 创建Client
            AlipayClient alipayClient = new DefaultAlipayClient(GATEWAY_URL, aliPayConfig.getAppId(),
                    aliPayConfig.getAppPrivateKey(), FORMAT, CHARSET, aliPayConfig.getAlipayPublicKey(), SIGN_TYPE);

            // 2. 创建 Request并设置Request参数
            AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
            request.setNotifyUrl(aliPayConfig.getNotifyUrl());

            JSONObject bizContent = new JSONObject();
            // 使用 RECHARGE_ 前缀区分充值订单
            bizContent.set("out_trade_no", rechargeRecord.getRechargeNo());
            bizContent.set("total_amount", rechargeRecord.getAmount());
            bizContent.set("subject", "余额充值");
            bizContent.set("product_code", "FAST_INSTANT_TRADE_PAY");

            LOGGER.info("充值支付参数：{}", bizContent.toString());
            request.setBizContent(bizContent.toString());
            request.setReturnUrl("http://localhost:8080/user-center"); // 充值完成后跳转到个人中心

            // 执行请求，拿到响应的结果
            String form = "";
            try {
                form = alipayClient.pageExecute(request).getBody();
            } catch (AlipayApiException e) {
                LOGGER.error("支付宝充值接口调用失败", e);
                throw new BusinessException(ErrorCodeEnum.ERROR, "支付通道异常，请稍后重试");
            }

            httpResponse.setContentType("text/html;charset=" + CHARSET);
            httpResponse.getWriter().write(form);
            httpResponse.getWriter().flush();
            httpResponse.getWriter().close();

        } finally {
            redisUtil.releaseLock(rechargeLockKey, rechargeLockValue);
        }
    }

    /**
     * 回滚库存（支付失败时调用）
     */
    private void rollbackStock(Long productId, Integer quantity) {
        try {
            String stockLockKey = LOCK_PREFIX_STOCK + productId;
            String stockLockValue = UUID.randomUUID().toString();
            if (redisUtil.tryLock(stockLockKey, stockLockValue, 5)) {
                try {
                    Product product = productMapper.selectById(productId);
                    if (product != null) {
                        product.setStock(product.getStock() + quantity);
                        product.setSalesCount(Math.max(0, product.getSalesCount() - quantity));
                        productMapper.updateById(product);
                        LOGGER.info("库存回滚成功，productId={}，回滚数量={}", productId, quantity);
                    }
                } finally {
                    redisUtil.releaseLock(stockLockKey, stockLockValue);
                }
            }
        } catch (Exception e) {
            LOGGER.error("库存回滚失败，productId={}，quantity={}", productId, quantity, e);
        }
    }

    /**
     * 处理支付宝异步回调通知，验签后更新库存和订单状态
     * <p>
     * 使用分布式锁防止并发重复处理，幂等守卫确保订单只处理一次。
     * 注意：此方法本身不开启事务，具体业务逻辑在各自方法中管理事务。
     * </p>
     *
     * @param request HTTP请求对象（含支付宝回调参数）
     * @throws Exception 验签或数据库异常
     * @author IhaveBB
     * @date 2026/03/22
     */
    public void handlePaymentNotify(HttpServletRequest request) throws Exception {

        if (!"TRADE_SUCCESS".equals(request.getParameter("trade_status"))) {
            return;
        }

        LOGGER.info("=========支付宝异步回调========");
        Map<String, String> params = new HashMap<>();
        Map<String, String[]> requestParams = request.getParameterMap();
        for (String name : requestParams.keySet()) {
            params.put(name, request.getParameter(name));
        }
        String sign = params.get("sign");
        String content = AlipaySignature.getSignCheckContentV1(params);
        boolean checkSignature = AlipaySignature.rsa256CheckContent(content, sign, aliPayConfig.getAlipayPublicKey(), "UTF-8"); // 验证签名
        // 支付宝验签
        if (!checkSignature) {
            LOGGER.error("支付宝回调验签失败");
            return;
        }

        String tradeNo = params.get("out_trade_no");

        // 判断是充值订单还是商品订单
        if (tradeNo.startsWith("RECHARGE_")) {
            // 处理充值订单回调 - 内部管理事务
            doHandleRechargeNotify(tradeNo, params);
            return;
        }

        // 处理商品订单回调 - 内部管理事务
        doHandleOrderNotify(tradeNo, params);
    }

    /**
     * 处理商品订单支付宝回调（独立事务）
     *
     * @param tradeNo 订单号
     * @param params  支付宝回调参数
     */
    @Transactional(rollbackFor = Exception.class)
    public void doHandleOrderNotify(String tradeNo, Map<String, String> params) {
        Long orderId = Long.parseLong(tradeNo);

        // 【分布式锁】防止回调重复处理
        String lockKey = LOCK_PREFIX_PAY + orderId;
        String lockValue = UUID.randomUUID().toString();
        boolean locked = redisUtil.tryLock(lockKey, lockValue, 30);
        if (!locked) {
            LOGGER.warn("回调处理中，跳过重复请求，orderId={}", orderId);
            return;
        }

        try {
            Order order = orderMapper.selectById(orderId);

            // 订单不存在或已处于支付后状态时跳过，防止重复回调重复处理
            if (order == null) {
                LOGGER.warn("支付宝回调忽略：订单不存在，orderId={}", orderId);
                return;
            }

            // 已支付或已取消的订单不再处理
            if (order.getStatus() != 0) {
                LOGGER.warn("支付宝回调忽略：订单状态已变更，orderId={}，当前状态={}", orderId, order.getStatus());
                return;
            }

            // 检查是否有支付中标记（验证是否是合法的支付流程）
            String payingFlag = (String) redisUtil.get(ORDER_PAYING_PREFIX + orderId);
            if (payingFlag == null) {
                LOGGER.warn("支付宝回调警告：订单无支付标记，可能存在异常，orderId={}", orderId);
                // 这里可以选择继续处理或拒绝，为了安全我们选择继续处理但记录警告
            }

            // 更新订单状态为已支付
            order.setStatus(1); // 已支付
            orderMapper.updateById(order);

            // 删除支付中标记
            redisUtil.del(ORDER_PAYING_PREFIX + orderId);

            // 【新增】记录支付记录
            PaymentRecord paymentRecord = new PaymentRecord();
            paymentRecord.setOrderId(order.getId());
            paymentRecord.setUserId(order.getUserId());
            paymentRecord.setAmount(order.getTotalPrice());
            paymentRecord.setPayType(2); // 支付宝支付
            paymentRecord.setStatus(1); // 支付成功
            paymentRecord.setTradeNo(params.get("trade_no")); // 支付宝交易号
            paymentRecord.setRemark("支付宝支付");
            paymentRecord.setCreatedAt(new Timestamp(System.currentTimeMillis()));
            paymentRecordMapper.insert(paymentRecord);

            LOGGER.info("支付宝回调处理成功，orderId={}，订单已更新为已支付状态", orderId);

        } finally {
            redisUtil.releaseLock(lockKey, lockValue);
        }
    }

    /**
     * 处理充值订单支付宝回调（内部方法，确保事务生效）
     *
     * @param rechargeNo 充值单号
     * @param params     支付宝回调参数
     */
    @Transactional(rollbackFor = Exception.class)
    public void doHandleRechargeNotify(String rechargeNo, Map<String, String> params) {
        String alipayTradeNo = params.get("trade_no");
        String totalAmount = params.get("total_amount");

        // 分布式锁防止重复处理 - 使用与rechargePay一致的key格式
        String lockKey = LOCK_PREFIX_PAY + "recharge:" + rechargeNo;
        String lockValue = UUID.randomUUID().toString();
        boolean locked = redisUtil.tryLock(lockKey, lockValue, 30);
        if (!locked) {
            LOGGER.warn("充值回调处理中，跳过重复请求，rechargeNo={}", rechargeNo);
            return;
        }

        try {
            // 查询充值记录
            LambdaQueryWrapper<RechargeRecord> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(RechargeRecord::getRechargeNo, rechargeNo);
            RechargeRecord rechargeRecord = rechargeRecordMapper.selectOne(queryWrapper);

            if (rechargeRecord == null) {
                LOGGER.error("充值回调：充值记录不存在，rechargeNo={}", rechargeNo);
                return;
            }

            // 已支付或已取消的不再处理
            if (rechargeRecord.getStatus() != 0) {
                LOGGER.warn("充值回调忽略：充值记录状态已变更，rechargeNo={}，当前状态={}", rechargeNo, rechargeRecord.getStatus());
                return;
            }

            // 验证金额
            BigDecimal payAmount = new BigDecimal(totalAmount);
            if (payAmount.compareTo(rechargeRecord.getAmount()) != 0) {
                LOGGER.error("充值回调金额不匹配，rechargeNo={}，期望金额={}，实际金额={}",
                        rechargeNo, rechargeRecord.getAmount(), payAmount);
                return;
            }

            // 更新充值记录为已支付
            rechargeRecord.setStatus(1);
            rechargeRecord.setTradeNo(alipayTradeNo);
            rechargeRecord.setPaidAt(new Timestamp(System.currentTimeMillis()));
            rechargeRecordMapper.updateById(rechargeRecord);

            // 删除支付中标记
            redisUtil.del(RECHARGE_PAYING_PREFIX + rechargeRecord.getId());

            // 增加用户余额
            User user = userMapper.selectById(rechargeRecord.getUserId());
            if (user != null) {
                BigDecimal balanceBefore = user.getBalance() != null ? user.getBalance() : BigDecimal.ZERO;
                BigDecimal balanceAfter = balanceBefore.add(rechargeRecord.getAmount());
                user.setBalance(balanceAfter);
                userMapper.updateById(user);

                // 记录余额变动
                BalanceRecord balanceRecord = new BalanceRecord();
                balanceRecord.setUserId(user.getId());
                balanceRecord.setAmount(rechargeRecord.getAmount());
                balanceRecord.setBalanceBefore(balanceBefore);
                balanceRecord.setBalanceAfter(balanceAfter);
                balanceRecord.setType(1); // 充值
                balanceRecord.setRemark("支付宝充值");
                balanceRecord.setCreatedAt(new Timestamp(System.currentTimeMillis()));
                balanceRecordMapper.insert(balanceRecord);

                LOGGER.info("充值成功，已增加用户余额，用户ID={}，充值金额={}，当前余额={}",
                        user.getId(), rechargeRecord.getAmount(), balanceAfter);
            } else {
                LOGGER.error("充值回调：用户不存在，userId={}", rechargeRecord.getUserId());
                // 抛出异常触发回滚
                throw new BusinessException(ErrorCodeEnum.USER_NOT_FOUND, "用户不存在，充值处理失败");
            }

            LOGGER.info("充值回调处理成功，rechargeNo={}，用户ID={}，充值金额={}",
                    rechargeNo, rechargeRecord.getUserId(), rechargeRecord.getAmount());

        } finally {
            redisUtil.releaseLock(lockKey, lockValue);
        }
    }

    /**
     * 定时回滚超时未支付订单的库存
     * <p>
     * 每分钟执行一次，检查创建时间超过10分钟且状态仍为待支付的订单，
     * 回滚库存并关闭订单。使用1分钟扫描间隔确保订单关闭延迟不超过1分钟。
     * </p>
     *
     * @author IhaveBB
     * @date 2026/03/23
     */
    @Scheduled(cron = "0 */1 * * * ?")
    public void rollbackExpiredOrderStock() {
        String lockKey = "lock:scheduler:rollback-expired-orders";
        String lockValue = UUID.randomUUID().toString();

        // 分布式锁防止多实例同时执行
        if (!redisUtil.tryLock(lockKey, lockValue, 60)) {
            LOGGER.debug("未获取到超时订单回滚锁，跳过本次执行");
            return;
        }

        try {
            // 查询创建时间超过10分钟的待支付订单
            // 使用11分钟作为边界，确保10分钟过期的订单一定被扫描到
            LocalDateTime expireTime = LocalDateTime.now().minusMinutes(11);
            LambdaQueryWrapper<Order> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Order::getStatus, 0) // 待支付
                    .lt(Order::getCreatedAt, Timestamp.valueOf(expireTime));

            List<Order> expiredOrders = orderMapper.selectList(queryWrapper);

            if (expiredOrders.isEmpty()) {
                return;
            }

            LOGGER.info("[超时订单回滚] 发现 {} 个超时未支付订单", expiredOrders.size());

            for (Order order : expiredOrders) {
                try {
                    // 检查是否有支付中标记（有标记说明用户发起了支付但可能未完成）
                    String payingFlag = (String) redisUtil.get(ORDER_PAYING_PREFIX + order.getId());
                    if (payingFlag != null) {
                        // 还有支付标记，可能支付宝回调还没到，跳过
                        LOGGER.debug("订单仍有支付标记，跳过回滚，orderId={}", order.getId());
                        continue;
                    }

                    // 为每个订单加锁处理
                    String orderLockKey = LOCK_PREFIX_PAY + order.getId();
                    String orderLockValue = UUID.randomUUID().toString();
                    if (!redisUtil.tryLock(orderLockKey, orderLockValue, 30)) {
                        LOGGER.debug("订单正在处理中，跳过，orderId={}", order.getId());
                        continue;
                    }

                    try {
                        // 双重检查订单状态
                        Order currentOrder = orderMapper.selectById(order.getId());
                        if (currentOrder == null || currentOrder.getStatus() != 0) {
                            continue;
                        }

                        // 回滚库存
                        rollbackStock(currentOrder.getProductId(), currentOrder.getQuantity());

                        // 关闭订单（状态设为4-已取消）
                        currentOrder.setStatus(4);
                        currentOrder.setLastStatus(0);
                        orderMapper.updateById(currentOrder);

                        LOGGER.info("[超时订单回滚] 订单已关闭并回滚库存，orderId={}", order.getId());

                    } finally {
                        redisUtil.releaseLock(orderLockKey, orderLockValue);
                    }

                } catch (Exception e) {
                    LOGGER.error("[超时订单回滚] 处理订单失败，orderId={}", order.getId(), e);
                }
            }

        } finally {
            redisUtil.releaseLock(lockKey, lockValue);
        }
    }
} 