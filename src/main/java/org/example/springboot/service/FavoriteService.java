package org.example.springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.springboot.entity.Favorite;
import org.example.springboot.entity.User;
import org.example.springboot.entity.Product;
import org.example.springboot.enums.ErrorCodeEnum;
import org.example.springboot.exception.BusinessException;
import org.example.springboot.mapper.FavoriteMapper;
import org.example.springboot.mapper.UserMapper;
import org.example.springboot.mapper.ProductMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FavoriteService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FavoriteService.class);

    @Autowired
    private FavoriteMapper favoriteMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ProductMapper productMapper;

    public Favorite createFavorite(Favorite favorite) {
        // 检查用户是否存在
        User user = userMapper.selectById(favorite.getUserId());
        if (user == null) {
            throw new BusinessException(ErrorCodeEnum.USER_NOT_FOUND, "用户不存在");
        }

        // 检查商品是否存在
        Product product = productMapper.selectById(favorite.getProductId());
        if (product == null) {
            throw new BusinessException(ErrorCodeEnum.PRODUCT_NOT_FOUND, "商品不存在");
        }

        // 检查是否已经收藏,已收藏直接更新状态
        LambdaQueryWrapper<Favorite> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Favorite::getUserId, favorite.getUserId())
                   .eq(Favorite::getProductId, favorite.getProductId());
        if (favoriteMapper.selectCount(queryWrapper) > 0) {
            favorite.setStatus(favorite.getStatus() == 0 ? 1 : 0);
            return updateFavoriteStatus(favorite.getUserId(), favorite.getProductId(), favorite.getStatus());
        }

        favorite.setStatus(1);
        int result = favoriteMapper.insert(favorite);
        if (result > 0) {
            LOGGER.info("创建收藏成功，收藏ID：{}", favorite.getId());
            return favorite;
        }
        throw new BusinessException(ErrorCodeEnum.OPERATION_FAILED, "创建收藏失败");
    }

    public Favorite updateFavoriteStatus(Long userId, Long productId, Integer status) {
        Favorite favorite = favoriteMapper.selectOne(new LambdaQueryWrapper<Favorite>()
            .eq(Favorite::getUserId, userId)
            .eq(Favorite::getProductId, productId));
        if (favorite == null) {
            throw new BusinessException(ErrorCodeEnum.FAVORITE_NOT_FOUND, "未找到收藏记录");
        }

        // 检查状态是否合法
        if (status < 0 || status > 1) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "收藏状态不合法");
        }

        favorite.setStatus(status);
        int result = favoriteMapper.updateById(favorite);
        if (result > 0) {
            LOGGER.info("更新收藏状态成功，收藏ID：{}，新状态：{}", favorite.getId(), status);
            return favorite;
        }
        throw new BusinessException(ErrorCodeEnum.OPERATION_FAILED, "更新收藏状态失败");
    }

    public void deleteFavorite(Long id) {
        int result = favoriteMapper.deleteById(id);
        if (result > 0) {
            LOGGER.info("删除收藏成功，收藏ID：{}", id);
            return;
        }
        throw new BusinessException(ErrorCodeEnum.OPERATION_FAILED, "删除收藏失败");
    }

    public Favorite getFavoriteById(Long id) {
        Favorite favorite = favoriteMapper.selectById(id);
        if (favorite != null) {
            // 填充关联信息
            favorite.setUser(userMapper.selectById(favorite.getUserId()));
            favorite.setProduct(productMapper.selectById(favorite.getProductId()));
            return favorite;
        }
        throw new BusinessException(ErrorCodeEnum.FAVORITE_NOT_FOUND, "未找到收藏记录");
    }

    public List<Favorite> getFavoritesByUserId(Long userId) {
        LambdaQueryWrapper<Favorite> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Favorite::getUserId, userId)
                   .eq(Favorite::getStatus, 1);
        List<Favorite> favorites = favoriteMapper.selectList(queryWrapper);
        if (favorites != null && !favorites.isEmpty()) {
            // 填充关联信息
            favorites.forEach(favorite -> {
                favorite.setUser(userMapper.selectById(favorite.getUserId()));
                favorite.setProduct(productMapper.selectById(favorite.getProductId()));
            });
            return favorites;
        }
        throw new BusinessException(ErrorCodeEnum.FAVORITE_NOT_FOUND, "未找到收藏记录");
    }

    public Page<Favorite> getFavoritesByPage(Long userId, Integer currentPage, Integer size) {
        LambdaQueryWrapper<Favorite> queryWrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            queryWrapper.eq(Favorite::getUserId, userId)
                       .eq(Favorite::getStatus, 1);
        }

        Page<Favorite> page = new Page<>(currentPage, size);
        Page<Favorite> result = favoriteMapper.selectPage(page, queryWrapper);

        // 填充关联信息
        result.getRecords().forEach(favorite -> {
            favorite.setUser(userMapper.selectById(favorite.getUserId()));
            favorite.setProduct(productMapper.selectById(favorite.getProductId()));
        });

        return result;
    }

    public void deleteBatch(List<Long> ids) {
        int result = favoriteMapper.deleteBatchIds(ids);
        if (result > 0) {
            LOGGER.info("批量删除收藏成功，删除数量：{}", result);
            return;
        }
        throw new BusinessException(ErrorCodeEnum.OPERATION_FAILED, "批量删除收藏失败");
    }
}
