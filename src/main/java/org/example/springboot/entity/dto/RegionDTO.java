package org.example.springboot.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "区域DTO")
public class RegionDTO {
    @Schema(description = "区域ID")
    private Long id;

    @Schema(description = "区域名称")
    private String name;
}
