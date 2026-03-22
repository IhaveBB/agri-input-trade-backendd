package org.example.springboot.service;

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
import org.example.springboot.entity.Order;
import org.example.springboot.entity.Product;
import org.example.springboot.enums.ErrorCodeEnum;
import org.example.springboot.exception.BusinessException;
import org.example.springboot.mapper.OrderMapper;
import org.example.springboot.mapper.ProductMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletResponse;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
public class AlipayService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AlipayService.class);
    private static final String GATEWAY_URL = "https://openapi-sandbox.dl.alipaydev.com/gateway.do";
    private static final String FORMAT = "JSON";
    private static final String CHARSET = "UTF-8";
    //签名方式
    private static final String SIGN_TYPE = "RSA2";

    @Resource
    private AliPayConfig aliPayConfig;

    @Autowired
    private OrderMapper orderMapper;
    
    @Autowired
    private ProductMapper productMapper;



    /**
     * 发起支付宝支付，生成支付表单并写入响应
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

        Product product = productMapper.selectById(order.getProductId());
        if (product == null) {
            throw new BusinessException(ErrorCodeEnum.PRODUCT_NOT_FOUND);
        }

        if (product.getStock() < order.getQuantity()) {
            throw new BusinessException(ErrorCodeEnum.PRODUCT_STOCK_INSUFFICIENT);
        }


        // 1. 创建Client，通用SDK提供的Client，负责调用支付宝的API
        AlipayClient alipayClient = new DefaultAlipayClient(GATEWAY_URL, aliPayConfig.getAppId(),
                aliPayConfig.getAppPrivateKey(), FORMAT, CHARSET, aliPayConfig.getAlipayPublicKey(), SIGN_TYPE);
        LOGGER.info("alipay:"+aliPayConfig.toString());
        // 2. 创建 Request并设置Request参数
        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();  // 发送请求的 Request类
        request.setNotifyUrl(aliPayConfig.getNotifyUrl());
        JSONObject bizContent = new JSONObject();

//        BigDecimal orderNum = BigDecimal.valueOf(order.getQuantity()); // 将Integer转换为BigDecimal
//        BigDecimal price = product.getPrice(); // 假设这个方法返回BigDecimal
//        BigDecimal totalAmount = orderNum.multiply(price); // 使用BigDecimal的multiply方


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
            e.printStackTrace();
        }
        httpResponse.setContentType("text/html;charset=" + CHARSET);
        httpResponse.getWriter().write(form);// 直接将完整的表单html输出到页面
        httpResponse.getWriter().flush();
        httpResponse.getWriter().close();
    }

    /**
     * 处理支付宝异步回调通知，验签后更新库存和订单状态
     * <p>
     * 使用幂等守卫：订单不存在或状态非0（待支付）时直接跳过，防止重复扣减库存。
     * </p>
     *
     * @param request HTTP请求对象（含支付宝回调参数）
     * @throws Exception 验签或数据库异常
     * @author IhaveBB
     * @date 2026/03/22
     */
    @Transactional(rollbackFor = Exception.class)
    public void handlePaymentNotify(HttpServletRequest request) throws Exception{

        // 使用常量在左侧防止 trade_status 参数缺失时 NPE
        if ("TRADE_SUCCESS".equals(request.getParameter("trade_status"))) {
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
            if (checkSignature) {
                String tradeNo = params.get("out_trade_no");

                Long orderId = Long.parseLong(tradeNo);
                Order order = orderMapper.selectById(orderId);

                // 订单不存在或已处于支付后状态时跳过，防止重复回调重复扣减库存
                if (order == null || order.getStatus() != 0) {
                    LOGGER.warn("支付宝回调忽略：订单不存在或已处理，orderId={}", orderId);
                    return;
                }

                // 更新商品库存和销量
                Product product = productMapper.selectById(order.getProductId());
                product.setSalesCount(product.getSalesCount() + order.getQuantity());
                product.setStock(product.getStock() - order.getQuantity());
                productMapper.updateById(product);

                // 更新订单状态
                order.setStatus(1); // 已支付
                orderMapper.updateById(order);



            }
        }

    }
} 