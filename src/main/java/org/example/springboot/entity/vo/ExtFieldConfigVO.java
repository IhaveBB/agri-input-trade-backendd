package org.example.springboot.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "分类扩展字段配置VO")
public class ExtFieldConfigVO {

    @Schema(description = "分类ID")
    private Integer categoryId;

    @Schema(description = "分类名称")
    private String categoryName;

    @Schema(description = "扩展字段列表")
    private List<ExtFieldVO> fields;

    @Data
    @Schema(description = "扩展字段VO")
    public static class ExtFieldVO {
        @Schema(description = "字段Key")
        private String key;

        @Schema(description = "字段标签")
        private String label;

        @Schema(description = "字段类型：text, textarea, select")
        private String type;

        @Schema(description = "选项列表（select类型用）")
        private List<OptionVO> options;
    }

    @Data
    @Schema(description = "选项VO")
    public static class OptionVO {
        @Schema(description = "选项值")
        private String value;

        @Schema(description = "选项标签")
        private String label;
    }
}
