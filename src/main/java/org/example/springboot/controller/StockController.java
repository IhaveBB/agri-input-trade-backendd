package org.example.springboot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.springboot.common.Result;
import org.example.springboot.entity.StockIn;
import org.example.springboot.entity.StockOut;
import org.example.springboot.enumClass.UserRole;
import org.example.springboot.service.StockService;
import org.example.springboot.util.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name = "库存管理接口")
@RestController
@RequestMapping("/stock")
public class StockController {

    @Autowired
    private StockService stockService;

    @Operation(summary = "创建入库记录")
    @PostMapping("/in")
    public Result<?> createStockIn(@RequestBody StockIn stockIn) {
        Long userId = UserContext.getUserId();
        String role = UserContext.getRole();

        if (userId == null) {
            return Result.error("-1", "用户未登录");
        }
        // 只有商户和管理员可以创建入库记录
        if (!UserRole.isMerchant(role) && !UserRole.isAdmin(role)) {
            return Result.error("-1", "无权限创建入库记录");
        }
        stockIn.setOperatorId(userId);
        return Result.success(stockService.createStockIn(stockIn));
    }

    @Operation(summary = "创建出库记录")
    @PostMapping("/out")
    public Result<?> createStockOut(@RequestBody StockOut stockOut) {
        Long userId = UserContext.getUserId();
        String role = UserContext.getRole();

        if (userId == null) {
            return Result.error("-1", "用户未登录");
        }
        // 只有商户和管理员可以创建出库记录
        if (!UserRole.isMerchant(role) && !UserRole.isAdmin(role)) {
            return Result.error("-1", "无权限创建出库记录");
        }
        stockOut.setOperatorId(userId);
        return Result.success(stockService.createStockOut(stockOut));
    }

    @Operation(summary = "获取入库记录列表")
    @GetMapping("/in/list")
    public Result<?> getStockInList(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String supplier,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "10") Integer size) {

        Long userId = UserContext.getUserId();
        String role = UserContext.getRole();
        Long operatorId = null;

        if (userId != null && UserRole.isMerchant(role)) {
            // 商户只能查看自己操作的记录
            operatorId = userId;
        }

        return Result.success(stockService.getStockInList(productId, supplier, status, operatorId, currentPage, size));
    }

    @Operation(summary = "获取出库记录列表")
    @GetMapping("/out/list")
    public Result<?> getStockOutList(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String orderNo,
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "10") Integer size) {

        Long userId = UserContext.getUserId();
        String role = UserContext.getRole();
        Long operatorId = null;

        if (userId != null && UserRole.isMerchant(role)) {
            // 商户只能查看自己操作的记录
            operatorId = userId;
        }

        return Result.success(stockService.getStockOutList(productId, type, status, operatorId, customerName, orderNo, currentPage, size));
    }

    @Operation(summary = "作废入库记录")
    @PutMapping("/in/{id}/invalidate")
    public Result<?> invalidateStockIn(@PathVariable Long id) {
        String role = UserContext.getRole();

        if (role == null) {
            return Result.error("-1", "用户未登录");
        }
        // 只有管理员可以作废记录
        if (!UserRole.isAdmin(role)) {
            return Result.error("-1", "无权限作废入库记录");
        }
        stockService.invalidateStockIn(id);
        return Result.success();
    }

    @Operation(summary = "作废出库记录")
    @PutMapping("/out/{id}/invalidate")
    public Result<?> invalidateStockOut(@PathVariable Long id) {
        String role = UserContext.getRole();

        if (role == null) {
            return Result.error("-1", "用户未登录");
        }
        // 只有管理员可以作废记录
        if (!UserRole.isAdmin(role)) {
            return Result.error("-1", "无权限作废出库记录");
        }
        stockService.invalidateStockOut(id);
        return Result.success();
    }

    @Operation(summary = "删除入库记录")
    @DeleteMapping("/in/{id}")
    public Result<?> deleteStockIn(@PathVariable Long id) {
        String role = UserContext.getRole();

        if (role == null) {
            return Result.error("-1", "用户未登录");
        }
        // 只有管理员可以删除记录
        if (!UserRole.isAdmin(role)) {
            return Result.error("-1", "无权限删除入库记录");
        }
        stockService.deleteStockIn(id);
        return Result.success();
    }

    @Operation(summary = "删除出库记录")
    @DeleteMapping("/out/{id}")
    public Result<?> deleteStockOut(@PathVariable Long id) {
        String role = UserContext.getRole();

        if (role == null) {
            return Result.error("-1", "用户未登录");
        }
        // 只有管理员可以删除记录
        if (!UserRole.isAdmin(role)) {
            return Result.error("-1", "无权限删除出库记录");
        }
        stockService.deleteStockOut(id);
        return Result.success();
    }
}
