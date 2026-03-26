package com.enterprise.zecs.service;

import com.enterprise.zecs.config.ZecsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * 복제 서비스
 * 
 * CICS 의 데이터센터 간 복제 기능을 Java 로 포팅
 * - Active/Active: 양방향 복제
 * - Active/Standby: 단방향 복제
 * - REST API 를 통한 파트너 데이터센터 통신
 */
@Slf4j
@Service
public class ReplicationService {
    
    private final ZecsProperties properties;
    private final RestTemplate restTemplate;
    
    /**
     * 복제용 HTTP 타임아웃 (밀리초)
     */
    private static final int REPLICATION_TIMEOUT = 2000;
    
    public ReplicationService(ZecsProperties properties) {
        this.properties = properties;
        this.restTemplate = createRestTemplate();
    }
    
    /**
     * 복제용 RestTemplate 생성
     * 
     * @return RestTemplate 객체
     */
    private RestTemplate createRestTemplate() {
        RestTemplate template = new RestTemplate();
        
        // 타임아웃 설정
        template.getInterceptors().add((request, body, execution) -> {
            request.getHeaders().set("Connection", "close");
            return execution.execute(request, body);
        });
        
        return template;
    }
    
    /**
     * 파트너 데이터센터로 복제 (POST/PUT 연산)
     * 
     * @param key 키
     * @param value 밸류 데이터
     * @param ttlSeconds TTL
     * @param instanceId 인스턴스 ID
     * @return 복제 성공 여부
     */
    public boolean replicateWrite(String key, byte[] value, long ttlSeconds, String instanceId) {
        if (!isReplicationEnabled()) {
            return true; // 복제 비활성화 시 성공으로 간주
        }
        
        String partnerUrl = getPartnerUrl();
        if (partnerUrl == null) {
            log.warn("Partner URL not configured for replication");
            return false;
        }
        
        try {
            String url = buildReplicationUrl(partnerUrl, instanceId, key, ttlSeconds);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            
            // Basic Auth 헤더 추가 (필요시)
            // String authHeader = createAuthHeader();
            // headers.set("Authorization", authHeader);
            
            HttpEntity<byte[]> request = new HttpEntity<>(value, headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    String.class
            );
            
            boolean success = response.getStatusCode().is2xxSuccessful();
            log.debug("Replicated write to partner: key={}, status={}", key, response.getStatusCode());
            
            return success;
            
        } catch (Exception e) {
            log.error("Failed to replicate write to partner: {}", e.getMessage());
            // 복제 실패 시에도 로컬 연산은 계속 진행
            return false;
        }
    }
    
    /**
     * 파트너 데이터센터로 삭제 복제 (DELETE 연산)
     * 
     * @param key 삭제할 키
     * @param instanceId 인스턴스 ID
     * @return 복제 성공 여부
     */
    public boolean replicateDelete(String key, String instanceId) {
        if (!isReplicationEnabled()) {
            return true;
        }
        
        String partnerUrl = getPartnerUrl();
        if (partnerUrl == null) {
            log.warn("Partner URL not configured for replication");
            return false;
        }
        
        try {
            String url = buildReplicationUrl(partnerUrl, instanceId, key, null);
            
            HttpHeaders headers = new HttpHeaders();
            
            HttpEntity<Void> request = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.DELETE,
                    request,
                    String.class
            );
            
            boolean success = response.getStatusCode().is2xxSuccessful();
            log.debug("Replicated delete to partner: key={}, status={}", key, response.getStatusCode());
            
            return success;
            
        } catch (Exception e) {
            log.error("Failed to replicate delete to partner: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 파트너 데이터센터의 만료 확인 (Active/Active 모드)
     * 
     * @param key 확인할 키
     * @return 만료되었으면 true
     */
    public boolean confirmExpiryAtPartner(String key) {
        if (!properties.getReplication().getMode().equals("AA")) {
            return true; // Active/Active 아니면 로컬 기준
        }
        
        // TODO: 파트너 데이터센터에 키의 만료 상태 조회
        // 현재는 항상 true 반환 (로컬 기준 삭제)
        return true;
    }
    
    /**
     * 복제 활성화 여부 확인
     * 
     * @return 복제 활성화 여부
     */
    public boolean isReplicationEnabled() {
        return properties.getReplication().isEnabled() && 
               properties.getReplication().getPartner().getHost() != null;
    }
    
    /**
     * 파트너 데이터센터 URL 생성
     * 
     * @return 파트너 URL
     */
    private String getPartnerUrl() {
        ZecsProperties.PartnerConfig partner = properties.getReplication().getPartner();
        if (partner.getHost() == null) {
            return null;
        }
        
        return String.format("%s://%s:%d",
                partner.getScheme() != null ? partner.getScheme() : "http",
                partner.getHost(),
                partner.getPort());
    }
    
    /**
     * 복제용 URL 생성
     * 
     * @param partnerUrl 파트너 기본 URL
     * @param instanceId 인스턴스 ID
     * @param key 키
     * @param ttlSeconds TTL
     * @return 전체 URL
     */
    private String buildReplicationUrl(String partnerUrl, String instanceId, String key, Long ttlSeconds) {
        StringBuilder url = new StringBuilder(partnerUrl);
        url.append("/resources/ecs/");
        url.append(properties.getOrganization());
        url.append("/");
        url.append(properties.getApplicationName());
        url.append("/");
        url.append(key);
        
        // TTL 파라미터 추가
        if (ttlSeconds != null && ttlSeconds > 0) {
            url.append("?ttl=").append(ttlSeconds);
        }
        
        // 복제 모드 표시 (파트너가 무조건 수용하도록)
        url.append("&replicate=true");
        
        return url.toString();
    }
    
    /**
     * 복제 모드 확인 (Active/Active)
     * 
     * @return Active/Active 모드이면 true
     */
    public boolean isActiveActiveMode() {
        return "AA".equalsIgnoreCase(properties.getReplication().getMode());
    }
    
    /**
     * 복제 모드 확인 (Active/Standby)
     * 
     * @return Active/Standby 모드이면 true
     */
    public boolean isActiveStandbyMode() {
        return "AS".equalsIgnoreCase(properties.getReplication().getMode());
    }
}
