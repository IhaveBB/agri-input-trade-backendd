package org.example.springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.springboot.entity.Category;
import org.example.springboot.entity.Product;
import org.example.springboot.entity.dto.CategoryCreateDTO;
import org.example.springboot.entity.dto.CategoryUpdateDTO;
import org.example.springboot.enums.ErrorCodeEnum;
import org.example.springboot.exception.BusinessException;
import org.example.springboot.mapper.CategoryMapper;
import org.example.springboot.mapper.ProductMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
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
     * 创建分类（管理员使用，使用 DTO）
     */
    public Category createCategory(CategoryCreateDTO dto) {
        Category category = new Category();
        BeanUtils.copyProperties(dto, category);
        return doCreateCategory(category, null);
    }

    /**
     * 商家申请新增自定义分类（使用 DTO）
     */
    public Category applyCustomCategory(CategoryCreateDTO dto, Long userId) {
        // 商家自定义分类的限制
        if (dto.getParentId() == null || dto.getParentId() <= 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "必须选择父级分类");
        }

        // 检查父分类是否存在
        Category parent = categoryMapper.selectById(dto.getParentId());
        if (parent == null) {
            throw new BusinessException(ErrorCodeEnum.CATEGORY_NOT_FOUND, "父分类不存在");
        }
        if (parent.getLevel() >= 3) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "最多支持三级分类，无法在三级分类下再添加");
        }

        // 检查名称是否重复（同级别下不能有同名）
        LambdaQueryWrapper<Category> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Category::getName, dto.getName())
                .eq(Category::getParentId, dto.getParentId());
        Long count = categoryMapper.selectCount(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCodeEnum.ALREADY_EXISTS, "同级分类下已存在同名分类");
        }

        // 检查是否存在名称包含关系
        LambdaQueryWrapper<Category> likeQuery = new LambdaQueryWrapper<>();
        likeQuery.like(Category::getName, dto.getName())
                .eq(Category::getParentId, dto.getParentId());
        Long likeCount = categoryMapper.selectCount(likeQuery);
        if (likeCount > 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "分类名称与现有分类相似，请使用其他名称");
        }

        // 构建分类实体
        Category category = new Category();
        BeanUtils.copyProperties(dto, category);
        category.setLevel(parent.getLevel() + 1);
        if (category.getSortOrder() == null) {
            category.setSortOrder(0);
        }
        category.setStatus(1);
        category.setIsCustom(1);
        category.setCreateUserId(userId);

        int result = categoryMapper.insert(category);
        if (result > 0) {
            LOGGER.info("商家申请新增分类成功，分类ID：{}，申请人ID：{}", category.getId(), userId);
            return category;
        }
        throw new BusinessException(ErrorCodeEnum.ERROR, "申请新增分类失败");
    }

    /**
     * 内部创建分类逻辑
     */
    private Category doCreateCategory(Category category, Long userId) {
        // 参数校验
        if (category.getName() == null || category.getName().trim().isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "分类名称不能为空");
        }

        // 校验父分类
        Long parentId = category.getParentId();
        Integer level = 1; // 默认一级分类

        if (parentId != null && parentId > 0) {
            Category parent = categoryMapper.selectById(parentId);
            if (parent == null) {
                throw new BusinessException(ErrorCodeEnum.CATEGORY_NOT_FOUND, "父分类不存在");
            }
            level = parent.getLevel() + 1;
            if (level > 3) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "最多支持三级分类");
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
            throw new BusinessException(ErrorCodeEnum.ALREADY_EXISTS, "同级分类下已存在同名分类");
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
            category.setIsCustom(userId != null ? 1 : 0);
        }
        category.setCreateUserId(userId);

        int result = categoryMapper.insert(category);
        if (result > 0) {
            LOGGER.info("创建分类成功，分类ID：{}，层级：{}", category.getId(), level);
            return category;
        }
        throw new BusinessException(ErrorCodeEnum.ERROR, "创建分类失败");
    }

    /**
     * 更新分类（使用 DTO）
     */
    public Category updateCategory(Long id, CategoryUpdateDTO dto) {
        Category existing = categoryMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCodeEnum.CATEGORY_NOT_FOUND, "分类不存在");
        }

        // 检查是否试图修改层级（parentId 或 level）
        if (dto.getParentId() != null && !dto.getParentId().equals(existing.getParentId())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "不允许修改分类层级");
        }
        if (dto.getLevel() != null && !dto.getLevel().equals(existing.getLevel())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "不允许修改分类层级");
        }

        // 如果修改了名称，检查同级是否重复
        if (dto.getName() != null && !dto.getName().equals(existing.getName())) {
            Long parentId = existing.getParentId();
            LambdaQueryWrapper<Category> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Category::getName, dto.getName())
                    .eq(Category::getParentId, parentId)
                    .ne(Category::getId, id);
            Long count = categoryMapper.selectCount(queryWrapper);
            if (count > 0) {
                throw new BusinessException(ErrorCodeEnum.ALREADY_EXISTS, "同级分类下已存在同名分类");
            }
        }

        // 更新字段
        Category category = new Category();
        category.setId(id);
        category.setName(dto.getName());
        category.setIcon(dto.getIcon());
        category.setDescription(dto.getDescription());
        category.setSortOrder(dto.getSortOrder());
        category.setStatus(dto.getStatus());

        int result = categoryMapper.updateById(category);
        if (result > 0) {
            LOGGER.info("更新分类成功，分类ID：{}", id);
            return categoryMapper.selectById(id);
        }
        throw new BusinessException(ErrorCodeEnum.ERROR, "更新分类失败");
    }

    /**
     * 删除分类
     */
    @Transactional
    public void deleteCategory(Long id) {
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException(ErrorCodeEnum.CATEGORY_NOT_FOUND, "分类不存在");
        }

        // 检查是否存在子分类
        LambdaQueryWrapper<Category> childQuery = new LambdaQueryWrapper<>();
        childQuery.eq(Category::getParentId, id);
        Long childCount = categoryMapper.selectCount(childQuery);
        if (childCount > 0) {
            throw new BusinessException(ErrorCodeEnum.HAS_ASSOCIATED_DATA, "无法删除分类，请先删除子分类");
        }

        // 检查是否存在关联商品
        LambdaQueryWrapper<Product> productQuery = new LambdaQueryWrapper<>();
        productQuery.eq(Product::getCategoryId, id);
        Long productCount = productMapper.selectCount(productQuery);
        if (productCount > 0) {
            throw new BusinessException(ErrorCodeEnum.HAS_ASSOCIATED_DATA, "无法删除分类，存在关联商品");
        }

        int result = categoryMapper.deleteById(id);
        if (result > 0) {
            LOGGER.info("删除分类成功，分类ID：{}", id);
        } else {
            throw new BusinessException(ErrorCodeEnum.ERROR, "删除分类失败");
        }
    }

    /**
     * 根据ID获取分类详情
     */
    public Category getCategoryById(Long id) {
        Category category = categoryMapper.selectById(id);
        if (category != null) {
            // 填充商品数量
            LambdaQueryWrapper<Product> productQuery = new LambdaQueryWrapper<>();
            productQuery.eq(Product::getCategoryId, id);
            category.setProductCount(Math.toIntExact(productMapper.selectCount(productQuery)));
            return category;
        }
        throw new BusinessException(ErrorCodeEnum.CATEGORY_NOT_FOUND, "未找到分类");
    }

    /**
     * 分页查询分类列表
     */
    public Page<Category> getCategoriesByPage(String name, Long parentId, Integer level, Integer status,
                                          Integer currentPage, Integer size) {
        LambdaQueryWrapper<Category> queryWrapper = new LambdaQueryWrapper<>();
        if (name != null && !name.isEmpty()) {
            queryWrapper.like(Category::getName, name);
        }

        // 如果只传了level没传parentId，按level查询；否则按parentId查询
        // 这样可以查询某个层级的所有分类
        if (level != null && parentId == null) {
            queryWrapper.eq(Category::getLevel, level);
        } else if (parentId != null) {
            queryWrapper.eq(Category::getParentId, parentId);
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

        return result;
    }

    /**
     * 获取所有分类（平铺列表）
     */
    public List<Category> getAllCategories() {
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
            return categories;
        }
        return new ArrayList<>();
    }

    /**
     * 获取分类树形结构
     */
    public List<Category> getCategoryTree() {
        LambdaQueryWrapper<Category> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Category::getStatus, 1); // 只获取启用的分类
        queryWrapper.orderByAsc(Category::getSortOrder).orderByAsc(Category::getLevel);

        List<Category> allCategories = categoryMapper.selectList(queryWrapper);
        if (allCategories == null || allCategories.isEmpty()) {
            return new ArrayList<>();
        }

        // 构建树形结构
        return buildCategoryTree(allCategories);
    }

    /**
     * 根据父分类ID获取子分类
     */
    public List<Category> getCategoriesByParentId(Long parentId) {
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
        return categories;
    }

    /**
     * 获取一级分类列表（用于前端首页展示）
     */
    public List<Category> getTopCategories() {
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
        return categories;
    }

    /**
     * 批量删除分类
     */
    @Transactional
    public void deleteBatch(List<Long> ids) {
        // 递归检查是否有子分类或关联商品
        for (Long id : ids) {
            checkCanDelete(id);
        }

        // 递归删除所有子分类
        for (Long id : ids) {
            deleteCategoryRecursive(id);
        }

        LOGGER.info("批量删除分类成功，删除数量：{}", ids.size());
    }

    /**
     * 检查分类是否可以删除
     */
    private void checkCanDelete(Long id) {
        // 检查是否存在子分类
        LambdaQueryWrapper<Category> childQuery = new LambdaQueryWrapper<>();
        childQuery.eq(Category::getParentId, id);
        Long childCount = categoryMapper.selectCount(childQuery);
        if (childCount > 0) {
            throw new BusinessException(ErrorCodeEnum.HAS_ASSOCIATED_DATA, "无法删除分类ID：" + id + "，存在子分类");
        }

        // 检查是否存在关联商品
        LambdaQueryWrapper<Product> productQuery = new LambdaQueryWrapper<>();
        productQuery.eq(Product::getCategoryId, id);
        Long productCount = productMapper.selectCount(productQuery);
        if (productCount > 0) {
            throw new BusinessException(ErrorCodeEnum.HAS_ASSOCIATED_DATA, "无法删除分类ID：" + id + "，存在关联商品");
        }
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
}
