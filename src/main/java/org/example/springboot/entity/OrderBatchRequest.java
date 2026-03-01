package org.example.springboot.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "批量创建订单请求")
public class OrderBatchRequest {
    @Schema(description = "用户 ID")
    private Long userId;

    @Schema(description = "地址 ID")
    private Long addressId;

    @Schema(description = "订单项列表")
    private List<OrderItem> items;

    @Data
    @Schema(description = "订单项")
    public static class OrderItem {
        @Schema(description = "商品 ID")
        private Long productId;

        @Schema(description = "购买数量")
        private Integer quantity;

        @Schema(description = "商品单价")
        private java.math.BigDecimal price;
    }
}
