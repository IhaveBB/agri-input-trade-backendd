package org.example.springboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.sql.Timestamp;

@Data
@Schema(description = "商品适用区域-季节关联实体")
@TableName("product_region_season")
public class ProductRegionSeason {
    @TableId(type = IdType.AUTO)
    @Schema(description = "ID")
    private Long id;

    @Schema(description = "商品ID")
    private Long productId;

    @Schema(description = "区域ID")
    private Long regionId;

    @Schema(description = "季节ID")
    private Long seasonId;

    @Schema(description = "创建时间")
    private Timestamp createdAt;
}
