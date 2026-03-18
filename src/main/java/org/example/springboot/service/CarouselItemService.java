package org.example.springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.example.springboot.entity.CarouselItem;
import org.example.springboot.entity.Product;
import org.example.springboot.enums.ErrorCodeEnum;
import org.example.springboot.exception.BusinessException;
import org.example.springboot.mapper.CarouselItemMapper;
import org.example.springboot.mapper.ProductMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CarouselItemService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CarouselItemService.class);

    @Resource
    private CarouselItemMapper carouselItemMapper;

    @Resource
    private ProductMapper productMapper;

    public CarouselItem createCarouselItem(CarouselItem carouselItem) {
        // 检查商品是否存在
        if (carouselItem.getProductId() != null && carouselItem.getProductId() != 0) {
            Product product = productMapper.selectById(carouselItem.getProductId());
            if (product == null) {
                throw new BusinessException(ErrorCodeEnum.PRODUCT_NOT_FOUND, "关联的商品不存在");
            }
        }

        carouselItemMapper.insert(carouselItem);
        LOGGER.info("Created carousel item: {}", carouselItem.getId());
        return carouselItem;
    }

    public void updateCarouselItem(Long id, CarouselItem carouselItem) {
        CarouselItem existing = carouselItemMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCodeEnum.CAROUSEL_NOT_FOUND, "轮播图不存在");
        }

        // 检查商品是否存在
        if (carouselItem.getProductId() != null && carouselItem.getProductId() != 0) {
            Product product = productMapper.selectById(carouselItem.getProductId());
            if (product == null) {
                throw new BusinessException(ErrorCodeEnum.PRODUCT_NOT_FOUND, "关联的商品不存在");
            }
        }

        carouselItem.setId(id);
        carouselItemMapper.updateById(carouselItem);
        LOGGER.info("Updated carousel item: {}", id);
    }

    public void deleteCarouselItem(Long id) {
        carouselItemMapper.deleteById(id);
        LOGGER.info("Deleted carousel item: {}", id);
    }

    public CarouselItem getCarouselItemById(Long id) {
        CarouselItem item = carouselItemMapper.selectById(id);
        if (item != null && item.getProductId() != 0) {
            item.setProduct(productMapper.selectById(item.getProductId()));
        }
        return item;
    }

    public List<CarouselItem> getActiveCarouselItems() {
        LambdaQueryWrapper<CarouselItem> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(CarouselItem::getStatus, 1)
                   .orderByAsc(CarouselItem::getSortOrder);
        List<CarouselItem> items = carouselItemMapper.selectList(queryWrapper);

        // 填充商品信息
        for (CarouselItem item : items) {
            if (item.getProductId() != null && item.getProductId() != 0) {
                item.setProduct(productMapper.selectById(item.getProductId()));
            }
        }

        return items;
    }

    public Page<CarouselItem> getCarouselItemsByPage(Integer currentPage, Integer size) {
        Page<CarouselItem> page = new Page<>(currentPage, size);
        Page<CarouselItem> result = carouselItemMapper.selectPage(page,
            new LambdaQueryWrapper<CarouselItem>().orderByAsc(CarouselItem::getSortOrder));

        // 填充商品信息
        for (CarouselItem item : result.getRecords()) {
            if (item.getProductId() != null && item.getProductId() != 0) {
                item.setProduct(productMapper.selectById(item.getProductId()));
            }
        }

        return result;
    }
}
