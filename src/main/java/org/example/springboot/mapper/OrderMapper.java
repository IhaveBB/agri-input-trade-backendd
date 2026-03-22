package org.example.springboot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.springboot.entity.Order;

/**
 * 订单 Mapper 接口
 *
 * @author IhaveBB
 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    /**
     * 获取商品在指定天数内的销量
     *
     * @param productId 商品ID
     * @param days      天数
     * @return 销量总数
     * @author IhaveBB
     * @date 2026/03/22
     */
    @Select("SELECT COALESCE(SUM(quantity), 0) FROM `order` " +
            "WHERE product_id = #{productId} AND status = 3 " +
            "AND created_at >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY)")
    Integer getProductSalesInDays(@Param("productId") Long productId, @Param("days") int days);

    /**
     * 获取商品昨日的销量
     *
     * @param productId 商品ID
     * @return 昨日销量
     * @author IhaveBB
     * @date 2026/03/22
     */
    @Select("SELECT COALESCE(SUM(quantity), 0) FROM `order` " +
            "WHERE product_id = #{productId} AND status = 3 " +
            "AND DATE(created_at) = DATE_SUB(CURDATE(), INTERVAL 1 DAY)")
    Integer getYesterdaySales(@Param("productId") Long productId);
} 