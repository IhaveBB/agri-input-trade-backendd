package org.example.springboot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;
import org.example.springboot.entity.Product;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    /**
     * 扣减库存（带库存充足校验）
     *
     * @param productId 商品ID
     * @param quantity  扣减数量
     * @return 影响行数，返回1表示扣减成功，0表示库存不足
     */
    @Update("UPDATE product SET stock = stock - #{quantity} " +
            "WHERE id = #{productId} AND stock >= #{quantity}")
    int decreaseStock(Long productId, Integer quantity);

    /**
     * 增加销量（订单完成时调用）
     *
     * @param productId 商品ID
     * @param quantity  销售数量
     */
    @Update("UPDATE product SET sales_count = sales_count + #{quantity} WHERE id = #{productId}")
    int increaseSalesCount(Long productId, Integer quantity);
} 