package org.example.springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.springboot.entity.BalanceRecord;
import org.example.springboot.entity.PaymentRecord;
import org.example.springboot.entity.RechargeRecord;
import org.example.springboot.entity.User;
import org.example.springboot.enums.ErrorCodeEnum;
import org.example.springboot.exception.BusinessException;
import org.example.springboot.mapper.BalanceRecordMapper;
import org.example.springboot.mapper.PaymentRecordMapper;
import org.example.springboot.mapper.RechargeRecordMapper;
import org.example.springboot.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * 余额服务类
 * 处理余额查询、充值、流水记录等业务
 *
 * @author IhaveBB
 * @date 2026/03/24
 */
@Service
public class BalanceService {
    private static final Logger LOGGER = LoggerFactory.getLogger(BalanceService.class);

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private BalanceRecordMapper balanceRecordMapper;

    @Autowired
    private PaymentRecordMapper paymentRecordMapper;

    @Autowired
    private RechargeRecordMapper rechargeRecordMapper;

    /**
     * 获取用户余额
     *
     * @param userId 用户ID
     * @return 用户余额
     * @author IhaveBB
     * @date 2026/03/24
     */
    public BigDecimal getUserBalance(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCodeEnum.USER_NOT_FOUND);
        }
        return user.getBalance() != null ? user.getBalance() : BigDecimal.ZERO;
    }

    /**
     * 管理员为用户充值余额
     * <p>
     * 只有管理员可以调用此方法，会增加用户余额并记录流水
     * </p>
     *
     * @param userId 用户ID
     * @param amount 充值金额
     * @param adminId 管理员ID
     * @param remark 备注
     * @return 充值后的余额
     * @author IhaveBB
     * @date 2026/03/24
     */
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal rechargeBalance(Long userId, BigDecimal amount, Long adminId, String remark) {
        // 参数校验
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "充值金额必须大于0");
        }

        // 检查用户是否存在
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCodeEnum.USER_NOT_FOUND);
        }

        // 增加余额
        BigDecimal balanceBefore = user.getBalance() != null ? user.getBalance() : BigDecimal.ZERO;
        BigDecimal balanceAfter = balanceBefore.add(amount);
        user.setBalance(balanceAfter);
        int result = userMapper.updateById(user);
        if (result <= 0) {
            throw new BusinessException(ErrorCodeEnum.ERROR, "充值失败，更新余额失败");
        }

        // 记录余额变动
        BalanceRecord balanceRecord = new BalanceRecord();
        balanceRecord.setUserId(userId);
        balanceRecord.setAmount(amount);
        balanceRecord.setBalanceBefore(balanceBefore);
        balanceRecord.setBalanceAfter(balanceAfter);
        balanceRecord.setType(1); // 充值
        balanceRecord.setRemark(remark != null ? remark : "管理员充值");
        balanceRecord.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        balanceRecordMapper.insert(balanceRecord);

        LOGGER.info("余额充值成功，用户ID：{}，充值金额：{}，操作管理员ID：{}，当前余额：{}",
                userId, amount, adminId, balanceAfter);

        return balanceAfter;
    }

    /**
     * 分页查询用户的余额变动记录
     *
     * @param userId      用户ID
     * @param currentPage 当前页码
     * @param size        每页条数
     * @return 余额变动记录分页列表
     * @author IhaveBB
     * @date 2026/03/24
     */
    public Page<BalanceRecord> getBalanceRecords(Long userId, Integer currentPage, Integer size) {
        LambdaQueryWrapper<BalanceRecord> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(BalanceRecord::getUserId, userId)
                .orderByDesc(BalanceRecord::getCreatedAt);

        Page<BalanceRecord> page = new Page<>(currentPage, size);
        return balanceRecordMapper.selectPage(page, queryWrapper);
    }

    /**
     * 分页查询用户的支付记录
     *
     * @param userId      用户ID
     * @param currentPage 当前页码
     * @param size        每页条数
     * @return 支付记录分页列表
     * @author IhaveBB
     * @date 2026/03/24
     */
    public Page<PaymentRecord> getPaymentRecords(Long userId, Integer currentPage, Integer size) {
        LambdaQueryWrapper<PaymentRecord> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(PaymentRecord::getUserId, userId)
                .orderByDesc(PaymentRecord::getCreatedAt);

        Page<PaymentRecord> page = new Page<>(currentPage, size);
        return paymentRecordMapper.selectPage(page, queryWrapper);
    }

    /**
     * 创建余额充值订单
     * <p>
     * 用户使用支付宝充值到余额，创建待支付的充值记录
     * </p>
     *
     * @param userId 用户ID
     * @param amount 充值金额
     * @return 创建的充值记录
     * @author IhaveBB
     * @date 2026/03/24
     */
    @Transactional(rollbackFor = Exception.class)
    public RechargeRecord createRechargeOrder(Long userId, BigDecimal amount) {
        // 参数校验
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "充值金额必须大于0");
        }

        // 检查用户是否存在
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCodeEnum.USER_NOT_FOUND);
        }

        // 创建充值记录
        RechargeRecord rechargeRecord = new RechargeRecord();
        rechargeRecord.setRechargeNo(generateRechargeNo(userId));
        rechargeRecord.setUserId(userId);
        rechargeRecord.setAmount(amount);
        rechargeRecord.setStatus(0); // 待支付
        rechargeRecord.setCreatedAt(new Timestamp(System.currentTimeMillis()));

        rechargeRecordMapper.insert(rechargeRecord);

        LOGGER.info("创建充值订单成功，用户ID：{}，充值单号：{}，金额：{}",
                userId, rechargeRecord.getRechargeNo(), amount);

        return rechargeRecord;
    }

    /**
     * 分页查询用户的充值记录
     *
     * @param userId      用户ID
     * @param currentPage 当前页码
     * @param size        每页条数
     * @return 充值记录分页列表
     * @author IhaveBB
     * @date 2026/03/24
     */
    public Page<RechargeRecord> getRechargeRecords(Long userId, Integer currentPage, Integer size) {
        LambdaQueryWrapper<RechargeRecord> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(RechargeRecord::getUserId, userId)
                .orderByDesc(RechargeRecord::getCreatedAt);

        Page<RechargeRecord> page = new Page<>(currentPage, size);
        return rechargeRecordMapper.selectPage(page, queryWrapper);
    }

    /**
     * 根据ID查询充值记录
     *
     * @param rechargeId 充值记录ID
     * @return 充值记录
     * @author IhaveBB
     * @date 2026/03/24
     */
    public RechargeRecord getRechargeRecordById(Long rechargeId) {
        RechargeRecord rechargeRecord = rechargeRecordMapper.selectById(rechargeId);
        if (rechargeRecord == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "充值记录不存在");
        }
        return rechargeRecord;
    }

    /**
     * 根据ID查询充值记录并校验用户权限
     *
     * @param rechargeId 充值记录ID
     * @param userId 当前用户ID
     * @return 充值记录
     * @author IhaveBB
     * @date 2026/03/24
     */
    public RechargeRecord getRechargeRecordById(Long rechargeId, Long userId) {
        RechargeRecord rechargeRecord = rechargeRecordMapper.selectById(rechargeId);
        if (rechargeRecord == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "充值记录不存在");
        }
        if (!rechargeRecord.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权操作该充值记录");
        }
        return rechargeRecord;
    }

    /**
     * 生成充值单号
     * <p>
     * 格式：RECHARGE_ + 时间戳 + 用户ID后4位 + 4位随机数
     * </p>
     *
     * @param userId 用户ID
     * @return 充值单号
     */
    private String generateRechargeNo(Long userId) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String userSuffix = String.format("%04d", userId % 10000);
        String random = String.format("%04d", (int) (Math.random() * 10000));
        return "RECHARGE_" + timestamp + userSuffix + random;
    }
}
