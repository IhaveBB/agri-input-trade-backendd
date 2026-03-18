package org.example.springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.springboot.entity.Dict.DictItem;
import org.example.springboot.enums.ErrorCodeEnum;
import org.example.springboot.exception.BusinessException;
import org.example.springboot.mapper.Dict.DictItemMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 字典项服务类
 *
 * @author IhaveBB
 * @date 2026/03/19
 */
@Service
public class DictItemService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DictItemService.class);

    @Autowired
    private DictItemMapper dictItemMapper;

    /**
     * 新增字典项
     *
     * @param dictItem 字典项实体
     * @author IhaveBB
     * @date 2026/03/19
     */
    public void add(DictItem dictItem) {
        QueryWrapper<DictItem> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("item_key", dictItem.getItemKey());

        List<Object> exitDictItem = dictItemMapper.selectObjs(queryWrapper);
        if (!exitDictItem.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.ALREADY_EXISTS, "该项目已存在");
        }

        int result = dictItemMapper.insert(dictItem);
        if (result <= 0) {
            throw new BusinessException(ErrorCodeEnum.ERROR, "保存失败");
        }
    }

    /**
     * 更新字典项
     *
     * @param id       字典项ID
     * @param dictItem 字典项实体
     * @author IhaveBB
     * @date 2026/03/19
     */
    public void update(int id, DictItem dictItem) {
        dictItem.setId(id);
        LOGGER.info("UPDATE dictItem:" + dictItem);
        int res = dictItemMapper.updateById(dictItem);
        if (res <= 0) {
            throw new BusinessException(ErrorCodeEnum.ERROR, "修改失败");
        }
    }

    /**
     * 根据字典类型编码查询字典项列表
     *
     * @param code 字典类型编码
     * @return 字典项列表
     * @author IhaveBB
     * @date 2026/03/19
     */
    public List<DictItem> getByTypeCode(String code) {
        LOGGER.info("itemList CODE:" + code);
        DictItem dictItem = new DictItem();
        dictItem.setDictTypeCode(code);
        QueryWrapper<DictItem> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("dict_type_code", dictItem.getDictTypeCode());
        List<DictItem> itemList = dictItemMapper.selectList(queryWrapper);
        LOGGER.info("LOADING itemList:" + itemList);
        return itemList;
    }

    /**
     * 根据ID删除字典项
     *
     * @param id 字典项ID
     * @author IhaveBB
     * @date 2026/03/19
     */
    public void deleteById(Integer id) {
        boolean result = dictItemMapper.deleteById(id) > 0;
        if (!result) {
            throw new BusinessException(ErrorCodeEnum.ERROR, "删除失败");
        }
    }

    /**
     * 批量删除字典项
     *
     * @param idList ID列表
     * @author IhaveBB
     * @date 2026/03/19
     */
    public void deleteBatch(List<Integer> idList) {
        boolean result = dictItemMapper.deleteByIds(idList) > 0;
        if (!result) {
            throw new BusinessException(ErrorCodeEnum.ERROR, "删除失败");
        }
    }

    /**
     * 查询所有字典项
     *
     * @return 字典项列表
     * @author IhaveBB
     * @date 2026/03/19
     */
    public List<DictItem> findAll() {
        QueryWrapper<DictItem> queryWrapper = new QueryWrapper<>();
        return dictItemMapper.selectList(queryWrapper);
    }

    /**
     * 分页查询字典项
     *
     * @param currentPage 当前页
     * @param size        每页大小
     * @param code        字典类型编码
     * @param itemKey     字典项key
     * @return 分页结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    public Page<DictItem> findPage(Integer currentPage, Integer size, String code, String itemKey) {
        Page<DictItem> page = new Page<>(currentPage, size);
        DictItem dictItem = new DictItem();
        dictItem.setDictTypeCode(code);
        dictItem.setItemKey(itemKey);
        QueryWrapper<DictItem> queryWrapper = new QueryWrapper<>();
        if (!code.isEmpty()) {
            queryWrapper.eq("dict_type_code", dictItem.getDictTypeCode());
        }
        if (!itemKey.isEmpty()) {
            queryWrapper.like("item_key", dictItem.getItemKey());
        }
        return dictItemMapper.selectPage(page, queryWrapper);
    }
}
