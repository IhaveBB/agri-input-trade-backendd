package com.nicebao.springboot;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nicebao.springboot.entity.Product;
import com.nicebao.springboot.entity.User;
import com.nicebao.springboot.entity.vo.ProductVO;
import com.nicebao.springboot.mapper.ProductMapper;
import com.nicebao.springboot.service.ProductExtService;
import com.nicebao.springboot.service.ProductService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class RedisCacheIntegrationTest {

    @Resource
    private CacheManager cacheManager;

    @Resource
    private ProductService productService;

    @Resource
    private ProductExtService productExtService;

    @Resource
    private ProductMapper productMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ObjectMapper objectMapper;

    @Test
    void springCacheUsesRedisForProductDetailCache() {
        assertTrue(cacheManager.getClass().getName().contains("RedisCacheManager"),
                "Spring Cache 应使用 RedisCacheManager");

        Product sample = firstProduct();
        String cacheKey = "cache:products:" + sample.getId();
        stringRedisTemplate.delete(cacheKey);

        Product first = productService.getProductById(sample.getId());
        assertNotNull(first);
        assertTrue(Boolean.TRUE.equals(stringRedisTemplate.hasKey(cacheKey)),
                "商品详情应写入 Redis 缓存: " + cacheKey);
        String cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);
        assertNotNull(cachedJson);
        assertFalse(cachedJson.contains("\"password\""),
                "商品缓存中不应序列化商户密码字段");

        Product cached = productService.getProductById(sample.getId());
        assertEquals(first.getId(), cached.getId());
        assertEquals(first.getName(), cached.getName());
    }

    @Test
    void springCacheUsesRedisForFrontendProductExtDetailCache() {
        assertTrue(cacheManager.getClass().getName().contains("RedisCacheManager"),
                "Spring Cache 应使用 RedisCacheManager");

        Product sample = firstProduct();
        String cacheKey = "cache:productExts:" + sample.getId();
        stringRedisTemplate.delete(cacheKey);

        ProductVO first = productExtService.getProductWithExt(sample.getId());
        assertNotNull(first);
        assertTrue(Boolean.TRUE.equals(stringRedisTemplate.hasKey(cacheKey)),
                "前端商品扩展详情应写入 Redis 缓存: " + cacheKey);

        ProductVO cached = productExtService.getProductWithExt(sample.getId());
        assertEquals(first.getId(), cached.getId());
        assertEquals(first.getName(), cached.getName());
    }

    @Test
    void springCacheUsesRedisForUnfilteredProductPageCache() {
        Set<String> oldKeys = stringRedisTemplate.keys("cache:productPages:*");
        if (oldKeys != null && !oldKeys.isEmpty()) {
            stringRedisTemplate.delete(oldKeys);
        }

        Page<Product> first = productService.getProductsByPage(
                null, null, null, null,
                1, 5, null, null,
                null, null);

        Set<String> keys = stringRedisTemplate.keys("cache:productPages:*");
        assertNotNull(keys);
        assertFalse(keys.isEmpty(), "无筛选商品分页应写入 Redis 缓存");

        Page<Product> cached = productService.getProductsByPage(
                null, null, null, null,
                1, 5, null, null,
                null, null);

        assertEquals(first.getCurrent(), cached.getCurrent());
        assertEquals(first.getSize(), cached.getSize());
        assertEquals(first.getRecords().size(), cached.getRecords().size());
    }

    @Test
    void userPasswordIsWriteOnlyForJson() throws Exception {
        User user = objectMapper.readValue("{\"username\":\"demo\",\"password\":\"secret\"}", User.class);
        assertEquals("secret", user.getPassword());

        String json = objectMapper.writeValueAsString(user);
        assertFalse(json.contains("password"), "用户 JSON 输出中不应包含密码字段");
        assertFalse(json.contains("secret"), "用户 JSON 输出中不应包含密码值");
    }

    private Product firstProduct() {
        List<Product> products = productMapper.selectList(new LambdaQueryWrapper<Product>()
                .orderByAsc(Product::getId)
                .last("LIMIT 1"));
        assertFalse(products.isEmpty(), "测试数据库中应至少存在一个商品");
        return products.get(0);
    }
}
