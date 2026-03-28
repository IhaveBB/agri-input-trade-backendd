package org.example.springboot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.example.springboot.entity.BalanceRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 余额变动记录 Mapper
 *
 * @author IhaveBB
 * @date 2026/03/24
 */
@Mapper
public interface BalanceRecordMapper extends BaseMapper<BalanceRecord> {
}
