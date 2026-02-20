package org.example.springboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.sql.Timestamp;

@Data
@Schema(description = "物流公司实体")
public class Company {
    @TableId(type = IdType.AUTO)
    @Schema(description = "公司ID")
    private Long id;

    @Schema(description = "公司名称")
    private String name;

    @Schema(description = "公司代码")
    private String code;

    @Schema(description = "联系电话")
    private String phone;

    @Schema(description = "状态:0停用,1启用")
    private Integer status;

    @Schema(description = "创建时间")
    private Timestamp createdAt;

    @Schema(description = "更新时间")
    private Timestamp updatedAt;
} 