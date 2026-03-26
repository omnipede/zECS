package com.enterprise.zecs.config;

import lombok.Data;
import lombok.Builder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * zECS 설정 속성 클래스
 * 
 * application.yml 의 zecs.* 속성을 바인딩
 */
@Data
@Component
@ConfigurationProperties(prefix = "zecs")
public class ZecsProperties {
    
    /**
     * 캐시 설정
     */
    private CacheConfig cache = new CacheConfig();
    
    /**
     * 보안 설정
     */
    private SecurityConfig security = new SecurityConfig();
    
    /**
     * 복제 설정
     */
    private ReplicationConfig replication = new ReplicationConfig();
    
    /**
     * 만료 처리 설정
     */
    private ExpirationConfig expiration = new ExpirationConfig();
    
    /**
     * 환경 이름 (DEV, QA, PROD 등)
     */
    private String environment = "DEV";
    
    /**
     * 조직 ID
     */
    private String organization = "devops";
    
    /**
     * 애플리케이션 이름
     */
    private String applicationName = "sessionData";
    
    @Data
    public static class CacheConfig {
        /**
         * 기본 TTL (초)
         */
        private long defaultTtl = 1800;
        
        /**
         * 최소 TTL (초)
         */
        private long minTtl = 300;
        
        /**
         * 최대 TTL (초)
         */
        private long maxTtl = 86400;
        
        /**
         * 최대 키 길이 (바이트)
         */
        private int maxKeyLength = 255;
        
        /**
         * 최대 밸류 크기 (바이트)
         */
        private int maxValueSize = 3200000;
        
        /**
         * 세그먼트 크기 (바이트)
         */
        private int segmentSize = 32000;
    }
    
    @Data
    public static class SecurityConfig {
        /**
         * 보안 활성화 여부
         */
        private boolean enabled = true;
        
        /**
         * 인증 방식 (BASIC, CERTIFICATE, AUTO 등)
         */
        private String authenticate = "BASIC";
        
        /**
         * 사용자 목록
         */
        private List<UserConfig> users = new ArrayList<>();
    }
    
    @Data
    public static class UserConfig {
        /**
         * 사용자 ID
         */
        private String username;
        
        /**
         * 비밀번호 (BCrypt 암호화)
         */
        private String password;
        
        /**
         * 접근 권한 (SELECT,UPDATE,DELETE)
         */
        private String access;
    }
    
    @Data
    public static class ReplicationConfig {
        /**
         * 복제 모드 (A1, AA, AS)
         */
        private String mode = "A1";
        
        /**
         * 복제 활성화 여부
         */
        private boolean enabled = false;
        
        /**
         * 파트너 설정
         */
        private PartnerConfig partner = new PartnerConfig();
        
        /**
         * 복제 포트
         */
        private int port = 50102;
    }
    
    @Data
    public static class PartnerConfig {
        /**
         * 파트너 호스트
         */
        private String host = "sysplex01-ecs.mycompany.com";
        
        /**
         * 파트너 포트
         */
        private int port = 50102;
        
        /**
         * 파트너 스키마
         */
        private String scheme = "http";
    }
    
    @Data
    public static class ExpirationConfig {
        /**
         * 만료 스캔 인터벌 (밀리초)
         */
        private long scanInterval = 1500;
        
        /**
         * 배치 삭제 크기
         */
        private int batchSize = 500;
        
        /**
         * 동기화 인터벌 (밀리초)
         */
        private long syncInterval = 1500;
    }
}
