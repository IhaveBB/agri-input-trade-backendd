package org.example.springboot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.springboot.common.Result;
import org.example.springboot.entity.EmailRecord;
import org.example.springboot.service.EmailRecordService;
import org.example.springboot.service.EmailRecordService.EmailStats;
import org.example.springboot.util.UserContext;
import org.springframework.web.bind.annotation.*;

/**
 * 邮件发送记录控制器
 *
 * @author IhaveBB
 * @date 2026/03/22
 */
@Slf4j
@Tag(name = "邮件记录", description = "邮件发送记录管理接口")
@RestController
@RequestMapping("/email-record")
public class EmailRecordController {

    @Resource
    private EmailRecordService emailRecordService;

    /**
     * 获取邮件发送记录列表（管理员）
     *
     * @param emailType 邮件类型（可选）
     * @param status    状态（可选）
     * @param pageNum   页码
     * @param pageSize  每页大小
     * @return 邮件记录分页列表
     * @author IhaveBB
     * @date 2026/03/22
     */
    @Operation(summary = "获取邮件记录列表（管理员）", description = "管理员查看所有邮件发送记录")
    @GetMapping("/list")
    public Result<Page<EmailRecord>> getEmailRecordList(
            @Parameter(description = "邮件类型") @RequestParam(required = false) String emailType,
            @Parameter(description = "状态") @RequestParam(required = false) String status,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int pageSize) {
        Page<EmailRecord> page = emailRecordService.getEmailRecordPage(emailType, status, pageNum, pageSize);
        return Result.success(page);
    }

    /**
     * 获取商户的邮件发送记录
     *
     * @param emailType 邮件类型（可选）
     * @param pageNum   页码
     * @param pageSize  每页大小
     * @return 邮件记录分页列表
     * @author IhaveBB
     * @date 2026/03/22
     */
    @Operation(summary = "获取商户邮件记录", description = "商户查看自己的邮件发送记录")
    @GetMapping("/merchant")
    public Result<Page<EmailRecord>> getMerchantEmailRecordList(
            @Parameter(description = "邮件类型") @RequestParam(required = false) String emailType,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int pageSize) {
        Long merchantId = UserContext.requireUserId();
        Page<EmailRecord> page = emailRecordService.getMerchantEmailRecordPage(merchantId, emailType, pageNum, pageSize);
        return Result.success(page);
    }

    /**
     * 获取邮件详情
     *
     * @param id 邮件记录ID
     * @return 邮件记录详情
     * @author IhaveBB
     * @date 2026/03/22
     */
    @Operation(summary = "获取邮件详情", description = "查看指定邮件记录的详细信息")
    @GetMapping("/{id}")
    public Result<EmailRecord> getEmailRecordDetail(
            @Parameter(description = "邮件记录ID") @PathVariable Long id) {
        EmailRecord record = emailRecordService.getEmailRecordById(id);
        return Result.success(record);
    }

    /**
     * 手动重试发送邮件
     *
     * @param id 邮件记录ID
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/22
     */
    @Operation(summary = "手动重试", description = "手动重试发送失败的邮件")
    @PostMapping("/{id}/retry")
    public Result<Void> manualRetry(
            @Parameter(description = "邮件记录ID") @PathVariable Long id) {
        boolean success = emailRecordService.manualRetry(id);
        if (success) {
            return Result.success();
        } else {
            return Result.error("-1", "重试失败");
        }
    }

    /**
     * 获取邮件发送统计
     *
     * @return 统计数据
     * @author IhaveBB
     * @date 2026/03/22
     */
    @Operation(summary = "邮件发送统计", description = "获取今日邮件发送统计数据")
    @GetMapping("/stats")
    public Result<EmailStats> getEmailStats() {
        EmailStats stats = emailRecordService.getEmailStats();
        return Result.success(stats);
    }
}
