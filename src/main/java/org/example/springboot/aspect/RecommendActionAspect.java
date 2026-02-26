package org.example.springboot.aspect;

import jakarta.annotation.Resource;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.example.springboot.common.Result;
import org.example.springboot.entity.Cart;
import org.example.springboot.entity.Favorite;
import org.example.springboot.entity.Product;
import org.example.springboot.service.RecommendActionService;
import org.example.springboot.util.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 推荐埋点AOP切面
 * 自动在用户浏览、收藏、加购、购买等行为时记录埋点
 */
@Aspect
@Component
public class RecommendActionAspect {

    private static final Logger LOGGER = LoggerFactory.getLogger(RecommendActionAspect.class);

    @Resource
    private RecommendActionService recommendActionService;

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
     */
    @Around("productDetailPointcut()")
    public Object recordProductClick(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();

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

                        recommendActionService.recordClick(
                                userId, productId, categoryId,
                                "NATURAL", "PRODUCT_DETAIL", null, null
                        );
                        LOGGER.debug("[埋点AOP] 用户{}点击商品{}", userId, productId);
                    }
                }
            }
        }

        return result;
    }

    /**
     * 收藏埋点 - 记录收藏行为
     */
    @Around("favoritePointcut()")
    public Object recordFavorite(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();

        if (result != null && result instanceof Result) {
            Result<?> resultObj = (Result<?>) result;
            Object data = resultObj.getData();

            if (data instanceof Favorite) {
                Favorite favorite = (Favorite) data;
                if (favorite.getUserId() != null && favorite.getProductId() != null) {
                    // 查询商品获取categoryId
                    // 这里简化处理，实际可以查询商品服务
                    recommendActionService.recordCollect(
                            favorite.getUserId(),
                            favorite.getProductId(),
                            null,
                            "NATURAL", "PRODUCT_DETAIL", null, null
                    );
                    LOGGER.debug("[埋点AOP] 用户{}收藏商品{}", favorite.getUserId(), favorite.getProductId());
                }
            }
        }

        return result;
    }

    /**
     * 购物车埋点 - 记录加购行为
     */
    @Around("cartPointcut()")
    public Object recordCart(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();

        if (result != null && result instanceof Result) {
            Result<?> resultObj = (Result<?>) result;
            Object data = resultObj.getData();

            if (data instanceof Cart) {
                Cart cart = (Cart) data;
                if (cart.getUserId() != null && cart.getProductId() != null) {
                    recommendActionService.recordCart(
                            cart.getUserId(),
                            cart.getProductId(),
                            null,
                            "NATURAL", "PRODUCT_DETAIL", null, null
                    );
                    LOGGER.debug("[埋点AOP] 用户{}加购商品{}", cart.getUserId(), cart.getProductId());
                }
            }
        }

        return result;
    }

    /**
     * 支付埋点 - 记录购买行为
     */
    @Around("payOrderPointcut()")
    public Object recordPurchase(ProceedingJoinPoint joinPoint) throws Throwable {
        // 先执行原方法
        Object result = joinPoint.proceed();

        // 获取订单ID（第一个参数）
        Object[] args = joinPoint.getArgs();
        if (args.length > 0 && args[0] instanceof Long) {
            Long orderId = (Long) args[0];
            Long userId = UserContext.getUserId();

            if (userId != null) {
                // 这里需要查询订单和商品信息
                // 简化处理，实际可以通过orderId查询
                LOGGER.debug("[埋点AOP] 用户{}支付订单{}", userId, orderId);
            }
        }

        return result;
    }
}
