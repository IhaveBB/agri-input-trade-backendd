package org.example.springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.springboot.common.Result;
import org.example.springboot.entity.*;
import org.example.springboot.entity.dto.ProductCreateDTO;
import org.example.springboot.entity.vo.ExtFieldConfigVO;
import org.example.springboot.entity.vo.ProductVO;
import org.example.springboot.mapper.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ProductExtService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProductExtService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ProductCropMapper productCropMapper;

    @Autowired
    private ProductRegionSeasonMapper productRegionSeasonMapper;

    @Autowired
    private RegionMapper regionMapper;

    @Autowired
    private SeasonMapper seasonMapper;

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private UserMapper userMapper;

    /**
     * 创建商品（含扩展信息）
     */
    @Transactional
    public Result<ProductVO> createProduct(ProductCreateDTO dto, Long merchantId) {
        try {
            // 1. 保存商品主信息
            Product product = convertToProduct(dto);
            product.setMerchantId(merchantId);
            product.setStatus(1);

            // 处理扩展属性
            if (dto.getExtraAttributes() != null) {
                product.setExtraAttributes(toJson(dto.getExtraAttributes()));
            }

            int result = productMapper.insert(product);
            if (result <= 0) {
                return Result.error("-1", "创建商品失败");
            }

            Long productId = product.getId();

            // 2. 保存适用作物（使用种子分类ID）
            if (dto.getCategoryIds() != null && !dto.getCategoryIds().isEmpty()) {
                saveProductCrops(productId, dto.getCategoryIds());
            }

            // 3. 保存区域-季节配置
            if (dto.getRegionSeasonConfigs() != null && !dto.getRegionSeasonConfigs().isEmpty()) {
                saveProductRegionSeasons(productId, dto.getRegionSeasonConfigs());
            }

            LOGGER.info("创建商品成功，商品ID：{}，商户ID：{}", productId, merchantId);
            return Result.success(convertToVO(productMapper.selectById(productId)));
        } catch (Exception e) {
            LOGGER.error("创建商品失败：{}", e.getMessage());
            return Result.error("-1", "创建商品失败：" + e.getMessage());
        }
    }

    /**
     * 更新商品（含扩展信息）
     */
    @Transactional
    public Result<ProductVO> updateProduct(Long id, ProductCreateDTO dto) {
        try {
            Product existingProduct = productMapper.selectById(id);
            if (existingProduct == null) {
                return Result.error("-1", "商品不存在");
            }

            Product product = convertToProduct(dto);
            product.setId(id);
            product.setMerchantId(existingProduct.getMerchantId());
            product.setCreatedAt(existingProduct.getCreatedAt());

            if (dto.getExtraAttributes() != null) {
                product.setExtraAttributes(toJson(dto.getExtraAttributes()));
            } else {
                product.setExtraAttributes(null);
            }

            int result = productMapper.updateById(product);
            if (result <= 0) {
                return Result.error("-1", "更新商品失败");
            }

            // 更新适用作物（使用种子分类ID）
            if (dto.getCategoryIds() != null) {
                LambdaQueryWrapper<ProductCrop> deleteCropQuery = new LambdaQueryWrapper<>();
                deleteCropQuery.eq(ProductCrop::getProductId, id);
                productCropMapper.delete(deleteCropQuery);

                if (!dto.getCategoryIds().isEmpty()) {
                    saveProductCrops(id, dto.getCategoryIds());
                }
            }

            // 更新区域-季节配置
            if (dto.getRegionSeasonConfigs() != null) {
                LambdaQueryWrapper<ProductRegionSeason> deleteQuery = new LambdaQueryWrapper<>();
                deleteQuery.eq(ProductRegionSeason::getProductId, id);
                productRegionSeasonMapper.delete(deleteQuery);

                if (!dto.getRegionSeasonConfigs().isEmpty()) {
                    saveProductRegionSeasons(id, dto.getRegionSeasonConfigs());
                }
            }

            LOGGER.info("更新商品成功，商品ID：{}", id);
            return Result.success(convertToVO(productMapper.selectById(id)));
        } catch (Exception e) {
            LOGGER.error("更新商品失败：{}", e.getMessage());
            return Result.error("-1", "更新商品失败：" + e.getMessage());
        }
    }

    /**
     * 获取商品详情（含扩展信息）
     */
    public Result<ProductVO> getProductWithExt(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            return Result.error("-1", "商品不存在");
        }

        ProductVO vo = convertToVO(product);

        // 填充适用作物（从分类表查询）
        LambdaQueryWrapper<ProductCrop> cropQuery = new LambdaQueryWrapper<>();
        cropQuery.eq(ProductCrop::getProductId, id);
        List<ProductCrop> productCrops = productCropMapper.selectList(cropQuery);
        if (!productCrops.isEmpty()) {
            List<Long> categoryIds = productCrops.stream()
                    .map(ProductCrop::getCategoryId)
                    .collect(Collectors.toList());
            List<Category> categories = categoryMapper.selectBatchIds(categoryIds);
            vo.setCrops(categories.stream().map(c -> {
                ProductVO.CropVO cropVO = new ProductVO.CropVO();
                cropVO.setId(c.getId());
                cropVO.setName(c.getName());
                cropVO.setParentId(c.getParentId());
                return cropVO;
            }).collect(Collectors.toList()));
        }

        // 填充区域-季节配置
        LambdaQueryWrapper<ProductRegionSeason> regionSeasonQuery = new LambdaQueryWrapper<>();
        regionSeasonQuery.eq(ProductRegionSeason::getProductId, id);
        List<ProductRegionSeason> regionSeasonList = productRegionSeasonMapper.selectList(regionSeasonQuery);
        if (!regionSeasonList.isEmpty()) {
            List<ProductVO.ProductRegionSeasonVO> vos = new ArrayList<>();
            for (ProductRegionSeason prs : regionSeasonList) {
                ProductVO.ProductRegionSeasonVO prsVO = new ProductVO.ProductRegionSeasonVO();
                prsVO.setId(prs.getId());
                prsVO.setRegionId(prs.getRegionId());
                prsVO.setSeasonId(prs.getSeasonId());

                Region region = regionMapper.selectById(prs.getRegionId());
                Season season = seasonMapper.selectById(prs.getSeasonId());
                if (region != null) prsVO.setRegionName(region.getName());
                if (season != null) prsVO.setSeasonName(season.getName());

                vos.add(prsVO);
            }
            vo.setRegionSeasonList(vos);
        }

        return Result.success(vo);
    }

    /**
     * 获取分类扩展字段配置
     */
    public Result<List<ExtFieldConfigVO>> getExtFieldsByCategory(Long categoryId) {
        List<ExtFieldConfigVO> configs = getExtFieldsConfig();
        if (categoryId != null) {
            // 根据categoryId过滤，返回对应分类的配置
            // 注意：categoryId是Long类型，配置中的categoryId是Integer，需要转换比较
            final Long filterId = categoryId;
            configs = configs.stream()
                    .filter(c -> c.getCategoryId() != null && c.getCategoryId().longValue() == filterId.longValue())
                    .collect(java.util.stream.Collectors.toList());
        }
        return Result.success(configs);
    }

    // ==================== 私有方法 ====================

    private void saveProductCrops(Long productId, List<Long> categoryIds) {
        for (Long categoryId : categoryIds) {
            ProductCrop productCrop = new ProductCrop();
            productCrop.setProductId(productId);
            productCrop.setCategoryId(categoryId);
            productCropMapper.insert(productCrop);
        }
    }

    private void saveProductRegionSeasons(Long productId, List<ProductCreateDTO.RegionSeasonConfigDTO> configs) {
        // 全国区域ID（根据region表数据）
        final Long NATIONAL_REGION_ID = 8L;
        // 全年季节ID（根据season表数据）
        final Long FULL_YEAR_SEASON_ID = 5L;

        // 处理配置：检查是否有全国/全年
        boolean hasNational = configs.stream()
                .anyMatch(c -> NATIONAL_REGION_ID.equals(c.getRegionId()));
        boolean hasFullYear = configs.stream()
                .anyMatch(c -> c.getSeasonIds() != null && c.getSeasonIds().contains(FULL_YEAR_SEASON_ID));

        for (ProductCreateDTO.RegionSeasonConfigDTO config : configs) {
            if (config.getSeasonIds() == null || config.getSeasonIds().isEmpty()) {
                continue;
            }

            // 如果有全国，只处理全国
            if (hasNational && !NATIONAL_REGION_ID.equals(config.getRegionId())) {
                continue;
            }

            List<Long> seasonIds = config.getSeasonIds();

            // 如果有全年，只处理全年
            if (hasFullYear) {
                seasonIds = seasonIds.stream()
                        .filter(s -> FULL_YEAR_SEASON_ID.equals(s))
                        .collect(Collectors.toList());
            }

            for (Long seasonId : seasonIds) {
                ProductRegionSeason prs = new ProductRegionSeason();
                prs.setProductId(productId);
                prs.setRegionId(config.getRegionId());
                prs.setSeasonId(seasonId);
                productRegionSeasonMapper.insert(prs);
            }
        }
    }

    private Product convertToProduct(ProductCreateDTO dto) {
        Product product = new Product();
        product.setName(dto.getName());
        product.setDescription(dto.getDescription());
        product.setPrice(dto.getPrice());
        product.setStock(dto.getStock());
        product.setIsDiscount(dto.getIsDiscount());
        product.setDiscountPrice(dto.getDiscountPrice());
        product.setCategoryId(dto.getCategoryId());
        product.setImageUrl(dto.getImageUrl());
        product.setPlaceOfOrigin(dto.getPlaceOfOrigin());
        return product;
    }

    private ProductVO convertToVO(Product product) {
        ProductVO vo = new ProductVO();
        vo.setId(product.getId());
        vo.setName(product.getName());
        vo.setDescription(product.getDescription());
        vo.setPrice(product.getPrice());
        vo.setStock(product.getStock());
        vo.setIsDiscount(product.getIsDiscount());
        vo.setDiscountPrice(product.getDiscountPrice());
        vo.setCategoryId(product.getCategoryId());
        vo.setImageUrl(product.getImageUrl());
        vo.setSalesCount(product.getSalesCount());
        vo.setMerchantId(product.getMerchantId());
        vo.setStatus(product.getStatus());
        vo.setPlaceOfOrigin(product.getPlaceOfOrigin());
        vo.setCreatedAt(product.getCreatedAt() != null ? product.getCreatedAt().toLocalDateTime() : null);
        vo.setUpdatedAt(product.getUpdatedAt() != null ? product.getUpdatedAt().toLocalDateTime() : null);

        // 解析扩展属性
        if (product.getExtraAttributes() != null) {
            vo.setExtraAttributes(parseJson(product.getExtraAttributes()));
        }

        // 填充分类名称
        if (product.getCategoryId() != null) {
            Category category = categoryMapper.selectById(product.getCategoryId());
            if (category != null) {
                vo.setCategoryName(category.getName());
            }
        }

        // 填充商户名称
        if (product.getMerchantId() != null) {
            User merchant = userMapper.selectById(product.getMerchantId());
            if (merchant != null) {
                vo.setMerchantName(merchant.getUsername());
            }
        }

        return vo;
    }

    private String toJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            LOGGER.error("Map转JSON失败：{}", e.getMessage());
            return null;
        }
    }

    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            LOGGER.error("JSON转Map失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取扩展字段配置
     */
    private List<ExtFieldConfigVO> getExtFieldsConfig() {
        List<ExtFieldConfigVO> configs = new ArrayList<>();

        // 种子
        configs.add(buildConfig(1, "种子", new String[]{
            "seedType:种子类别:select:1-常规种,2-杂交种",
            "varietyName:品种名称:text",
            "manufacturer:生产商:text",
            "importer:进口商/分装单位:text",
            "qualityIndicator:质量指标:textarea",
            "varietyDescription:品种说明:textarea",
            "netContent:净含量:text",
            "productionDate:生产年月:text",
            "seedBusinessLicense:经营许可证编号:text",
            "isGmo:是否转基因:select:0-否,1-是"
        }));

        // 农药
        configs.add(buildConfig(2, "农药", new String[]{
            "registrationNumber:农药登记证号:text",
            "productionLicenseNumber:生产许可证号:text",
            "activeIngredientContent:有效成分含量:text",
            "formulationType:剂型:text",
            "toxicityLevel:毒性等级:select:1-微毒,2-低毒,3-中毒,4-高毒,5-剧毒"
        }));

        // 肥料
        configs.add(buildConfig(3, "肥料", new String[]{
            "fertilizerGrade:肥料等级:select:1-优等品,2-一等品,3-合格品",
            "nutrientContent:养分含量:textarea",
            "additiveContent:添加物含量:textarea"
        }));

        // 饲料
        configs.add(buildConfig(4, "饲料", new String[]{
            "compositionGuarantee:产品成分分析保证值:textarea",
            "rawMaterialComposition:原料组成:textarea"
        }));

        // 兽药
        configs.add(buildConfig(5, "兽药", new String[]{
            "effectiveComponent:有效成分:text",
            "pharmacologicalEffect:药理作用:textarea",
            "indications:适用症状:textarea",
            "dosage:用法用量:text",
            "withdrawalPeriod:休药期:text",
            "adverseReactions:不良反应:textarea"
        }));

        // 农膜
        configs.add(buildConfig(6, "农膜", new String[]{
            "applicableScope:适用范围:text",
            "filmCategory:薄膜类别:text",
            "thickness:厚度:text",
            "width:宽度:text",
            "length:长度:text"
        }));

        // 农机
        configs.add(buildConfig(7, "农机", new String[]{
            "purpose:用途:text",
            "structureParams:产品结构参数:textarea",
            "material:材质:text"
        }));

        return configs;
    }

    private ExtFieldConfigVO buildConfig(Integer categoryId, String categoryName, String[] fieldDefs) {
        ExtFieldConfigVO config = new ExtFieldConfigVO();
        config.setCategoryId(categoryId);
        config.setCategoryName(categoryName);

        List<ExtFieldConfigVO.ExtFieldVO> fields = new ArrayList<>();
        for (String def : fieldDefs) {
            String[] parts = def.split(":");
            ExtFieldConfigVO.ExtFieldVO field = new ExtFieldConfigVO.ExtFieldVO();
            field.setKey(parts[0]);
            field.setLabel(parts[1]);
            field.setType(parts[2]);

            if (parts.length > 3) {
                List<ExtFieldConfigVO.OptionVO> options = new ArrayList<>();
                for (String opt : parts[3].split(",")) {
                    String[] optParts = opt.split("-");
                    ExtFieldConfigVO.OptionVO optionVO = new ExtFieldConfigVO.OptionVO();
                    optionVO.setValue(optParts[0]);
                    optionVO.setLabel(optParts.length > 1 ? optParts[1] : optParts[0]);
                    options.add(optionVO);
                }
                field.setOptions(options);
            }
            fields.add(field);
        }
        config.setFields(fields);

        return config;
    }
}
