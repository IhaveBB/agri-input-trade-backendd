package com.nicebao.springboot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import com.nicebao.springboot.entity.EmailRecord;

/**
 * 邮件发送记录 Mapper
 *
 * @author IhaveBB
 * @date 2026/03/22
 */
@Mapper
public interface EmailRecordMapper extends BaseMapper<EmailRecord> {
}
