package org.example.springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.springboot.entity.Article;
import org.example.springboot.enums.ErrorCodeEnum;
import org.example.springboot.exception.BusinessException;
import org.example.springboot.mapper.ArticleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ArticleService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArticleService.class);

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private FileService fileService;

    public Article createArticle(Article article) {
        int result = articleMapper.insert(article);
        if (result > 0) {
            LOGGER.info("创建资讯成功，资讯ID：{}", article.getId());
            return article;
        }
        throw new BusinessException(ErrorCodeEnum.OPERATION_FAILED, "创建资讯失败");
    }

    public Article updateArticle(Long id, Article article) {
        Article oldArticle = articleMapper.selectById(id);
        if (oldArticle == null) {
            throw new BusinessException(ErrorCodeEnum.ARTICLE_NOT_FOUND, "资讯不存在");
        }

        // 处理封面图片
        String oldImg = oldArticle.getCoverImage();
        String newImg = article.getCoverImage();
        if (oldImg != null && newImg != null && !oldImg.equals(newImg)) {
            fileService.fileRemove(oldImg);
        }

        article.setId(id);
        int result = articleMapper.updateById(article);
        if (result > 0) {
            LOGGER.info("更新资讯成功，资讯ID：{}", id);
            return article;
        }
        throw new BusinessException(ErrorCodeEnum.OPERATION_FAILED, "更新资讯失败");
    }

    public void deleteArticle(Long id) {
        Article article = articleMapper.selectById(id);
        if (article != null && article.getCoverImage() != null) {
            fileService.fileRemove(article.getCoverImage());
        }

        int result = articleMapper.deleteById(id);
        if (result > 0) {
            LOGGER.info("删除资讯成功，资讯ID：{}", id);
            return;
        }
        throw new BusinessException(ErrorCodeEnum.OPERATION_FAILED, "删除资讯失败");
    }

    public Article getArticleById(Long id) {
        Article article = articleMapper.selectById(id);
        if (article != null) {
            // 增加浏览量
            article.setViewCount(article.getViewCount() + 1);
            articleMapper.updateById(article);
            return article;
        }
        throw new BusinessException(ErrorCodeEnum.ARTICLE_NOT_FOUND, "未找到资讯");
    }

    public Page<Article> getArticlesByPage(String title, Integer status,
                                         Integer currentPage, Integer size) {
        LambdaQueryWrapper<Article> queryWrapper = new LambdaQueryWrapper<>();

        if (title != null && !title.trim().isEmpty()) {
            queryWrapper.like(Article::getTitle, title.trim());
        }
        if (status != null) {
            queryWrapper.eq(Article::getStatus, status);
        }

        queryWrapper.orderByDesc(Article::getCreatedAt);

        Page<Article> page = new Page<>(currentPage, size);
        return articleMapper.selectPage(page, queryWrapper);
    }

    public void updateArticleStatus(Long id, Integer status) {
        Article article = articleMapper.selectById(id);
        if (article == null) {
            throw new BusinessException(ErrorCodeEnum.ARTICLE_NOT_FOUND, "资讯不存在");
        }

        article.setStatus(status);
        int result = articleMapper.updateById(article);
        if (result > 0) {
            LOGGER.info("更新资讯状态成功，资讯ID：{}，新状态：{}", id, status);
            return;
        }
        throw new BusinessException(ErrorCodeEnum.OPERATION_FAILED, "更新资讯状态失败");
    }

    public void deleteBatch(List<Long> ids) {
        // 删除相关的封面图片
        List<Article> articles = articleMapper.selectBatchIds(ids);
        for (Article article : articles) {
            if (article.getCoverImage() != null) {
                fileService.fileRemove(article.getCoverImage());
            }
        }

        int result = articleMapper.deleteBatchIds(ids);
        if (result > 0) {
            LOGGER.info("批量删除资讯成功，删除数量：{}", result);
            return;
        }
        throw new BusinessException(ErrorCodeEnum.OPERATION_FAILED, "批量删除资讯失败");
    }
}
