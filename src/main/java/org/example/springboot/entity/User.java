package org.example.springboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;

@Data
@Schema(description = "用户实体")
public class User {
    @TableId(type = IdType.AUTO)
    @Schema(description = "用户ID")
    private Long id;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "密码")
    private String password;

    @Schema(description = "真实姓名")
    private String name;

    @Schema(description = "用户角色")
    private String role;

    @Schema(description = "电子邮箱")
    private String email;

    @Schema(description = "账号状态")
    private Integer status;

    @Schema(description = "营业执照URL")
    private String businessLicense;

    @Schema(description = "位置信息")
    private String location;

    @Schema(description = "感兴趣作物分类ID列表，逗号分隔（从注册信息获取）")
    private String interestedCrops;

    @Schema(description = "感兴趣动物分类ID列表，逗号分隔（从注册信息获取）")
    private String interestedAnimals;

    @Schema(description = "创建时间")
    private Timestamp createdAt;

    @Schema(description = "更新时间")
    private Timestamp updatedAt;

    @Schema(description = "账户余额")
    private BigDecimal balance;

    @TableField(exist = false)
    private List<Menu> menuList;
    @TableField(exist = false)
    private String token;
}
