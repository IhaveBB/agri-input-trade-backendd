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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 物流服务
 * <p>
 * 负责物流记录的创建、状态流转、查询及删除。
 * 物流→订单状态联动规则统一在 {@link #LOGISTICS_TO_ORDER_STATUS} 映射表中维护，
 * 消除多处分散的 switch/if 硬编码，保证状态转换的一致性。
 * </p>
 *
 * <pre>
 * 物流状态 → 订单状态映射：
 *   0（待发货）→ 1（待发货）
 *   1（已发货）→ 2（已发货）
 *   2（已签收）→ 3（已完成）
 *   3（已取消）→ 4（已取消）
 * </pre>
 *
 * @author IhaveBB
 * @date 2026/03/22
 */
@Service
public class LogisticsService {
    private static final Logger LOGGER = LoggerFactory.getLogger(LogisticsService.class);

    /**
     * 物流状态 → 订单状态映射表
     * <p>
     * 集中维护状态联动规则，新增状态只需修改此处，无需改动业务方法。
     * </p>
     */
    private static final Map<Integer, Integer> LOGISTICS_TO_ORDER_STATUS = new HashMap<>();

    static {
        LOGISTICS_TO_ORDER_STATUS.put(0, 1); // 待发货 → 待发货
        LOGISTICS_TO_ORDER_STATUS.put(1, 2); // 已发货 → 已发货
        LOGISTICS_TO_ORDER_STATUS.put(2, 3); // 已签收 → 已完成
        LOGISTICS_TO_ORDER_STATUS.put(3, 4); // 已取消 → 已取消
    }

    @Autowired
    private LogisticsMapper logisticsMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private AddressMapper addressMapper;

    @Autowired
    private ProductMapper productMapper;

    /**
     * 创建物流记录
     * <p>
     * 同一订单只允许存在一条物流记录，重复创建时抛出 {@link ErrorCodeEnum#ALREADY_EXISTS}。
     * 创建成功后自动将关联订单状态更新为"已发货"（status=2）。
     * </p>
     *
     * @param logistics 物流实体（需包含 orderId、companyName、receiverName、receiverPhone、receiverAddress）
     * @return 创建成功的物流记录
     * @author IhaveBB
     * @date 2026/03/22
     */
    public Logistics createLogistics(Logistics logistics) {
        // 检查订单是否存在
        Order order = orderMapper.selectById(logistics.getOrderId());
        if (order == null) {
            throw new BusinessException(ErrorCodeEnum.ORDER_NOT_FOUND);
        }

        // 检查订单状态是否为已支付
        if (!Integer.valueOf(1).equals(order.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.ORDER_STATUS_ERROR, "订单状态不正确，只能为已支付订单创建物流");
        }

        // 防止同一订单重复创建物流
        Long existingCount = logisticsMapper.selectCount(
                new LambdaQueryWrapper<Logistics>().eq(Logistics::getOrderId, logistics.getOrderId())
        );
        if (existingCount > 0) {
            throw new BusinessException(ErrorCodeEnum.ALREADY_EXISTS, "该订单已存在物流记录，请勿重复创建");
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

        if (logistics.getStatus() == null) {
            logistics.setStatus(1); // 默认已发货
        }

        int result = logisticsMapper.insert(logistics);
        if (result <= 0) {
            throw new BusinessException(ErrorCodeEnum.OPERATION_FAILED, "创建物流信息失败");
        }

        // 通过状态映射表同步订单状态，避免硬编码
        syncOrderStatus(logistics.getOrderId(), logistics.getStatus());

        LOGGER.info("创建物流信息成功，物流ID：{}，订单ID：{}", logistics.getId(), logistics.getOrderId());
        return logistics;
    }

    /**
     * 更新物流信息（不变更状态联动）
     *
     * @param logistics 物流实体（必须含 id）
     * @return 更新后的物流实体
     * @author IhaveBB
     * @date 2026/03/22
     */
    public Logistics updateLogistics(Logistics logistics) {
        Logistics existing = logisticsMapper.selectById(logistics.getId());
        if (existing == null) {
            throw new BusinessException(ErrorCodeEnum.LOGISTICS_NOT_FOUND);
        }
        int result = logisticsMapper.updateById(logistics);
        if (result <= 0) {
            throw new BusinessException(ErrorCodeEnum.OPERATION_FAILED, "更新物流信息失败");
        }
        LOGGER.info("更新物流信息成功，物流ID：{}", logistics.getId());
        return logistics;
    }

    /**
     * 更新物流状态，并同步关联订单状态
     * <p>
     * 状态联动规则见 {@link #LOGISTICS_TO_ORDER_STATUS}。
     * </p>
     *
     * @param id     物流ID
     * @param status 新物流状态
     * @return 更新后的物流实体
     * @author IhaveBB
     * @date 2026/03/22
     */
    public Logistics updateLogisticsStatus(Long id, Integer status) {
        Logistics logistics = logisticsMapper.selectById(id);
        if (logistics == null) {
            throw new BusinessException(ErrorCodeEnum.LOGISTICS_NOT_FOUND);
        }

        logistics.setStatus(status);
        int result = logisticsMapper.updateById(logistics);
        if (result <= 0) {
            throw new BusinessException(ErrorCodeEnum.OPERATION_FAILED, "更新物流状态失败");
        }

        syncOrderStatus(logistics.getOrderId(), status);
        LOGGER.info("更新物流状态成功，物流ID：{}，新状态：{}", id, status);
        return logistics;
    }

    /**
     * 删除物流记录
     * <p>
     * 仅允许删除"待发货（0）"或"已取消（3）"状态的物流记录。
     * </p>
     *
     * @param id 物流ID
     * @author IhaveBB
     * @date 2026/03/22
     */
    public void deleteLogistics(Long id) {
        Logistics logistics = logisticsMapper.selectById(id);
        if (logistics == null) {
            throw new BusinessException(ErrorCodeEnum.LOGISTICS_NOT_FOUND);
        }

        if (!Integer.valueOf(0).equals(logistics.getStatus()) && !Integer.valueOf(3).equals(logistics.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.LOGISTICS_STATUS_ERROR, "当前物流状态不允许删除");
        }

        int result = logisticsMapper.deleteById(id);
        if (result <= 0) {
            throw new BusinessException(ErrorCodeEnum.OPERATION_FAILED, "删除物流信息失败");
        }
        LOGGER.info("删除物流信息成功，物流ID：{}", id);
    }

    /**
     * 根据ID获取物流详情（含关联订单）
     *
     * @param id 物流ID
     * @return 物流实体
     * @author IhaveBB
     * @date 2026/03/22
     */
    public Logistics getLogisticsById(Long id) {
        Logistics logistics = logisticsMapper.selectById(id);
        if (logistics == null) {
            throw new BusinessException(ErrorCodeEnum.LOGISTICS_NOT_FOUND);
        }
        logistics.setOrder(orderMapper.selectById(logistics.getOrderId()));
        return logistics;
    }

    /**
     * 根据订单ID获取物流信息
     *
     * @param orderId 订单ID
     * @return 物流实体，订单无物流时返回 null
     * @author IhaveBB
     * @date 2026/03/22
     */
    public Logistics getLogisticsByOrderId(Long orderId) {
        LambdaQueryWrapper<Logistics> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Logistics::getOrderId, orderId);
        Logistics logistics = logisticsMapper.selectOne(queryWrapper);
        if (logistics != null) {
            logistics.setOrder(orderMapper.selectById(logistics.getOrderId()));
        }
        return logistics;
    }

    /**
     * 分页查询物流列表（不含用户ID过滤）
     *
     * @param orderId     订单ID（精确，可为 null）
     * @param merchantId  商户ID（可为 null）
     * @param status      物流状态（可为 null）
     * @param currentPage 当前页码
     * @param size        每页条数
     * @return 分页物流列表
     * @author IhaveBB
     * @date 2026/03/22
     */
    public Page<Logistics> getLogisticsByPage(Long orderId, Long merchantId, Integer status,
                                               Integer currentPage, Integer size) {
        return getLogisticsByPage(orderId, merchantId, status, null, currentPage, size);
    }

    /**
     * 分页查询物流列表（含用户ID过滤）
     * <p>
     * 商户维度过滤时先查商品再关联订单；用户维度过滤直接关联订单。
     * </p>
     *
     * @param orderId     订单ID（精确，可为 null）
     * @param merchantId  商户ID（可为 null）
     * @param status      物流状态（可为 null）
     * @param userId      用户ID（可为 null）
     * @param currentPage 当前页码
     * @param size        每页条数
     * @return 分页物流列表，含关联订单和商品信息
     * @author IhaveBB
     * @date 2026/03/22
     */
    public Page<Logistics> getLogisticsByPage(Long orderId, Long merchantId, Integer status,
                                               Long userId, Integer currentPage, Integer size) {
        Page<Logistics> page = new Page<>(currentPage, size);
        LambdaQueryWrapper<Logistics> queryWrapper = new LambdaQueryWrapper<>();

        queryWrapper.eq(orderId != null, Logistics::getOrderId, orderId);
        queryWrapper.eq(status != null, Logistics::getStatus, status);

        if (merchantId != null) {
            List<Long> productIds = productMapper.selectList(
                    new LambdaQueryWrapper<Product>().eq(Product::getMerchantId, merchantId)
            ).stream().map(Product::getId).collect(Collectors.toList());

            if (productIds.isEmpty()) {
                return new Page<>(currentPage, size);
            }

            List<Long> orderIds = orderMapper.selectList(
                    new LambdaQueryWrapper<Order>().in(Order::getProductId, productIds)
            ).stream().map(Order::getId).collect(Collectors.toList());

            if (orderIds.isEmpty()) {
                return new Page<>(currentPage, size);
            }
            queryWrapper.in(Logistics::getOrderId, orderIds);
        }

        queryWrapper.orderByDesc(Logistics::getCreatedAt);

        Page<Logistics> result = logisticsMapper.selectPage(page, queryWrapper);

        result.getRecords().forEach(logistics -> {
            Order order = orderMapper.selectById(logistics.getOrderId());
            if (order != null) {
                logistics.setOrder(order);
                order.setProduct(productMapper.selectById(order.getProductId()));
            }
        });

        LOGGER.info("分页查询物流信息成功，当前页：{}，每页大小：{}", currentPage, size);
        return result;
    }

    /**
     * 批量删除物流记录
     * <p>
     * 每条记录均须满足可删除状态（待发货或已取消），任一不满足则整批拒绝。
     * </p>
     *
     * @param ids 物流ID列表
     * @author IhaveBB
     * @date 2026/03/22
     */
    public void deleteBatch(List<Long> ids) {
        for (Long id : ids) {
            Logistics logistics = logisticsMapper.selectById(id);
            if (logistics != null && !Integer.valueOf(0).equals(logistics.getStatus()) && !Integer.valueOf(3).equals(logistics.getStatus())) {
                throw new BusinessException(ErrorCodeEnum.LOGISTICS_STATUS_ERROR,
                        "物流ID：" + id + " 当前状态不允许删除");
            }
        }

        int result = logisticsMapper.deleteBatchIds(ids);
        if (result <= 0) {
            throw new BusinessException(ErrorCodeEnum.OPERATION_FAILED, "批量删除物流信息失败");
        }
        LOGGER.info("批量删除物流信息成功，删除数量：{}", result);
    }

    /**
     * 签收物流（不校验用户身份）
     *
     * @param id 物流ID
     * @return 更新后的物流实体
     * @author IhaveBB
     * @date 2026/03/22
     */
    public Logistics signLogistics(Long id) {
        return signLogistics(id, null);
    }

    /**
     * 签收物流（可校验用户身份）
     * <p>
     * 签收后物流状态置为 2（已签收），关联订单状态联动为 3（已完成）。
     * </p>
     *
     * @param id            物流ID
     * @param currentUserId 当前操作用户ID，非 null 时校验订单归属
     * @return 更新后的物流实体
     * @author IhaveBB
     * @date 2026/03/22
     */
    public Logistics signLogistics(Long id, Long currentUserId) {
        Logistics logistics = logisticsMapper.selectById(id);
        if (logistics == null) {
            throw new BusinessException(ErrorCodeEnum.LOGISTICS_NOT_FOUND);
        }

        if (currentUserId != null) {
            Order order = orderMapper.selectById(logistics.getOrderId());
            if (order != null && !order.getUserId().equals(currentUserId)) {
                throw new BusinessException(ErrorCodeEnum.PERMISSION_DENIED, "无权限签收他人订单的物流");
            }
        }

        if (!Integer.valueOf(1).equals(logistics.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.LOGISTICS_STATUS_ERROR, "当前物流状态不允许签收");
        }

        logistics.setStatus(2);
        int result = logisticsMapper.updateById(logistics);
        if (result <= 0) {
            throw new BusinessException(ErrorCodeEnum.OPERATION_FAILED, "物流签收失败");
        }

        syncOrderStatus(logistics.getOrderId(), 2);
        LOGGER.info("物流签收成功，物流ID：{}", id);
        return logistics;
    }

    /**
     * 根据物流新状态同步关联订单状态
     * <p>
     * 查询 {@link #LOGISTICS_TO_ORDER_STATUS} 映射表，映射关系不存在时不做变更。
     * </p>
     *
     * @param orderId         订单ID
     * @param logisticsStatus 物流新状态
     */
    private void syncOrderStatus(Long orderId, Integer logisticsStatus) {
        Integer targetOrderStatus = LOGISTICS_TO_ORDER_STATUS.get(logisticsStatus);
        if (targetOrderStatus == null) {
            return;
        }
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            return;
        }
        order.setStatus(targetOrderStatus);
        orderMapper.updateById(order);
        LOGGER.info("物流状态变更，同步订单状态：订单ID={}，物流状态={}→订单状态={}",
                orderId, logisticsStatus, targetOrderStatus);
    }
}
