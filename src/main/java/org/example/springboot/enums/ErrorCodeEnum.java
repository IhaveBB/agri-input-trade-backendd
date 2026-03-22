package org.example.springboot.enums;

/**
 * 错误码枚举
 *
 * @author IhaveBB
 * @date 2026/03/18
 */
public enum ErrorCodeEnum {

    /**
     * 通用成功
     */
    SUCCESS("0", "成功"),

    /**
     * 通用错误
     */
    ERROR("-1", "操作失败"),

    /**
     * 参数错误
     */
    PARAM_ERROR("400", "参数错误"),

    /**
     * 资源不存在
     */
    NOT_FOUND("404", "资源不存在"),

    /**
     * 资源已存在
     */
    ALREADY_EXISTS("409", "资源已存在"),

    /**
     * 无权限
     */
    FORBIDDEN("403", "无权限操作"),

    /**
     * 未登录
     */
    UNAUTHORIZED("401", "未登录"),

    /**
     * 系统内部错误
     */
    INTERNAL_ERROR("500", "系统内部错误"),

    /**
     * 商品不存在
     */
    PRODUCT_NOT_FOUND("P001", "商品不存在"),

    /**
     * 库存不足
     */
    PRODUCT_STOCK_INSUFFICIENT("P002", "库存不足"),

    /**
     * 商品状态非法
     */
    PRODUCT_STATUS_INVALID("P003", "商品状态非法"),

    /**
     * 订单不存在
     */
    ORDER_NOT_FOUND("O001", "订单不存在"),

    /**
     * 订单状态非法
     */
    ORDER_STATUS_INVALID("O002", "订单状态非法"),

    /**
     * 分类不存在
     */
    CATEGORY_NOT_FOUND("C001", "分类不存在"),

    /**
     * 用户不存在
     */
    USER_NOT_FOUND("U001", "用户不存在"),

    /**
     * 地址不存在
     */
    ADDRESS_NOT_FOUND("A001", "地址不存在"),

    /**
     * 购物车为空
     */
    CART_EMPTY("T001", "购物车为空"),

    /**
     * 物流不存在
     */
    LOGISTICS_NOT_FOUND("L001", "物流信息不存在"),

    /**
     * 文件操作失败
     */
    FILE_OPERATION_FAILED("F001", "文件操作失败"),

    /**
     * 退款失败
     */
    REFUND_FAILED("R001", "退款失败"),

    /**
     * 支付失败
     */
    PAYMENT_FAILED("PAY001", "支付失败"),

    /**
     * 存在关联数据无法删除
     */
    HAS_ASSOCIATED_DATA("D001", "存在关联数据，无法删除"),

    /**
     * 评价不存在
     */
    REVIEW_NOT_FOUND("V001", "评价不存在"),

    /**
     * 未购买商品，不允许评价
     */
    PURCHASE_REQUIRED("V002", "请先购买该商品后再进行评价"),

    /**
     * 重复评价
     */
    REVIEW_ALREADY_EXISTS("V003", "您已经评价过该商品"),

    /**
     * 收藏不存在
     */
    FAVORITE_NOT_FOUND("FAV001", "收藏不存在"),

    /**
     * 文件不存在
     */
    FILE_NOT_FOUND("F002", "文件不存在"),

    /**
     * 文件上传失败
     */
    FILE_UPLOAD_FAILED("F003", "文件上传失败"),

    /**
     * 资讯不存在
     */
    ARTICLE_NOT_FOUND("AR001", "资讯不存在"),

    /**
     * 轮播图不存在
     */
    CAROUSEL_NOT_FOUND("CR001", "轮播图不存在"),

    /**
     * 入库记录不存在
     */
    STOCK_IN_NOT_FOUND("S001", "入库记录不存在"),

    /**
     * 出库记录不存在
     */
    STOCK_OUT_NOT_FOUND("S002", "出库记录不存在"),

    /**
     * 地区不存在
     */
    REGION_NOT_FOUND("RG001", "地区不存在"),

    /**
     * 农事季节不存在
     */
    SEASON_NOT_FOUND("SN001", "农事季节不存在"),

    /**
     * 系统错误（业务级，与 INTERNAL_ERROR 区分使用）
     */
    SYSTEM_ERROR("SYS_ERR", "系统错误"),

    /**
     * 操作失败
     */
    OPERATION_FAILED("OP001", "操作失败"),

    /**
     * 订单状态错误
     */
    ORDER_STATUS_ERROR("O003", "订单状态错误"),

    /**
     * 物流状态错误
     */
    LOGISTICS_STATUS_ERROR("L002", "物流状态错误"),

    /**
     * 权限不足（细粒度鉴权，与 FORBIDDEN 区分使用）
     */
    PERMISSION_DENIED("PERM_DENIED", "权限不足");

    private final String code;
    private final String message;

    ErrorCodeEnum(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
