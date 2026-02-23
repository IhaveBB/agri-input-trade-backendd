package org.example.springboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.sql.Timestamp;

@Data
@Schema(description = "商品适用作物关联实体")
@TableName("product_crop")
public class ProductCrop {
    @TableId(type = IdType.AUTO)
    @Schema(description = "ID")
    private Long id;

    @Schema(description = "商品ID")
    private Long productId;

    @Schema(description = "作物分类ID（对应category表的种子分类四级分类）")
    private Long categoryId;

    @Schema(description = "创建时间")
    private Timestamp createdAt;
}
