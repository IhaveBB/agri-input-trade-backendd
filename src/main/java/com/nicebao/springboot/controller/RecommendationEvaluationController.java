package com.nicebao.springboot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nicebao.springboot.common.Result;
import com.nicebao.springboot.service.RecommendationEvaluationService;
import com.nicebao.springboot.service.RecommendationEvaluationService.EvaluationResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 推荐算法运行监控评估控制器
 * <p>
 * 提供后台管理侧的推荐算法粗略评估接口。该接口直接调用线上推荐策略，
 * 不做按时间切分的离线回放，不能作为论文实验数据来源。
 * </p>
 *
 * @author IhaveBB
 * @date 2026/03/21
 */
@Slf4j
@Tag(name = "推荐算法评估", description = "推荐算法运行监控指标计算")
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
    @Operation(summary = "评估所有算法", description = "后台运行监控用粗略评估，不作为论文离线回放结果")
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
    @Operation(summary = "评估融合推荐算法", description = "后台运行监控用粗略评估，不作为论文离线回放结果")
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
    @Operation(summary = "评估纯CF算法", description = "后台运行监控用粗略评估，不作为论文离线回放结果")
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
    @Operation(summary = "评估热销算法", description = "后台运行监控用粗略评估，不作为论文离线回放结果")
    @GetMapping("/hot")
    public Result<EvaluationResult> evaluateHotAlgorithm() {
        log.info("[评估接口] 评估热销推荐算法");
        EvaluationResult result = evaluationService.evaluateHotAlgorithm();
        return Result.success(result);
    }
}
