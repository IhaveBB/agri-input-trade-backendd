package org.example.springboot.entity.dto.statistics;

import lombok.Data;
import java.io.Serializable;

/**
 * 商户信息DTO
 */
@Data
public class MerchantDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 商户ID */
    private Long id;

    /** 商户名称 */
    private String name;

    /** 商户用户名 */
    private String username;

    /** 商户地址 */
    private String location;
}
