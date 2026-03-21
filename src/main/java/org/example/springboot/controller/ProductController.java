package org.example.springboot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.springboot.annotation.RequiresRole;
import org.example.springboot.common.Result;
import org.example.springboot.entity.Product;
import org.example.springboot.enums.ErrorCodeEnum;
import org.example.springboot.exception.BusinessException;
import org.example.springboot.entity.dto.ProductCreateDTO;
import org.example.springboot.entity.vo.ExtFieldConfigVO;
import org.example.springboot.entity.vo.ProductVO;
import org.example.springboot.enumClass.UserRole;
import org.example.springboot.service.ProductExtService;
import org.example.springboot.service.ProductService;
import org.example.springboot.util.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商品管理控制器
 * 提供商品的增删改查功能
 *
 * @author IhaveBB
 * @date 2026/03/19
 */
@Tag(name = "商品管理接口")
@RestController
@RequestMapping("/product")
public class ProductController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProductController.class);

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductExtService productExtService;

    /**
     * 创建商品（基础版）
     * 权限：商户或管理员，需要手动设置商户ID
     *
     * @param product 商品实体
     * @return 创建成功的商品
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "创建商品（基础版）")
    @RequiresRole({"MERCHANT", "ADMIN"})
    @PostMapping
    public Result<Product> createProduct(@RequestBody Product product) {
        Long userId = UserContext.getUserId();
        // 设置商品所属商户ID为当前用户ID
        product.setMerchantId(userId);
        return Result.success(productService.createProduct(product));
    }

    /**
     * 创建商品（含扩展信息）
     * 权限：商户或管理员，需要手动设置商户ID
     *
     * @param dto 商品创建DTO
     * @return 创建成功的商品
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "创建商品（含扩展信息）")
    @RequiresRole({"MERCHANT", "ADMIN"})
    @PostMapping("/ext")
    public Result<ProductVO> createProductWithExt(@RequestBody ProductCreateDTO dto) {
        Long userId = UserContext.getUserId();
        return Result.success(productExtService.createProduct(dto, userId));
    }

    /**
     * 更新商品信息（基础版）
     * 权限：商户（只能修改自己的商品）或管理员
     *
     * @param id      商品ID
     * @param product 商品实体
     * @return 更新后的商品
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "更新商品信息（基础版）")
    @RequiresRole
    @PutMapping("/{id}")
    public Result<Product> updateProduct(@PathVariable Long id, @RequestBody Product product) {
        Long userId = UserContext.getUserId();
        String role = UserContext.getRole();

        Product existingProduct = productService.getProductByIdValue(id);
        if (existingProduct == null) {
            throw new BusinessException(ErrorCodeEnum.PRODUCT_NOT_FOUND);
        }

        // 权限检查：普通用户不能修改，商户只能修改自己的商品
        if (UserRole.isUser(role)) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权限修改商品");
        } else if (UserRole.isMerchant(role)) {
            if (!existingProduct.getMerchantId().equals(userId)) {
                throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权限修改其他商户的商品");
            }
        }
        // 管理员可以修改所有商品

        return Result.success(productService.updateProduct(id, product));
    }

    /**
     * 更新商品信息（含扩展信息）
     * 权限：商户（只能修改自己的商品）或管理员
     *
     * @param id  商品ID
     * @param dto 商品DTO
     * @return 更新后的商品
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "更新商品信息（含扩展信息）")
    @RequiresRole
    @PutMapping("/ext/{id}")
    public Result<ProductVO> updateProductWithExt(@PathVariable Long id, @RequestBody ProductCreateDTO dto) {
        Long userId = UserContext.getUserId();
        String role = UserContext.getRole();

        Product existingProduct = productService.getProductByIdValue(id);
        if (existingProduct == null) {
            throw new BusinessException(ErrorCodeEnum.PRODUCT_NOT_FOUND);
        }

        // 权限检查：普通用户不能修改，商户只能修改自己的商品
        if (UserRole.isUser(role)) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权限修改商品");
        } else if (UserRole.isMerchant(role)) {
            if (!existingProduct.getMerchantId().equals(userId)) {
                throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权限修改其他商户的商品");
            }
        }
        // 管理员可以修改所有商品

        return Result.success(productExtService.updateProduct(id, dto));
    }

    /**
     * 获取商品详情（含扩展信息）
     * 无需权限验证
     *
     * @param id 商品ID
     * @return 商品详情
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "获取商品详情（含扩展信息）")
    @GetMapping("/ext/{id}")
    public Result<ProductVO> getProductWithExt(@PathVariable Long id) {
        return Result.success(productExtService.getProductWithExt(id));
    }

    /**
     * 获取分类扩展字段配置
     * 无需权限验证
     *
     * @param categoryId 分类ID
     * @return 扩展字段配置列表
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "获取分类扩展字段配置")
    @GetMapping("/ext/fields")
    public Result<List<ExtFieldConfigVO>> getExtFieldsByCategory(
            @RequestParam(required = false) Long categoryId) {
        return Result.success(productExtService.getExtFieldsByCategory(categoryId));
    }

    /**
     * 删除商品
     * 权限：只有管理员
     *
     * @param id 商品ID
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "删除商品")
    @RequiresRole("ADMIN")
    @DeleteMapping("/{id}")
    public Result<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return Result.success();
    }

    /**
     * 根据ID获取商品详情
     * 无需权限验证
     *
     * @param id 商品ID
     * @return 商品详情
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "根据ID获取商品详情")
    @GetMapping("/{id}")
    public Result<Product> getProductById(@PathVariable Long id) {
        return Result.success(productService.getProductById(id));
    }

    /**
     * 分页查询商品列表
     * 无需权限验证，但商户只能查看自己店铺的商品
     *
     * @param name        商品名称
     * @param categoryId  分类ID
     * @param status      状态
     * @param currentPage 当前页
     * @param size        每页大小
     * @param sortField   排序字段
     * @param sortOrder   排序方式
     * @param minPrice    最低价格
     * @param maxPrice    最高价格
     * @return 分页商品列表
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "分页查询商品列表")
    @GetMapping("/page")
    public Result<?> getProductsByPage(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice) {

        Long userId = UserContext.getUserId();
        String role = UserContext.getRole();
        Long merchantId = null;

        if (userId != null && UserRole.isMerchant(role)) {
            // 商户只能查看自己店铺的商品
            merchantId = userId;
        }
        // 管理员和普通用户可以查看所有商品

        return Result.success(productService.getProductsByPage(name, categoryId, merchantId, status,
                currentPage, size, sortField, sortOrder, minPrice, maxPrice));
    }

    /**
     * 更新商品状态
     * 权限：商户（只能修改自己的商品）或管理员
     *
     * @param id     商品ID
     * @param status 新状态
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "更新商品状态")
    @RequiresRole
    @PutMapping("/{id}/status")
    public Result<Void> updateProductStatus(@PathVariable Long id, @RequestParam Integer status) {
        Long userId = UserContext.getUserId();
        String role = UserContext.getRole();

        // 获取商品信息
        Product product = productService.getProductByIdValue(id);
        if (product == null) {
            throw new BusinessException(ErrorCodeEnum.PRODUCT_NOT_FOUND);
        }

        // 权限检查：普通用户不能修改，商户只能修改自己的商品
        if (UserRole.isUser(role)) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权限修改商品状态");
        } else if (UserRole.isMerchant(role)) {
            if (!product.getMerchantId().equals(userId)) {
                throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权限修改其他商户的商品状态");
            }
        }
        // 管理员可以修改所有商品状态

        productService.updateProductStatus(id, status);
        return Result.success();
    }

    /**
     * 批量删除商品
     * 权限：只有管理员
     *
     * @param ids 商品ID列表
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "批量删除商品")
    @RequiresRole("ADMIN")
    @DeleteMapping("/batch")
    public Result<Void> deleteBatch(@RequestParam List<Long> ids) {
        productService.deleteBatch(ids);
        return Result.success();
    }

    /**
     * 获取全部商品
     * 无需权限验证，但商户只能查看自己店铺的商品
     *
     * @return 商品列表
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "获取全部商品")
    @GetMapping("/all")
    public Result<?> getAllProducts() {
        Long userId = UserContext.getUserId();
        String role = UserContext.getRole();
        Long merchantId = null;

        if (userId != null && UserRole.isMerchant(role)) {
            // 商户只能查看自己店铺的商品
            merchantId = userId;
        }

        return Result.success(productService.getProductsByPage(null, null, merchantId, null, 1, Integer.MAX_VALUE, null, null, null, null).getRecords());
    }

    /**
     * 批量更新商品状态
     * 权限：只有管理员
     *
     * @param ids    商品ID列表
     * @param status 新状态
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "批量更新商品状态")
    @RequiresRole("ADMIN")
    @PutMapping("/batch/status")
    public Result<Void> updateBatchStatus(@RequestParam List<Long> ids, @RequestParam Integer status) {
        productService.updateBatchStatus(ids, status);
        return Result.success();
    }

}
