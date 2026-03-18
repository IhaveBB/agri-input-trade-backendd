package org.example.springboot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.example.springboot.common.Result;
import org.example.springboot.entity.Menu;
import org.example.springboot.service.MenuService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name="菜单管理接口")
@RestController
@RequestMapping("/menu")
public class MenuController {
    @Resource
    private MenuService menuService;

    public static final Logger LOGGER = LoggerFactory.getLogger(MenuController.class);

    @Operation(summary = "增加菜单")
    @PostMapping("/save")
    public Result<?> save(@RequestBody Menu menu) {
        System.out.println(menu);
        menuService.save(menu);
        return Result.success();
    }

    @Operation(summary = "更新菜单")
    @PutMapping("/{id}")
    public Result<?> update(@PathVariable int id, @RequestBody Menu menu) {
        menuService.update(id, menu);
        return Result.success();
    }

    @Operation(summary = "批量删除菜单")
    @DeleteMapping("/deleteBatch")
    public Result<?> deleteBatch(@RequestParam List<Integer> ids) {
        menuService.deleteBatch(ids);
        return Result.success();
    }

    @Operation(summary = "根据id删除菜单项")
    @DeleteMapping("/deleteById/{id}")
    public Result<?> deleteById(@PathVariable("id") Integer id) {
        menuService.deleteById(id);
        return Result.success("删除成功");
    }

    @GetMapping("/find/{id}")
    @Operation(summary = "根据id获取菜单")
    public Result<?> findById(@PathVariable("id") Integer id) {
        return Result.success(menuService.findById(id));
    }

    @GetMapping("/findAll")
    @Operation(summary = "查询菜单")
    public Result<?> findAll() {
        return Result.success(menuService.findAll());
    }

    @GetMapping("/findParentMenus")
    @Operation(summary = "分页查询一级菜单")
    public Result<?> findParentMenus(@RequestParam Integer currentPage,
                                     @RequestParam Integer size) {
        return Result.success(menuService.findParentMenus(currentPage, size));
    }

    @GetMapping("/findChildrenMenus/{id}")
    @Operation(summary = "根据父级ID查询子菜单")
    public Result<?> findChildrenMenus(@PathVariable("id") Long parentId) {
        return Result.success(menuService.findChildrenMenus(parentId));
    }

    @GetMapping("/getMenuTree/{userId}")
    @Operation(summary = "获取菜单树")
    public Result<?> getMenuTree(@PathVariable Integer userId) {
        return Result.success(menuService.getMenuTree(userId));
    }
}
