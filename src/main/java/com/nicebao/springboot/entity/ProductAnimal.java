package com.nicebao.springboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 商品适用动物关联实体
 *
 * @author IhaveBB
 * @date 2026/03/29
 */
@Data
@TableName("product_animal")
public class ProductAnimal {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long productId;

    private Long categoryId;
}
