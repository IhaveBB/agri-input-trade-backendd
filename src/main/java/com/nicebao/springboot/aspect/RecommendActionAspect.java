package com.nicebao.springboot.aspect;

import jakarta.annotation.Resource;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import com.nicebao.springboot.common.Result;
import com.nicebao.springboot.entity.Cart;
import com.nicebao.springboot.entity.Favorite;
import com.nicebao.springboot.entity.Order;
import com.nicebao.springboot.entity.Product;
import com.nicebao.springboot.entity.vo.ProductVO;
import com.nicebao.springboot.mapper.OrderMapper;
import com.nicebao.springboot.mapper.ProductMapper;
import com.nicebao.springboot.service.FusionRecommendationService;
import com.nicebao.springboot.service.RecommendActionService;
import com.nicebao.springboot.util.UserContext;
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
    @Pointcut("execution(* com.nicebao.springboot.controller.ProductController.getProductById(..)) || " +
            "execution(* com.nicebao.springboot.controller.ProductController.getProductWithExt(..))")
    public void productDetailPointcut() {}

    /**
     * 收藏切入点 - 用户收藏商品时记录
     */
    @Pointcut("execution(* com.nicebao.springboot.service.FavoriteService.createFavorite(..))")
    public void favoritePointcut() {}

    /**
     * 购物车切入点 - 用户添加购物车时记录
     */
    @Pointcut("execution(* com.nicebao.springboot.service.CartService.addToCart(..))")
    public void cartPointcut() {}

    /**
     * 订单支付切入点 - 用户支付成功时记录
     */
    @Pointcut("execution(* com.nicebao.springboot.service.OrderService.payOrder(..))")
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
            Long productId = getFirstLongArg(joinPoint);
            Long userId = UserContext.getUserId();
            Long categoryId = extractCategoryId(unwrapResult(result));

            if (userId != null && productId != null) {
                String source = fusionRecommendationService.isProductRecommended(userId, productId)
                        ? "RECOMMEND" : "NATURAL";

                recommendActionService.recordClick(
                        userId, productId, categoryId,
                        source, "PRODUCT_DETAIL", null, null
                );
                LOGGER.debug("[埋点AOP] 用户{}点击商品{}，来源：{}", userId, productId, source);
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
            Object data = unwrapResult(result);
            if (data instanceof Favorite favorite) {
                if (favorite.getUserId() != null
                        && favorite.getProductId() != null
                        && Integer.valueOf(1).equals(favorite.getStatus())) {
                    Long categoryId = findProductCategoryId(favorite.getProductId());
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
            Object data = unwrapResult(result);
            if (data instanceof Cart cart) {
                if (cart.getUserId() != null && cart.getProductId() != null) {
                    Long categoryId = findProductCategoryId(cart.getProductId());
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

    private Long getFirstLongArg(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        return args.length > 0 && args[0] instanceof Long ? (Long) args[0] : null;
    }

    private Object unwrapResult(Object result) {
        if (result instanceof Result<?> resultObj) {
            return resultObj.getData();
        }
        return result;
    }

    private Long extractCategoryId(Object data) {
        if (data instanceof Product product) {
            return product.getCategoryId();
        }
        if (data instanceof ProductVO productVO) {
            return productVO.getCategoryId();
        }
        return null;
    }

    private Long findProductCategoryId(Long productId) {
        try {
            Product product = productMapper.selectById(productId);
            return product != null ? product.getCategoryId() : null;
        } catch (Exception e) {
            LOGGER.warn("[埋点AOP] 查询商品categoryId失败: {}", e.getMessage());
            return null;
        }
    }
}
