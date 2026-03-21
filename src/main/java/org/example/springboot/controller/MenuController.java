package org.example.springboot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.example.springboot.annotation.RequiresRole;
import org.example.springboot.common.Result;
import org.example.springboot.entity.Menu;
import org.example.springboot.service.MenuService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 菜单管理控制器
 * 提供菜单的增删改查功能
 *
 * @author IhaveBB
 * @date 2026/03/19
 */
@Tag(name = "菜单管理接口")
@RestController
@RequestMapping("/menu")
public class MenuController {
    @Resource
    private MenuService menuService;

    public static final Logger LOGGER = LoggerFactory.getLogger(MenuController.class);

    /**
     * 增加菜单
     * 权限：只有管理员
     *
     * @param menu 菜单实体
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "增加菜单")
    @RequiresRole("ADMIN")
    @PostMapping("/save")
    public Result<?> save(@RequestBody Menu menu) {
        menuService.save(menu);
        return Result.success();
    }

    /**
     * 更新菜单
     * 权限：只有管理员
     *
     * @param id   菜单ID
     * @param menu 菜单实体
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "更新菜单")
    @RequiresRole("ADMIN")
    @PutMapping("/{id}")
    public Result<?> update(@PathVariable int id, @RequestBody Menu menu) {
        menuService.update(id, menu);
        return Result.success();
    }

    /**
     * 批量删除菜单
     * 权限：只有管理员
     *
     * @param ids 菜单ID列表
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "批量删除菜单")
    @RequiresRole("ADMIN")
    @DeleteMapping("/deleteBatch")
    public Result<?> deleteBatch(@RequestParam List<Integer> ids) {
        menuService.deleteBatch(ids);
        return Result.success();
    }

    /**
     * 根据ID删除菜单项
     * 权限：只有管理员
     *
     * @param id 菜单ID
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "根据id删除菜单项")
    @RequiresRole("ADMIN")
    @DeleteMapping("/deleteById/{id}")
    public Result<?> deleteById(@PathVariable("id") Integer id) {
        menuService.deleteById(id);
        return Result.success("删除成功");
    }

    /**
     * 根据ID获取菜单
     * 权限：需要登录
     *
     * @param id 菜单ID
     * @return 菜单详情
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "根据id获取菜单")
    @RequiresRole
    @GetMapping("/find/{id}")
    public Result<?> findById(@PathVariable("id") Integer id) {
        return Result.success(menuService.findById(id));
    }

    /**
     * 查询所有菜单
     * 权限：需要登录
     *
     * @return 菜单列表
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "查询菜单")
    @RequiresRole
    @GetMapping("/findAll")
    public Result<?> findAll() {
        return Result.success(menuService.findAll());
    }

    /**
     * 分页查询一级菜单
     * 权限：需要登录
     *
     * @param currentPage 当前页
     * @param size        每页大小
     * @return 分页菜单列表
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "分页查询一级菜单")
    @RequiresRole
    @GetMapping("/findParentMenus")
    public Result<?> findParentMenus(@RequestParam Integer currentPage,
                                     @RequestParam Integer size) {
        return Result.success(menuService.findParentMenus(currentPage, size));
    }

    /**
     * 根据父级ID查询子菜单
     * 权限：需要登录
     *
     * @param parentId 父级ID
     * @return 子菜单列表
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "根据父级ID查询子菜单")
    @RequiresRole
    @GetMapping("/findChildrenMenus/{id}")
    public Result<?> findChildrenMenus(@PathVariable("id") Long parentId) {
        return Result.success(menuService.findChildrenMenus(parentId));
    }

    /**
     * 获取用户菜单树
     * 权限：需要登录
     *
     * @param userId 用户ID
     * @return 菜单树
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "获取菜单树")
    @RequiresRole
    @GetMapping("/getMenuTree/{userId}")
    public Result<?> getMenuTree(@PathVariable Integer userId) {
        return Result.success(menuService.getMenuTree(userId));
    }
}
