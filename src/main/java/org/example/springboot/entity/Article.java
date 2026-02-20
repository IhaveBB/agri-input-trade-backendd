package org.example.springboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.sql.Timestamp;

@Data
@Schema(description = "资讯实体")
public class Article {
    @TableId(type = IdType.AUTO)
    @Schema(description = "资讯ID")
    private Long id;

    @NotBlank(message = "资讯标题不能为空")
    @Schema(description = "资讯标题")
    private String title;

    @NotBlank(message = "资讯内容不能为空")
    @Schema(description = "资讯内容")
    private String content;

    @Schema(description = "资讯摘要")
    private String summary;

    @Schema(description = "封面图片URL")
    private String coverImage;

    @Schema(description = "浏览量")
    private Integer viewCount = 0;

    @NotNull(message = "状态不能为空")
    @Schema(description = "状态：0-下架，1-上架")
    private Integer status = 1;

    @Schema(description = "创建时间")
    private Timestamp createdAt;

    @Schema(description = "更新时间")
    private Timestamp updatedAt;
} 