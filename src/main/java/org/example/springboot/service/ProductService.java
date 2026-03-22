package org.example.springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.example.springboot.entity.*;
import org.example.springboot.enums.ErrorCodeEnum;
import org.example.springboot.exception.BusinessException;
import org.example.springboot.mapper.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProductService.class);

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private CartMapper cartMapper;

    @Autowired
    private ReviewMapper reviewMapper;

    @Autowired
    private FavoriteMapper favoriteMapper;

    @Autowired
    private FileService fileService;

    @Autowired
    private CarouselItemMapper carouselItemMapper;

    @Resource
    private StockInMapper stockInMapper;
    @Resource
    private StockOutMapper stockOutMapper;
    /**
     * 创建商品
     *
     * @param product 商品实体
     * @return 创建成功的商品
     * @author IhaveBB
     * @date 2026/03/18
     */
    @Caching(evict = {
            @CacheEvict(value = "productPages", allEntries = true),
            @CacheEvict(value = "products", allEntries = true)
    })
    public Product createProduct(Product product) {
        int result = productMapper.insert(product);
        if (result <= 0) {
            throw new BusinessException(ErrorCodeEnum.ERROR, "创建商品失败");
        }
        LOGGER.info("创建商品成功，商品ID：{}", product.getId());
        return product;
    }
    /**
     * 更新商品
     *
     * @param id      商品ID
     * @param product 商品实体
     * @return 更新成功的商品
     * @author IhaveBB
     * @date 2026/03/18
     */
    @Caching(evict = {
            @CacheEvict(value = "productPages", allEntries = true),
            @CacheEvict(value = "products", allEntries = true)
    })
    public Product updateProduct(Long id, Product product) {
        Product oldProduct = productMapper.selectById(id);
        if (oldProduct == null) {
            throw new BusinessException(ErrorCodeEnum.PRODUCT_NOT_FOUND);
        }
        String oldImg = oldProduct.getImageUrl();
        String newImg = product.getImageUrl();
        if (oldImg != null && !oldImg.equals(newImg)) {
            fileService.fileRemove(oldImg);
        }
        product.setId(id);

        // 检查库存是否合法
        if (product.getStock() != null && product.getStock() < 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "库存不能为负数");
        }

        int result = productMapper.updateById(product);
        if (result <= 0) {
            throw new BusinessException(ErrorCodeEnum.ERROR, "更新商品失败");
        }
        LOGGER.info("更新商品成功，商品ID：{}", id);
        return product;
    }
    /**
     * 删除商品
     *
     * @param id 商品ID
     * @author IhaveBB
     * @date 2026/03/18
     */
    @Caching(evict = {
            @CacheEvict(value = "productPages", allEntries = true),
            @CacheEvict(value = "products", allEntries = true)
    })
    public void deleteProduct(Long id) {
        // 检查是否存在关联轮播图
        LambdaQueryWrapper<CarouselItem> carouselQuery = new LambdaQueryWrapper<>();
        carouselQuery.eq(CarouselItem::getProductId, id);
        Long carouselCount = carouselItemMapper.selectCount(carouselQuery);
        if (carouselCount > 0) {
            throw new BusinessException(ErrorCodeEnum.HAS_ASSOCIATED_DATA, "无法删除商品，存在关联轮播图记录");
        }
        Long stockInCount = stockInMapper.selectCount(new LambdaQueryWrapper<StockIn>().eq(StockIn::getProductId, id));
        if (stockInCount > 0) {
            throw new BusinessException(ErrorCodeEnum.HAS_ASSOCIATED_DATA, "无法删除商品，存在入库记录");
        }
        Long stockOutCount = stockOutMapper.selectCount(new LambdaQueryWrapper<StockOut>().eq(StockOut::getProductId, id));
        if (stockOutCount > 0) {
            throw new BusinessException(ErrorCodeEnum.HAS_ASSOCIATED_DATA, "无法删除商品，存在出库记录");
        }
        // 检查是否存在关联订单
        LambdaQueryWrapper<Order> orderQuery = new LambdaQueryWrapper<>();
        orderQuery.eq(Order::getProductId, id);
        Long orderCount = orderMapper.selectCount(orderQuery);
        if (orderCount > 0) {
            throw new BusinessException(ErrorCodeEnum.HAS_ASSOCIATED_DATA, "无法删除商品，存在关联订单记录");
        }

        // 检查是否存在购物车记录
        LambdaQueryWrapper<Cart> cartQuery = new LambdaQueryWrapper<>();
        cartQuery.eq(Cart::getProductId, id);
        Long cartCount = cartMapper.selectCount(cartQuery);
        if (cartCount > 0) {
            throw new BusinessException(ErrorCodeEnum.HAS_ASSOCIATED_DATA, "无法删除商品，存在购物车记录");
        }

        // 检查是否存在评价记录
        LambdaQueryWrapper<Review> reviewQuery = new LambdaQueryWrapper<>();
        reviewQuery.eq(Review::getProductId, id);
        Long reviewCount = reviewMapper.selectCount(reviewQuery);
        if (reviewCount > 0) {
            throw new BusinessException(ErrorCodeEnum.HAS_ASSOCIATED_DATA, "无法删除商品，存在评价记录");
        }

        // 检查是否存在收藏记录
        LambdaQueryWrapper<Favorite> favoriteQuery = new LambdaQueryWrapper<>();
        favoriteQuery.eq(Favorite::getProductId, id);
        Long favoriteCount = favoriteMapper.selectCount(favoriteQuery);
        if (favoriteCount > 0) {
            throw new BusinessException(ErrorCodeEnum.HAS_ASSOCIATED_DATA, "无法删除商品，存在收藏记录");
        }

        int result = productMapper.deleteById(id);
        if (result <= 0) {
            throw new BusinessException(ErrorCodeEnum.ERROR, "删除商品失败");
        }
        LOGGER.info("删除商品成功，商品ID：{}", id);
    }

    /**
     * 根据ID获取商品
     *
     * @param productId 商品ID
     * @return 商品实体
     * @author IhaveBB
     * @date 2026/03/18
     */
    @Cacheable(value = "products", key = "#productId")
    public Product getProductById(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException(ErrorCodeEnum.PRODUCT_NOT_FOUND);
        }
        // 填充关联信息
        product.setMerchant(userMapper.selectById(product.getMerchantId()));
        product.setCategory(categoryMapper.selectById(product.getCategoryId()));
        return product;
    }

    /**
     * 获取商品实体（不包装Result，用于控制层权限验证）
     *
     * @param productId 商品ID
     * @return 商品实体，不存在时返回 null
     * @author IhaveBB
     * @date 2026/03/21
     */
    public Product getProductByIdValue(Long productId) {
        return productMapper.selectById(productId);
    }
    /**
     * 分页查询商品列表
     * <p>
     * 支持按名称、分类、商户、状态、价格区间筛选，支持按销量/价格/默认（创建时间）排序。
     * 无筛选条件时开启分页缓存，有筛选条件时实时查询。
     * </p>
     *
     * @param name        商品名称（模糊匹配，可为 null）
     * @param categoryId  分类ID（精确，可为 null）
     * @param merchantId  商户ID（精确，可为 null）
     * @param status      商品状态（可为 null）
     * @param currentPage 当前页码（从 1 开始）
     * @param size        每页条数
     * @param sortField   排序字段：sales / price / 默认创建时间
     * @param sortOrder   排序方向：asc / desc
     * @param minPrice    价格下限（含折扣价，可为 null）
     * @param maxPrice    价格上限（含折扣价，可为 null）
     * @return 分页结果，含关联的商户和分类信息
     * @author IhaveBB
     * @date 2026/03/21
     */
    @Cacheable(value = "productPages",
            key = "{#currentPage, #size, #sortField, #sortOrder}",
            condition = "#name == null && #categoryId == null && #merchantId == null && #status == null && #minPrice == null && #maxPrice == null")
    public Page<Product> getProductsByPage(String name, Long categoryId, Long merchantId, Integer status,
                                           Integer currentPage, Integer size, String sortField, String sortOrder,
                                           Double minPrice, Double maxPrice) {
        LambdaQueryWrapper<Product> queryWrapper = new LambdaQueryWrapper<>();

        // 添加基本查询条件
        if (StringUtils.isNotBlank(name)) {
            queryWrapper.like(Product::getName, name);
        }
        if (categoryId != null) {
            queryWrapper.eq(Product::getCategoryId, categoryId);
        }
        if (merchantId != null) {
            queryWrapper.eq(Product::getMerchantId, merchantId);
        }
        if (status != null) {
            queryWrapper.eq(Product::getStatus, status);
        }

        // 处理排序
        boolean isAsc = "asc".equalsIgnoreCase(sortOrder);
        if (StringUtils.isNotBlank(sortField)) {
            switch (sortField) {
                case "sales":
                    queryWrapper.orderBy(true, isAsc, Product::getSalesCount);
                    break;
                case "price":
                    // 对于价格排序，使用自定义SQL考虑折扣价格
                    String orderDirection = isAsc ? "ASC" : "DESC";
                    queryWrapper.apply("1=1")  // 添加一个空条件，防止语法错误
                        .last("ORDER BY CASE WHEN is_discount = 1 THEN discount_price ELSE price END " + orderDirection);
                    break;
                default:
                    queryWrapper.orderByDesc(Product::getCreatedAt);
            }
        } else {
            queryWrapper.orderByDesc(Product::getCreatedAt);
        }

        // 添加价格区间筛选
        if (minPrice != null || maxPrice != null) {
            queryWrapper.and(wrapper -> {
                // 使用CASE WHEN语句根据是否有折扣选择正确的价格字段
                if (minPrice != null) {
                    wrapper.apply("(CASE WHEN is_discount = 1 THEN discount_price ELSE price END) >= {0}", minPrice);
                }
                if (maxPrice != null) {
                    wrapper.apply("(CASE WHEN is_discount = 1 THEN discount_price ELSE price END) <= {0}", maxPrice);
                }
            });
        }

        // 创建分页对象并执行查询
        Page<Product> page = new Page<>(currentPage, size);
        Page<Product> result = productMapper.selectPage(page, queryWrapper);

        // 填充关联信息
        result.getRecords().forEach(product -> {
            product.setMerchant(userMapper.selectById(product.getMerchantId()));
            product.setCategory(categoryMapper.selectById(product.getCategoryId()));
        });

        return result;
    }
    /**
     * 更新商品状态
     *
     * @param id     商品ID
     * @param status 新状态
     * @author IhaveBB
     * @date 2026/03/18
     */
    @Caching(evict = {
            @CacheEvict(value = "productPages", allEntries = true),
            @CacheEvict(value = "products", allEntries = true)
    })
    public void updateProductStatus(Long id, Integer status) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(ErrorCodeEnum.PRODUCT_NOT_FOUND);
        }
        product.setStatus(status);
        int result = productMapper.updateById(product);
        if (result <= 0) {
            throw new BusinessException(ErrorCodeEnum.ERROR, "更新商品状态失败");
        }
        LOGGER.info("更新商品状态成功，商品ID：{}，新状态：{}", id, status);
    }
    /**
     * 批量删除商品
     *
     * @param ids 商品ID列表
     * @author IhaveBB
     * @date 2026/03/18
     */
    @Caching(evict = {
            @CacheEvict(value = "productPages", allEntries = true),
            @CacheEvict(value = "products", allEntries = true)
    })
    public void deleteBatch(List<Long> ids) {
        // 检查每个商品是否存在关联记录
        for (Long id : ids) {
            // 检查轮播图
            LambdaQueryWrapper<CarouselItem> carouselQuery = new LambdaQueryWrapper<>();
            carouselQuery.eq(CarouselItem::getProductId, id);
            if (carouselItemMapper.selectCount(carouselQuery) > 0) {
                throw new BusinessException(ErrorCodeEnum.HAS_ASSOCIATED_DATA, "无法删除商品ID：" + id + "，存在关联轮播图记录");
            }

            // 检查订单
            LambdaQueryWrapper<Order> orderQuery = new LambdaQueryWrapper<>();
            orderQuery.eq(Order::getProductId, id);
            if (orderMapper.selectCount(orderQuery) > 0) {
                throw new BusinessException(ErrorCodeEnum.HAS_ASSOCIATED_DATA, "无法删除商品ID：" + id + "，存在关联订单记录");
            }

            // 检查购物车
            LambdaQueryWrapper<Cart> cartQuery = new LambdaQueryWrapper<>();
            cartQuery.eq(Cart::getProductId, id);
            if (cartMapper.selectCount(cartQuery) > 0) {
                throw new BusinessException(ErrorCodeEnum.HAS_ASSOCIATED_DATA, "无法删除商品ID：" + id + "，存在购物车记录");
            }

            // 检查评价
            LambdaQueryWrapper<Review> reviewQuery = new LambdaQueryWrapper<>();
            reviewQuery.eq(Review::getProductId, id);
            if (reviewMapper.selectCount(reviewQuery) > 0) {
                throw new BusinessException(ErrorCodeEnum.HAS_ASSOCIATED_DATA, "无法删除商品ID：" + id + "，存在评价记录");
            }

            // 检查收藏
            LambdaQueryWrapper<Favorite> favoriteQuery = new LambdaQueryWrapper<>();
            favoriteQuery.eq(Favorite::getProductId, id);
            if (favoriteMapper.selectCount(favoriteQuery) > 0) {
                throw new BusinessException(ErrorCodeEnum.HAS_ASSOCIATED_DATA, "无法删除商品ID：" + id + "，存在收藏记录");
            }
        }

        int result = productMapper.deleteBatchIds(ids);
        if (result <= 0) {
            throw new BusinessException(ErrorCodeEnum.ERROR, "批量删除商品失败");
        }
        LOGGER.info("批量删除商品成功，删除数量：{}", result);
    }
    /**
     * 批量更新商品状态
     *
     * @param ids    商品ID列表
     * @param status 新状态
     * @author IhaveBB
     * @date 2026/03/18
     */
    @Transactional(rollbackFor = Exception.class)
    @Caching(evict = {
            @CacheEvict(value = "productPages", allEntries = true),
            @CacheEvict(value = "products", allEntries = true)
    })
    public void updateBatchStatus(List<Long> ids, Integer status) {
        // 检查状态值是否有效（0-待审核，1-下架，2-上架）
        if (status != 0 && status != 1 && status != 2) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "无效的商品状态值，合法值：0-待审核，1-下架，2-上架");
        }

        // 检查商品是否存在
        List<Product> products = productMapper.selectBatchIds(ids);
        if (products.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PRODUCT_NOT_FOUND, "未找到指定商品");
        }
        if (products.size() != ids.size()) {
            throw new BusinessException(ErrorCodeEnum.PRODUCT_NOT_FOUND, "部分商品不存在");
        }

        // 逐个更新商品状态
        int successCount = 0;
        for (Product product : products) {
            product.setStatus(status);
            int result = productMapper.updateById(product);
            if (result > 0) {
                successCount++;
            }
        }

        if (successCount != products.size()) {
            throw new BusinessException(ErrorCodeEnum.ERROR, "部分商品状态更新失败");
        }
        LOGGER.info("批量更新商品状态成功，更新数量：{}，新状态：{}", successCount, status);
    }
} 