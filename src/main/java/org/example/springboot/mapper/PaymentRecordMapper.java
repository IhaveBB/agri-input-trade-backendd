package org.example.springboot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.example.springboot.entity.PaymentRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 支付记录 Mapper
 *
 * @author IhaveBB
 * @date 2026/03/24
 */
@Mapper
public interface PaymentRecordMapper extends BaseMapper<PaymentRecord> {
}
