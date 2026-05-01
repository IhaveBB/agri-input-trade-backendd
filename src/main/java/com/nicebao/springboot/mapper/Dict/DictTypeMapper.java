package com.nicebao.springboot.mapper.Dict;



import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import com.nicebao.springboot.entity.Dict.SysDict;

@Mapper
public interface DictTypeMapper extends BaseMapper<SysDict> {
}
