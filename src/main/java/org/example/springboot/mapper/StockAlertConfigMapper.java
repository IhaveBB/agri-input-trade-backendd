package org.example.springboot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.springboot.entity.StockAlertConfig;

/**
 * 库存预警配置 Mapper
 *
 * @author IhaveBB
 * @date 2026/03/22
 */
@Mapper
public interface StockAlertConfigMapper extends BaseMapper<StockAlertConfig> {
}
