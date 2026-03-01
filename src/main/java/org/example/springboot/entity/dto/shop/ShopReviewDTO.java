package org.example.springboot.entity.dto.shop;

import lombok.Data;
import java.io.Serializable;

/**
 * 店铺评价 DTO
 */
@Data
public class ShopReviewDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 评价 ID */
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 用户名 */
    private String username;

    /** 商品 ID */
    private Long productId;

    /** 商品名称 */
    private String productName;

    /** 评分 */
    private Integer rating;

    /** 评价内容 */
    private String content;

    /** 评价图片 */
    private String imageUrl;

    /** 创建时间 */
    private String createdAt;
}
