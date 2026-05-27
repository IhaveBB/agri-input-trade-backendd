package com.nicebao.springboot;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nicebao.springboot.config.RecommendationConfig;
import com.nicebao.springboot.entity.Cart;
import com.nicebao.springboot.entity.Favorite;
import com.nicebao.springboot.entity.Order;
import com.nicebao.springboot.entity.Product;
import com.nicebao.springboot.entity.RecommendAction;
import com.nicebao.springboot.entity.Review;
import com.nicebao.springboot.entity.dto.ProductProfileDTO;
import com.nicebao.springboot.entity.dto.UserProfileDTO;
import com.nicebao.springboot.mapper.CartMapper;
import com.nicebao.springboot.mapper.FavoriteMapper;
import com.nicebao.springboot.mapper.OrderMapper;
import com.nicebao.springboot.mapper.ProductMapper;
import com.nicebao.springboot.mapper.RecommendActionMapper;
import com.nicebao.springboot.mapper.ReviewMapper;
import com.nicebao.springboot.service.FusionRecommendationService;
import com.nicebao.springboot.service.RecommendationAlgorithmSupport;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 论文推荐实验离线评估。
 *
 * <p>本测试固定读取 {@code sql/evaluation_complete_chain_test_data.sql} 生成的数据库数据。
 * 评估时对每个用户执行 Leave-One-Out 时间切分：隐藏该用户最后一次已完成购买，
 * 只把隐藏购买之前的行为放入训练矩阵，再检查推荐列表是否命中被隐藏商品。</p>
 *
 * <p>画像相关计算直接复用 FusionRecommendationService#getUserProfileBefore、
 * getProductProfile、computeProfileMatchScore；CF 相似度、预测分、归一化、融合分和
 * 行为权重处理复用 RecommendationAlgorithmSupport，与线上推荐策略使用同一个公共
 * 计算组件。测试中只额外负责“按测试时间截断数据”，避免直接调用线上接口造成
 * 最后一次购买泄露。</p>
 */
@SpringBootTest
class RecommendationOfflinePaperEvaluationTest {

    private static final long USER_ID_START = 9101L;
    private static final long USER_ID_END = 9115L;
    private static final long MERCHANT_ID_START = 9001L;
    private static final long MERCHANT_ID_END = 9004L;
    private static final long PRODUCT_ID_START = 9201L;
    private static final long PRODUCT_ID_END = 9224L;
    private static final long ORDER_ID_START = 9401L;
    private static final long ORDER_ID_END = 9460L;
    private static final int[] TOP_K_VALUES = {5, 10};
    private static final double[] FUSION_THETAS = {0.3, 0.5, 0.7};
    private static final String EVAL_SEASON = "春";
    private static final Path CHART_OUTPUT = Path.of("docs", "recommendation_cf_vs_fusion.png");

    @Resource
    private ProductMapper productMapper;
    @Resource
    private OrderMapper orderMapper;
    @Resource
    private FavoriteMapper favoriteMapper;
    @Resource
    private CartMapper cartMapper;
    @Resource
    private RecommendActionMapper recommendActionMapper;
    @Resource
    private ReviewMapper reviewMapper;
    @Resource
    private RecommendationConfig recommendationConfig;
    @Resource
    private FusionRecommendationService fusionRecommendationService;
    @Resource
    private RecommendationAlgorithmSupport algorithmSupport;

    @Test
    void lowRatingPenaltyUsesSameFormulaAsOnlineRecommendation() {
        /*
         * 低分评价惩罚是交互矩阵构建的一部分，线上推荐和离线评估都从
         * RecommendationAlgorithmSupport 调用同一套公式。这里单独做一个小断言，
         * 防止后续修改权重时论文中“2分及以下额外回扣购买权重50%”的口径失效。
         */
        double reviewWeight = algorithmSupport.reviewInteractionWeight(
                2, recommendationConfig.getReviewWeight());
        double lowRatingPenalty = algorithmSupport.lowRatingPurchasePenalty(
                2, recommendationConfig.getPurchaseWeight());

        assertEquals(-1.0, reviewWeight, 0.000001);
        assertEquals(-2.5, lowRatingPenalty, 0.000001);

        Map<Long, Map<Long, Double>> matrix = new HashMap<>();
        algorithmSupport.addInteraction(matrix, 9101L, 9201L, recommendationConfig.getPurchaseWeight());
        algorithmSupport.addInteraction(matrix, 9101L, 9201L, reviewWeight);
        algorithmSupport.addInteraction(matrix, 9101L, 9201L, lowRatingPenalty);

        // 购买 5 分 + 2 星评价 -1 分 + 低分额外惩罚 -2.5 分，最终交互强度为 1.5。
        assertEquals(1.5, matrix.get(9101L).get(9201L), 0.000001);
    }

    @Test
    void evaluatePaperRecommendationAlgorithms() {
        /*
         * 一、读取固定评估对象。
         *
         * 这里不从 Excel 或随机数据读取，而是直接读取系统数据库中由
         * evaluation_complete_chain_test_data.sql 生成的固定 ID 区间数据。
         * 这样商品状态、库存、销量、画像关联表、订单、评价和推荐行为日志都来自
         * 同一套业务表，避免单独文件数据和系统实际数据不一致。
         */
        List<Product> products = loadEvalProducts();
        Map<Long, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        /*
         * 二、构造 Leave-One-Out 测试集。
         *
         * 每个用户最后一笔已完成订单作为“被隐藏答案”，其余发生在该时间之前的
         * 行为才可进入训练矩阵。这个步骤不能直接调用线上 recommend 接口，因为线上
         * 接口会读取用户全部已完成订单，包含最后一笔购买，评估时会泄露答案。
         */
        List<TestCase> tests = buildLeaveOneOutTests(productMap.keySet());
        if (tests.isEmpty()) {
            throw new IllegalStateException("没有可评估用户，请先执行 sql/evaluation_complete_chain_test_data.sql");
        }

        /*
         * 三、按线上算法口径构造训练信号。
         *
         * buildTrainMatrix 只负责“按测试时间截断数据库记录”；行为权重累加、无效
         * 点击过滤、点击去重、点击权重封顶、低分评价惩罚等规则均调用
         * RecommendationAlgorithmSupport，线上推荐服务也使用同一个组件。
         */
        Map<Long, Map<Long, Double>> trainMatrix = buildTrainMatrix(tests, productMap.keySet());
        Map<Long, Map<Long, Double>> itemSimilarity = algorithmSupport.computeItemSimilarityMatrix(
                trainMatrix,
                recommendationConfig.getSimilarityThreshold(),
                recommendationConfig.getTopK());

        /*
         * 四、分别计算四类算法的候选商品分数。
         *
         * 热门推荐只使用训练矩阵中的历史热度，不能使用商品当前 salesCount，
         * 因为 salesCount 已包含隐藏测试购买后的销量，会造成评估泄露。
         * 协同过滤推荐使用公共组件计算 Item-CF 预测分和 Min-Max 归一化。
         * 画像匹配推荐直接复用 FusionRecommendationService 的用户画像、商品画像和
         * 画像匹配函数，并固定实验季节为春，避免结果随运行日期漂移。
         * 融合推荐使用公共组件中的线性融合函数，theta 取系统配置默认值 0.7。
         */
        Map<Long, Double> popularity = computeHotScores(trainMatrix);
        Map<Long, Map<Long, Double>> profileScores = computeProfileScores(tests, products);
        Map<Long, Map<Long, Double>> cfScores = computeCfScores(tests, products, trainMatrix, itemSimilarity);

        /*
         * 五、固定论文数据规模。
         *
         * 这些断言是为了防止数据库测试数据被修改后仍误用旧论文表格。一旦用户数、
         * 商品数、订单数或训练交互对数变化，测试会失败，提醒重新生成表格和图。
         */
        assertFixedPaperDataset(products, tests, trainMatrix);
        assertDatasetUsability(tests, trainMatrix, profileScores);

        Map<String, Map<Integer, MetricSummary>> allResults = new LinkedHashMap<>();
        allResults.put("热门推荐", evaluate(rankPopular(tests, products, popularity, trainMatrix), tests, productMap));
        allResults.put("协同过滤推荐", evaluate(rankByScores(tests, products, cfScores, popularity, trainMatrix), tests, productMap));
        allResults.put("画像匹配推荐", evaluate(rankByScores(tests, products, profileScores, popularity, trainMatrix), tests, productMap));

        Map<Double, Map<Integer, MetricSummary>> thetaResults = new LinkedHashMap<>();
        for (double theta : FUSION_THETAS) {
            Map<Long, Map<Long, Double>> fusionScores = computeFusionScores(tests, products, cfScores, profileScores, theta);
            Map<Integer, MetricSummary> summary =
                    evaluate(rankByScores(tests, products, fusionScores, popularity, trainMatrix), tests, productMap);
            thetaResults.put(theta, summary);
            if (Math.abs(theta - recommendationConfig.getTheta()) < 0.000001) {
                allResults.put("融合推荐", summary);
            }
        }

        printDatasetSummary(tests, trainMatrix);
        printMainResultTable(allResults);
        printThetaTable(thetaResults);
        printProfileAudit(tests, products, profileScores);
        printUserHitAudit(tests, allResults);
        writeCfVsFusionChart(allResults);
    }

    private List<Product> loadEvalProducts() {
        // 候选商品与论文数据集保持一致：只取评估区间内已上架且有库存的商品。
        return productMapper.selectList(new LambdaQueryWrapper<Product>()
                .between(Product::getId, PRODUCT_ID_START, PRODUCT_ID_END)
                .eq(Product::getStatus, 1)
                .gt(Product::getStock, 0)
                .orderByAsc(Product::getId));
    }

    private List<TestCase> buildLeaveOneOutTests(Set<Long> evalProductIds) {
        // 只用已完成订单构造测试样本，未付款、退款、取消订单不代表真实购买完成。
        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .between(Order::getUserId, USER_ID_START, USER_ID_END)
                .eq(Order::getStatus, 3)
                .in(Order::getProductId, evalProductIds)
                .orderByAsc(Order::getUserId)
                .orderByAsc(Order::getCreatedAt)
                .orderByAsc(Order::getId));
        Map<Long, List<Order>> byUser = orders.stream()
                .filter(o -> o.getUserId() != null && o.getProductId() != null && o.getCreatedAt() != null)
                .collect(Collectors.groupingBy(Order::getUserId, LinkedHashMap::new, Collectors.toList()));

        List<TestCase> tests = new ArrayList<>();
        for (Map.Entry<Long, List<Order>> entry : byUser.entrySet()) {
            List<Order> userOrders = entry.getValue();
            if (userOrders.size() < 2) {
                continue;
            }
            // 每个用户最后一笔完成购买作为唯一测试样本，其余更早行为作为训练信号。
            userOrders.sort(Comparator.comparing(Order::getCreatedAt).thenComparing(Order::getId));
            Order testOrder = userOrders.get(userOrders.size() - 1);
            tests.add(new TestCase(testOrder.getUserId(), testOrder.getProductId(), toLocalDateTime(testOrder.getCreatedAt())));
        }
        tests.sort(Comparator.comparing(TestCase::userId));
        return tests;
    }

    private Map<Long, Map<Long, Double>> buildTrainMatrix(List<TestCase> tests, Set<Long> evalProductIds) {
        Map<Long, TestCase> testByUser = tests.stream()
                .collect(Collectors.toMap(TestCase::userId, Function.identity()));
        Map<Long, Map<Long, Double>> matrix = new HashMap<>();

        // 以下五类行为与线上交互矩阵来源一致，但每条记录都必须早于该用户测试购买时间。
        addCompletedOrders(matrix, testByUser, evalProductIds);
        addFavorites(matrix, testByUser, evalProductIds);
        addCarts(matrix, testByUser, evalProductIds);
        addClicks(matrix, testByUser, evalProductIds);
        addReviews(matrix, testByUser, evalProductIds);

        matrix.values().forEach(userMap -> userMap.entrySet().removeIf(e -> e.getValue() <= 0));
        matrix.entrySet().removeIf(e -> e.getValue().isEmpty());
        return matrix;
    }

    private void addCompletedOrders(Map<Long, Map<Long, Double>> matrix,
                                    Map<Long, TestCase> testByUser,
                                    Set<Long> evalProductIds) {
        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .between(Order::getUserId, USER_ID_START, USER_ID_END)
                .eq(Order::getStatus, 3)
                .in(Order::getProductId, evalProductIds));
        for (Order order : orders) {
            if (order.getUserId() == null || order.getProductId() == null || order.getCreatedAt() == null) {
                continue;
            }
            TestCase test = testByUser.get(order.getUserId());
            if (test == null || !beforeTest(order.getProductId(), toLocalDateTime(order.getCreatedAt()), test)) {
                continue;
            }
            algorithmSupport.addInteraction(matrix, order.getUserId(), order.getProductId(),
                    recommendationConfig.getPurchaseWeight());
        }
    }

    private void addFavorites(Map<Long, Map<Long, Double>> matrix,
                              Map<Long, TestCase> testByUser,
                              Set<Long> evalProductIds) {
        List<Favorite> favorites = favoriteMapper.selectList(new LambdaQueryWrapper<Favorite>()
                .between(Favorite::getUserId, USER_ID_START, USER_ID_END)
                .eq(Favorite::getStatus, 1)
                .in(Favorite::getProductId, evalProductIds));
        for (Favorite favorite : favorites) {
            if (favorite.getUserId() == null || favorite.getProductId() == null || favorite.getCreatedAt() == null) {
                continue;
            }
            TestCase test = testByUser.get(favorite.getUserId());
            if (test == null || !beforeTest(favorite.getProductId(), toLocalDateTime(favorite.getCreatedAt()), test)) {
                continue;
            }
            algorithmSupport.addInteraction(matrix, favorite.getUserId(), favorite.getProductId(),
                    recommendationConfig.getFavoriteWeight());
        }
    }

    private void addCarts(Map<Long, Map<Long, Double>> matrix,
                          Map<Long, TestCase> testByUser,
                          Set<Long> evalProductIds) {
        List<Cart> carts = cartMapper.selectList(new LambdaQueryWrapper<Cart>()
                .between(Cart::getUserId, USER_ID_START, USER_ID_END)
                .in(Cart::getProductId, evalProductIds));
        for (Cart cart : carts) {
            if (cart.getUserId() == null || cart.getProductId() == null || cart.getCreatedAt() == null) {
                continue;
            }
            TestCase test = testByUser.get(cart.getUserId());
            if (test == null || !beforeTest(cart.getProductId(), toLocalDateTime(cart.getCreatedAt()), test)) {
                continue;
            }
            algorithmSupport.addInteraction(matrix, cart.getUserId(), cart.getProductId(),
                    recommendationConfig.getCartWeight());
        }
    }

    private void addClicks(Map<Long, Map<Long, Double>> matrix,
                           Map<Long, TestCase> testByUser,
                           Set<Long> evalProductIds) {
        List<RecommendAction> clicks = recommendActionMapper.selectList(new LambdaQueryWrapper<RecommendAction>()
                .between(RecommendAction::getUserId, USER_ID_START, USER_ID_END)
                .eq(RecommendAction::getActionType, "CLICK")
                .in(RecommendAction::getProductId, evalProductIds)
                .orderByAsc(RecommendAction::getCreatedAt));

        Map<String, LocalDateTime> lastClickTime = new HashMap<>();
        Map<String, Double> clickWeight = new HashMap<>();
        double cap = recommendationConfig.getClickWeight() * 3.0;
        for (RecommendAction click : clicks) {
            if (click.getUserId() == null || click.getProductId() == null || click.getCreatedAt() == null) {
                continue;
            }
            if (!algorithmSupport.isEffectiveClick(click.getDuration())) {
                continue;
            }
            TestCase test = testByUser.get(click.getUserId());
            if (test == null || !beforeTest(click.getProductId(), click.getCreatedAt(), test)) {
                continue;
            }
            String key = click.getUserId() + "_" + click.getProductId();
            LocalDateTime last = lastClickTime.get(key);
            if (algorithmSupport.isDuplicateClickWithinOneMinute(last, click.getCreatedAt())) {
                continue;
            }
            lastClickTime.put(key, click.getCreatedAt());

            double current = clickWeight.getOrDefault(key, 0.0);
            double add = algorithmSupport.cappedClickWeight(
                    current, recommendationConfig.getClickWeight(), cap);
            if (add <= 0) {
                continue;
            }
            clickWeight.merge(key, add, Double::sum);
            algorithmSupport.addInteraction(matrix, click.getUserId(), click.getProductId(), add);
        }
    }

    private void addReviews(Map<Long, Map<Long, Double>> matrix,
                            Map<Long, TestCase> testByUser,
                            Set<Long> evalProductIds) {
        List<Review> reviews = reviewMapper.selectList(new LambdaQueryWrapper<Review>()
                .between(Review::getUserId, USER_ID_START, USER_ID_END)
                .eq(Review::getStatus, 1)
                .isNotNull(Review::getRating)
                .in(Review::getProductId, evalProductIds));
        for (Review review : reviews) {
            if (review.getUserId() == null || review.getProductId() == null
                    || review.getRating() == null || review.getRating() <= 0 || review.getCreatedAt() == null) {
                continue;
            }
            TestCase test = testByUser.get(review.getUserId());
            if (test == null || !beforeTest(review.getProductId(), toLocalDateTime(review.getCreatedAt()), test)) {
                continue;
            }
            double reviewWeight = algorithmSupport.reviewInteractionWeight(
                    review.getRating(), recommendationConfig.getReviewWeight());
            algorithmSupport.addInteraction(matrix, review.getUserId(), review.getProductId(), reviewWeight);
            double penalty = algorithmSupport.lowRatingPurchasePenalty(
                    review.getRating(), recommendationConfig.getPurchaseWeight());
            if (penalty < 0) {
                algorithmSupport.addInteraction(matrix, review.getUserId(), review.getProductId(), penalty);
            }
        }
    }

    private boolean beforeTest(Long productId, LocalDateTime eventTime, TestCase test) {
        // 排除测试商品本身，防止同一商品在测试时间前的派生行为泄露答案。
        return eventTime.isBefore(test.testTime()) && !Objects.equals(productId, test.testProductId());
    }

    private Map<Long, Double> computeHotScores(Map<Long, Map<Long, Double>> trainMatrix) {
        Map<Long, Double> scores = new HashMap<>();
        for (Map<Long, Double> userInteractions : trainMatrix.values()) {
            for (Map.Entry<Long, Double> entry : userInteractions.entrySet()) {
                scores.merge(entry.getKey(), entry.getValue(), Double::sum);
            }
        }
        return scores;
    }

    private Map<Long, Map<Long, Double>> computeProfileScores(List<TestCase> tests, List<Product> products) {
        Map<Long, Map<Long, Double>> scores = new HashMap<>();
        for (TestCase test : tests) {
            // 用户画像按测试时间截断，避免最后一次购买参与偏好作物和消费能力计算。
            UserProfileDTO userProfile = fusionRecommendationService.getUserProfileBefore(test.userId(), test.testTime());
            Map<Long, Double> userScores = new HashMap<>();
            for (Product product : products) {
                ProductProfileDTO productProfile = fusionRecommendationService.getProductProfile(product.getId());
                userScores.put(product.getId(),
                        fusionRecommendationService.computeProfileMatchScore(userProfile, productProfile, EVAL_SEASON));
            }
            scores.put(test.userId(), userScores);
        }
        return scores;
    }

    private Map<Long, Map<Long, Double>> computeCfScores(List<TestCase> tests,
                                                         List<Product> products,
                                                         Map<Long, Map<Long, Double>> trainMatrix,
                                                         Map<Long, Map<Long, Double>> itemSimilarity) {
        Map<Long, Map<Long, Double>> rawScores = new HashMap<>();
        for (TestCase test : tests) {
            Map<Long, Double> userInteractions = trainMatrix.getOrDefault(test.userId(), Map.of());
            Set<Long> interactedProducts = userInteractions.keySet();
            Map<Long, Double> userScores = new HashMap<>();
            for (Product product : products) {
                if (interactedProducts.contains(product.getId())) {
                    continue;
                }
                // CF 预测分公式直接复用线上公共组件。
                userScores.put(product.getId(), algorithmSupport.computeCfScore(
                        product.getId(), userInteractions, itemSimilarity));
            }
            // 归一化也复用线上公共组件，保证融合前 CF 分数口径一致。
            rawScores.put(test.userId(), algorithmSupport.normalizeScores(userScores));
        }
        return rawScores;
    }

    private Map<Long, Map<Long, Double>> computeFusionScores(List<TestCase> tests,
                                                             List<Product> products,
                                                             Map<Long, Map<Long, Double>> cfScores,
                                                             Map<Long, Map<Long, Double>> profileScores,
                                                             double theta) {
        Map<Long, Map<Long, Double>> fusion = new HashMap<>();
        for (TestCase test : tests) {
            Map<Long, Double> userScores = new HashMap<>();
            for (Product product : products) {
                double cf = cfScores.getOrDefault(test.userId(), Map.of()).getOrDefault(product.getId(), 0.0);
                double profile = profileScores.getOrDefault(test.userId(), Map.of()).getOrDefault(product.getId(), 0.5);
                // 融合公式与 FusionRecommendationService 保持一致：theta*CF + (1-theta)*画像。
                userScores.put(product.getId(), algorithmSupport.computeFusionScore(theta, cf, profile));
            }
            fusion.put(test.userId(), userScores);
        }
        return fusion;
    }

    private Map<Long, List<RankedItem>> rankPopular(List<TestCase> tests,
                                                    List<Product> products,
                                                    Map<Long, Double> popularity,
                                                    Map<Long, Map<Long, Double>> trainMatrix) {
        Map<Long, Map<Long, Double>> scores = new HashMap<>();
        for (TestCase test : tests) {
            Map<Long, Double> userScores = new HashMap<>();
            for (Product product : products) {
                userScores.put(product.getId(), popularity.getOrDefault(product.getId(), 0.0));
            }
            scores.put(test.userId(), userScores);
        }
        return rankByScores(tests, products, scores, popularity, trainMatrix);
    }

    private Map<Long, List<RankedItem>> rankByScores(List<TestCase> tests,
                                                     List<Product> products,
                                                     Map<Long, Map<Long, Double>> scores,
                                                     Map<Long, Double> popularity,
                                                     Map<Long, Map<Long, Double>> trainMatrix) {
        Map<Long, List<RankedItem>> ranked = new LinkedHashMap<>();
        for (TestCase test : tests) {
            Set<Long> interacted = trainMatrix.getOrDefault(test.userId(), Map.of()).keySet();
            List<RankedItem> items = products.stream()
                    .filter(product -> !interacted.contains(product.getId()))
                    .map(product -> new RankedItem(
                            product.getId(),
                            product.getCategoryId(),
                            scores.getOrDefault(test.userId(), Map.of()).getOrDefault(product.getId(), 0.0),
                            popularity.getOrDefault(product.getId(), 0.0)))
                    .sorted(Comparator.comparing(RankedItem::score).reversed()
                            .thenComparing(Comparator.comparing(RankedItem::popular).reversed())
                            .thenComparing(RankedItem::productId))
                    .toList();
            ranked.put(test.userId(), items);
        }
        return ranked;
    }

    private Map<Integer, MetricSummary> evaluate(Map<Long, List<RankedItem>> ranked,
                                                 List<TestCase> tests,
                                                 Map<Long, Product> productMap) {
        Map<Integer, MetricSummary> summary = new LinkedHashMap<>();
        for (int k : TOP_K_VALUES) {
            List<UserMetric> userMetrics = new ArrayList<>();
            Set<Long> coveredProducts = new HashSet<>();
            for (TestCase test : tests) {
                List<RankedItem> topK = ranked.getOrDefault(test.userId(), List.of()).stream().limit(k).toList();
                coveredProducts.addAll(topK.stream().map(RankedItem::productId).collect(Collectors.toSet()));
                Integer hitRank = null;
                for (int i = 0; i < topK.size(); i++) {
                    if (Objects.equals(topK.get(i).productId(), test.testProductId())) {
                        hitRank = i + 1;
                        break;
                    }
                }
                long categoryCount = topK.stream().map(RankedItem::categoryId).filter(Objects::nonNull).distinct().count();
                userMetrics.add(new UserMetric(test.userId(), hitRank != null, hitRank,
                        categoryCount / (double) k));
            }
            double users = userMetrics.size();
            double hits = userMetrics.stream().filter(UserMetric::hit).count();
            double precision = hits / (users * k);
            double recall = hits / users;
            double ndcg = userMetrics.stream()
                    .mapToDouble(m -> m.hitRank() == null ? 0.0 : 1.0 / log2(m.hitRank() + 1))
                    .average()
                    .orElse(0.0);
            double diversity = userMetrics.stream().mapToDouble(UserMetric::diversity).average().orElse(0.0);
            double coverage = coveredProducts.size() / (double) productMap.size();
            summary.put(k, new MetricSummary(precision, recall, recall, ndcg, coverage, diversity, (int) users));
        }
        return summary;
    }

    private double log2(double value) {
        return Math.log(value) / Math.log(2.0);
    }

    private void printDatasetSummary(List<TestCase> tests, Map<Long, Map<Long, Double>> trainMatrix) {
        long ordinaryUsers = tests.stream().map(TestCase::userId).distinct().count();
        long merchants = MERCHANT_ID_END - MERCHANT_ID_START + 1;
        long products = PRODUCT_ID_END - PRODUCT_ID_START + 1;
        long orders = orderMapper.selectCount(new LambdaQueryWrapper<Order>().between(Order::getId, ORDER_ID_START, ORDER_ID_END));
        long completedOrders = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .between(Order::getId, ORDER_ID_START, ORDER_ID_END)
                .eq(Order::getStatus, 3));
        long trainPairs = trainMatrix.values().stream().mapToLong(Map::size).sum();

        System.out.println("\n=== Dataset Summary ===");
        System.out.printf("ordinary_users=%d, merchants=%d, products=%d, orders=%d, completed_orders=%d, evaluated_users=%d, test_samples=%d, train_user_product_pairs=%d%n",
                ordinaryUsers, merchants, products, orders, completedOrders, tests.size(), tests.size(), trainPairs);
    }

    private void assertFixedPaperDataset(List<Product> products,
                                         List<TestCase> tests,
                                         Map<Long, Map<Long, Double>> trainMatrix) {
        long orders = orderMapper.selectCount(new LambdaQueryWrapper<Order>().between(Order::getId, ORDER_ID_START, ORDER_ID_END));
        long completedOrders = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .between(Order::getId, ORDER_ID_START, ORDER_ID_END)
                .eq(Order::getStatus, 3));
        long trainPairs = trainMatrix.values().stream().mapToLong(Map::size).sum();

        assertEquals(24, products.size(), "论文评估候选商品数发生变化，请重新生成表格");
        assertEquals(15, tests.size(), "论文评估用户/测试样本数发生变化，请重新生成表格");
        assertEquals(60, orders, "论文评估订单数发生变化，请重新生成表格");
        assertEquals(48, completedOrders, "论文评估已完成订单数发生变化，请重新生成表格");
        assertEquals(35, trainPairs, "论文评估训练用户-商品交互对数发生变化，请重新生成表格");
    }

    private void assertDatasetUsability(List<TestCase> tests,
                                        Map<Long, Map<Long, Double>> trainMatrix,
                                        Map<Long, Map<Long, Double>> profileScores) {
        /*
         * 数据可用性门槛：
         * 1. 每个评估用户都必须有历史训练行为；
         * 2. 隐藏测试商品不能出现在该用户训练矩阵中；
         * 3. 隐藏测试商品必须能被用户画像规则给出正向或中性解释。
         *
         * 这三个断言用于防止“随意随机购买”或“答案泄露”进入论文实验。
         */
        for (TestCase test : tests) {
            Map<Long, Double> userTrain = trainMatrix.getOrDefault(test.userId(), Map.of());
            assertFalse(userTrain.isEmpty(),
                    "评估用户 " + test.userId() + " 没有训练历史，无法验证推荐算法");
            assertFalse(userTrain.containsKey(test.testProductId()),
                    "评估用户 " + test.userId() + " 的隐藏测试商品泄露到训练矩阵");

            double hiddenProfileScore = profileScores
                    .getOrDefault(test.userId(), Map.of())
                    .getOrDefault(test.testProductId(), 0.0);
            assertTrue(hiddenProfileScore > 0.0,
                    "评估用户 " + test.userId() + " 的隐藏测试商品无法由画像规则解释");
        }
    }

    private void printMainResultTable(Map<String, Map<Integer, MetricSummary>> results) {
        System.out.println("\n=== Main Result Table ===");
        System.out.println("| 推荐算法 | Precision@5 | Recall@5 | HR@5 | NDCG@5 | Precision@10 | Recall@10 | HR@10 | NDCG@10 | Coverage@10 | Diversity@10 | 评估用户数 |");
        System.out.println("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|");
        for (Map.Entry<String, Map<Integer, MetricSummary>> entry : results.entrySet()) {
            MetricSummary k5 = entry.getValue().get(5);
            MetricSummary k10 = entry.getValue().get(10);
            System.out.printf("| %s | %.4f | %.4f | %.4f | %.4f | %.4f | %.4f | %.4f | %.4f | %.4f | %.4f | %d |%n",
                    entry.getKey(),
                    k5.precision(), k5.recall(), k5.hr(), k5.ndcg(),
                    k10.precision(), k10.recall(), k10.hr(), k10.ndcg(),
                    k10.coverage(), k10.diversity(), k10.evaluatedUsers());
        }
    }

    private void printThetaTable(Map<Double, Map<Integer, MetricSummary>> thetaResults) {
        System.out.println("\n=== Fusion Theta Sensitivity ===");
        System.out.println("| theta | Precision@5 | Recall@5 | HR@5 | NDCG@5 | Precision@10 | Recall@10 | HR@10 | NDCG@10 | Coverage@10 | Diversity@10 |");
        System.out.println("|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|");
        for (Map.Entry<Double, Map<Integer, MetricSummary>> entry : thetaResults.entrySet()) {
            MetricSummary k5 = entry.getValue().get(5);
            MetricSummary k10 = entry.getValue().get(10);
            System.out.printf("| %.1f | %.4f | %.4f | %.4f | %.4f | %.4f | %.4f | %.4f | %.4f | %.4f | %.4f |%n",
                    entry.getKey(),
                    k5.precision(), k5.recall(), k5.hr(), k5.ndcg(),
                    k10.precision(), k10.recall(), k10.hr(), k10.ndcg(),
                    k10.coverage(), k10.diversity());
        }
    }

    private void printProfileAudit(List<TestCase> tests,
                                   List<Product> products,
                                   Map<Long, Map<Long, Double>> profileScores) {
        Set<Long> topCategories = new TreeSet<>();
        int seedProducts = 0;
        int cropProducts = 0;
        int animalProducts = 0;
        int neutralProducts = 0;
        int matchedPairs = 0;
        for (Product product : products) {
            ProductProfileDTO profile = fusionRecommendationService.getProductProfile(product.getId());
            Long top = profile.getTopCategoryId();
            if (top != null) {
                topCategories.add(top);
            }
            if (Long.valueOf(1L).equals(top)) {
                seedProducts++;
            } else if (Long.valueOf(2L).equals(top) || Long.valueOf(3L).equals(top)) {
                cropProducts++;
            } else if (Long.valueOf(4L).equals(top) || Long.valueOf(5L).equals(top)) {
                animalProducts++;
            } else {
                neutralProducts++;
            }
        }
        for (TestCase test : tests) {
            for (Product product : products) {
                double score = profileScores.getOrDefault(test.userId(), Map.of()).getOrDefault(product.getId(), 0.5);
                if (score >= 1.0) {
                    matchedPairs++;
                }
            }
        }
        System.out.println("\n=== Profile Usage Audit ===");
        System.out.printf("top_category_ids=%s, seed_products=%d, crop_match_products=%d, animal_match_products=%d, neutral_products=%d, full_profile_matches=%d%n",
                topCategories, seedProducts, cropProducts, animalProducts, neutralProducts, matchedPairs);
        System.out.printf("profile_score_source=FusionRecommendationService.getUserProfileBefore/getProductProfile/computeProfileMatchScore(season=%s)%n",
                EVAL_SEASON);
    }

    private void printUserHitAudit(List<TestCase> tests, Map<String, Map<Integer, MetricSummary>> allResults) {
        System.out.println("\n=== Evaluated Test Cases ===");
        for (TestCase test : tests) {
            System.out.printf("user=%d, hidden_test_product=%d, test_time=%s%n",
                    test.userId(), test.testProductId(), test.testTime());
        }
        System.out.println("metric_source=Leave-One-Out hidden last completed order; no online recommendation exposure is recorded by this test.");
    }

    private void writeCfVsFusionChart(Map<String, Map<Integer, MetricSummary>> results) {
        MetricSummary cf = results.get("协同过滤推荐").get(10);
        MetricSummary fusion = results.get("融合推荐").get(10);

        String[] metrics = {"HR@10", "NDCG@10", "Coverage@10", "Diversity@10"};
        double[] cfValues = {cf.hr(), cf.ndcg(), cf.coverage(), cf.diversity()};
        double[] fusionValues = {fusion.hr(), fusion.ndcg(), fusion.coverage(), fusion.diversity()};

        int width = 1400;
        int height = 860;
        int left = 150;
        int right = 90;
        int top = 110;
        int bottom = 150;
        int plotWidth = width - left - right;
        int plotHeight = height - top - bottom;

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        Color background = Color.WHITE;
        Color axis = new Color(70, 70, 70);
        Color grid = new Color(225, 229, 234);
        Color cfColor = new Color(80, 126, 180);
        Color fusionColor = new Color(72, 151, 116);

        g.setColor(background);
        g.fillRect(0, 0, width, height);

        Font titleFont = preferredFont(Font.BOLD, 32);
        Font labelFont = preferredFont(Font.PLAIN, 24);
        Font smallFont = preferredFont(Font.PLAIN, 20);
        Font valueFont = preferredFont(Font.BOLD, 20);

        g.setFont(titleFont);
        g.setColor(new Color(25, 35, 45));
        drawCentered(g, "普通协同过滤与画像融合推荐效果对比", width / 2, 58);

        g.setFont(smallFont);
        g.setColor(new Color(90, 96, 105));
        drawCentered(g, "数据来源：Java 离线回放评估，K=10", width / 2, 91);

        g.setStroke(new BasicStroke(1.2f));
        for (int i = 0; i <= 5; i++) {
            double value = i / 5.0;
            int y = top + plotHeight - (int) Math.round(value * plotHeight);
            g.setColor(grid);
            g.drawLine(left, y, width - right, y);
            g.setColor(axis);
            g.setFont(smallFont);
            String label = String.format("%.1f", value);
            g.drawString(label, left - 58, y + 7);
        }

        g.setColor(axis);
        g.setStroke(new BasicStroke(2f));
        g.drawLine(left, top, left, top + plotHeight);
        g.drawLine(left, top + plotHeight, width - right, top + plotHeight);

        int groupWidth = plotWidth / metrics.length;
        int barWidth = 72;
        int gap = 18;
        for (int i = 0; i < metrics.length; i++) {
            int groupLeft = left + i * groupWidth;
            int center = groupLeft + groupWidth / 2;
            int cfX = center - barWidth - gap / 2;
            int fusionX = center + gap / 2;
            drawBar(g, cfX, top, plotHeight, barWidth, cfValues[i], cfColor, valueFont);
            drawBar(g, fusionX, top, plotHeight, barWidth, fusionValues[i], fusionColor, valueFont);

            g.setFont(labelFont);
            g.setColor(new Color(45, 50, 55));
            drawCentered(g, metrics[i], center, top + plotHeight + 46);
        }

        int legendY = height - 58;
        drawLegend(g, width / 2 - 220, legendY, cfColor, "普通协同过滤推荐", labelFont);
        drawLegend(g, width / 2 + 70, legendY, fusionColor, "画像融合推荐", labelFont);

        g.dispose();

        try {
            Files.createDirectories(CHART_OUTPUT.getParent());
            ImageIO.write(image, "png", CHART_OUTPUT.toFile());
            System.out.printf("chart_output=%s%n", CHART_OUTPUT.toAbsolutePath());
        } catch (IOException e) {
            throw new IllegalStateException("生成推荐对比图失败: " + CHART_OUTPUT, e);
        }
    }

    private void drawBar(Graphics2D g,
                         int x,
                         int top,
                         int plotHeight,
                         int barWidth,
                         double value,
                         Color color,
                         Font valueFont) {
        int barHeight = (int) Math.round(value * plotHeight);
        int y = top + plotHeight - barHeight;
        g.setColor(color);
        g.fillRoundRect(x, y, barWidth, barHeight, 8, 8);
        g.setColor(color.darker());
        g.drawRoundRect(x, y, barWidth, barHeight, 8, 8);
        g.setFont(valueFont);
        g.setColor(new Color(35, 40, 45));
        drawCentered(g, String.format("%.4f", value), x + barWidth / 2, y - 12);
    }

    private void drawLegend(Graphics2D g, int x, int y, Color color, String text, Font font) {
        g.setColor(color);
        g.fillRoundRect(x, y - 22, 34, 22, 5, 5);
        g.setColor(new Color(45, 50, 55));
        g.setFont(font);
        g.drawString(text, x + 48, y - 3);
    }

    private void drawCentered(Graphics2D g, String text, int centerX, int baselineY) {
        FontMetrics metrics = g.getFontMetrics();
        int x = centerX - metrics.stringWidth(text) / 2;
        g.drawString(text, x, baselineY);
    }

    private Font preferredFont(int style, int size) {
        String[] candidates = {
                "PingFang SC", "Microsoft YaHei", "Noto Sans CJK SC",
                "SimHei", "Songti SC", Font.SANS_SERIF
        };
        Set<String> families = Set.of(GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames());
        for (String candidate : candidates) {
            if (families.contains(candidate)) {
                return new Font(candidate, style, size);
            }
        }
        return new Font(Font.SANS_SERIF, style, size);
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp.toLocalDateTime();
    }

    private record TestCase(Long userId, Long testProductId, LocalDateTime testTime) {}

    private record RankedItem(Long productId, Long categoryId, double score, double popular) {}

    private record UserMetric(Long userId, boolean hit, Integer hitRank, double diversity) {}

    private record MetricSummary(double precision,
                                 double recall,
                                 double hr,
                                 double ndcg,
                                 double coverage,
                                 double diversity,
                                 int evaluatedUsers) {}
}
