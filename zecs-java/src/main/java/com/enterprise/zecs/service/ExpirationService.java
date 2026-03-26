package com.enterprise.zecs.service;

import com.enterprise.zecs.config.ZecsProperties;
import com.enterprise.zecs.repository.CacheRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 만료 처리 서비스
 * 
 * CICS ZECS000 (백그라운드 만료 프로세스) 를 Java 로 포팅
 * - 주기적으로 만료된 키 스캔
 * - 자동 삭제
 * - 교차 데이터센터 조율 (복제 모드 시)
 */
@Slf4j
@Service
public class ExpirationService {
    
    private final CacheRepository cacheRepository;
    private final ZecsProperties properties;
    private final ReplicationService replicationService;
    
    /**
     * 인스턴스 ID (환경 + 애플리케이션)
     */
    private String instanceId;
    
    public ExpirationService(CacheRepository cacheRepository,
                            ZecsProperties properties,
                            ReplicationService replicationService) {
        this.cacheRepository = cacheRepository;
        this.properties = properties;
        this.replicationService = replicationService;
        this.instanceId = buildInstanceId();
    }
    
    /**
     * 인스턴스 ID 생성
     * 
     * @return 인스턴스 ID (예: ZC00)
     */
    private String buildInstanceId() {
        // 환경 코드로 인스턴스 ID 생성 (간소화)
        String env = properties.getEnvironment();
        return switch (env.toUpperCase()) {
            case "DEV" -> "ZC00";
            case "QA" -> "ZC01";
            case "PROD" -> "ZC02";
            default -> "ZC00";
        };
    }
    
    /**
     * 만료 키 삭제 작업 (주기적 실행)
     * 
     * CICS ZX## 트랜잭션에 해당하는 백그라운드 태스크
     * application.yml 의 expiration.scan-interval 에 따라 실행
     */
    @Scheduled(fixedRateString = "${zecs.expiration.scan-interval:1500}")
    public void processExpiration() {
        log.trace("Starting expiration process for instance: {}", instanceId);
        
        try {
            // 만료된 키 조회
            int batchSize = properties.getExpiration().getBatchSize();
            Set<String> expiredKeys = cacheRepository.getExpiredKeys(instanceId, batchSize);
            
            if (expiredKeys.isEmpty()) {
                log.trace("No expired keys found");
                return;
            }
            
            log.debug("Found {} expired keys", expiredKeys.size());
            
            // 만료된 키 삭제
            int deletedCount = 0;
            for (String key : expiredKeys) {
                // 복제 모드인 경우 파트너 데이터센터 확인
                if (shouldDeleteKey(key)) {
                    if (cacheRepository.delete(instanceId, key)) {
                        deletedCount++;
                        log.trace("Deleted expired key: {}", key);
                    }
                }
            }
            
            log.info("Expiration process completed. Deleted {}/{} keys", 
                    deletedCount, expiredKeys.size());
            
        } catch (Exception e) {
            log.error("Error during expiration process: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 키 삭제 여부 결정
     * 
     * 복제 모드 (AA/AS) 인 경우 파트너 데이터센터의 상태 확인
     * 
     * @param key 삭제 대상 키
     * @return 삭제 여부
     */
    private boolean shouldDeleteKey(String key) {
        // 복제 모드가 아니면 항상 삭제
        if (!properties.getReplication().isEnabled()) {
            return true;
        }
        
        // Active/Active 모드: 양쪽 데이터센터 모두 만료되었는지 확인
        if (properties.getReplication().getMode().equals("AA")) {
            return replicationService.confirmExpiryAtPartner(key);
        }
        
        // Active/Standby 모드: 주 데이터센터 기준 삭제
        return true;
    }
    
    /**
     * 수동 만료 처리 (관리자용)
     * 
     * @param instanceId 인스턴스 ID
     * @return 삭제된 키 수
     */
    public int processExpirationManual(String instanceId) {
        log.info("Manual expiration process for instance: {}", instanceId);
        
        Set<String> expiredKeys = cacheRepository.getExpiredKeys(instanceId, 
                properties.getExpiration().getBatchSize());
        
        int deletedCount = 0;
        for (String key : expiredKeys) {
            if (cacheRepository.delete(instanceId, key)) {
                deletedCount++;
            }
        }
        
        log.info("Manual expiration completed. Deleted {} keys", deletedCount);
        return deletedCount;
    }
    
    /**
     * 인스턴스 ID 설정 (테스트용)
     * 
     * @param instanceId 인스턴스 ID
     */
    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }
}
