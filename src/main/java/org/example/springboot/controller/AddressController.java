package org.example.springboot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.springboot.annotation.RequiresRole;
import org.example.springboot.common.Result;
import org.example.springboot.entity.Address;
import org.example.springboot.entity.dto.AddressCreateDTO;
import org.example.springboot.entity.dto.AddressUpdateDTO;
import org.example.springboot.enumClass.UserRole;
import org.example.springboot.enums.ErrorCodeEnum;
import org.example.springboot.exception.BusinessException;
import org.example.springboot.service.AddressService;
import org.example.springboot.util.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 地址管理控制器
 * 提供用户收货地址的增删改查功能，包括权限验证
 *
 * @author IhaveBB
 * @date 2026/03/19
 */
@Tag(name = "地址管理接口")
@RestController
@RequestMapping("/address")
public class AddressController {
    private static final Logger LOGGER = LoggerFactory.getLogger(AddressController.class);

    @Autowired
    private AddressService addressService;

    /**
     * 创建地址
     * 权限：需要登录
     *
     * @param dto 地址创建DTO
     * @return 创建后的地址记录
     * @author IhaveBB
     * @date 2026/03/21
     */
    @Operation(summary = "创建地址")
    @RequiresRole
    @PostMapping
    public Result<?> createAddress(@Valid @RequestBody AddressCreateDTO dto) {
        Long currentUserId = UserContext.getUserId();
        return Result.success(addressService.createAddress(currentUserId, dto));
    }

    /**
     * 更新地址信息
     * 权限：需要登录，只能修改自己的地址
     *
     * @param id  地址ID
     * @param dto 地址更新DTO
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/21
     */
    @Operation(summary = "更新地址信息")
    @RequiresRole
    @PutMapping("/{id}")
    public Result<?> updateAddress(@PathVariable Long id, @Valid @RequestBody AddressUpdateDTO dto) {
        Long currentUserId = UserContext.getUserId();
        String role = UserContext.getRole();

        // 验证地址是否属于当前用户（管理员可以修改任意地址）
        Address existingAddress = addressService.getAddressById(id);
        if (!UserRole.isAdmin(role) && !existingAddress.getUserId().equals(currentUserId)) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权限修改他人地址");
        }

        addressService.updateAddress(id, dto);
        return Result.success();
    }

    /**
     * 删除地址
     * 权限：需要登录，只能删除自己的地址
     *
     * @param id 地址ID
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "删除地址")
    @RequiresRole
    @DeleteMapping("/{id}")
    public Result<?> deleteAddress(@PathVariable Long id) {
        Long currentUserId = UserContext.getUserId();
        String role = UserContext.getRole();

        // 验证地址是否属于当前用户（管理员可以删除任意地址）
        Address existingAddress = addressService.getAddressById(id);
        if (!UserRole.isAdmin(role) && !existingAddress.getUserId().equals(currentUserId)) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权限删除他人地址");
        }

        addressService.deleteAddress(id);
        return Result.success();
    }

    /**
     * 根据ID获取地址详情
     * 权限：需要登录，只能查看自己的地址
     *
     * @param id 地址ID
     * @return 地址详情
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "根据ID获取地址详情")
    @RequiresRole
    @GetMapping("/{id}")
    public Result<?> getAddressById(@PathVariable Long id) {
        Long currentUserId = UserContext.getUserId();
        String role = UserContext.getRole();

        // 验证地址是否属于当前用户（管理员可以查看任意地址）
        Address address = addressService.getAddressById(id);
        if (!UserRole.isAdmin(role) && !address.getUserId().equals(currentUserId)) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权限查看他人地址");
        }

        return Result.success(address);
    }

    /**
     * 根据用户ID获取地址列表
     * 权限：需要登录，普通用户只能查看自己的地址
     *
     * @param userId 用户ID
     * @return 地址列表
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "根据用户ID获取地址列表")
    @RequiresRole
    @GetMapping("/user/{userId}")
    public Result<?> getAddressesByUserId(@PathVariable Long userId) {
        Long currentUserId = UserContext.getUserId();
        String role = UserContext.getRole();

        // 普通用户只能查看自己的地址
        if (!UserRole.isAdmin(role) && !currentUserId.equals(userId)) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权限查看他人地址");
        }

        return Result.success(addressService.getAddressesByUserId(userId));
    }

    /**
     * 分页查询地址列表
     * 权限：需要登录，普通用户只能查看自己的地址
     *
     * @param userId      用户ID（可选）
     * @param currentPage 当前页
     * @param size        每页大小
     * @return 分页地址列表
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "分页查询地址列表")
    @RequiresRole
    @GetMapping("/page")
    public Result<?> getAddressesByPage(
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "10") Integer size) {
        Long currentUserId = UserContext.getUserId();
        String role = UserContext.getRole();

        // 普通用户强制只能查看自己的地址
        if (UserRole.isUser(role)) {
            userId = currentUserId;
        }
        // 商户和管理员可以查看指定用户的地址

        return Result.success(addressService.getAddressesByPage(userId, currentPage, size));
    }

    /**
     * 批量删除地址
     * 权限：需要登录，只能删除自己的地址
     *
     * @param ids 地址ID列表
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "批量删除地址")
    @RequiresRole
    @DeleteMapping("/batch")
    public Result<?> deleteBatch(@RequestParam List<Long> ids) {
        Long currentUserId = UserContext.getUserId();
        String role = UserContext.getRole();

        // 验证所有地址是否属于当前用户（管理员可以删除任意地址）
        if (!UserRole.isAdmin(role)) {
            for (Long id : ids) {
                Address address = addressService.getAddressById(id);
                if (address != null && !address.getUserId().equals(currentUserId)) {
                    throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权限删除他人地址");
                }
            }
        }

        addressService.deleteBatch(ids);
        return Result.success();
    }
}
