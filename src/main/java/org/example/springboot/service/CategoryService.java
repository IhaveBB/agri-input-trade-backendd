package org.example.springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.springboot.common.Result;
import org.example.springboot.entity.Category;
import org.example.springboot.entity.Product;
import org.example.springboot.mapper.CategoryMapper;
import org.example.springboot.mapper.ProductMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CategoryService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CategoryService.class);

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private ProductMapper productMapper;

    /**
     * 创建分类
     */
    public Result<?> createCategory(Category category, Long userId) {
        try {
            // 参数校验
            if (category.getName() == null || category.getName().trim().isEmpty()) {
                return Result.error("-1", "分类名称不能为空");
            }

            // 校验父分类
            Long parentId = category.getParentId();
            Integer level = 1; // 默认一级分类

            if (parentId != null && parentId > 0) {
                Category parent = categoryMapper.selectById(parentId);
                if (parent == null) {
                    return Result.error("-1", "父分类不存在");
                }
                level = parent.getLevel() + 1;
                if (level > 3) {
                    return Result.error("-1", "最多支持三级分类");
                }
            } else {
                parentId = 0L; // 顶级分类
                category.setParentId(0L);
            }

            // 检查名称是否重复（同级别下不能有同名）
            LambdaQueryWrapper<Category> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Category::getName, category.getName())
                    .eq(Category::getParentId, parentId);
            Long count = categoryMapper.selectCount(queryWrapper);
            if (count > 0) {
                return Result.error("-1", "同级分类下已存在同名分类");
            }

            // 设置默认值
            category.setLevel(level);
            if (category.getSortOrder() == null) {
                category.setSortOrder(0);
            }
            if (category.getStatus() == null) {
                category.setStatus(1);
            }
            if (category.getIsCustom() == null) {
                // 判断是否为商家自定义分类：如果有用户ID则是自定义，否则是系统预置
                category.setIsCustom(userId != null ? 1 : 0);
            }
            category.setCreateUserId(userId);

            int result = categoryMapper.insert(category);
            if (result > 0) {
                LOGGER.info("创建分类成功，分类ID：{}，层级：{}", category.getId(), level);
                return Result.success(category);
            }
            return Result.error("-1", "创建分类失败");
        } catch (Exception e) {
            LOGGER.error("创建分类失败：{}", e.getMessage());
            return Result.error("-1", "创建分类失败：" + e.getMessage());
        }
    }

    /**
     * 更新分类
     */
    public Result<?> updateCategory(Long id, Category category) {
        try {
            Category existing = categoryMapper.selectById(id);
            if (existing == null) {
                return Result.error("-1", "分类不存在");
            }

            // 如果修改了名称，检查同级是否重复
            if (category.getName() != null && !category.getName().equals(existing.getName())) {
                Long parentId = existing.getParentId();
                LambdaQueryWrapper<Category> queryWrapper = new LambdaQueryWrapper<>();
                queryWrapper.eq(Category::getName, category.getName())
                        .eq(Category::getParentId, parentId)
                        .ne(Category::getId, id);
                Long count = categoryMapper.selectCount(queryWrapper);
                if (count > 0) {
                    return Result.error("-1", "同级分类下已存在同名分类");
                }
            }

            category.setId(id);
            // 不允许修改层级关系（会导致数据不一致）
            category.setParentId(null);
            category.setLevel(null);
            category.setCreateUserId(null);

            int result = categoryMapper.updateById(category);
            if (result > 0) {
                LOGGER.info("更新分类成功，分类ID：{}", id);
                return Result.success(categoryMapper.selectById(id));
            }
            return Result.error("-1", "更新分类失败");
        } catch (Exception e) {
            LOGGER.error("更新分类失败：{}", e.getMessage());
            return Result.error("-1", "更新分类失败：" + e.getMessage());
        }
    }

    /**
     * 删除分类
     */
    @Transactional
    public Result<?> deleteCategory(Long id) {
        try {
            Category category = categoryMapper.selectById(id);
            if (category == null) {
                return Result.error("-1", "分类不存在");
            }

            // 检查是否存在子分类
            LambdaQueryWrapper<Category> childQuery = new LambdaQueryWrapper<>();
            childQuery.eq(Category::getParentId, id);
            Long childCount = categoryMapper.selectCount(childQuery);
            if (childCount > 0) {
                return Result.error("-1", "无法删除分类，请先删除子分类");
            }

            // 检查是否存在关联商品
            LambdaQueryWrapper<Product> productQuery = new LambdaQueryWrapper<>();
            productQuery.eq(Product::getCategoryId, id);
            Long productCount = productMapper.selectCount(productQuery);
            if (productCount > 0) {
                return Result.error("-1", "无法删除分类，存在关联商品");
            }

            int result = categoryMapper.deleteById(id);
            if (result > 0) {
                LOGGER.info("删除分类成功，分类ID：{}", id);
                return Result.success();
            }
            return Result.error("-1", "删除分类失败");
        } catch (Exception e) {
            LOGGER.error("删除分类失败：{}", e.getMessage());
            return Result.error("-1", "删除分类失败：" + e.getMessage());
        }
    }

    /**
     * 根据ID获取分类详情
     */
    public Result<?> getCategoryById(Long id) {
        Category category = categoryMapper.selectById(id);
        if (category != null) {
            // 填充商品数量
            LambdaQueryWrapper<Product> productQuery = new LambdaQueryWrapper<>();
            productQuery.eq(Product::getCategoryId, id);
            category.setProductCount(Math.toIntExact(productMapper.selectCount(productQuery)));
            return Result.success(category);
        }
        return Result.error("-1", "未找到分类");
    }

    /**
     * 分页查询分类列表
     */
    public Result<?> getCategoriesByPage(String name, Long parentId, Integer level, Integer status,
                                          Integer currentPage, Integer size) {
        LambdaQueryWrapper<Category> queryWrapper = new LambdaQueryWrapper<>();
        if (name != null && !name.isEmpty()) {
            queryWrapper.like(Category::getName, name);
        }
        if (parentId != null) {
            queryWrapper.eq(Category::getParentId, parentId);
        }
        if (level != null) {
            queryWrapper.eq(Category::getLevel, level);
        }
        if (status != null) {
            queryWrapper.eq(Category::getStatus, status);
        }
        queryWrapper.orderByAsc(Category::getSortOrder).orderByDesc(Category::getCreatedAt);

        Page<Category> page = new Page<>(currentPage, size);
        Page<Category> result = categoryMapper.selectPage(page, queryWrapper);

        // 填充每个分类的商品数量
        result.getRecords().forEach(category -> {
            LambdaQueryWrapper<Product> productQuery = new LambdaQueryWrapper<>();
            productQuery.eq(Product::getCategoryId, category.getId());
            category.setProductCount(Math.toIntExact(productMapper.selectCount(productQuery)));
        });

        return Result.success(result);
    }

    /**
     * 获取所有分类（平铺列表）
     */
    public Result<?> getAllCategories() {
        LambdaQueryWrapper<Category> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByAsc(Category::getSortOrder).orderByAsc(Category::getLevel);

        List<Category> categories = categoryMapper.selectList(queryWrapper);
        if (categories != null && !categories.isEmpty()) {
            // 填充每个分类的商品数量
            categories.forEach(category -> {
                LambdaQueryWrapper<Product> productQuery = new LambdaQueryWrapper<>();
                productQuery.eq(Product::getCategoryId, category.getId());
                category.setProductCount(Math.toIntExact(productMapper.selectCount(productQuery)));
            });
            return Result.success(categories);
        }
        return Result.success(new ArrayList<>());
    }

    /**
     * 获取分类树形结构
     */
    public Result<?> getCategoryTree() {
        LambdaQueryWrapper<Category> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Category::getStatus, 1); // 只获取启用的分类
        queryWrapper.orderByAsc(Category::getSortOrder).orderByAsc(Category::getLevel);

        List<Category> allCategories = categoryMapper.selectList(queryWrapper);
        if (allCategories == null || allCategories.isEmpty()) {
            return Result.success(new ArrayList<>());
        }

        // 构建树形结构
        List<Category> tree = buildCategoryTree(allCategories);
        return Result.success(tree);
    }

    /**
     * 根据父分类ID获取子分类
     */
    public Result<?> getCategoriesByParentId(Long parentId) {
        LambdaQueryWrapper<Category> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Category::getParentId, parentId);
        queryWrapper.eq(Category::getStatus, 1); // 只获取启用的分类
        queryWrapper.orderByAsc(Category::getSortOrder);

        List<Category> categories = categoryMapper.selectList(queryWrapper);
        if (categories != null && !categories.isEmpty()) {
            categories.forEach(category -> {
                LambdaQueryWrapper<Product> productQuery = new LambdaQueryWrapper<>();
                productQuery.eq(Product::getCategoryId, category.getId());
                category.setProductCount(Math.toIntExact(productMapper.selectCount(productQuery)));
            });
        }
        return Result.success(categories);
    }

    /**
     * 获取一级分类列表（用于前端首页展示）
     */
    public Result<?> getTopCategories() {
        LambdaQueryWrapper<Category> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Category::getParentId, 0);
        queryWrapper.eq(Category::getStatus, 1);
        queryWrapper.orderByAsc(Category::getSortOrder);

        List<Category> categories = categoryMapper.selectList(queryWrapper);
        if (categories != null && !categories.isEmpty()) {
            categories.forEach(category -> {
                LambdaQueryWrapper<Product> productQuery = new LambdaQueryWrapper<>();
                productQuery.eq(Product::getCategoryId, category.getId());
                category.setProductCount(Math.toIntExact(productMapper.selectCount(productQuery)));
            });
        }
        return Result.success(categories);
    }

    /**
     * 批量删除分类
     */
    @Transactional
    public Result<?> deleteBatch(List<Long> ids) {
        try {
            // 递归检查是否有子分类或关联商品
            for (Long id : ids) {
                Result<?> checkResult = checkCanDelete(id);
                if (!"0".equals(checkResult.getCode())) {
                    return checkResult;
                }
            }

            // 递归删除所有子分类
            for (Long id : ids) {
                deleteCategoryRecursive(id);
            }

            LOGGER.info("批量删除分类成功，删除数量：{}", ids.size());
            return Result.success();
        } catch (Exception e) {
            LOGGER.error("批量删除分类失败：{}", e.getMessage());
            return Result.error("-1", "批量删除分类失败：" + e.getMessage());
        }
    }

    /**
     * 检查分类是否可以删除
     */
    private Result<?> checkCanDelete(Long id) {
        // 检查是否存在子分类
        LambdaQueryWrapper<Category> childQuery = new LambdaQueryWrapper<>();
        childQuery.eq(Category::getParentId, id);
        Long childCount = categoryMapper.selectCount(childQuery);
        if (childCount > 0) {
            return Result.error("-1", "无法删除分类ID：" + id + "，存在子分类");
        }

        // 检查是否存在关联商品
        LambdaQueryWrapper<Product> productQuery = new LambdaQueryWrapper<>();
        productQuery.eq(Product::getCategoryId, id);
        Long productCount = productMapper.selectCount(productQuery);
        if (productCount > 0) {
            return Result.error("-1", "无法删除分类ID：" + id + "，存在关联商品");
        }

        return Result.success();
    }

    /**
     * 递归删除分类及其子分类
     */
    private void deleteCategoryRecursive(Long id) {
        // 先删除所有子分类
        LambdaQueryWrapper<Category> childQuery = new LambdaQueryWrapper<>();
        childQuery.eq(Category::getParentId, id);
        List<Category> children = categoryMapper.selectList(childQuery);

        for (Category child : children) {
            deleteCategoryRecursive(child.getId());
        }

        // 再删除当前分类
        categoryMapper.deleteById(id);
        LOGGER.info("删除分类ID：{}", id);
    }

    /**
     * 构建分类树形结构
     */
    private List<Category> buildCategoryTree(List<Category> allCategories) {
        // 获取所有一级分类
        List<Category> rootCategories = allCategories.stream()
                .filter(c -> c.getParentId() == null || c.getParentId() == 0)
                .collect(Collectors.toList());

        // 递归填充子分类
        for (Category root : rootCategories) {
            fillChildren(root, allCategories);
        }

        return rootCategories;
    }

    /**
     * 递归填充子分类
     */
    private void fillChildren(Category parent, List<Category> allCategories) {
        List<Category> children = allCategories.stream()
                .filter(c -> parent.getId().equals(c.getParentId()))
                .collect(Collectors.toList());

        for (Category child : children) {
            fillChildren(child, allCategories);
        }

        parent.setChildren(children);
    }

    /**
     * 商家申请新增自定义分类
     */
    public Result<?> applyCustomCategory(Category category, Long userId) {
        try {
            // 商家自定义分类的限制
            if (category.getName() == null || category.getName().trim().isEmpty()) {
                return Result.error("-1", "分类名称不能为空");
            }
            if (category.getParentId() == null || category.getParentId() <= 0) {
                return Result.error("-1", "必须选择父级分类");
            }

            // 检查父分类是否存在
            Category parent = categoryMapper.selectById(category.getParentId());
            if (parent == null) {
                return Result.error("-1", "父分类不存在");
            }
            if (parent.getLevel() >= 3) {
                return Result.error("-1", "最多支持三级分类，无法在三级分类下再添加");
            }

            // 检查名称是否重复（同级别下不能有同名）
            LambdaQueryWrapper<Category> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Category::getName, category.getName())
                    .eq(Category::getParentId, category.getParentId());
            Long count = categoryMapper.selectCount(queryWrapper);
            if (count > 0) {
                return Result.error("-1", "同级分类下已存在同名分类");
            }

            // 检查是否存在名称包含关系（如已存在"小麦"，则不能创建"高产小麦"）
            LambdaQueryWrapper<Category> likeQuery = new LambdaQueryWrapper<>();
            likeQuery.like(Category::getName, category.getName())
                    .eq(Category::getParentId, category.getParentId());
            Long likeCount = categoryMapper.selectCount(likeQuery);
            if (likeCount > 0) {
                return Result.error("-1", "分类名称与现有分类相似，请使用其他名称");
            }

            // 创建商家自定义分类（待审核状态默认启用）
            category.setLevel(parent.getLevel() + 1);
            if (category.getSortOrder() == null) {
                category.setSortOrder(0);
            }
            category.setStatus(1); // 商家自定义分类默认启用
            category.setIsCustom(1); // 标记为商家自定义
            category.setCreateUserId(userId);

            int result = categoryMapper.insert(category);
            if (result > 0) {
                LOGGER.info("商家申请新增分类成功，分类ID：{}，申请人ID：{}", category.getId(), userId);
                return Result.success(category);
            }
            return Result.error("-1", "申请新增分类失败");
        } catch (Exception e) {
            LOGGER.error("申请新增分类失败：{}", e.getMessage());
            return Result.error("-1", "申请新增分类失败：" + e.getMessage());
        }
    }
}
