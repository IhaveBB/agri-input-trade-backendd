package com.nicebao.springboot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import com.nicebao.springboot.entity.ProductAnimal;

/**
 * 商品适用动物关联 Mapper
 *
 * @author IhaveBB
 * @date 2026/03/29
 */
@Mapper
public interface ProductAnimalMapper extends BaseMapper<ProductAnimal> {
}
