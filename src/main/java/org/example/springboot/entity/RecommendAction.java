package org.example.springboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 推荐行为记录实体
 * 用于埋点记录推荐商品的用户行为，评估推荐系统效果
 */
@Data
@TableName("recommend_action")
@Schema(description = "推荐行为记录")
public class RecommendAction {

    @TableId(type = IdType.AUTO)
    @Schema(description = "记录ID")
    private Long id;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "商品ID")
    private Long productId;

    @Schema(description = "商品分类ID")
    private Long categoryId;

    /**
     * 行为类型
     * EXPOSURE - 曝光（推荐商品展示给用户）
     * CLICK - 点击（用户点击查看商品详情）
     * COLLECT - 收藏（用户收藏商品）
     * CART - 加入购物车
     * BUY - 购买（最终转化）
     */
    @Schema(description = "行为类型: EXPOSURE/CLICK/COLLECT/CART/BUY")
    private String actionType;

    /**
     * 推荐来源/算法类型
     * COLLABORATIVE - 协同过滤推荐
     * LOCATION - 地理位置推荐
     * HOT - 热门推荐
     * HYBRID - 混合推荐
     * NATURAL - 自然流量（非推荐）
     */
    @Schema(description = "推荐来源: COLLABORATIVE/LOCATION/HOT/HYBRID/NATURAL")
    private String source;

    /**
     * 推荐位置/场景
     * HOME_PAGE - 首页推荐
     * PRODUCT_DETAIL - 商品详情页推荐
     * CART_PAGE - 购物车页推荐
     * SEARCH_RESULT - 搜索结果推荐
     */
    @Schema(description = "推荐场景")
    private String scene;

    @Schema(description = "推荐列表中的位置（从1开始）")
    private Integer position;

    @Schema(description = "推荐商品ID列表（上下文，用于归因）")
    private String contextProductIds;

    @Schema(description = "停留时长（秒），仅CLICK行为有效")
    private Integer duration;

    @Schema(description = "行为发生时间")
    private LocalDateTime createdAt;
}
