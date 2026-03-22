package org.example.springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.example.springboot.entity.Product;
import org.example.springboot.entity.User;
import org.example.springboot.entity.dto.shop.ShopDTO;
import org.example.springboot.entity.dto.shop.ShopReviewDTO;
import org.example.springboot.entity.dto.shop.ShopStatisticsDTO;
import org.example.springboot.enums.ErrorCodeEnum;
import org.example.springboot.exception.BusinessException;
import org.example.springboot.mapper.ProductMapper;
import org.example.springboot.mapper.ShopMapper;
import org.example.springboot.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 店铺服务
 */
@Service
public class ShopService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShopService.class);

    @Resource
    private UserMapper userMapper;

    @Resource
    private ProductMapper productMapper;

    @Resource
    private ShopMapper shopMapper;

    /**
     * 获取店铺信息
     *
     * @param merchantId 商户ID
     * @return 店铺详情DTO（含评分、商品数、总销量等统计数据）
     * @author IhaveBB
     * @date 2026/03/22
     */
    public ShopDTO getShopInfo(Long merchantId) {
        User merchant = userMapper.selectById(merchantId);
        if (merchant == null) {
            throw new BusinessException(ErrorCodeEnum.USER_NOT_FOUND, "商户不存在");
        }

        ShopDTO dto = new ShopDTO();
        dto.setId(merchant.getId());
        dto.setShopName(merchant.getUsername());
        dto.setMerchantName(merchant.getName());
        dto.setLocation(merchant.getLocation());
        dto.setBusinessLicense(merchant.getBusinessLicense());

        // 获取统计数据
        ShopStatisticsDTO statistics = shopMapper.getShopStatistics(merchantId);
        if (statistics != null) {
            dto.setRating(statistics.getAverageRating());
            dto.setReviewCount(statistics.getReviewCount());
        }

        // 商品总数
        Integer productCount = shopMapper.getProductCount(merchantId);
        dto.setProductCount(productCount != null ? productCount : 0);

        // 总销量
        Integer totalSales = shopMapper.getTotalSales(merchantId);
        dto.setTotalSales(totalSales != null ? totalSales : 0);

        return dto;
    }

    /**
     * 获取店铺商品列表（分页）
     *
     * @param merchantId 商户ID
     * @param pageNum    页码
     * @param pageSize   每页条数
     * @return 包含商品列表和总数的Map
     * @author IhaveBB
     * @date 2026/03/22
     */
    public Map<String, Object> getShopProducts(Long merchantId, Integer pageNum, Integer pageSize) {
        Map<String, Object> result = new HashMap<>();

        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getMerchantId, merchantId)
               .eq(Product::getStatus, 1)
               .orderByDesc(Product::getCreatedAt);

        if (pageNum != null && pageSize != null) {
            long offset = (long) (pageNum - 1) * pageSize;
            wrapper.last("LIMIT " + offset + ", " + pageSize);
        }

        List<Product> products = productMapper.selectList(wrapper);

        // 查询总数
        LambdaQueryWrapper<Product> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(Product::getMerchantId, merchantId)
                    .eq(Product::getStatus, 1);
        Long total = productMapper.selectCount(countWrapper);

        result.put("products", products);
        result.put("total", total);
        result.put("pageNum", pageNum);
        result.put("pageSize", pageSize);

        return result;
    }

    /**
     * 获取店铺评价列表（分页）
     *
     * @param merchantId 商户ID
     * @param pageNum    页码
     * @param pageSize   每页条数
     * @return 包含评价列表和总数的Map
     * @author IhaveBB
     * @date 2026/03/22
     */
    public Map<String, Object> getShopReviews(Long merchantId, Integer pageNum, Integer pageSize) {
        Map<String, Object> result = new HashMap<>();

        long offset = (long) (pageNum - 1) * pageSize;
        List<ShopReviewDTO> reviews = shopMapper.getShopReviews(merchantId, offset, pageSize);

        // 查询总数
        ShopStatisticsDTO statistics = shopMapper.getShopStatistics(merchantId);
        long total = statistics != null ? statistics.getReviewCount() : 0;

        result.put("reviews", reviews);
        result.put("total", total);
        result.put("pageNum", pageNum);
        result.put("pageSize", pageSize);

        return result;
    }

    /**
     * 获取店铺统计信息（好评率、各星级评价数等）
     *
     * @param merchantId 商户ID
     * @return 包含统计信息的Map
     * @author IhaveBB
     * @date 2026/03/22
     */
    public Map<String, Object> getShopStatistics(Long merchantId) {
        Map<String, Object> result = new HashMap<>();

        ShopStatisticsDTO statistics = shopMapper.getShopStatistics(merchantId);
        if (statistics == null) {
            statistics = new ShopStatisticsDTO();
            statistics.setReviewCount(0L);
            statistics.setAverageRating(0.0);
            statistics.setGoodReviewCount(0L);
            statistics.setMediumReviewCount(0L);
            statistics.setBadReviewCount(0L);
            statistics.setTotalRating(0.0);
            statistics.setPositiveRate("0%");
        } else {
            // 计算好评率
            if (statistics.getReviewCount() > 0) {
                double positiveRate = (double) statistics.getGoodReviewCount() / statistics.getReviewCount() * 100;
                statistics.setPositiveRate(String.format("%.1f", positiveRate) + "%");
            } else {
                statistics.setPositiveRate("0%");
            }
        }

        result.put("statistics", statistics);
        return result;
    }
}
