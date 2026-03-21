package org.example.springboot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.springboot.annotation.RequiresRole;
import org.example.springboot.common.Result;
import org.example.springboot.entity.StockIn;
import org.example.springboot.entity.StockOut;
import org.example.springboot.enumClass.UserRole;
import org.example.springboot.enums.ErrorCodeEnum;
import org.example.springboot.exception.BusinessException;
import org.example.springboot.service.StockService;
import org.example.springboot.util.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 库存管理控制器
 * 提供入库、出库记录的增删改查功能
 *
 * @author IhaveBB
 * @date 2026/03/19
 */
@Tag(name = "库存管理接口")
@RestController
@RequestMapping("/stock")
public class StockController {

    @Autowired
    private StockService stockService;

    /**
     * 创建入库记录
     * 权限：商户或管理员
     *
     * @param stockIn 入库记录
     * @return 创建结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "创建入库记录")
    @RequiresRole({"MERCHANT", "ADMIN"})
    @PostMapping("/in")
    public Result<?> createStockIn(@RequestBody StockIn stockIn) {
        return Result.success(stockService.createStockIn(stockIn));
    }

    /**
     * 创建出库记录
     * 权限：商户或管理员
     *
     * @param stockOut 出库记录
     * @return 创建结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "创建出库记录")
    @RequiresRole({"MERCHANT", "ADMIN"})
    @PostMapping("/out")
    public Result<?> createStockOut(@RequestBody StockOut stockOut) {
        return Result.success(stockService.createStockOut(stockOut));
    }

    /**
     * 获取入库记录列表（分页）
     * 权限：需要登录
     *
     * @param productId   商品ID
     * @param supplier    供应商（模糊查询）
     * @param status      状态
     * @param operatorId  操作人ID
     * @param currentPage 当前页
     * @param size        每页大小
     * @return 分页入库记录
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "获取入库记录列表")
    @RequiresRole
    @GetMapping("/in/list")
    public Result<?> getStockInList(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String supplier,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long operatorId,
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(stockService.getStockInList(productId, supplier, status, operatorId, currentPage, size));
    }

    /**
     * 获取出库记录列表（分页）
     * 权限：需要登录
     *
     * @param productId    商品ID
     * @param type         出库类型
     * @param status       状态
     * @param operatorId   操作人ID
     * @param customerName 客户名称
     * @param orderNo      订单号
     * @param currentPage  当前页
     * @param size         每页大小
     * @return 分页出库记录
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "获取出库记录列表")
    @RequiresRole
    @GetMapping("/out/list")
    public Result<?> getStockOutList(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String orderNo,
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(stockService.getStockOutList(productId, type, status, operatorId, customerName, orderNo, currentPage, size));
    }

    /**
     * 作废入库记录
     * 权限：只有管理员
     *
     * @param id 入库记录ID
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "作废入库记录")
    @RequiresRole("ADMIN")
    @PutMapping("/in/{id}/invalidate")
    public Result<?> invalidateStockIn(@PathVariable Long id) {
        stockService.invalidateStockIn(id);
        return Result.success();
    }

    /**
     * 作废出库记录
     * 权限：只有管理员
     *
     * @param id 出库记录ID
     * @return 操作结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "作废出库记录")
    @RequiresRole("ADMIN")
    @PutMapping("/out/{id}/invalidate")
    public Result<?> invalidateStockOut(@PathVariable Long id) {
        stockService.invalidateStockOut(id);
        return Result.success();
    }
}
