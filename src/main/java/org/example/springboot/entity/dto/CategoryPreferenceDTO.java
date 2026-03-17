package org.example.springboot.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data; /**
 * 品类偏好 DTO
 */
@Data
@Schema(description = "品类偏好")
public class CategoryPreferenceDTO {

    @Schema(description = "分类 ID")
    private Long categoryId;

    @Schema(description = "分类名称")
    private String categoryName;

    @Schema(description = "偏好权重")
    private Double weight;
}
