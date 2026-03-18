package org.example.springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.springboot.entity.Address;
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

    public Address createAddress(Address address) {
        int result = addressMapper.insert(address);
        if (result > 0) {
            LOGGER.info("创建地址成功，地址ID：{}", address.getId());
            return address;
        }
        throw new BusinessException(ErrorCodeEnum.ERROR, "创建地址失败");
    }

    public Address updateAddress(Long id, Address address) {
        address.setId(id);
        int result = addressMapper.updateById(address);
        if (result > 0) {
            LOGGER.info("更新地址成功，地址ID：{}", id);
            return address;
        }
        throw new BusinessException(ErrorCodeEnum.ERROR, "更新地址失败");
    }

    public void deleteAddress(Long id) {
        int result = addressMapper.deleteById(id);
        if (result > 0) {
            LOGGER.info("删除地址成功，地址ID：{}", id);
        } else {
            throw new BusinessException(ErrorCodeEnum.ERROR, "删除地址失败");
        }
    }

    public Address getAddressById(Long id) {
        Address address = addressMapper.selectById(id);
        if (address != null) {
            return address;
        }
        throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "未找到地址");
    }

    public List<Address> getAddressesByUserId(Long userId) {
        LambdaQueryWrapper<Address> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Address::getUserId, userId);
        List<Address> addresses = addressMapper.selectList(queryWrapper);
        // 如果没有地址，返回空数组
        return addresses != null ? addresses : new java.util.ArrayList<>();
    }

    public Page<Address> getAddressesByPage(Long userId, Integer currentPage, Integer size) {
        LambdaQueryWrapper<Address> queryWrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            queryWrapper.eq(Address::getUserId, userId);
        }

        Page<Address> page = new Page<>(currentPage, size);
        Page<Address> result = addressMapper.selectPage(page, queryWrapper);

        return result;
    }

    public void deleteBatch(List<Long> ids) {
        int result = addressMapper.deleteBatchIds(ids);
        if (result > 0) {
            LOGGER.info("批量删除地址成功，删除数量：{}", result);
        } else {
            throw new BusinessException(ErrorCodeEnum.ERROR, "批量删除地址失败");
        }
    }
}
