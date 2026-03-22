package org.example.springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.springboot.entity.EmailRecord;
import org.example.springboot.entity.Product;
import org.example.springboot.entity.User;
import org.example.springboot.enums.EmailFailCode;
import org.example.springboot.enums.EmailStatus;
import org.example.springboot.enums.EmailType;
import org.example.springboot.enums.RelatedType;
import org.example.springboot.mapper.EmailRecordMapper;
import org.example.springboot.mapper.ProductMapper;
import org.example.springboot.mapper.UserMapper;
import org.example.springboot.service.alert.StockAlertEvaluator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 邮件发送记录服务
 * 负责邮件发送、记录管理、失败重试
 *
 * @author IhaveBB
 * @date 2026/03/22
 */
@Slf4j
@Service
public class EmailRecordService {

    @Resource
    private EmailRecordMapper emailRecordMapper;

    @Resource
    private JavaMailSender javaMailSender;

    @Resource
    private UserMapper userMapper;

    @Resource
    private ProductMapper productMapper;

    @Resource
    private StockAlertConfigService stockAlertConfigService;

    @Value("${user.fromEmail}")
    private String fromEmail;

    /**
     * 最大重试次数
     */
    private static final int MAX_RETRY = 3;

    /**
     * 重试间隔（分钟）
     */
    private static final int RETRY_INTERVAL_MINUTES = 1;

    /**
     * 发送库存预警邮件（异步）
     *
     * @param alertResult 预警评估结果
     * @author IhaveBB
     * @date 2026/03/22
     */
    @Async
    @Transactional
    public void sendStockAlertEmailAsync(StockAlertEvaluator.AlertResult alertResult) {
        sendStockAlertEmail(alertResult);
    }

    /**
     * 发送库存预警邮件
     *
     * @param alertResult 预警评估结果
     * @return 邮件记录
     * @author IhaveBB
     * @date 2026/03/22
     */
    @Transactional
    public EmailRecord sendStockAlertEmail(StockAlertEvaluator.AlertResult alertResult) {
        // 构建邮件内容
        String subject = String.format("【库存预警】%s，您店铺中的商品库存不足", alertResult.getMerchantName());
        String content = buildStockAlertContent(alertResult);

        // 创建邮件记录
        EmailRecord record = new EmailRecord();
        record.setRecipientEmail(alertResult.getMerchantEmail());
        record.setRecipientName(alertResult.getMerchantName());
        record.setSubject(subject);
        record.setContent(content);
        record.setEmailType(EmailType.STOCK_ALERT.getCode());
        record.setStatus(EmailStatus.PENDING.getCode());
        record.setRetryCount(0);
        record.setMaxRetry(MAX_RETRY);
        record.setRelatedType(RelatedType.MERCHANT.getCode());
        record.setRelatedId(alertResult.getMerchantId());
        record.setProductId(alertResult.getProductId());

        emailRecordMapper.insert(record);

        // 发送邮件
        doSendEmail(record);

        // 更新商品的上次预警时间
        stockAlertConfigService.updateLastAlertTime(alertResult.getProductId(), alertResult.getCurrentStock());

        return record;
    }

    /**
     * 构建库存预警邮件内容
     *
     * @param alertResult 预警评估结果
     * @return 邮件内容
     * @author IhaveBB
     * @date 2026/03/22
     */
    private String buildStockAlertContent(StockAlertEvaluator.AlertResult alertResult) {
        StringBuilder content = new StringBuilder();
        content.append("尊敬的 ").append(alertResult.getMerchantName()).append("：\n\n");
        content.append("您好！\n\n");
        content.append("您店铺中的商品「").append(alertResult.getProductName()).append("」库存不足，请及时补货。\n\n");
        content.append("【商品信息】\n");
        content.append("- 商品名称：").append(alertResult.getProductName()).append("\n");
        content.append("- 当前库存：").append(alertResult.getCurrentStock()).append(" 件\n");
        content.append("\n【预警信息】\n");
        content.append("- ").append(alertResult.getAlertMessage()).append("\n");
        content.append("\n【建议操作】\n");
        content.append("- ").append(alertResult.getSuggestion()).append("\n");
        content.append("\n如已补货，请及时更新库存信息，系统将自动停止重复提醒。\n\n");
        content.append("此邮件为系统自动发送，请勿直接回复。\n\n");
        content.append("---\n");
        content.append("农资采销系统\n");
        content.append(LocalDateTime.now().toString()).append("\n");

        return content.toString();
    }

    /**
     * 发送验证码邮件（带记录）
     *
     * @param email     收件人邮箱
     * @param code      验证码
     * @param subject   邮件主题
     * @param content   邮件内容
     * @param userId    用户ID
     * @author IhaveBB
     * @date 2026/03/22
     */
    @Transactional
    public void sendVerificationEmail(String email, String code, String subject, String content, Long userId) {
        // 创建邮件记录
        EmailRecord record = new EmailRecord();
        record.setRecipientEmail(email);
        record.setSubject(subject);
        record.setContent(content);
        record.setEmailType(EmailType.VERIFICATION.getCode());
        record.setStatus(EmailStatus.PENDING.getCode());
        record.setRetryCount(0);
        record.setMaxRetry(MAX_RETRY);
        record.setRelatedType(RelatedType.USER.getCode());
        record.setRelatedId(userId);

        emailRecordMapper.insert(record);

        // 发送邮件
        doSendEmail(record);
    }

    /**
     * 执行邮件发送
     *
     * @param record 邮件记录
     * @author IhaveBB
     * @date 2026/03/22
     */
    @Transactional
    public void doSendEmail(EmailRecord record) {
        try {
            record.setStatus(EmailStatus.SENDING.getCode());
            emailRecordMapper.updateById(record);

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(record.getRecipientEmail());
            message.setSubject(record.getSubject());
            message.setText(record.getContent());

            javaMailSender.send(message);

            // 发送成功
            record.setStatus(EmailStatus.SUCCESS.getCode());
            emailRecordMapper.updateById(record);

            log.info("邮件发送成功：id={}, email={}", record.getId(), record.getRecipientEmail());

        } catch (Exception e) {
            handleSendFailure(record, e);
        }
    }

    /**
     * 处理发送失败
     *
     * @param record 邮件记录
     * @param e       异常
     * @author IhaveBB
     * @date 2026/03/22
     */
    private void handleSendFailure(EmailRecord record, Exception e) {
        log.error("邮件发送失败：id={}, error={}", record.getId(), e.getMessage());

        EmailFailCode failCode = EmailFailCode.fromErrorMessage(e.getMessage());
        record.setFailCode(failCode.getCode());
        record.setFailReason(e.getMessage());

        if (failCode.isRetryable() && record.getRetryCount() < record.getMaxRetry()) {
            // 可重试失败
            record.setStatus(EmailStatus.RETRYABLE_FAIL.getCode());
            record.setRetryCount(record.getRetryCount() + 1);
            record.setNextRetryTime(LocalDateTime.now().plusMinutes(RETRY_INTERVAL_MINUTES));
        } else {
            // 永久失败
            record.setStatus(EmailStatus.PERMANENT_FAIL.getCode());
        }

        emailRecordMapper.updateById(record);
    }

    /**
     * 重试发送失败的邮件
     *
     * @return 重试的邮件数量
     * @author IhaveBB
     * @date 2026/03/22
     */
    @Transactional
    public int retryFailedEmails() {
        // 查询需要重试的邮件
        List<EmailRecord> retryList = emailRecordMapper.selectList(
                new LambdaQueryWrapper<EmailRecord>()
                        .eq(EmailRecord::getStatus, EmailStatus.RETRYABLE_FAIL.getCode())
                        .le(EmailRecord::getNextRetryTime, LocalDateTime.now())
        );

        int successCount = 0;
        for (EmailRecord record : retryList) {
            try {
                doSendEmail(record);
                if (EmailStatus.SUCCESS.getCode().equals(record.getStatus())) {
                    successCount++;
                }
            } catch (Exception e) {
                log.error("重试邮件发送失败：id={}", record.getId(), e);
            }
        }

        return successCount;
    }

    /**
     * 手动重试指定邮件
     *
     * @param recordId 邮件记录ID
     * @return 是否重试成功
     * @author IhaveBB
     * @date 2026/03/22
     */
    @Transactional
    public boolean manualRetry(Long recordId) {
        EmailRecord record = emailRecordMapper.selectById(recordId);
        if (record == null) {
            return false;
        }

        if (EmailStatus.RETRYABLE_FAIL.getCode().equals(record.getStatus()) ||
            EmailStatus.PERMANENT_FAIL.getCode().equals(record.getStatus())) {

            // 重置状态，保留重试次数（手动重试也要计数）
            record.setStatus(EmailStatus.PENDING.getCode());
            record.setFailCode(null);
            record.setFailReason(null);
            emailRecordMapper.updateById(record);

            // 使用手动重试的发送方法
            doSendEmailWithManualRetryCount(record);
            return EmailStatus.SUCCESS.getCode().equals(record.getStatus());
        }

        return false;
    }

    /**
     * 执行邮件发送（手动重试，单独计数）
     *
     * @param record 邮件记录
     * @author IhaveBB
     * @date 2026/03/22
     */
    @Transactional
    public void doSendEmailWithManualRetryCount(EmailRecord record) {
        try {
            record.setStatus(EmailStatus.SENDING.getCode());
            emailRecordMapper.updateById(record);

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(record.getRecipientEmail());
            message.setSubject(record.getSubject());
            message.setText(record.getContent());

            javaMailSender.send(message);

            // 发送成功
            record.setStatus(EmailStatus.SUCCESS.getCode());
            emailRecordMapper.updateById(record);

            log.info("邮件发送成功：id={}, email={}", record.getId(), record.getRecipientEmail());

        } catch (Exception e) {
            // 手动重试失败，增加重试次数
            record.setRetryCount(record.getRetryCount() + 1);

            EmailFailCode failCode = EmailFailCode.fromErrorMessage(e.getMessage());
            record.setFailCode(failCode.getCode());
            record.setFailReason(e.getMessage());

            if (failCode.isRetryable() && record.getRetryCount() < record.getMaxRetry()) {
                record.setStatus(EmailStatus.RETRYABLE_FAIL.getCode());
                record.setNextRetryTime(LocalDateTime.now().plusMinutes(RETRY_INTERVAL_MINUTES));
            } else {
                record.setStatus(EmailStatus.PERMANENT_FAIL.getCode());
            }

            emailRecordMapper.updateById(record);
            log.error("邮件发送失败：id={}, retryCount={}, error={}", record.getId(), record.getRetryCount(), e.getMessage());
        }
    }

    /**
     * 分页查询邮件记录（管理员）
     *
     * @param emailType 邮件类型（可选）
     * @param status    状态（可选）
     * @param pageNum   页码
     * @param pageSize  每页大小
     * @return 分页结果
     * @author IhaveBB
     * @date 2026/03/22
     */
    public Page<EmailRecord> getEmailRecordPage(String emailType, String status, int pageNum, int pageSize) {
        LambdaQueryWrapper<EmailRecord> wrapper = new LambdaQueryWrapper<>();

        if (emailType != null && !emailType.isBlank()) {
            wrapper.eq(EmailRecord::getEmailType, emailType);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(EmailRecord::getStatus, status);
        }

        wrapper.orderByDesc(EmailRecord::getCreatedAt);

        return emailRecordMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    /**
     * 分页查询商户的邮件记录
     *
     * @param merchantId 商户ID
     * @param emailType  邮件类型（可选）
     * @param pageNum    页码
     * @param pageSize   每页大小
     * @return 分页结果
     * @author IhaveBB
     * @date 2026/03/22
     */
    public Page<EmailRecord> getMerchantEmailRecordPage(Long merchantId, String emailType, int pageNum, int pageSize) {
        LambdaQueryWrapper<EmailRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EmailRecord::getRelatedType, RelatedType.MERCHANT.getCode())
               .eq(EmailRecord::getRelatedId, merchantId);

        if (emailType != null && !emailType.isBlank()) {
            wrapper.eq(EmailRecord::getEmailType, emailType);
        }

        wrapper.orderByDesc(EmailRecord::getCreatedAt);

        return emailRecordMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    /**
     * 获取邮件发送统计
     *
     * @return 统计数据
     * @author IhaveBB
     * @date 2026/03/22
     */
    public EmailStats getEmailStats() {
        EmailStats stats = new EmailStats();

        // 今日发送总数
        LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        Long todayTotal = emailRecordMapper.selectCount(
                new LambdaQueryWrapper<EmailRecord>()
                        .ge(EmailRecord::getCreatedAt, todayStart)
        );
        stats.setTodayTotal(todayTotal != null ? todayTotal.intValue() : 0);

        // 今日成功数
        Long todaySuccess = emailRecordMapper.selectCount(
                new LambdaQueryWrapper<EmailRecord>()
                        .eq(EmailRecord::getStatus, EmailStatus.SUCCESS.getCode())
                        .ge(EmailRecord::getCreatedAt, todayStart)
        );
        stats.setTodaySuccess(todaySuccess != null ? todaySuccess.intValue() : 0);

        // 今日失败数
        Long todayFail = emailRecordMapper.selectCount(
                new LambdaQueryWrapper<EmailRecord>()
                        .in(EmailRecord::getStatus, EmailStatus.RETRYABLE_FAIL.getCode(), EmailStatus.PERMANENT_FAIL.getCode())
                        .ge(EmailRecord::getCreatedAt, todayStart)
        );
        stats.setTodayFail(todayFail != null ? todayFail.intValue() : 0);

        // 待重试数
        Long pendingRetry = emailRecordMapper.selectCount(
                new LambdaQueryWrapper<EmailRecord>()
                        .eq(EmailRecord::getStatus, EmailStatus.RETRYABLE_FAIL.getCode())
        );
        stats.setPendingRetry(pendingRetry != null ? pendingRetry.intValue() : 0);

        return stats;
    }

    /**
     * 根据ID获取邮件记录
     *
     * @param id 邮件记录ID
     * @return 邮件记录
     * @author IhaveBB
     * @date 2026/03/22
     */
    public EmailRecord getEmailRecordById(Long id) {
        return emailRecordMapper.selectById(id);
    }

    /**
     * 邮件发送统计
     */
    public static class EmailStats {
        private int todayTotal;
        private int todaySuccess;
        private int todayFail;
        private int pendingRetry;

        public int getTodayTotal() {
            return todayTotal;
        }

        public void setTodayTotal(int todayTotal) {
            this.todayTotal = todayTotal;
        }

        public int getTodaySuccess() {
            return todaySuccess;
        }

        public void setTodaySuccess(int todaySuccess) {
            this.todaySuccess = todaySuccess;
        }

        public int getTodayFail() {
            return todayFail;
        }

        public void setTodayFail(int todayFail) {
            this.todayFail = todayFail;
        }

        public int getPendingRetry() {
            return pendingRetry;
        }

        public void setPendingRetry(int pendingRetry) {
            this.pendingRetry = pendingRetry;
        }
    }
}
