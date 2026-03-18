package org.example.springboot.controller.Dict;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.springboot.common.Result;
import org.example.springboot.entity.Dict.SysDict;
import org.example.springboot.service.SysDictService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 字典类型管理接口
 *
 * @author IhaveBB
 * @date 2026/03/19
 */
@Tag(name = "字典类型管理接口")
@RestController
@RequestMapping("/sysdict")
public class SysDictController {

    @Autowired
    private SysDictService sysDictService;

    /**
     * 新增字典类型
     *
     * @param sysDict 字典类型实体
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @PostMapping("/save")
    @Operation(summary = "新增字典类型")
    public Result<?> save(@RequestBody @Valid SysDict sysDict) {
        sysDictService.add(sysDict);
        return Result.success();
    }

    /**
     * 根据ID删除字典类型
     *
     * @param id 字典类型ID
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @DeleteMapping("/deleteById/{id}")
    @Operation(summary = "根据ID删除字典类型")
    public Result<?> deleteById(@PathVariable Integer id) {
        sysDictService.deleteById(id);
        return Result.success();
    }

    /**
     * 批量删除字典类型
     *
     * @param idList ID列表
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @PostMapping("/deleteBatch")
    @Operation(summary = "批量删除字典类型")
    public Result<?> deleteBatch(@RequestBody List<Integer> idList) {
        sysDictService.deleteBatch(idList);
        return Result.success();
    }

    /**
     * 查询所有字典类型
     *
     * @return 字典类型列表
     * @author IhaveBB
     * @date 2026/03/19
     */
    @GetMapping("/findAll")
    @Operation(summary = "查询所有字典类型")
    public Result<?> findAll() {
        List<SysDict> sysDictList = sysDictService.findAll();
        return Result.success(sysDictList);
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
    @GetMapping("/findPage")
    @Operation(summary = "分页查询字典类型")
    public Result<?> findPage(@RequestParam Integer pageNum,
                              @RequestParam Integer pageSize,
                              @RequestParam(name = "dictTypeName", defaultValue = "") String dictTypeName) {
        Page<SysDict> sysDictPage = sysDictService.findPage(pageNum, pageSize, dictTypeName);
        return Result.success(sysDictPage);
    }
}
