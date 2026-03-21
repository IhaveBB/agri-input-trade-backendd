package org.example.springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.springboot.entity.Logistics;
import org.example.springboot.entity.Order;
import org.example.springboot.entity.Product;
import org.example.springboot.enums.ErrorCodeEnum;
import org.example.springboot.exception.BusinessException;
import org.example.springboot.mapper.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class LogisticsService {
    private static final Logger LOGGER = LoggerFactory.getLogger(LogisticsService.class);

    @Autowired
    private LogisticsMapper logisticsMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private AddressMapper addressMapper;

    @Autowired
    private ProductMapper productMapper;

    public Logistics createLogistics(Logistics logistics) {
        // 检查订单是否存在
        Order order = orderMapper.selectById(logistics.getOrderId());
        if (order == null) {
            throw new BusinessException(ErrorCodeEnum.ORDER_NOT_FOUND, "订单不存在");
        }

        // 检查订单状态是否为已支付
        if (order.getStatus() != 1) {
            throw new BusinessException(ErrorCodeEnum.ORDER_STATUS_ERROR, "订单状态不正确，只能为已支付订单创建物流");
        }

        // 检查必填字段
        if (logistics.getCompanyName() == null || logistics.getCompanyName().trim().isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "物流公司名称不能为空");
        }
        if (logistics.getReceiverName() == null || logistics.getReceiverName().trim().isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "收货人姓名不能为空");
        }
        if (logistics.getReceiverPhone() == null || logistics.getReceiverPhone().trim().isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "收货人电话不能为空");
        }
        if (logistics.getReceiverAddress() == null || logistics.getReceiverAddress().trim().isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "收货地址不能为空");
        }
        // 使用前端传入的状态，如果没有传入则默认为已发货(1)
        if (logistics.getStatus() == null) {
            logistics.setStatus(1);
        }

        int result = logisticsMapper.insert(logistics);
        if (result > 0) {
            // 更新订单状态为已发货
            order.setStatus(2);
            orderMapper.updateById(order);

            LOGGER.info("创建物流信息成功，物流ID：{}", logistics.getId());
            return logistics;
        }
        throw new BusinessException(ErrorCodeEnum.OPERATION_FAILED, "创建物流信息失败");
    }

    public Logistics updateLogisticsStatus(Long id, Integer status) {
        Logistics logistics = logisticsMapper.selectById(id);
        if (logistics == null) {
            throw new BusinessException(ErrorCodeEnum.LOGISTICS_NOT_FOUND, "未找到物流信息");
        }

        logistics.setStatus(status);
        int result = logisticsMapper.updateById(logistics);
        if (result > 0) {
            // 根据物流状态更新订单状态
            Order order = orderMapper.selectById(logistics.getOrderId());
            if (order != null) {
                switch (status) {
                    case 0:
                        order.setStatus(1);
                        break;
                    case 1:
                        order.setStatus(2);
                        break;
                    case 2:
                        order.setStatus(3);
                        break;
                    case 3:
                        order.setStatus(4);
                        break;
                }
                orderMapper.updateById(order);
            }

            LOGGER.info("更新物流状态成功，物流ID：{}，新状态：{}", id, status);
            return logistics;
        }
        throw new BusinessException(ErrorCodeEnum.OPERATION_FAILED, "更新物流状态失败");
    }

    public void deleteLogistics(Long id) {
        Logistics logistics = logisticsMapper.selectById(id);
        if (logistics == null) {
            throw new BusinessException(ErrorCodeEnum.LOGISTICS_NOT_FOUND, "未找到物流信息");
        }

        // 检查物流状态，只有未发货或已取消的物流信息才能删除
        if (logistics.getStatus() != 0 && logistics.getStatus() != 3) {
            throw new BusinessException(ErrorCodeEnum.LOGISTICS_STATUS_ERROR, "当前物流状态不允许删除");
        }

        int result = logisticsMapper.deleteById(id);
        if (result > 0) {
            LOGGER.info("删除物流信息成功，物流ID：{}", id);
            return;
        }
        throw new BusinessException(ErrorCodeEnum.OPERATION_FAILED, "删除物流信息失败");
    }

    public Logistics getLogisticsById(Long id) {
        Logistics logistics = logisticsMapper.selectById(id);
        if (logistics != null) {
            // 填充关联信息
            logistics.setOrder(orderMapper.selectById(logistics.getOrderId()));
            return logistics;
        }
        throw new BusinessException(ErrorCodeEnum.LOGISTICS_NOT_FOUND, "未找到物流信息");
    }

    public Logistics getLogisticsByOrderId(Long orderId) {
        LambdaQueryWrapper<Logistics> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Logistics::getOrderId, orderId);
        Logistics logistics = logisticsMapper.selectOne(queryWrapper);
        if (logistics != null) {
            // 填充关联信息
            logistics.setOrder(orderMapper.selectById(logistics.getOrderId()));
        }
        return logistics;
    }

    public Page<Logistics> getLogisticsByPage(Long orderId, Long merchantId, Integer status, Integer currentPage, Integer size) {
        return getLogisticsByPage(orderId, merchantId, status, null, currentPage, size);
    }

    public Page<Logistics> getLogisticsByPage(Long orderId, Long merchantId, Integer status, Long userId, Integer currentPage, Integer size) {
        // 创建分页对象
        Page<Logistics> page = new Page<>(currentPage, size);

        // 创建查询构造器
        LambdaQueryWrapper<Logistics> queryWrapper = new LambdaQueryWrapper<>();

        // 基础条件
        queryWrapper.eq(orderId != null, Logistics::getOrderId, orderId);
        queryWrapper.eq(status != null, Logistics::getStatus, status);

        if (merchantId != null) {
            // 先查询该商户的所有商品ID
            LambdaQueryWrapper<Product> productWrapper = new LambdaQueryWrapper<>();
            productWrapper.eq(Product::getMerchantId, merchantId);
            List<Product> products = productMapper.selectList(productWrapper);
            List<Long> productIds = products.stream().map(Product::getId).collect(Collectors.toList());

            if (!productIds.isEmpty()) {
                // 查询包含这些商品的订单ID
                LambdaQueryWrapper<Order> orderWrapper = new LambdaQueryWrapper<>();
                orderWrapper.in(Order::getProductId, productIds);
                List<Order> orders = orderMapper.selectList(orderWrapper);
                List<Long> orderIds = orders.stream().map(Order::getId).collect(Collectors.toList());

                if (!orderIds.isEmpty()) {
                    queryWrapper.in(Logistics::getOrderId, orderIds);
                } else {
                    // 如果没有找到相关订单，直接返回空结果
                    return new Page<>();
                }
            } else {
                // 如果没有找到该商户的商品，直接返回空结果
                return new Page<>();
            }
        }

        // 按创建时间倒序排序
        queryWrapper.orderByDesc(Logistics::getCreatedAt);

        // 执行分页查询
        Page<Logistics> result = logisticsMapper.selectPage(page, queryWrapper);

        // 填充关联信息
        result.getRecords().forEach(logistics -> {
            Order order = orderMapper.selectById(logistics.getOrderId());
            if (order != null) {
                logistics.setOrder(order);
                // 可以选择性地填充产品信息
                order.setProduct(productMapper.selectById(order.getProductId()));
            }
        });

        LOGGER.info("分页查询物流信息成功，当前页：{}，每页大小：{}", currentPage, size);
        return result;
    }

    public void deleteBatch(List<Long> ids) {
        // 检查每个物流信息的状态
        for (Long id : ids) {
            Logistics logistics = logisticsMapper.selectById(id);
            if (logistics != null && logistics.getStatus() != 0 && logistics.getStatus() != 3) {
                throw new BusinessException(ErrorCodeEnum.LOGISTICS_STATUS_ERROR, "物流ID：" + id + " 当前状态不允许删除");
            }
        }

        int result = logisticsMapper.deleteBatchIds(ids);
        if (result > 0) {
            LOGGER.info("批量删除物流信息成功，删除数量：{}", result);
            return;
        }
        throw new BusinessException(ErrorCodeEnum.OPERATION_FAILED, "批量删除物流信息失败");
    }

    public Logistics signLogistics(Long id) {
        return signLogistics(id, null);
    }

    public Logistics signLogistics(Long id, Long currentUserId) {
        Logistics logistics = logisticsMapper.selectById(id);
        if (logistics == null) {
            throw new BusinessException(ErrorCodeEnum.LOGISTICS_NOT_FOUND, "未找到物流信息");
        }

        // 权限检查：如果提供了currentUserId，验证是否为订单所有者
        if (currentUserId != null) {
            Order order = orderMapper.selectById(logistics.getOrderId());
            if (order != null && !order.getUserId().equals(currentUserId)) {
                throw new BusinessException(ErrorCodeEnum.PERMISSION_DENIED, "无权限签收他人订单的物流");
            }
        }

        // 检查当前状态是否为已发货
        if (logistics.getStatus() != 1) {
            throw new BusinessException(ErrorCodeEnum.LOGISTICS_STATUS_ERROR, "当前物流状态不允许签收");
        }

        // 更新物流状态为已签收
        logistics.setStatus(2);
        int result = logisticsMapper.updateById(logistics);

        if (result > 0) {
            // 更新订单状态为已完成
            Order order = orderMapper.selectById(logistics.getOrderId());
            if (order != null) {
                order.setStatus(3);
                orderMapper.updateById(order);
            }

            LOGGER.info("物流签收成功，物流ID：{}", id);
            return logistics;
        }
        throw new BusinessException(ErrorCodeEnum.OPERATION_FAILED, "物流签收失败");
    }
}
