package org.example.springboot.aspect;

import jakarta.annotation.Resource;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.example.springboot.common.Result;
import org.example.springboot.entity.Cart;
import org.example.springboot.entity.Favorite;
import org.example.springboot.entity.Order;
import org.example.springboot.entity.Product;
import org.example.springboot.mapper.OrderMapper;
import org.example.springboot.mapper.ProductMapper;
import org.example.springboot.service.FusionRecommendationService;
import org.example.springboot.service.RecommendActionService;
import org.example.springboot.util.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 推荐埋点AOP切面
 * 自动在用户浏览、收藏、加购、购买等行为时记录埋点
 *
 * @author IhaveBB
 * @date 2026/03/19
 */
@Aspect
@Component
@org.springframework.core.annotation.Order(2)  // 确保在权限校验AOP之后执行
public class RecommendActionAspect {

    private static final Logger LOGGER = LoggerFactory.getLogger(RecommendActionAspect.class);

    @Resource
    private RecommendActionService recommendActionService;

    @Resource
    private FusionRecommendationService fusionRecommendationService;

    @Resource
    private ProductMapper productMapper;

    @Resource
    private OrderMapper orderMapper;

    /**
     * 商品详情切入点 - 用户查看商品详情时记录点击
     */
    @Pointcut("execution(* org.example.springboot.service.ProductService.getProductById(..))")
    public void productDetailPointcut() {}

    /**
     * 收藏切入点 - 用户收藏商品时记录
     */
    @Pointcut("execution(* org.example.springboot.service.FavoriteService.createFavorite(..))")
    public void favoritePointcut() {}

    /**
     * 购物车切入点 - 用户添加购物车时记录
     */
    @Pointcut("execution(* org.example.springboot.service.CartService.addToCart(..))")
    public void cartPointcut() {}

    /**
     * 订单支付切入点 - 用户支付成功时记录
     */
    @Pointcut("execution(* org.example.springboot.service.OrderService.payOrder(..))")
    public void payOrderPointcut() {}

    /**
     * 商品详情埋点 - 记录点击
     *
     * @param joinPoint 切点
     * @return 方法执行结果
     * @throws Throwable 异常
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Around("productDetailPointcut()")
    public Object recordProductClick(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();

        try {
            // 获取商品ID（第一个参数）
            Object[] args = joinPoint.getArgs();
            if (args.length > 0 && args[0] instanceof Long) {
                Long productId = (Long) args[0];
                Long userId = UserContext.getUserId();

                if (userId != null && productId != null) {
                    // 从返回结果中获取categoryId
                    if (result != null && result instanceof Result) {
                        Result<?> resultObj = (Result<?>) result;
                        Object data = resultObj.getData();
                        if (data instanceof Product) {
                            Product product = (Product) data;
                            Long categoryId = product.getCategoryId();

                            // 判断来源：如果商品在用户的推荐列表中，则标记为 RECOMMEND
                            String source = fusionRecommendationService.isProductRecommended(userId, productId)
                                    ? "RECOMMEND" : "NATURAL";

                            recommendActionService.recordClick(
                                    userId, productId, categoryId,
                                    source, "PRODUCT_DETAIL", null, null
                            );
                            LOGGER.debug("[埋点AOP] 用户{}点击商品{}，来源：{}", userId, productId, source);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 埋点失败只记录日志，不影响主业务
            LOGGER.error("[埋点AOP] 记录商品点击埋点失败: {}", e.getMessage(), e);
        }

        return result;
    }

    /**
     * 收藏埋点 - 记录收藏行为
     *
     * @param joinPoint 切点
     * @return 方法执行结果
     * @throws Throwable 异常
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Around("favoritePointcut()")
    public Object recordFavorite(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();

        // 埋点逻辑放在try-catch中，确保埋点失败不影响主业务
        try {
            if (result != null && result instanceof Result) {
                Result<?> resultObj = (Result<?>) result;
                Object data = resultObj.getData();

                if (data instanceof Favorite) {
                    Favorite favorite = (Favorite) data;
                    if (favorite.getUserId() != null && favorite.getProductId() != null) {
                        // 查询商品获取categoryId
                        Long categoryId = null;
                        try {
                            Product product = productMapper.selectById(favorite.getProductId());
                            if (product != null) {
                                categoryId = product.getCategoryId();
                            }
                        } catch (Exception e) {
                            LOGGER.warn("[埋点AOP] 查询商品categoryId失败: {}", e.getMessage());
                        }

                        // 判断来源：如果商品在用户的推荐列表中，则标记为 RECOMMEND
                        String source = fusionRecommendationService.isProductRecommended(favorite.getUserId(), favorite.getProductId())
                                ? "RECOMMEND" : "NATURAL";

                        recommendActionService.recordCollect(
                                favorite.getUserId(),
                                favorite.getProductId(),
                                categoryId,
                                source, "PRODUCT_DETAIL", null, null
                        );
                        LOGGER.debug("[埋点AOP] 用户{}收藏商品{}，来源：{}", favorite.getUserId(), favorite.getProductId(), source);
                    }
                }
            }
        } catch (Exception e) {
            // 埋点失败只记录日志，不影响主业务
            LOGGER.error("[埋点AOP] 记录收藏埋点失败: {}", e.getMessage(), e);
        }

        return result;
    }

    /**
     * 购物车埋点 - 记录加购行为
     *
     * @param joinPoint 切点
     * @return 方法执行结果
     * @throws Throwable 异常
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Around("cartPointcut()")
    public Object recordCart(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();

        // 埋点逻辑放在try-catch中，确保埋点失败不影响主业务
        try {
            if (result != null && result instanceof Result) {
                Result<?> resultObj = (Result<?>) result;
                Object data = resultObj.getData();

                if (data instanceof Cart) {
                    Cart cart = (Cart) data;
                    if (cart.getUserId() != null && cart.getProductId() != null) {
                        // 查询商品获取categoryId
                        Long categoryId = null;
                        try {
                            Product product = productMapper.selectById(cart.getProductId());
                            if (product != null) {
                                categoryId = product.getCategoryId();
                            }
                        } catch (Exception e) {
                            LOGGER.warn("[埋点AOP] 查询商品categoryId失败: {}", e.getMessage());
                        }

                        // 判断来源：如果商品在用户的推荐列表中，则标记为 RECOMMEND
                        String source = fusionRecommendationService.isProductRecommended(cart.getUserId(), cart.getProductId())
                                ? "RECOMMEND" : "NATURAL";

                        recommendActionService.recordCart(
                                cart.getUserId(),
                                cart.getProductId(),
                                categoryId,
                                source, "PRODUCT_DETAIL", null, null
                        );
                        LOGGER.debug("[埋点AOP] 用户{}加购商品{}，来源：{}", cart.getUserId(), cart.getProductId(), source);
                    }
                }
            }
        } catch (Exception e) {
            // 埋点失败只记录日志，不影响主业务
            LOGGER.error("[埋点AOP] 记录加购埋点失败: {}", e.getMessage(), e);
        }

        return result;
    }

    /**
     * 支付埋点 - 记录购买行为
     *
     * @param joinPoint 切点
     * @return 方法执行结果
     * @throws Throwable 异常
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Around("payOrderPointcut()")
    public Object recordPurchase(ProceedingJoinPoint joinPoint) throws Throwable {
        // 先执行原方法
        Object result = joinPoint.proceed();

        // 埋点逻辑放在try-catch中，确保埋点失败不影响主业务
        try {
            // 获取订单ID（第一个参数）
            Object[] args = joinPoint.getArgs();
            if (args.length > 0 && args[0] instanceof Long) {
                Long orderId = (Long) args[0];
                Long userId = UserContext.getUserId();

                if (userId != null) {
                    // 查询订单获取商品信息
                    Order order = orderMapper.selectById(orderId);
                    if (order != null && order.getProductId() != null) {
                        Long productId = order.getProductId();

                        // 查询商品获取categoryId
                        Long categoryId = null;
                        try {
                            Product product = productMapper.selectById(productId);
                            if (product != null) {
                                categoryId = product.getCategoryId();
                            }
                        } catch (Exception e) {
                            LOGGER.warn("[埋点AOP] 查询商品categoryId失败: {}", e.getMessage());
                        }

                        // 判断来源：如果商品在用户的推荐列表中，则标记为 RECOMMEND
                        String source = fusionRecommendationService.isProductRecommended(userId, productId)
                                ? "RECOMMEND" : "NATURAL";

                        recommendActionService.recordBuy(
                                userId,
                                productId,
                                categoryId,
                                source, "PRODUCT_DETAIL", null, null
                        );
                        LOGGER.debug("[埋点AOP] 用户{}支付订单{}, 商品{}，来源：{}", userId, orderId, productId, source);
                    }
                }
            }
        } catch (Exception e) {
            // 埋点失败只记录日志，不影响主业务
            LOGGER.error("[埋点AOP] 记录购买埋点失败: {}", e.getMessage(), e);
        }

        return result;
    }
}
