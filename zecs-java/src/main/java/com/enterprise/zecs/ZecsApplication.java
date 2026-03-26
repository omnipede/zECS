package com.enterprise.zecs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * zECS Java 애플리케이션 메인 클래스
 * 
 * Enterprise Caching System - Java Implementation
 * 
 * CICS/TS 기반의 zECS 를 Spring Boot 로 포팅
 * - RESTful API 지원 (GET, POST, PUT, DELETE)
 * - Redis 기반 분산 캐싱
 * - Basic Authentication 보안
 * - 자동 만료 처리
 * - 데이터센터 간 복제 지원
 */
@SpringBootApplication
@EnableScheduling
public class ZecsApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(ZecsApplication.class, args);
    }
}
