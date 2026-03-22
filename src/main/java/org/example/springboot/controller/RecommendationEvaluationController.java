package org.example.springboot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.springboot.common.Result;
import org.example.springboot.service.RecommendationEvaluationService;
import org.example.springboot.service.RecommendationEvaluationService.EvaluationResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 推荐算法评估控制器
 * <p>
 * 提供推荐算法离线评估接口，用于论文实验对比
 * </p>
 *
 * @author IhaveBB
 * @date 2026/03/21
 */
@Slf4j
@Tag(name = "推荐算法评估", description = "推荐算法离线评估指标计算")
@RestController
@RequestMapping("/api/evaluation")
@RequiredArgsConstructor
public class RecommendationEvaluationController {

    private final RecommendationEvaluationService evaluationService;

    /**
     * 评估所有推荐算法
     *
     * @return 各算法的评估结果
     */
    @Operation(summary = "评估所有算法", description = "计算融合推荐、纯CF、热销推荐的各项指标")
    @GetMapping("/all")
    public Result<List<EvaluationResult>> evaluateAllAlgorithms() {
        log.info("[评估接口] 开始评估所有算法");
        List<EvaluationResult> results = evaluationService.evaluateAllAlgorithms();
        return Result.success(results);
    }

    /**
     * 评估融合推荐算法
     *
     * @return 评估结果
     */
    @Operation(summary = "评估融合推荐算法", description = "计算融合推荐算法的准确率、召回率、F1、NDCG等指标")
    @GetMapping("/fusion")
    public Result<EvaluationResult> evaluateFusionAlgorithm() {
        log.info("[评估接口] 评估融合推荐算法");
        EvaluationResult result = evaluationService.evaluateFusionAlgorithm();
        return Result.success(result);
    }

    /**
     * 评估纯协同过滤算法
     *
     * @return 评估结果
     */
    @Operation(summary = "评估纯CF算法", description = "计算纯协同过滤算法的各项指标")
    @GetMapping("/cf")
    public Result<EvaluationResult> evaluateCFAlgorithm() {
        log.info("[评估接口] 评估纯协同过滤算法");
        EvaluationResult result = evaluationService.evaluateCFAlgorithm();
        return Result.success(result);
    }

    /**
     * 评估热销推荐算法
     *
     * @return 评估结果
     */
    @Operation(summary = "评估热销算法", description = "计算热销推荐算法的各项指标")
    @GetMapping("/hot")
    public Result<EvaluationResult> evaluateHotAlgorithm() {
        log.info("[评估接口] 评估热销推荐算法");
        EvaluationResult result = evaluationService.evaluateHotAlgorithm();
        return Result.success(result);
    }
}
