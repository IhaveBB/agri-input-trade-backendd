package org.example.springboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.sql.Timestamp;

@Data
@Schema(description = "物流实体")
public class Logistics {
    @TableId(type = IdType.AUTO)
    @Schema(description = "物流ID")
    private Long id;

    @Schema(description = "订单ID")
    private Long orderId;

    @Schema(description = "收货人姓名")
    private String receiverName;

    @Schema(description = "收货人电话")
    private String receiverPhone;

    @Schema(description = "收货地址")
    private String receiverAddress;

    @Schema(description = "物流公司名称") 
    private String companyName;

    @Schema(description = "物流单号")
    private String trackingNumber;

    @Schema(description = "预计到达时间")
    //时间
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Timestamp expectedArrivalTime;

    @Schema(description = "物流状态:0待发货,1已发货,2已签收,3已取消")
    private Integer status;

    @Schema(description = "创建时间")
    private Timestamp createdAt;

    @Schema(description = "更新时间")
    private Timestamp updatedAt;

    @TableField(exist = false)
    @Schema(description = "订单信息")
    private Order order;
} 