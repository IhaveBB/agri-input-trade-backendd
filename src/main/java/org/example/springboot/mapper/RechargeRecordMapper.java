package org.example.springboot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.springboot.entity.RechargeRecord;

/**
 * 充值记录 Mapper
 *
 * @author IhaveBB
 * @date 2026/03/24
 */
@Mapper
public interface RechargeRecordMapper extends BaseMapper<RechargeRecord> {
}