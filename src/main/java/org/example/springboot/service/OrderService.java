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
import org.example.springboot.enums.ErrorCodeEnum;
import org.example.springboot.exception.BusinessException;
import org.example.springboot.mapper.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderService {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrderService.class);

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



    /**
     * 创建订单
     *
     * @param order 订单实体
     * @return 创建的订单
     * @author IhaveBB
     * @date 2026/03/18
     */
    public Order createOrder(Order order) {
        // 检查商品库存
        Product product = productMapper.selectById(order.getProductId());
        if (product == null) {
            throw new BusinessException(ErrorCodeEnum.PRODUCT_NOT_FOUND);
        }
        if (product.getStock() < order.getQuantity()) {
            throw new BusinessException(ErrorCodeEnum.PRODUCT_STOCK_INSUFFICIENT);
        }

        // 计算总价
        order.setTotalPrice(order.getPrice().multiply(BigDecimal.valueOf(order.getQuantity())));

        int result = orderMapper.insert(order);
        if (result <= 0) {
            throw new BusinessException(ErrorCodeEnum.ERROR, "创建订单失败");
        }

        LOGGER.info("创建订单成功，订单ID：{}", order.getId());
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

        // 查找该订单的物流信息
        LambdaQueryWrapper<Logistics> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Logistics::getOrderId, id);
        Logistics logistics = logisticsMapper.selectOne(queryWrapper);

        if (logistics != null) {
            // 如果订单状态变为已退款，更新物流状态为已取消
            if (status == 6) { // 6表示已退款
                logistics.setStatus(3); // 3表示已取消
                logisticsMapper.updateById(logistics);
                LOGGER.info("订单退款成功，同步更新物流状态为已取消，物流ID：{}", logistics.getId());
            }
            // 如果订单状态变为已完成，更新物流状态为已签收
            else if (status == 3) { // 3表示已完成
                logistics.setStatus(2); // 2表示已签收
                logisticsMapper.updateById(logistics);
                LOGGER.info("订单已完成，同步更新物流状态为已签收，物流ID：{}", logistics.getId());
            }
        }

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
     * 支付订单
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

        Product product = productMapper.selectById(order.getProductId());
        if (product == null) {
            throw new BusinessException(ErrorCodeEnum.PRODUCT_NOT_FOUND);
        }

        if (product.getStock() < order.getQuantity()) {
            throw new BusinessException(ErrorCodeEnum.PRODUCT_STOCK_INSUFFICIENT);
        }

        product.setSalesCount(product.getSalesCount() + order.getQuantity());
        product.setStock(product.getStock() - order.getQuantity());
        order.setStatus(1);
        int res = productMapper.updateById(product);

        if (res <= 0) {
            throw new BusinessException(ErrorCodeEnum.PAYMENT_FAILED, "支付异常，更新商品信息失败");
        }

        int orderRes = orderMapper.updateById(order);
        if (orderRes <= 0) {
            throw new BusinessException(ErrorCodeEnum.PAYMENT_FAILED, "支付异常，更新订单状态失败");
        }

        LOGGER.info("支付订单成功，订单ID：{}", id);
    }

    /**
     * 更新订单收货地址
     *
     * @param name    收货人姓名
     * @param id      订单ID
     * @param address 收货地址
     * @param phone   联系电话
     * @return 更新后的订单
     * @author IhaveBB
     * @date 2026/03/18
     */
    public Order updateOrderAddress(String name, Long id, String address, String phone) {
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ErrorCodeEnum.ORDER_NOT_FOUND);
        }

        // 检查订单状态，只有未发货的订单才能修改地址
        if (order.getStatus() > 1) {
            throw new BusinessException(ErrorCodeEnum.ORDER_STATUS_INVALID, "订单已发货，无法修改收货地址");
        }
        order.setRecvName(name);
        order.setRecvAddress(address);
        order.setRecvPhone(phone);

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

        // 查找该订单的物流信息
        LambdaQueryWrapper<Logistics> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Logistics::getOrderId, id);
        Logistics logistics = logisticsMapper.selectOne(queryWrapper);

        if (logistics != null) {
            // 如果订单状态变为已退款，更新物流状态为已取消
            if (order.getStatus() == 6 && existingOrder.getStatus() != 6) {
                logistics.setStatus(3); // 3表示已取消
                logisticsMapper.updateById(logistics);
                LOGGER.info("订单退款成功，同步更新物流状态为已取消，物流ID：{}", logistics.getId());
            }
            // 如果订单状态变为已完成，更新物流状态为已签收
            else if (order.getStatus() == 3 && existingOrder.getStatus() != 3) {
                logistics.setStatus(2); // 2表示已签收
                logisticsMapper.updateById(logistics);
                LOGGER.info("订单已完成，同步更新物流状态为已签收，物流ID：{}", logistics.getId());
            }
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
     * 处理退款申请
     * @param id 订单ID
     * @param status 退款状态：6-同意退款, 7-拒绝退款
     * @param remark 处理备注
     * @return 处理结果
     */
    /**
     * 判断订单是否属于指定商户
     * @param orderId 订单ID
     * @param merchantId 商户ID
     * @return 是否属于该商户
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

        // 如果同意退款，恢复商品库存
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
        }

        // 同步更新物流状态
        LambdaQueryWrapper<Logistics> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Logistics::getOrderId, id);
        Logistics logistics = logisticsMapper.selectOne(queryWrapper);
        if (logistics != null && status == 6) { // 如果同意退款
            logistics.setStatus(3); // 设置物流状态为已取消
            logisticsMapper.updateById(logistics);
            LOGGER.info("订单退款成功，同步更新物流状态为已取消，物流ID：{}", logistics.getId());
        }

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

            // 注意：库存和销量在支付成功后扣减，不在创建订单时扣减
            LOGGER.info("批量创建订单成功，商品 ID：{}", item.getProductId());
        }

        LOGGER.info("批量创建订单完成，用户 ID：{}，订单数量：{}", request.getUserId(), request.getItems().size());
    }
} 