package org.example.springboot.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.springboot.entity.dto.shop.ShopReviewDTO;
import org.example.springboot.entity.dto.shop.ShopStatisticsDTO;

import java.util.List;

/**
 * 店铺 Mapper
 */
@Mapper
public interface ShopMapper {

    /**
     * 获取店铺统计信息
     */
    @Select("SELECT " +
            "COUNT(DISTINCT r.id) as reviewCount, " +
            "COALESCE(AVG(r.rating), 0) as averageRating, " +
            "SUM(CASE WHEN r.rating >= 4 THEN 1 ELSE 0 END) as goodReviewCount, " +
            "SUM(CASE WHEN r.rating = 3 THEN 1 ELSE 0 END) as mediumReviewCount, " +
            "SUM(CASE WHEN r.rating <= 2 THEN 1 ELSE 0 END) as badReviewCount " +
            "FROM review r " +
            "JOIN product p ON r.product_id = p.id " +
            "WHERE p.merchant_id = #{merchantId}")
    ShopStatisticsDTO getShopStatistics(@Param("merchantId") Long merchantId);

    /**
     * 获取店铺评价列表
     */
    @Select("<script>" +
            "SELECT r.id, r.user_id, u.username, r.product_id, p.name as productName, " +
            "r.rating, r.content, r.created_at " +
            "FROM review r " +
            "JOIN product p ON r.product_id = p.id " +
            "JOIN user u ON r.user_id = u.id " +
            "WHERE p.merchant_id = #{merchantId} " +
            "ORDER BY r.created_at DESC " +
            "<if test='offset != null and limit != null'>" +
            "LIMIT #{offset}, #{limit}" +
            "</if>" +
            "</script>")
    List<ShopReviewDTO> getShopReviews(@Param("merchantId") Long merchantId,
                                       @Param("offset") Long offset,
                                       @Param("limit") Integer limit);

    /**
     * 获取店铺商品总数
     */
    @Select("SELECT COUNT(*) FROM product WHERE merchant_id = #{merchantId} AND status = 1 AND deleted = 0")
    Integer getProductCount(@Param("merchantId") Long merchantId);

    /**
     * 获取店铺总销量（基于已完成订单）
     */
    @Select("SELECT COALESCE(SUM(o.quantity), 0) FROM `order` o " +
            "JOIN product p ON o.product_id = p.id " +
            "WHERE p.merchant_id = #{merchantId} AND o.status = 3")
    Integer getTotalSales(@Param("merchantId") Long merchantId);
}
