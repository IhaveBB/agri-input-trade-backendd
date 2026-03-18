package org.example.springboot.controller.Dict;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.springboot.common.Result;
import org.example.springboot.entity.Dict.DictItem;
import org.example.springboot.service.DictItemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 字典项管理接口
 *
 * @author IhaveBB
 * @date 2026/03/19
 */
@Tag(name = "字典项管理接口")
@RestController
@RequestMapping("/dictitem")
public class DictItemController {
    private static final Logger LOGGER = LoggerFactory.getLogger(DictItemController.class);

    @Autowired
    private DictItemService dictItemService;

    /**
     * 新增字典项
     *
     * @param dictItem 字典项实体
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @PostMapping("/save")
    @Operation(summary = "新增字典项")
    public Result<?> save(@RequestBody DictItem dictItem) {
        dictItemService.add(dictItem);
        return Result.success();
    }

    /**
     * 更新字典项
     *
     * @param id       字典项ID
     * @param dictItem 字典项实体
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "更新字典项")
    @PutMapping("/{id}")
    public Result<?> update(@PathVariable int id, @RequestBody DictItem dictItem) {
        dictItemService.update(id, dictItem);
        return Result.success();
    }

    /**
     * 根据字典类型编码查询字典项列表
     *
     * @param code 字典类型编码
     * @return 字典项列表
     * @author IhaveBB
     * @date 2026/03/19
     */
    @GetMapping("/findByType")
    @Operation(summary = "根据字典类型编码查询字典项列表")
    public Result<?> getByTypeCode(@RequestParam(defaultValue = "") String code) {
        List<DictItem> itemList = dictItemService.getByTypeCode(code);
        return Result.success(itemList);
    }

    /**
     * 根据ID删除字典项
     *
     * @param id 字典项ID
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @DeleteMapping("/deleteById/{id}")
    @Operation(summary = "根据ID删除字典项")
    public Result<?> deleteById(@PathVariable Integer id) {
        dictItemService.deleteById(id);
        return Result.success();
    }

    /**
     * 批量删除字典项
     *
     * @param idList ID列表
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @DeleteMapping("/deleteBatch")
    @Operation(summary = "批量删除字典项")
    public Result<?> deleteBatch(@RequestParam List<Integer> idList) {
        dictItemService.deleteBatch(idList);
        return Result.success();
    }

    /**
     * 查询所有字典项
     *
     * @return 字典项列表
     * @author IhaveBB
     * @date 2026/03/19
     */
    @GetMapping("/findAll")
    @Operation(summary = "查询所有字典项")
    public Result<?> findAll() {
        List<DictItem> dictItemList = dictItemService.findAll();
        return Result.success(dictItemList);
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
    @GetMapping("/findPage")
    @Operation(summary = "分页查询字典项")
    public Result<?> findPage(@RequestParam Integer currentPage,
                              @RequestParam Integer size,
                              @RequestParam String code,
                              @RequestParam(name = "itemKey", defaultValue = "") String itemKey) {
        Page<DictItem> dictItemPage = dictItemService.findPage(currentPage, size, code, itemKey);
        return Result.success(dictItemPage);
    }
}
