package org.example.springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.springboot.entity.Address;
import org.example.springboot.entity.dto.AddressCreateDTO;
import org.example.springboot.entity.dto.AddressUpdateDTO;
import org.example.springboot.enums.ErrorCodeEnum;
import org.example.springboot.exception.BusinessException;
import org.example.springboot.mapper.AddressMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AddressService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AddressService.class);

    @Autowired
    private AddressMapper addressMapper;

    /**
     * 创建地址
     *
     * @param userId 用户ID（从登录上下文获取）
     * @param dto    地址创建DTO
     * @return 创建后的地址
     * @author IhaveBB
     * @date 2026/03/22
     */
    public Address createAddress(Long userId, AddressCreateDTO dto) {
        Address address = new Address();
        address.setUserId(userId);
        address.setReceiver(dto.getReceiver());
        address.setPhone(dto.getPhone());
        address.setAddress(dto.getAddress());

        int result = addressMapper.insert(address);
        if (result > 0) {
            LOGGER.info("创建地址成功，用户ID：{}，地址ID：{}", userId, address.getId());
            return address;
        }
        throw new BusinessException(ErrorCodeEnum.ERROR, "创建地址失败");
    }

    /**
     * 更新地址
     *
     * @param id  地址ID
     * @param dto 地址更新DTO
     * @return 更新后的地址
     * @author IhaveBB
     * @date 2026/03/22
     */
    public Address updateAddress(Long id, AddressUpdateDTO dto) {
        Address address = new Address();
        address.setId(id);
        address.setReceiver(dto.getReceiver());
        address.setPhone(dto.getPhone());
        address.setAddress(dto.getAddress());

        int result = addressMapper.updateById(address);
        if (result > 0) {
            LOGGER.info("更新地址成功，地址ID：{}", id);
            return address;
        }
        throw new BusinessException(ErrorCodeEnum.ERROR, "更新地址失败");
    }

    /**
     * 根据ID删除地址
     *
     * @param id 地址ID
     * @author IhaveBB
     * @date 2026/03/22
     */
    public void deleteAddress(Long id) {
        int result = addressMapper.deleteById(id);
        if (result > 0) {
            LOGGER.info("删除地址成功，地址ID：{}", id);
        } else {
            throw new BusinessException(ErrorCodeEnum.ERROR, "删除地址失败");
        }
    }

    /**
     * 根据ID获取地址
     *
     * @param id 地址ID
     * @return 地址实体
     * @author IhaveBB
     * @date 2026/03/22
     */
    public Address getAddressById(Long id) {
        Address address = addressMapper.selectById(id);
        if (address != null) {
            return address;
        }
        throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "未找到地址");
    }

    /**
     * 根据用户ID获取地址列表
     *
     * @param userId 用户ID
     * @return 用户地址列表，无数据时返回空列表
     * @author IhaveBB
     * @date 2026/03/22
     */
    public List<Address> getAddressesByUserId(Long userId) {
        LambdaQueryWrapper<Address> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Address::getUserId, userId);
        List<Address> addresses = addressMapper.selectList(queryWrapper);
        return addresses != null ? addresses : new java.util.ArrayList<>();
    }

    /**
     * 分页查询地址列表
     *
     * @param userId      用户ID（可为null，管理员场景）
     * @param currentPage 当前页码
     * @param size        每页条数
     * @return 分页地址列表
     * @author IhaveBB
     * @date 2026/03/22
     */
    public Page<Address> getAddressesByPage(Long userId, Integer currentPage, Integer size) {
        LambdaQueryWrapper<Address> queryWrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            queryWrapper.eq(Address::getUserId, userId);
        }

        Page<Address> page = new Page<>(currentPage, size);
        return addressMapper.selectPage(page, queryWrapper);
    }

    /**
     * 批量删除地址
     *
     * @param ids 地址ID列表
     * @author IhaveBB
     * @date 2026/03/22
     */
    public void deleteBatch(List<Long> ids) {
        int result = addressMapper.deleteBatchIds(ids);
        if (result > 0) {
            LOGGER.info("批量删除地址成功，删除数量：{}", result);
        } else {
            throw new BusinessException(ErrorCodeEnum.ERROR, "批量删除地址失败");
        }
    }
}
