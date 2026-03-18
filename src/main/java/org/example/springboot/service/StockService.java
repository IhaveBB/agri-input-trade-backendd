package org.example.springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.springboot.entity.Product;
import org.example.springboot.entity.StockIn;
import org.example.springboot.entity.StockOut;
import org.example.springboot.enums.ErrorCodeEnum;
import org.example.springboot.exception.BusinessException;
import org.example.springboot.mapper.ProductMapper;
import org.example.springboot.mapper.StockInMapper;
import org.example.springboot.mapper.StockOutMapper;
import org.example.springboot.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class StockService {
    private static final Logger LOGGER = LoggerFactory.getLogger(StockService.class);

    @Autowired
    private StockInMapper stockInMapper;

    @Autowired
    private StockOutMapper stockOutMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private UserMapper userMapper;

    @Transactional
    public StockIn createStockIn(StockIn stockIn) {
        // 检查商品是否存在
        Product product = productMapper.selectById(stockIn.getProductId());
        if (product == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "商品不存在");
        }

        // 计算总价
        stockIn.setTotalPrice(stockIn.getUnitPrice().multiply(new BigDecimal(stockIn.getQuantity())));

        // 更新商品库存
        product.setStock(product.getStock() + stockIn.getQuantity());
        productMapper.updateById(product);

        // 保存入库记录
        int result = stockInMapper.insert(stockIn);
        if (result > 0) {
            LOGGER.info("创建入库记录成功，入库ID：{}", stockIn.getId());
            return stockIn;
        }
        throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR, "创建入库记录失败");
    }

    @Transactional
    public StockOut createStockOut(StockOut stockOut) {
        // 检查商品是否存在
        Product product = productMapper.selectById(stockOut.getProductId());
        if (product == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "商品不存在");
        }

        // 检查库存是否足够
        if (product.getStock() < stockOut.getQuantity()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "库存不足");
        }

        // 计算总价
        stockOut.setTotalPrice(stockOut.getUnitPrice().multiply(new BigDecimal(stockOut.getQuantity())));

        // 更新商品库存
        product.setStock(product.getStock() - stockOut.getQuantity());
        productMapper.updateById(product);

        // 保存出库记录
        int result = stockOutMapper.insert(stockOut);
        if (result > 0) {
            LOGGER.info("创建出库记录成功，出库ID：{}", stockOut.getId());
            return stockOut;
        }
        throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR, "创建出库记录失败");
    }

    // 获取入库记录列表
    public Page<StockIn> getStockInList(Long productId, String supplier, Integer status, Long operatorId,
                                      Integer currentPage, Integer size) {
        LambdaQueryWrapper<StockIn> queryWrapper = new LambdaQueryWrapper<>();

        if (productId != null) {
            queryWrapper.eq(StockIn::getProductId, productId);
        }
        if (supplier != null) {
            queryWrapper.like(StockIn::getSupplier, supplier);
        }
        if (status != null) {
            queryWrapper.eq(StockIn::getStatus, status);
        }
        if (operatorId != null) {
            queryWrapper.eq(StockIn::getOperatorId, operatorId);
        }

        queryWrapper.orderByDesc(StockIn::getId);

        Page<StockIn> page = new Page<>(currentPage, size);
        Page<StockIn> result = stockInMapper.selectPage(page, queryWrapper);

        // 填充关联信息
        result.getRecords().forEach(stockIn -> {
            stockIn.setProduct(productMapper.selectById(stockIn.getProductId()));
            stockIn.setOperator(userMapper.selectById(stockIn.getOperatorId()));
        });

        return result;
    }

    // 获取出库记录列表
    public Page<StockOut> getStockOutList(Long productId, Integer type, Integer status, Long operatorId,
                                        String customerName, String orderNo,
                                        Integer currentPage, Integer size) {
        LambdaQueryWrapper<StockOut> queryWrapper = new LambdaQueryWrapper<>();

        if (productId != null) {
            queryWrapper.eq(StockOut::getProductId, productId);
        }
        if (type != null) {
            queryWrapper.eq(StockOut::getType, type);
        }
        if (status != null) {
            queryWrapper.eq(StockOut::getStatus, status);
        }
        if (operatorId != null) {
            queryWrapper.eq(StockOut::getOperatorId, operatorId);
        }
        if (customerName != null) {
            queryWrapper.like(StockOut::getCustomerName, customerName);
        }
        if (orderNo != null) {
            queryWrapper.eq(StockOut::getOrderNo, orderNo);
        }

        queryWrapper.orderByDesc(StockOut::getId);

        Page<StockOut> page = new Page<>(currentPage, size);
        Page<StockOut> result = stockOutMapper.selectPage(page, queryWrapper);

        // 填充关联信息
        result.getRecords().forEach(stockOut -> {
            stockOut.setProduct(productMapper.selectById(stockOut.getProductId()));
            stockOut.setOperator(userMapper.selectById(stockOut.getOperatorId()));
        });

        return result;
    }

    // 作废入库记录
    @Transactional
    public void invalidateStockIn(Long id) {
        StockIn stockIn = stockInMapper.selectById(id);
        if (stockIn == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "入库记录不存在");
        }

        if (stockIn.getStatus() == 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "该记录已作废");
        }

        // 更新商品库存
        Product product = productMapper.selectById(stockIn.getProductId());
        if (product != null) {
            product.setStock(product.getStock() - stockIn.getQuantity());
            productMapper.updateById(product);
        }

        // 更新入库记录状态
        stockIn.setStatus(0);
        stockInMapper.updateById(stockIn);
    }

    // 作废出库记录
    @Transactional
    public void invalidateStockOut(Long id) {
        StockOut stockOut = stockOutMapper.selectById(id);
        if (stockOut == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "出库记录不存在");
        }

        if (stockOut.getStatus() == 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "该记录已作废");
        }

        // 更新商品库存
        Product product = productMapper.selectById(stockOut.getProductId());
        if (product != null) {
            product.setStock(product.getStock() + stockOut.getQuantity());
            productMapper.updateById(product);
        }

        // 更新出库记录状态
        stockOut.setStatus(0);
        stockOutMapper.updateById(stockOut);
    }

    // 删除入库记录
    @Transactional
    public void deleteStockIn(Long id) {
        StockIn stockIn = stockInMapper.selectById(id);
        if (stockIn == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "入库记录不存在");
        }

        // 如果记录状态为正常，需要先作废（减少库存）
        if (stockIn.getStatus() == 1) {
            invalidateStockIn(id);
        }

        // 删除记录
        int result = stockInMapper.deleteById(id);
        if (result > 0) {
            LOGGER.info("删除入库记录成功，入库ID：{}", id);
            return;
        }
        throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR, "删除入库记录失败");
    }

    // 删除出库记录
    @Transactional
    public void deleteStockOut(Long id) {
        StockOut stockOut = stockOutMapper.selectById(id);
        if (stockOut == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "出库记录不存在");
        }

        // 如果记录状态为正常，需要先作废（恢复库存）
        if (stockOut.getStatus() == 1) {
            invalidateStockOut(id);
        }

        // 删除记录
        int result = stockOutMapper.deleteById(id);
        if (result > 0) {
            LOGGER.info("删除出库记录成功，出库ID：{}", id);
            return;
        }
        throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR, "删除出库记录失败");
    }
}
