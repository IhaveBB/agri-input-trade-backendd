package org.example.springboot.entity.dto.shop;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 店铺信息 DTO
 */
@Data
public class ShopDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 店铺 ID（商户 ID） */
    private Long id;

    /** 店铺名称（商户用户名） */
    private String shopName;

    /** 商户真实姓名 */
    private String merchantName;

    /** 店铺位置 */
    private String location;

    /** 营业执照 URL */
    private String businessLicense;

    /** 店铺评分 */
    private Double rating;

    /** 评价总数 */
    private Long reviewCount;

    /** 商品总数 */
    private Integer productCount;

    /** 总销量 */
    private Integer totalSales;

    /** 店铺描述 */
    private String description;
}
