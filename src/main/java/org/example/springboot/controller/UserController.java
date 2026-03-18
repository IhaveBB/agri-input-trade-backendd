package org.example.springboot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.springboot.common.Result;
import org.example.springboot.entity.User;
import org.example.springboot.entity.UserPasswordUpdate;
import org.example.springboot.entity.dto.UserLocationDTO;
import org.example.springboot.enumClass.UserRole;
import org.example.springboot.enums.ErrorCodeEnum;
import org.example.springboot.exception.BusinessException;
import org.example.springboot.service.UserService;
import org.example.springboot.util.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name="用户管理接口")
@RestController
@RequestMapping("/user")
public class UserController {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserController.class);
    @Autowired
    private UserService userService;

    @Operation(summary = "根据id获取用户信息")
    @GetMapping("/{id}")
    public Result<?> getUserById(@PathVariable int id) {
        Long currentUserId = UserContext.getUserId();
        String role = UserContext.getRole();

        // 权限检查：只能查看自己的信息，或者管理员查看他人信息
        if (currentUserId != null && !currentUserId.equals((long) id) && !UserRole.isAdmin(role)) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权限查看他人信息");
        }

        User user = userService.getUserById(id);
        user.setPassword(null);
        return Result.success(user);
    }

    @Operation(summary = "根据username获取用户信息")
    @GetMapping("/username/{username}")
    public Result<?> getUserByUsername(@PathVariable String username) {
        Long currentUserId = UserContext.getUserId();
        String usernameFromContext = UserContext.getUsername();
        String role = UserContext.getRole();

        // 权限检查
        if (currentUserId != null && !usernameFromContext.equals(username) && !UserRole.isAdmin(role)) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权限查看他人信息");
        }

        User user = userService.getByUsername(username);
        user.setPassword(null);
        return Result.success(user);
    }

    @Operation(summary = "登录")
    @PostMapping("/login")
    public Result<?> login(@RequestBody User user) {
        return Result.success(userService.login(user));

    }

    @Operation(summary = "密码修改")
    @PutMapping("/password/{id}")
    public Result<?> updatePassword(@PathVariable int id, @RequestBody UserPasswordUpdate userPasswordUpdate) {
        Long currentUserId = UserContext.getUserId();
        String role = UserContext.getRole();

        // 权限检查：只能修改自己的密码，管理员可以修改任何人的密码
        if (currentUserId != null && !currentUserId.equals((long) id) && !UserRole.isAdmin(role)) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权限修改他人密码");
        }

        userService.updatePassword(id, userPasswordUpdate);
        return Result.success();
    }

    @Operation(summary = "忘记密码")
    @GetMapping("/forget")
    public Result<?> forgetPassword(@RequestParam String email, @RequestParam String newPassword) {
        userService.forgetPassword(email, newPassword);
        return Result.success();
    }

    @Operation(summary = "更新用户位置")
    @PutMapping("/location")
    public Result<?> updateLocation(@RequestBody UserLocationDTO userLocationDTO) {
        Long currentUserId = UserContext.getUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCodeEnum.UNAUTHORIZED);
        }
        userService.updateUserLocation(currentUserId, userLocationDTO.getLocation());
        return Result.success();
    }

    @Operation(summary = "分页查询用户")
    @GetMapping("/page")
    public Result<?> getUsersByPage(
            @RequestParam(defaultValue = "") String username,
            @RequestParam(defaultValue = "") String name,
            @RequestParam(defaultValue = "") String role,
            @RequestParam(defaultValue = "") String status,

            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "10") Integer size) {

        String currentRole = UserContext.getRole();

        // 权限检查：只有管理员可以分页查询用户
        if (currentRole == null || !UserRole.isAdmin(currentRole)) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权限查询用户列表，只有管理员可以查询");
        }

        Page<User> page = userService.getUsersByPage(username, name, role, status, currentPage, size);
        return Result.success(page);
    }

    @Operation(summary = "根据角色获取用户列表")
    @GetMapping("/role/{role}")
    public Result<?> getUserByRole(@PathVariable String role) {
        String currentRole = UserContext.getRole();

        // 权限检查：只有管理员可以按角色查询用户
        if (currentRole == null || !UserRole.isAdmin(currentRole)) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权限查询用户列表，只有管理员可以查询");
        }

        List<User> users = userService.getUserByRole(role);
        return Result.success(users);
    }

    @Operation(summary = "批量删除用户")
    @DeleteMapping("/deleteBatch")
    public Result<?> deleteBatch(@RequestParam List<Integer> ids) {
        String role = UserContext.getRole();

        // 权限检查：只有管理员可以批量删除用户
        if (role == null || !UserRole.isAdmin(role)) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权限批量删除用户，只有管理员可以删除");
        }

        userService.deleteBatch(ids);
        return Result.success();
    }

    @Operation(summary = "获取所有用户")
    @GetMapping
    public Result<?> getAllUsers() {
        String role = UserContext.getRole();

        // 权限检查：只有管理员可以获取所有用户
        if (role == null || !UserRole.isAdmin(role)) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权限获取用户列表，只有管理员可以获取");
        }

        List<User> users = userService.getAllUsers();
        return Result.success(users);
    }

    @Operation(summary = "创建新用户")
    @PostMapping("/add")
    public Result<?> createUser(@RequestBody User user) {
        userService.createUser(user);
        return Result.success(user);
    }

    @Operation(summary = "更新用户信息")
    @PutMapping("/{id}")
    public Result<?> updateUser(@PathVariable Long id, @RequestBody User user) {
        Long currentUserId = UserContext.getUserId();
        String role = UserContext.getRole();

        // 权限检查：只能更新自己的信息，管理员可以更新任何人的信息
        if (currentUserId != null && !currentUserId.equals(id) && !UserRole.isAdmin(role)) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权限更新他人信息");
        }

        // 禁止普通用户修改自己的角色
        if (currentUserId != null && currentUserId.equals(id) && UserRole.isUser(role)) {
            user.setRole(role);
        }

        userService.updateUser(id, user);
        return Result.success(user);
    }

    @Operation(summary = "根据id删除用户")
    @DeleteMapping("/delete/{id}")
    public Result<?> deleteUserById(@PathVariable int id) {
        String role = UserContext.getRole();

        // 权限检查：只有管理员可以删除用户
        if (role == null || !UserRole.isAdmin(role)) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权限删除用户，只有管理员可以删除");
        }

        userService.deleteUserById(id);
        return Result.success();
    }
}
