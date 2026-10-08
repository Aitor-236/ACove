package com.acove.blog.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data 
@Component 
@ConfigurationProperties (prefix = "jwt")
public class JwtProperties {
    private String secretKey;
    private long expireTime;
}
