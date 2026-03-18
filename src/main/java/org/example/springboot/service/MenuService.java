package org.example.springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.springboot.entity.Menu;
import org.example.springboot.entity.User;
import org.example.springboot.enums.ErrorCodeEnum;
import org.example.springboot.exception.BusinessException;
import org.example.springboot.mapper.MenuMapper;
import org.example.springboot.mapper.UserMapper;
import org.example.springboot.util.MenusUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class MenuService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MenuService.class);

    @Autowired
    private MenuMapper menuMapper;

    @Autowired
    private UserMapper userMapper;

    private void updateMenuRole(Menu menu) {
        // 获取父级菜单
        Menu parentMenu = menuMapper.selectOne(Wrappers.<Menu>lambdaQuery().eq(Menu::getId, menu.getId()));

        if (menu.getPid() == null) {
            // 当前menu是父级菜单，获取其所有子菜单
            List<Menu> submenus = menuMapper.selectList(Wrappers.<Menu>lambdaQuery().eq(Menu::getPid, menu.getId()));
            String parentRole = menu.getRole();

            // 遍历所有子菜单，更新它们的角色为父菜单的角色
            for (Menu submenu : submenus) {
                submenu.setRole(parentRole);
                menuMapper.updateById(submenu);
            }
            return;
        }
        if (parentMenu == null) {
            return;
        }

        Integer parentId = parentMenu.getId();
        List<Menu> childrenMenus = menuMapper.selectList(Wrappers.<Menu>lambdaQuery().eq(Menu::getPid, parentId));

        Set<String> parentRoleSet = new HashSet<>();
        for (Menu childMenu : childrenMenus) {
            String childRole = childMenu.getRole();
            if (childRole != null && !childRole.isEmpty()) {
                String[] roleArray = childRole.split(",");
                for (String role : roleArray) {
                    parentRoleSet.add(role.trim());
                }
            }
        }

        StringBuilder parentRoles = new StringBuilder();
        for (String role : parentRoleSet) {
            if (!parentRoles.isEmpty()) {
                parentRoles.append(",");
            }
            parentRoles.append(role);
        }
        menu.setRole(String.valueOf(parentRoles));
        menuMapper.updateById(menu);
    }

    public void save(Menu menu) {
        Menu res = menuMapper.selectOne(Wrappers.<Menu>lambdaQuery().eq(Menu::getId, menu.getId()));
        if (res != null) {
            int i = menuMapper.updateById(menu);
            if (i > 0) {
                this.updateMenuRole(menu);
                return;
            }
            throw new BusinessException(ErrorCodeEnum.OPERATION_FAILED, "更新失败");
        } else {
            int insert = menuMapper.insert(menu);
            if (insert > 0) {
                this.updateMenuRole(menu);
                return;
            }
            throw new BusinessException(ErrorCodeEnum.OPERATION_FAILED, "插入失败");
        }
    }

    public void update(int id, Menu menu) {
        menu.setId(id);
        LOGGER.info("UPDATE menu:" + menu);
        int res = menuMapper.updateById(menu);
        if (res <= 0) {
            throw new BusinessException(ErrorCodeEnum.OPERATION_FAILED, "修改失败");
        }
    }

    public void deleteBatch(List<Integer> ids) {
        int b = menuMapper.deleteBatchIds(ids);
        if (b <= 0) {
            throw new BusinessException(ErrorCodeEnum.OPERATION_FAILED, "删除失败");
        }
    }

    public void deleteById(Integer id) {
        int result = menuMapper.deleteById(id);
        if (result == 0) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "删除失败，未找到指定的菜单项");
        }
    }

    public Menu findById(Integer id) {
        Menu menu = menuMapper.selectById(id);
        if (menu == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "未找到指定的菜单项");
        }
        return menu;
    }

    public List<Menu> findAll() {
        QueryWrapper<Menu> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByAsc("sort_num");

        List<Menu> menuList = menuMapper.selectList(queryWrapper);

        List<Menu> parentList = new ArrayList<>();
        for (Menu menu : menuList) {
            if (menu.getPid() == null) {
                List<Menu> children = new ArrayList<>();
                for (Menu subMenu : menuList) {
                    if (menu.getId().equals(subMenu.getPid())) {
                        children.add(subMenu);
                    }
                }
                if (!children.isEmpty()) {
                    menu.setChildren(children);
                }
                parentList.add(menu);
            }
        }

        return parentList;
    }

    public Page<Menu> findParentMenus(Integer currentPage, Integer size) {
        Page<Menu> page = new Page<>(currentPage, size);
        QueryWrapper<Menu> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByAsc("sort_num");
        queryWrapper.isNull("pid");

        Page<Menu> menuPage = menuMapper.selectPage(page, queryWrapper);
        List<Menu> records = menuPage.getRecords();
        for (Menu record : records) {
            if (record.getPid() == null && record.getPath() == null) {
                record.setHasChildren(true);
            }
        }
        menuPage.setRecords(records);

        return menuPage;
    }

    public List<Menu> findChildrenMenus(Long parentId) {
        QueryWrapper<Menu> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("pid", parentId);
        queryWrapper.orderByAsc("sort_num");

        return menuMapper.selectList(queryWrapper);
    }

    public List<Menu> getMenuTree(Integer userId) {
        User user = userMapper.selectById(userId);
        LOGGER.info("userId:{}", userId);
        LOGGER.info(user.toString());

        if (ObjectUtils.isNull(user)) {
            throw new BusinessException(ErrorCodeEnum.USER_NOT_FOUND, "用户不存在");
        }
        List<Menu> roleMenuList = menuMapper.selectList(null);
        return MenusUtils.allocMenus(roleMenuList, user.getRole());
    }
}
