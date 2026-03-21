package org.example.springboot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.springboot.annotation.RequiresRole;
import org.example.springboot.common.Result;
import org.example.springboot.entity.User;
import org.example.springboot.entity.UserPasswordUpdate;
import org.example.springboot.entity.dto.UserLocationDTO;
import org.example.springboot.enumClass.UserRole;
import org.example.springboot.enums.ErrorCodeEnum;
import org.example.springboot.exception.BusinessException;
import org.example.springboot.service.EmailService;
import org.example.springboot.service.UserService;
import org.example.springboot.util.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户管理控制器
 * 提供用户的增删改查、登录、密码管理等功能
 *
 * @author IhaveBB
 * @date 2026/03/19
 */
@Tag(name="用户管理接口")
@RestController
@RequestMapping("/user")
public class UserController {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private UserService userService;

    @Autowired
    private EmailService emailService;

    /**
     * 根据ID获取用户信息
     * 权限：只能查看自己的信息，或者管理员查看他人信息
     *
     * @param id 用户ID
     * @return 用户信息（密码已隐藏）
     * @author IhaveBB
     * @date 2026/03/19
     */
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

    /**
     * 根据用户名获取用户信息
     * 权限：只能查看自己的信息，或者管理员查看他人信息
     *
     * @param username 用户名
     * @return 用户信息（密码已隐藏）
     * @author IhaveBB
     * @date 2026/03/19
     */
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

    /**
     * 用户登录
     * 无需权限验证
     *
     * @param user 登录信息（用户名和密码）
     * @return 登录成功后的用户信息（含token）
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "登录")
    @PostMapping("/login")
    public Result<?> login(@RequestBody User user) {
        return Result.success(userService.login(user));
    }

    /**
     * 修改密码
     * 权限：只能修改自己的密码，管理员可以修改任何人的密码
     *
     * @param id                 用户ID
     * @param userPasswordUpdate 密码更新信息
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
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

    /**
     * 忘记密码 - 重置密码
     * 无需登录，但需要邮箱验证码
     *
     * @param email       用户邮箱
     * @param code        验证码
     * @param newPassword 新密码
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "忘记密码")
    @PostMapping("/forget")
    public Result<?> forgetPassword(
            @RequestParam String email,
            @RequestParam String code,
            @RequestParam String newPassword) {

        // 验证验证码
        if (!emailService.verifyCode(email, code)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "验证码错误或已过期");
        }

        userService.forgetPassword(email, newPassword);
        return Result.success();
    }

    /**
     * 更新用户位置
     * 权限：只需登录
     *
     * @param userLocationDTO 位置信息
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "更新用户位置")
    @RequiresRole
    @PutMapping("/location")
    public Result<?> updateLocation(@RequestBody UserLocationDTO userLocationDTO) {
        Long currentUserId = UserContext.getUserId();
        userService.updateUserLocation(currentUserId, userLocationDTO.getLocation());
        return Result.success();
    }

    /**
     * 分页查询用户
     * 权限：只有管理员
     *
     * @param username    用户名（模糊查询）
     * @param name        姓名（模糊查询）
     * @param role        角色
     * @param status      状态
     * @param currentPage 当前页
     * @param size        每页大小
     * @return 分页用户列表
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "分页查询用户")
    @RequiresRole("ADMIN")
    @GetMapping("/page")
    public Result<?> getUsersByPage(
            @RequestParam(defaultValue = "") String username,
            @RequestParam(defaultValue = "") String name,
            @RequestParam(defaultValue = "") String role,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "10") Integer size) {
        Page<User> page = userService.getUsersByPage(username, name, role, status, currentPage, size);
        return Result.success(page);
    }

    /**
     * 根据角色获取用户列表
     * 权限：只有管理员
     *
     * @param role 角色
     * @return 用户列表
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "根据角色获取用户列表")
    @RequiresRole("ADMIN")
    @GetMapping("/role/{role}")
    public Result<?> getUserByRole(@PathVariable String role) {
        List<User> users = userService.getUserByRole(role);
        return Result.success(users);
    }

    /**
     * 批量删除用户
     * 权限：只有管理员
     *
     * @param ids 用户ID列表
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "批量删除用户")
    @RequiresRole("ADMIN")
    @DeleteMapping("/deleteBatch")
    public Result<?> deleteBatch(@RequestParam List<Integer> ids) {
        userService.deleteBatch(ids);
        return Result.success();
    }

    /**
     * 获取所有用户
     * 权限：只有管理员
     *
     * @return 用户列表
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "获取所有用户")
    @RequiresRole("ADMIN")
    @GetMapping
    public Result<?> getAllUsers() {
        List<User> users = userService.getAllUsers();
        return Result.success(users);
    }

    /**
     * 创建新用户（注册）
     * 无需权限验证
     *
     * @param user 用户信息
     * @return 创建后的用户
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "创建新用户")
    @PostMapping("/add")
    public Result<?> createUser(@RequestBody User user) {
        userService.createUser(user);
        return Result.success(user);
    }

    /**
     * 更新用户信息
     * 权限：只能更新自己的信息，管理员可以更新任何人的信息
     *
     * @param id   用户ID
     * @param user 用户信息
     * @return 更新后的用户
     * @author IhaveBB
     * @date 2026/03/19
     */
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

    /**
     * 根据ID删除用户
     * 权限：只有管理员
     *
     * @param id 用户ID
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "根据id删除用户")
    @RequiresRole("ADMIN")
    @DeleteMapping("/delete/{id}")
    public Result<?> deleteUserById(@PathVariable int id) {
        userService.deleteUserById(id);
        return Result.success();
    }
}
