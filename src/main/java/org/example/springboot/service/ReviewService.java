package org.example.springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.example.springboot.entity.Order;
import org.example.springboot.entity.Review;
import org.example.springboot.entity.User;
import org.example.springboot.entity.Product;
import org.example.springboot.entity.dto.ReviewCreateDTO;
import org.example.springboot.enums.ErrorCodeEnum;
import org.example.springboot.exception.BusinessException;
import org.example.springboot.mapper.OrderMapper;
import org.example.springboot.mapper.ReviewMapper;
import org.example.springboot.mapper.UserMapper;
import org.example.springboot.mapper.ProductMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 评价服务
 * <p>
 * 负责商品评价的创建、查询、状态管理及删除。
 * 评价创建时执行三层校验：已购买验证、重复评价检测、评分合法性校验。
 * </p>
 *
 * @author IhaveBB
 * @date 2026/03/22
 */
@Service
public class ReviewService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReviewService.class);

    @Autowired
    private ReviewMapper reviewMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ProductMapper productMapper;

    @Resource
    private OrderMapper orderMapper;

    /**
     * 创建评价（使用 DTO）
     * <p>
     * 校验链：用户存在 → 商品存在 → 已完成订单验证 → 重复评价检测 → 评分范围校验 → 写入。
     * </p>
     *
     * @param userId 用户ID（从上下文获取，不可为 null）
     * @param dto    评价创建DTO，含商品ID、评分、评价内容
     * @return 创建成功的评价记录
     * @author IhaveBB
     * @date 2026/03/22
     */
    public Review createReview(Long userId, ReviewCreateDTO dto) {
        // 检查用户是否存在
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCodeEnum.USER_NOT_FOUND);
        }

        // 检查商品是否存在
        Product product = productMapper.selectById(dto.getProductId());
        if (product == null) {
            throw new BusinessException(ErrorCodeEnum.PRODUCT_NOT_FOUND);
        }

        // 验证用户是否已购买该商品（必须存在已完成订单 status=3）
        LambdaQueryWrapper<Order> purchaseCheck = new LambdaQueryWrapper<>();
        purchaseCheck.eq(Order::getUserId, userId)
                .eq(Order::getProductId, dto.getProductId())
                .eq(Order::getStatus, 3);
        Long purchaseCount = orderMapper.selectCount(purchaseCheck);
        if (purchaseCount == null || purchaseCount == 0) {
            throw new BusinessException(ErrorCodeEnum.PURCHASE_REQUIRED);
        }

        // 检查是否已存在该商品的有效评价（防止重复评价）
        LambdaQueryWrapper<Review> duplicateCheck = new LambdaQueryWrapper<>();
        duplicateCheck.eq(Review::getUserId, userId)
                .eq(Review::getProductId, dto.getProductId())
                .eq(Review::getStatus, 1);
        Long existingCount = reviewMapper.selectCount(duplicateCheck);
        if (existingCount > 0) {
            throw new BusinessException(ErrorCodeEnum.REVIEW_ALREADY_EXISTS);
        }

        // 校验评分范围（1-5）
        if (dto.getRating() != null && (dto.getRating() < 1 || dto.getRating() > 5)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "评分必须在 1 到 5 之间");
        }

        // 构建评价实体
        Review review = new Review();
        review.setUserId(userId);
        review.setProductId(dto.getProductId());
        review.setRating(dto.getRating());
        review.setContent(dto.getContent());
        review.setStatus(1); // 默认启用

        int result = reviewMapper.insert(review);
        if (result <= 0) {
            throw new BusinessException(ErrorCodeEnum.OPERATION_FAILED, "创建评价失败");
        }
        LOGGER.info("创建评价成功，评价ID：{}，用户ID：{}，商品ID：{}", review.getId(), userId, dto.getProductId());
        return review;
    }

    /**
     * 更新评价状态（审核启用/禁用）
     *
     * @param id     评价ID
     * @param status 新状态：1-启用，0-禁用
     * @return 更新后的评价
     * @author IhaveBB
     * @date 2026/03/22
     */
    public Review updateReviewStatus(Long id, Integer status) {
        Review review = reviewMapper.selectById(id);
        if (review == null) {
            throw new BusinessException(ErrorCodeEnum.REVIEW_NOT_FOUND);
        }

        review.setStatus(status);
        int result = reviewMapper.updateById(review);
        if (result <= 0) {
            throw new BusinessException(ErrorCodeEnum.OPERATION_FAILED, "更新评价状态失败");
        }
        LOGGER.info("更新评价状态成功，评价ID：{}，新状态：{}", id, status);
        return review;
    }

    /**
     * 删除评价
     *
     * @param id 评价ID
     * @author IhaveBB
     * @date 2026/03/22
     */
    public void deleteReview(Long id) {
        int result = reviewMapper.deleteById(id);
        if (result <= 0) {
            throw new BusinessException(ErrorCodeEnum.OPERATION_FAILED, "删除评价失败");
        }
        LOGGER.info("删除评价成功，评价ID：{}", id);
    }

    /**
     * 根据ID获取评价详情（含关联用户和商品）
     *
     * @param id 评价ID
     * @return 评价实体
     * @author IhaveBB
     * @date 2026/03/22
     */
    public Review getReviewById(Long id) {
        Review review = reviewMapper.selectById(id);
        if (review == null) {
            throw new BusinessException(ErrorCodeEnum.REVIEW_NOT_FOUND);
        }
        review.setUser(userMapper.selectById(review.getUserId()));
        review.setProduct(productMapper.selectById(review.getProductId()));
        return review;
    }

    /**
     * 根据商品ID获取评价列表
     * <p>
     * 商品暂无评价时返回空列表，不抛出异常。
     * </p>
     *
     * @param productId 商品ID
     * @param status    评价状态过滤（null 表示不过滤）
     * @return 评价列表，无数据时返回空列表
     * @author IhaveBB
     * @date 2026/03/22
     */
    public List<Review> getReviewsByProductId(Long productId, Integer status) {
        LambdaQueryWrapper<Review> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Review::getProductId, productId);
        if (status != null) {
            queryWrapper.eq(Review::getStatus, status);
        }
        List<Review> reviews = reviewMapper.selectList(queryWrapper);
        if (reviews == null || reviews.isEmpty()) {
            return Collections.emptyList();
        }
        reviews.forEach(review -> {
            review.setUser(userMapper.selectById(review.getUserId()));
            review.setProduct(productMapper.selectById(review.getProductId()));
        });
        return reviews;
    }

    /**
     * 分页查询评价列表
     * <p>
     * 支持按商品ID、商品名称、用户ID、用户名、商户ID、评价状态多条件过滤。
     * </p>
     *
     * @param productId   商品ID（精确，可为 null）
     * @param productName 商品名称（模糊，可为 null）
     * @param userId      用户ID（精确，可为 null）
     * @param username    用户名（模糊，可为 null）
     * @param merchantId  商户ID（可为 null，非 null 时只返回该商户商品的评价）
     * @param status      评价状态（可为 null）
     * @param currentPage 当前页码（从 1 开始）
     * @param size        每页条数
     * @return 分页评价列表，含关联用户和商品信息
     * @author IhaveBB
     * @date 2026/03/22
     */
    public Page<Review> getReviewsByPage(Long productId, String productName, Long userId, String username,
                                         Long merchantId, Integer status, Integer currentPage, Integer size) {
        LambdaQueryWrapper<Review> queryWrapper = new LambdaQueryWrapper<>();
        if (productId != null) {
            queryWrapper.eq(Review::getProductId, productId);
        }
        if (StringUtils.isNotBlank(productName)) {
            List<Long> productIds = productMapper.selectList(
                    new LambdaQueryWrapper<Product>().like(Product::getName, productName)
            ).stream().map(Product::getId).collect(Collectors.toList());
            if (productIds.isEmpty()) {
                // 商品名无匹配时直接返回空分页，避免无效全表查询
                Page<Review> emptyPage = new Page<>(currentPage, size);
                emptyPage.setTotal(0);
                return emptyPage;
            }
            queryWrapper.in(Review::getProductId, productIds);
        }
        if (StringUtils.isNotBlank(username)) {
            List<Long> userIds = userMapper.selectList(
                    new LambdaQueryWrapper<User>().like(User::getUsername, username)
            ).stream().map(User::getId).collect(Collectors.toList());
            if (userIds.isEmpty()) {
                Page<Review> emptyPage = new Page<>(currentPage, size);
                emptyPage.setTotal(0);
                return emptyPage;
            }
            queryWrapper.in(Review::getUserId, userIds);
        }
        if (userId != null) {
            queryWrapper.eq(Review::getUserId, userId);
        }
        if (merchantId != null) {
            List<Long> productIds = productMapper.selectList(
                    new LambdaQueryWrapper<Product>().eq(Product::getMerchantId, merchantId)
            ).stream().map(Product::getId).collect(Collectors.toList());
            if (productIds.isEmpty()) {
                Page<Review> emptyPage = new Page<>(currentPage, size);
                emptyPage.setTotal(0);
                return emptyPage;
            }
            queryWrapper.in(Review::getProductId, productIds);
        }
        if (status != null) {
            queryWrapper.eq(Review::getStatus, status);
        }

        Page<Review> page = new Page<>(currentPage, size);
        Page<Review> result = reviewMapper.selectPage(page, queryWrapper);

        result.getRecords().forEach(review -> {
            review.setUser(userMapper.selectById(review.getUserId()));
            review.setProduct(productMapper.selectById(review.getProductId()));
        });

        return result;
    }
}
