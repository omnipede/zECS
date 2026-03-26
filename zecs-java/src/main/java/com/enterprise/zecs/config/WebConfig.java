package com.enterprise.zecs.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 설정 클래스
 * 
 * CORS 및 웹 관련 설정을 정의
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    /**
     * CORS 설정
     * 
     * @param registry CorsRegistry
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 모든 Origin 허용 (CICS 의 Access-Control-Allow-Origin: * 과 동일)
        registry.addMapping("/resources/ecs/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
