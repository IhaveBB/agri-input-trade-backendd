package com.nicebao.springboot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;
import com.nicebao.springboot.entity.Menu;

@Mapper
public interface MenuMapper extends BaseMapper<Menu> {
}
