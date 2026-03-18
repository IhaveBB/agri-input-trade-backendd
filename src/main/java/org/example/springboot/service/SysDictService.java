package org.example.springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.springboot.entity.Dict.SysDict;
import org.example.springboot.enums.ErrorCodeEnum;
import org.example.springboot.exception.BusinessException;
import org.example.springboot.mapper.Dict.DictTypeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 字典类型服务类
 *
 * @author IhaveBB
 * @date 2026/03/19
 */
@Service
public class SysDictService {

    @Autowired
    private DictTypeMapper sysDictMapper;

    /**
     * 新增字典类型
     *
     * @param sysDict 字典类型实体
     * @author IhaveBB
     * @date 2026/03/19
     */
    public void add(SysDict sysDict) {
        QueryWrapper<SysDict> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("dict_type_code", sysDict.getDictTypeCode());
        List<Object> exitDictType = sysDictMapper.selectObjs(queryWrapper);
        if (exitDictType != null && !exitDictType.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.ALREADY_EXISTS, "该类型已存在");
        }
        int result = sysDictMapper.insert(sysDict);
        if (result <= 0) {
            throw new BusinessException(ErrorCodeEnum.ERROR, "保存失败");
        }
    }

    /**
     * 根据ID删除字典类型
     *
     * @param id 字典类型ID
     * @author IhaveBB
     * @date 2026/03/19
     */
    public void deleteById(Integer id) {
        boolean result = sysDictMapper.deleteById(id) > 0;
        if (!result) {
            throw new BusinessException(ErrorCodeEnum.ERROR, "删除失败");
        }
    }

    /**
     * 批量删除字典类型
     *
     * @param idList ID列表
     * @author IhaveBB
     * @date 2026/03/19
     */
    public void deleteBatch(List<Integer> idList) {
        boolean result = sysDictMapper.deleteBatchIds(idList) > 0;
        if (!result) {
            throw new BusinessException(ErrorCodeEnum.ERROR, "删除失败");
        }
    }

    /**
     * 查询所有字典类型
     *
     * @return 字典类型列表
     * @author IhaveBB
     * @date 2026/03/19
     */
    public List<SysDict> findAll() {
        QueryWrapper<SysDict> queryWrapper = new QueryWrapper<>();
        return sysDictMapper.selectList(queryWrapper);
    }

    /**
     * 分页查询字典类型
     *
     * @param pageNum      页码
     * @param pageSize     每页大小
     * @param dictTypeName 字典类型名称
     * @return 分页结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    public Page<SysDict> findPage(Integer pageNum, Integer pageSize, String dictTypeName) {
        Page<SysDict> page = new Page<>(pageNum, pageSize);
        QueryWrapper<SysDict> queryWrapper = new QueryWrapper<>();
        if (!dictTypeName.isEmpty()) {
            queryWrapper.like("dict_type_name", dictTypeName);
        }
        return sysDictMapper.selectPage(page, queryWrapper);
    }
}
