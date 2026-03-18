package org.example.springboot.exception;

import org.example.springboot.enums.ErrorCodeEnum;

/**
 * 业务异常类
 *
 * @author IhaveBB
 * @date 2026/03/18
 */
public class BusinessException extends RuntimeException {

    private final String code;

    /**
     * 使用错误码枚举构造异常
     *
     * @param errorCode 错误码枚举
     */
    public BusinessException(ErrorCodeEnum errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    /**
     * 使用错误码枚举和自定义消息构造异常
     *
     * @param errorCode     错误码枚举
     * @param customMessage 自定义消息
     */
    public BusinessException(ErrorCodeEnum errorCode, String customMessage) {
        super(customMessage);
        this.code = errorCode.getCode();
    }

    /**
     * 使用错误码枚举和原因构造异常
     *
     * @param errorCode 错误码枚举
     * @param cause     原因
     */
    public BusinessException(ErrorCodeEnum errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.code = errorCode.getCode();
    }

    /**
     * 获取错误码
     *
     * @return 错误码
     */
    public String getCode() {
        return code;
    }
}
