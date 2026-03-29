package org.example.springboot.config;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "alipay")
@Data
public class AliPayConfig {

    private String appId;
    private String appPrivateKey;
    private String alipayPublicKey;
    private String notifyUrl;
    private String returnUrl;
    private String gatewayUrl;
    private String format = "JSON";
    private String charset = "UTF-8";
    private String signType = "RSA2";

    /**
     * AlipayClient 单例 Bean
     */
    @Bean
    public AlipayClient alipayClient() {
        return new DefaultAlipayClient(
                gatewayUrl,
                appId,
                appPrivateKey,
                format,
                charset,
                alipayPublicKey,
                signType
        );
    }

    @Override
    public String toString() {
        return "AliPayConfig{" +
                "appId='" + appId + '\'' +
                ", gatewayUrl='" + gatewayUrl + '\'' +
                ", notifyUrl='" + notifyUrl + '\'' +
                ", returnUrl='" + returnUrl + '\'' +
                '}';
    }
}
