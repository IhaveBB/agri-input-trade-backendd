package org.example.springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.example.springboot.entity.Order;
import org.example.springboot.entity.OrderBatchRequest;
import org.example.springboot.entity.Product;
import org.example.springboot.entity.Logistics;
import org.example.springboot.entity.Address;
import org.example.springboot.entity.dto.OrderAddressUpdateDTO;
import org.example.springboot.entity.dto.OrderCreateDTO;
import org.example.springboot.enums.ErrorCodeEnum;
import org.example.springboot.exception.BusinessException;
import org.example.springboot.mapper.*;
import org.example.springboot.util.RedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.example.springboot.entity.User;
import org.example.springboot.entity.BalanceRecord;
import org.example.springboot.entity.PaymentRecord;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import java.util.UUID;

@Service
public class OrderService {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrderService.class);
    private static final String LOCK_PREFIX_PAY = "lock:pay:order:";
    private static final String LOCK_PREFIX_STOCK = "lock:pay:stock:";

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private LogisticsMapper logisticsMapper;

    @Autowired
    private AddressMapper addressMapper;

    @Autowired
    private BalanceRecordMapper balanceRecordMapper;

    @Autowired
    private PaymentRecordMapper paymentRecordMapper;

    @Autowired
    private RedisUtil redisUtil;



    /**
     * 创建订单（使用 DTO）
     *
     * @param userId 用户ID（从上下文获取）
     * @param dto    订单创建DTO
     * @return 创建的订单
     * @author IhaveBB
     * @date 2026/03/21
     */
    public Order createOrder(Long userId, OrderCreateDTO dto) {
        // 检查商品库存
        Product product = productMapper.selectById(dto.getProductId());
        if (product == null) {
            throw new BusinessException(ErrorCodeEnum.PRODUCT_NOT_FOUND);
        }
        if (product.getStock() < dto.getQuantity()) {
            throw new BusinessException(ErrorCodeEnum.PRODUCT_STOCK_INSUFFICIENT);
        }

        // 【新增】扣减库存
        int stockAffected = productMapper.decreaseStock(product.getId(), dto.getQuantity());
        if (stockAffected <= 0) {
            throw new BusinessException(ErrorCodeEnum.PRODUCT_STOCK_INSUFFICIENT, "库存扣减失败，可能库存不足");
        }
        LOGGER.info("创建订单扣减库存成功，商品ID：{}，扣减数量：{}", product.getId(), dto.getQuantity());

        // 构建订单实体
        Order order = new Order();
        order.setUserId(userId);
        order.setProductId(dto.getProductId());
        order.setQuantity(dto.getQuantity());
        order.setPrice(dto.getPrice());
        order.setTotalPrice(dto.getPrice().multiply(BigDecimal.valueOf(dto.getQuantity())));
        order.setStatus(0); // 待支付
        order.setRemark(dto.getRemark());
        order.setRecvName(dto.getRecvName());
        order.setRecvPhone(dto.getRecvPhone());
        order.setRecvAddress(dto.getRecvAddress());

        int result = orderMapper.insert(order);
        if (result <= 0) {
            throw new BusinessException(ErrorCodeEnum.ERROR, "创建订单失败");
        }

        LOGGER.info("创建订单成功，订单ID：{}，用户ID：{}", order.getId(), userId);
        return order;
    }

    /**
     * 更新订单状态
     *
     * @param id     订单ID
     * @param status 新状态
     * @return 更新后的订单
     * @author IhaveBB
     * @date 2026/03/18
     */
    @Transactional(rollbackFor = Exception.class)
    public Order updateOrderStatus(Long id, Integer status) {
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ErrorCodeEnum.ORDER_NOT_FOUND);
        }

        order.setLastStatus(order.getStatus());
        order.setStatus(status);
        int result = orderMapper.updateById(order);
        if (result <= 0) {
            throw new BusinessException(ErrorCodeEnum.ERROR, "更新订单状态失败");
        }

        syncLogisticsOnStatusChange(id, status);

        LOGGER.info("更新订单状态成功，订单ID：{}，新状态：{}", id, status);
        return order;
    }

    /**
     * 删除订单
     *
     * @param id 订单ID
     * @author IhaveBB
     * @date 2026/03/18
     */
    public void deleteOrder(Long id) {
        deleteRelation(id);
        int result = orderMapper.deleteById(id);
        if (result <= 0) {
            throw new BusinessException(ErrorCodeEnum.ERROR, "删除订单失败");
        }
        LOGGER.info("删除订单成功，订单ID：{}", id);
    }

    /**
     * 根据订单新状态同步物流状态
     * <p>
     * 状态映射规则：订单退款（6）→ 物流取消（3）；订单完成（3）→ 物流签收（2）。
     * </p>
     *
     * @param orderId   订单ID
     * @param newStatus 订单新状态
     * @author IhaveBB
     * @date 2026/03/21
     */
    private void syncLogisticsOnStatusChange(Long orderId, Integer newStatus) {
        LambdaQueryWrapper<Logistics> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Logistics::getOrderId, orderId);
        Logistics logistics = logisticsMapper.selectOne(queryWrapper);
        if (logistics == null) {
            return;
        }
        if (newStatus == 6) {
            logistics.setStatus(3); // 已取消
            logisticsMapper.updateById(logistics);
            LOGGER.info("订单退款成功，同步更新物流状态为已取消，物流ID：{}", logistics.getId());
        } else if (newStatus == 3) {
            logistics.setStatus(2); // 已签收
            logisticsMapper.updateById(logistics);
            LOGGER.info("订单已完成，同步更新物流状态为已签收，物流ID：{}", logistics.getId());
        }
    }

    /**
     * 删除订单关联的物流信息
     *
     * @param id 订单ID
     * @author IhaveBB
     * @date 2026/03/21
     */
    public void deleteRelation(Long id){
        logisticsMapper.delete(new LambdaQueryWrapper<Logistics>().eq(Logistics::getOrderId,id));
    }

    /**
     * 根据ID获取订单
     *
     * @param id 订单ID
     * @return 订单实体
     * @author IhaveBB
     * @date 2026/03/18
     */
    public Order getOrderById(Long id) {
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ErrorCodeEnum.ORDER_NOT_FOUND);
        }
        // 填充关联信息
        order.setUser(userMapper.selectById(order.getUserId()));
        order.setProduct(productMapper.selectById(order.getProductId()));
        return order;
    }

    /**
     * 根据用户ID获取订单列表
     *
     * @param userId 用户ID
     * @return 订单列表
     * @author IhaveBB
     * @date 2026/03/18
     */
    public List<Order> getOrdersByUserId(Long userId) {
        LambdaQueryWrapper<Order> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Order::getUserId, userId);
        queryWrapper.orderByDesc(Order::getCreatedAt);
        List<Order> orders = orderMapper.selectList(queryWrapper);
        if (orders != null && !orders.isEmpty()) {
            // 填充关联信息
            orders.forEach(order -> {
                order.setUser(userMapper.selectById(order.getUserId()));
                order.setProduct(productMapper.selectById(order.getProductId()));
            });
        }
        return orders;
    }

    /**
     * 分页查询订单列表
     * <p>
     * 支持按用户ID、订单ID、状态、商户ID过滤；商户维度查询时先查商品再关联订单。
     * </p>
     *
     * @param userId      用户ID（可为 null，管理员场景）
     * @param id          订单ID（精确查找，可为 null）
     * @param status      订单状态（可为 null）
     * @param merchantId  商户ID（非 null 时只返回该商户的订单，可为 null）
     * @param currentPage 当前页码
     * @param size        每页条数
     * @return 分页订单列表，含关联用户、商品、商户信息
     * @author IhaveBB
     * @date 2026/03/21
     */
    public Page<Order> getOrdersByPage(Long userId,Long id,String status, Long merchantId,Integer currentPage, Integer size) {
        LambdaQueryWrapper<Order> queryWrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            queryWrapper.eq(Order::getUserId, userId);
        }
        if (id != null) {
            queryWrapper.eq(Order::getId, id);
        }
        if(StringUtils.isNotBlank(status)){
            queryWrapper.eq(Order::getStatus,status);
        }
        if (merchantId != null) {
            List<Product> product = productMapper.selectList(new LambdaQueryWrapper<Product>().eq(Product::getMerchantId, merchantId));

            if(!product.isEmpty()){
            List<Long> productIds = product.stream().map(Product::getId).collect(Collectors.toList());
            queryWrapper.in(Order::getProductId, productIds);
            }else{
                Page<Order> page = new Page<>(currentPage, size);
                page.setTotal(0);
                page.setRecords(null);
                return page;
            }
        }

        queryWrapper.orderByDesc(Order::getCreatedAt);

        Page<Order> page = new Page<>(currentPage, size);
        Page<Order> result = orderMapper.selectPage(page, queryWrapper);

        // 填充关联信息
        result.getRecords().forEach(order -> {
            order.setUser(userMapper.selectById(order.getUserId()));
            Product product = productMapper.selectById(order.getProductId());
            if(product!=null){
                order.setProduct(product);
                order.setMerchant(userMapper.selectById(product.getMerchantId()));
            }else{
                order.setProduct(null);
                order.setMerchant(null);
            }
        });

        return result;
    }

    /**
     * 申请退款
     *
     * @param id     订单ID
     * @param reason 退款原因
     * @return 更新后的订单
     * @author IhaveBB
     * @date 2026/03/18
     */
    public Order refundOrder(Long id, String reason) {
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ErrorCodeEnum.ORDER_NOT_FOUND);
        }

        // 检查订单状态是否允许退款
        if (order.getStatus() != 1 && order.getStatus() != 2) {
            throw new BusinessException(ErrorCodeEnum.ORDER_STATUS_INVALID, "当前订单状态不允许退款");
        }

        order.setLastStatus(order.getStatus());  // 保存当前状态
        order.setStatus(5);  // 设为退款中
        order.setRefundStatus(1); // 申请退款
        order.setRefundReason(reason);
        int result = orderMapper.updateById(order);
        if (result <= 0) {
            throw new BusinessException(ErrorCodeEnum.REFUND_FAILED, "申请退款失败");
        }
        LOGGER.info("申请退款成功，订单ID：{}", id);
        return order;
    }

    /**
     * 批量删除订单
     *
     * @param ids 订单ID列表
     * @author IhaveBB
     * @date 2026/03/18
     */
    public void deleteBatch(List<Long> ids) {
        // 检查每个订单是否存在关联记录
        for (Long id : ids) {
            // 检查物流
            deleteRelation(id);
        }

        int result = orderMapper.deleteBatchIds(ids);
        if (result <= 0) {
            throw new BusinessException(ErrorCodeEnum.ERROR, "批量删除订单失败");
        }
        LOGGER.info("批量删除订单成功，删除数量：{}", result);
    }
    /**
     * 支付订单（余额支付）
     * <p>
     * 使用 Redis 分布式锁防止并发超卖，数据库原子操作扣减库存。
     * </p>
     *
     * @param id 订单ID
     * @author IhaveBB
     * @date 2026/03/18
     */
    @Transactional(rollbackFor = Exception.class)
    public void payOrder(Long id) {
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ErrorCodeEnum.ORDER_NOT_FOUND);
        }

        // 检查订单状态，只允许待支付订单
        if (order.getStatus() != 0) {
            throw new BusinessException(ErrorCodeEnum.ORDER_STATUS_INVALID, "订单状态不允许支付");
        }

        Product product = productMapper.selectById(order.getProductId());
        if (product == null) {
            throw new BusinessException(ErrorCodeEnum.PRODUCT_NOT_FOUND);
        }

        // 【分布式锁】防止同一订单重复支付
        String orderLockKey = LOCK_PREFIX_PAY + id;
        String orderLockValue = UUID.randomUUID().toString();
        boolean orderLocked = redisUtil.tryLock(orderLockKey, orderLockValue, 30);
        if (!orderLocked) {
            LOGGER.warn("支付请求过于频繁，订单正在处理中，orderId={}", id);
            throw new BusinessException(ErrorCodeEnum.ERROR, "支付请求过于频繁，请稍后重试");
        }

        try {
            // 【分布式锁】防止同一商品并发超卖
            String stockLockKey = LOCK_PREFIX_STOCK + product.getId();
            String stockLockValue = UUID.randomUUID().toString();
            boolean stockLocked = redisUtil.tryLock(stockLockKey, stockLockValue, 10);
            if (!stockLocked) {
                LOGGER.warn("商品库存锁定失败，系统繁忙，productId={}", product.getId());
                throw new BusinessException(ErrorCodeEnum.ERROR, "系统繁忙，请稍后重试");
            }

            try {
                // 双重检查订单状态（加锁后再次确认）
                order = orderMapper.selectById(id);
                if (order == null || order.getStatus() != 0) {
                    throw new BusinessException(ErrorCodeEnum.ORDER_STATUS_INVALID, "订单状态已变更");
                }

                // 双重检查库存（加锁后再次确认）
                product = productMapper.selectById(order.getProductId());
                if (product == null) {
                    throw new BusinessException(ErrorCodeEnum.PRODUCT_NOT_FOUND);
                }
                // 【修改】库存已在创建订单时扣减，这里只检查库存是否为负（异常情况）
                if (product.getStock() < 0) {
                    LOGGER.error("库存异常为负数，productId={}，stock={}", product.getId(), product.getStock());
                    throw new BusinessException(ErrorCodeEnum.ERROR, "商品库存异常");
                }

                // 【移除】不再重复扣减库存，已在创建订单时扣减
                LOGGER.info("余额支付库存检查通过，productId={}，当前库存={}", product.getId(), product.getStock());

            } finally {
                redisUtil.releaseLock(stockLockKey, stockLockValue);
            }

            // 【新增】检查用户余额并扣减
            User user = userMapper.selectById(order.getUserId());
            if (user == null) {
                throw new BusinessException(ErrorCodeEnum.USER_NOT_FOUND);
            }

            BigDecimal balance = user.getBalance() != null ? user.getBalance() : BigDecimal.ZERO;
            if (balance.compareTo(order.getTotalPrice()) < 0) {
                throw new BusinessException(ErrorCodeEnum.ERROR, "余额不足，请先充值");
            }

            // 扣减余额
            BigDecimal newBalance = balance.subtract(order.getTotalPrice());
            user.setBalance(newBalance);
            int userUpdateResult = userMapper.updateById(user);
            if (userUpdateResult <= 0) {
                throw new BusinessException(ErrorCodeEnum.ERROR, "余额扣减失败");
            }

            // 记录余额变动
            BalanceRecord balanceRecord = new BalanceRecord();
            balanceRecord.setUserId(user.getId());
            balanceRecord.setAmount(order.getTotalPrice().negate());
            balanceRecord.setBalanceBefore(balance);
            balanceRecord.setBalanceAfter(newBalance);
            balanceRecord.setType(2); // 消费
            balanceRecord.setOrderId(order.getId());
            balanceRecord.setRemark("订单支付");
            balanceRecord.setCreatedAt(new Timestamp(System.currentTimeMillis()));
            balanceRecordMapper.insert(balanceRecord);

            // 更新订单状态
            order.setStatus(1);
            int orderRes = orderMapper.updateById(order);
            if (orderRes <= 0) {
                throw new BusinessException(ErrorCodeEnum.PAYMENT_FAILED, "支付异常，更新订单状态失败");
            }

            // 记录支付记录
            PaymentRecord paymentRecord = new PaymentRecord();
            paymentRecord.setOrderId(order.getId());
            paymentRecord.setUserId(user.getId());
            paymentRecord.setAmount(order.getTotalPrice());
            paymentRecord.setPayType(1); // 余额支付
            paymentRecord.setStatus(1); // 支付成功
            paymentRecord.setRemark("余额支付");
            paymentRecord.setCreatedAt(new Timestamp(System.currentTimeMillis()));
            paymentRecordMapper.insert(paymentRecord);

            LOGGER.info("支付订单成功，订单ID：{}，余额支付，扣除余额：{}，剩余余额：{}", id, order.getTotalPrice(), newBalance);

        } finally {
            redisUtil.releaseLock(orderLockKey, orderLockValue);
        }
    }

    /**
     * 更新订单收货地址（使用 DTO）
     *
     * @param id  订单ID
     * @param dto 收货地址更新DTO
     * @return 更新后的订单
     * @author IhaveBB
     * @date 2026/03/21
     */
    public Order updateOrderAddress(Long id, OrderAddressUpdateDTO dto) {
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ErrorCodeEnum.ORDER_NOT_FOUND);
        }

        // 检查订单状态，只有未发货的订单才能修改地址
        if (order.getStatus() > 1) {
            throw new BusinessException(ErrorCodeEnum.ORDER_STATUS_INVALID, "订单已发货，无法修改收货地址");
        }
        order.setRecvName(dto.getRecvName());
        order.setRecvAddress(dto.getRecvAddress());
        order.setRecvPhone(dto.getRecvPhone());

        int result = orderMapper.updateById(order);
        if (result <= 0) {
            throw new BusinessException(ErrorCodeEnum.ERROR, "更新订单收货信息失败");
        }
        LOGGER.info("更新订单收货信息成功，订单ID：{}", id);
        return order;
    }
    /**
     * 更新订单
     *
     * @param id    订单ID
     * @param order 订单实体
     * @return 更新后的订单
     * @author IhaveBB
     * @date 2026/03/18
     */
    public Order updateOrder(Long id, Order order) {
        Order existingOrder = orderMapper.selectById(id);
        if (existingOrder == null) {
            throw new BusinessException(ErrorCodeEnum.ORDER_NOT_FOUND);
        }

        // 设置ID确保更新正确的订单
        order.setId(id);

        // 保持原有的不可修改字段
        order.setCreatedAt(existingOrder.getCreatedAt());
        order.setUserId(existingOrder.getUserId());
        order.setProductId(existingOrder.getProductId());
        order.setTotalPrice(existingOrder.getTotalPrice());

        int result = orderMapper.updateById(order);
        if (result <= 0) {
            throw new BusinessException(ErrorCodeEnum.ERROR, "更新订单信息失败");
        }

        // 仅在状态发生变化时同步物流
        if (!existingOrder.getStatus().equals(order.getStatus())) {
            syncLogisticsOnStatusChange(id, order.getStatus());
        }

        LOGGER.info("更新订单成功，订单ID：{}", id);
        return order;
    }

    /**
     * 获取订单物流信息
     *
     * @param orderId 订单ID
     * @return 物流信息
     * @author IhaveBB
     * @date 2026/03/18
     */
    public Logistics getOrderLogistics(Long orderId) {
        // 检查订单是否存在
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCodeEnum.ORDER_NOT_FOUND);
        }

        // 查询物流信息
        LambdaQueryWrapper<Logistics> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Logistics::getOrderId, orderId);
        Logistics logistics = logisticsMapper.selectOne(queryWrapper);

        if (logistics == null) {
            throw new BusinessException(ErrorCodeEnum.LOGISTICS_NOT_FOUND);
        }

        // 填充关联信息
        logistics.setOrder(order);
        return logistics;
    }

    /**
     * 判断订单是否属于指定商户
     *
     * @param orderId    订单ID
     * @param merchantId 商户ID
     * @return 订单对应的商品归属该商户时返回 true，否则返回 false
     * @author IhaveBB
     * @date 2026/03/21
     */
    public boolean isOrderBelongToMerchant(Long orderId, Long merchantId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            return false;
        }
        Product product = productMapper.selectById(order.getProductId());
        if (product == null) {
            return false;
        }
        return product.getMerchantId() != null && product.getMerchantId().equals(merchantId);
    }

    /**
     * 处理退款申请
     *
     * @param id     订单ID
     * @param status 退款状态：6-同意退款, 7-拒绝退款
     * @param remark 处理备注
     * @return 更新后的订单
     * @author IhaveBB
     * @date 2026/03/18
     */
    @Transactional(rollbackFor = Exception.class)
    public Order handleRefund(Long id, Integer status, String remark) {
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ErrorCodeEnum.ORDER_NOT_FOUND);
        }

        // 检查订单是否处于退款中状态
        if (order.getStatus() != 5) {
            throw new BusinessException(ErrorCodeEnum.ORDER_STATUS_INVALID, "订单当前状态不是退款中");
        }

        // 保存原始状态
        order.setLastStatus(order.getStatus());
        // 更新状态
        order.setStatus(status);
        order.setRefundStatus(status == 6 ? 3 : 4); // 3-已退款, 4-退款失败
        order.setRefundTime(Timestamp.valueOf(LocalDateTime.now()));
        order.setRemark(remark);

        int result = orderMapper.updateById(order);
        if (result <= 0) {
            throw new BusinessException(ErrorCodeEnum.REFUND_FAILED, "处理退款失败");
        }

        // 如果同意退款，恢复商品库存并退回余额
        if (status == 6) {
            Product product = productMapper.selectById(order.getProductId());
            if (product != null) {
                // 增加库存
                product.setStock(product.getStock() + order.getQuantity());
                // 减少销量
                if (product.getSalesCount() >= order.getQuantity()) {
                    product.setSalesCount(product.getSalesCount() - order.getQuantity());
                }
                productMapper.updateById(product);
                LOGGER.info("退款成功，已恢复商品库存，商品ID：{}，数量：{}", product.getId(), order.getQuantity());
            }

            // 【新增】退回余额
            User user = userMapper.selectById(order.getUserId());
            if (user != null) {
                BigDecimal balance = user.getBalance() != null ? user.getBalance() : BigDecimal.ZERO;
                BigDecimal newBalance = balance.add(order.getTotalPrice());
                user.setBalance(newBalance);
                userMapper.updateById(user);

                // 记录余额变动
                BalanceRecord balanceRecord = new BalanceRecord();
                balanceRecord.setUserId(user.getId());
                balanceRecord.setAmount(order.getTotalPrice());
                balanceRecord.setBalanceBefore(balance);
                balanceRecord.setBalanceAfter(newBalance);
                balanceRecord.setType(3); // 退款
                balanceRecord.setOrderId(order.getId());
                balanceRecord.setRemark("订单退款");
                balanceRecord.setCreatedAt(new Timestamp(System.currentTimeMillis()));
                balanceRecordMapper.insert(balanceRecord);

                LOGGER.info("退款成功，已退回余额，用户ID：{}，金额：{}，当前余额：{}", user.getId(), order.getTotalPrice(), newBalance);
            }
        }

        // 同步更新物流状态
        syncLogisticsOnStatusChange(id, status);

        LOGGER.info("处理退款成功，订单ID：{}，处理结果：{}", id, status == 6 ? "已退款" : "拒绝退款");
        return order;
    }

    /**
     * 批量创建订单（从购物车下单）
     *
     * @param request 批量创建订单请求
     * @author IhaveBB
     * @date 2026/03/18
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchCreateOrders(OrderBatchRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "订单商品不能为空");
        }

        // 获取收货地址信息
        Address address = addressMapper.selectById(request.getAddressId());
        if (address == null) {
            throw new BusinessException(ErrorCodeEnum.ADDRESS_NOT_FOUND);
        }

        // 批量创建订单
        for (OrderBatchRequest.OrderItem item : request.getItems()) {
            // 检查商品是否存在和库存是否充足
            Product product = productMapper.selectById(item.getProductId());
            if (product == null) {
                throw new BusinessException(ErrorCodeEnum.PRODUCT_NOT_FOUND, "商品不存在：ID=" + item.getProductId());
            }
            if (product.getStock() < item.getQuantity()) {
                throw new BusinessException(ErrorCodeEnum.PRODUCT_STOCK_INSUFFICIENT,
                        "库存不足：商品 ID=" + item.getProductId() + ", 需要=" + item.getQuantity() + ", 库存=" + product.getStock());
            }

            // 【新增】扣减库存
            int stockAffected = productMapper.decreaseStock(product.getId(), item.getQuantity());
            if (stockAffected <= 0) {
                throw new BusinessException(ErrorCodeEnum.PRODUCT_STOCK_INSUFFICIENT,
                        "库存扣减失败：商品 ID=" + item.getProductId());
            }
            LOGGER.info("批量创建订单扣减库存成功，商品ID：{}，扣减数量：{}", product.getId(), item.getQuantity());

            // 创建订单
            Order order = new Order();
            order.setUserId(request.getUserId());
            order.setProductId(item.getProductId());
            order.setQuantity(item.getQuantity());
            order.setPrice(item.getPrice());
            order.setTotalPrice(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
            order.setStatus(0); // 待支付
            order.setRecvName(address.getReceiver());
            order.setRecvPhone(address.getPhone());
            order.setRecvAddress(address.getAddress());

            int result = orderMapper.insert(order);
            if (result <= 0) {
                throw new BusinessException(ErrorCodeEnum.ERROR, "创建订单失败：商品 ID=" + item.getProductId());
            }

            LOGGER.info("批量创建订单成功，商品 ID：{}，库存已扣减", item.getProductId());
        }

        LOGGER.info("批量创建订单完成，用户 ID：{}，订单数量：{}", request.getUserId(), request.getItems().size());
    }
} 