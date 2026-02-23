package org.example.springboot.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "季节DTO")
public class SeasonDTO {
    @Schema(description = "季节ID")
    private Long id;

    @Schema(description = "季节名称")
    private String name;
}
