package com.nicebao.springboot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;
import com.nicebao.springboot.entity.Notice;

@Mapper
public interface NoticeMapper extends BaseMapper<Notice> {
}
