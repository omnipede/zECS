package com.enterprise.zecs.service;

import com.enterprise.zecs.config.ZecsProperties;
import com.enterprise.zecs.model.CacheEntry;
import com.enterprise.zecs.model.SecurityDefinition;
import com.enterprise.zecs.repository.CacheRepository;
import com.enterprise.zecs.security.UserAccessManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * 캐시 서비스
 * 
 * zECS 의 핵심 비즈니스 로직 구현
 * - CRUD 연산 (GET, POST, PUT, DELETE)
 * - 권한 검증
 * - TTL 검증
 */
@Slf4j
@Service
public class CacheService {
    
    private final CacheRepository cacheRepository;
    private final UserAccessManager userAccessManager;
    private final ZecsProperties properties;
    
    /**
     * HTTP 상태 코드 상수 (CICS 응답 코드 매핑)
     */
    public static final int HTTP_OK = 200;
    public static final int HTTP_CREATED = 201;
    public static final int HTTP_NO_CONTENT = 204;
    public static final int HTTP_BAD_REQUEST = 400;
    public static final int HTTP_UNAUTHORIZED = 401;
    public static final int HTTP_CONFLICT = 409;
    public static final int HTTP_SERVER_ERROR = 507;
    
    public CacheService(CacheRepository cacheRepository, 
                       UserAccessManager userAccessManager,
                       ZecsProperties properties) {
        this.cacheRepository = cacheRepository;
        this.userAccessManager = userAccessManager;
        this.properties = properties;
    }
    
    /**
     * 캐시 엔트리 조회 (GET)
     * 
     * @param instanceId 인스턴스 ID
     * @param key 조회할 키
     * @param userId 사용자 ID
     * @return CacheEntry 객체 (Optional)
     */
    @Transactional(readOnly = true)
    public Optional<CacheEntry> get(String instanceId, String key, String userId) {
        log.debug("GET request for instance: {}, key: {}, user: {}", instanceId, key, userId);
        
        // 권한 확인
        if (!hasAccess(userId, SecurityDefinition.AccessType.SELECT)) {
            log.warn("User {} does not have SELECT access", userId);
            return Optional.empty();
        }
        
        // 키 유효성 검사
        if (!isValidKey(key)) {
            log.warn("Invalid key format: {}", key);
            return Optional.empty();
        }
        
        CacheEntry entry = cacheRepository.get(instanceId, key);
        
        if (entry == null) {
            log.debug("Key not found: {}", key);
            return Optional.empty();
        }
        
        // 만료 확인
        if (entry.isExpired()) {
            log.debug("Key expired: {}", key);
            cacheRepository.delete(instanceId, key);
            return Optional.empty();
        }
        
        return Optional.of(entry);
    }
    
    /**
     * 캐시 엔트리 생성/수정 (POST)
     * 
     * @param instanceId 인스턴스 ID
     * @param key 키
     * @param value 밸류 데이터
     * @param ttlSeconds TTL (초)
     * @param userId 사용자 ID
     * @param mediaType 미디어 타입
     * @return HTTP 상태 코드
     */
    @Transactional
    public int post(String instanceId, String key, byte[] value, Long ttlSeconds, 
                   String userId, String mediaType) {
        log.debug("POST request for instance: {}, key: {}, user: {}, size: {} bytes", 
                instanceId, key, userId, value != null ? value.length : 0);
        
        // 권한 확인
        if (!hasAccess(userId, SecurityDefinition.AccessType.UPDATE)) {
            log.warn("User {} does not have UPDATE access", userId);
            return HTTP_UNAUTHORIZED;
        }
        
        // 키 유효성 검사
        ValidationResult validation = validateKey(key);
        if (!validation.isValid()) {
            log.warn("Invalid key: {}", validation.getMessage());
            return HTTP_BAD_REQUEST;
        }
        
        // 밸류 유효성 검사
        if (value == null || value.length == 0) {
            log.warn("Empty value for key: {}", key);
            return HTTP_BAD_REQUEST;
        }
        
        if (value.length > properties.getCache().getMaxValueSize()) {
            log.warn("Value size {} exceeds maximum {}", value.length, 
                    properties.getCache().getMaxValueSize());
            return HTTP_BAD_REQUEST;
        }
        
        // TTL 검증 및 설정
        long effectiveTtl = validateTtl(ttlSeconds);
        
        // 캐시 엔트리 생성
        CacheEntry entry = CacheEntry.builder()
                .key(key)
                .value(value)
                .absoluteTime(Instant.now())
                .ttlSeconds(effectiveTtl)
                .mediaType(mediaType)
                .instanceId(instanceId)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        
        // 기존 키 확인 (POST 는 중복 시 오류)
        boolean exists = cacheRepository.exists(instanceId, key);
        
        if (cacheRepository.save(instanceId, entry)) {
            return exists ? HTTP_OK : HTTP_CREATED;
        }
        
        return HTTP_SERVER_ERROR;
    }
    
    /**
     * 캐시 엔트리 생성/수정 (PUT)
     * 
     * @param instanceId 인스턴스 ID
     * @param key 키
     * @param value 밸류 데이터
     * @param ttlSeconds TTL (초)
     * @param userId 사용자 ID
     * @param mediaType 미디어 타입
     * @return HTTP 상태 코드
     */
    @Transactional
    public int put(String instanceId, String key, byte[] value, Long ttlSeconds,
                  String userId, String mediaType) {
        log.debug("PUT request for instance: {}, key: {}, user: {}, size: {} bytes", 
                instanceId, key, userId, value != null ? value.length : 0);
        
        // 권한 확인
        if (!hasAccess(userId, SecurityDefinition.AccessType.UPDATE)) {
            log.warn("User {} does not have UPDATE access", userId);
            return HTTP_UNAUTHORIZED;
        }
        
        // 키 유효성 검사
        ValidationResult validation = validateKey(key);
        if (!validation.isValid()) {
            log.warn("Invalid key: {}", validation.getMessage());
            return HTTP_BAD_REQUEST;
        }
        
        // 밸류 유효성 검사
        if (value == null || value.length == 0) {
            log.warn("Empty value for key: {}", key);
            return HTTP_BAD_REQUEST;
        }
        
        if (value.length > properties.getCache().getMaxValueSize()) {
            log.warn("Value size {} exceeds maximum {}", value.length, 
                    properties.getCache().getMaxValueSize());
            return HTTP_BAD_REQUEST;
        }
        
        // TTL 검증 및 설정
        long effectiveTtl = validateTtl(ttlSeconds);
        
        // 캐시 엔트리 생성
        CacheEntry entry = CacheEntry.builder()
                .key(key)
                .value(value)
                .absoluteTime(Instant.now())
                .ttlSeconds(effectiveTtl)
                .mediaType(mediaType)
                .instanceId(instanceId)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        
        if (cacheRepository.save(instanceId, entry)) {
            return HTTP_OK;
        }
        
        return HTTP_SERVER_ERROR;
    }
    
    /**
     * 캐시 엔트리 삭제 (DELETE)
     * 
     * @param instanceId 인스턴스 ID
     * @param key 삭제할 키
     * @param userId 사용자 ID
     * @return HTTP 상태 코드
     */
    @Transactional
    public int delete(String instanceId, String key, String userId) {
        log.debug("DELETE request for instance: {}, key: {}, user: {}", instanceId, key, userId);
        
        // 권한 확인
        if (!hasAccess(userId, SecurityDefinition.AccessType.DELETE)) {
            log.warn("User {} does not have DELETE access", userId);
            return HTTP_UNAUTHORIZED;
        }
        
        // 키 유효성 검사
        if (!isValidKey(key)) {
            log.warn("Invalid key format: {}", key);
            return HTTP_BAD_REQUEST;
        }
        
        // 키 존재 확인
        if (!cacheRepository.exists(instanceId, key)) {
            log.debug("Key not found: {}", key);
            return HTTP_NO_CONTENT;
        }
        
        if (cacheRepository.delete(instanceId, key)) {
            return HTTP_OK;
        }
        
        return HTTP_SERVER_ERROR;
    }
    
    /**
     * 전체 캐시 초기화 (clear=*)
     * 
     * @param instanceId 인스턴스 ID
     * @param userId 사용자 ID
     * @return 삭제된 키 수
     */
    @Transactional
    public long clearAll(String instanceId, String userId) {
        log.debug("CLEAR ALL request for instance: {}, user: {}", instanceId, userId);
        
        // 권한 확인 (DELETE 권한 필요)
        if (!hasAccess(userId, SecurityDefinition.AccessType.DELETE)) {
            log.warn("User {} does not have DELETE access for clear all", userId);
            return 0;
        }
        
        return cacheRepository.clearAll(instanceId);
    }
    
    /**
     * 사용자 권한 확인
     */
    private boolean hasAccess(String userId, SecurityDefinition.AccessType accessType) {
        // 보안이 비활성화되어 있으면 항상 허용
        if (!properties.getSecurity().isEnabled()) {
            return true;
        }
        
        if (userId == null || userId.trim().isEmpty()) {
            return false;
        }
        
        return userAccessManager.hasAccess(userId, accessType);
    }
    
    /**
     * 키 유효성 검사
     */
    private boolean isValidKey(String key) {
        return validateKey(key).isValid();
    }
    
    /**
     * 키 유효성 검사 (상세 메시지 포함)
     */
    private ValidationResult validateKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            return ValidationResult.invalid("Key must be greater than 0 bytes");
        }
        
        // 키 길이 확인 (바이트 기준)
        byte[] keyBytes = key.getBytes();
        if (keyBytes.length > properties.getCache().getMaxKeyLength()) {
            return ValidationResult.invalid(
                "Key exceeds maximum " + properties.getCache().getMaxKeyLength() + " bytes");
        }
        
        // 임베디드 공백 확인
        if (key.contains(" ")) {
            return ValidationResult.invalid("Key cannot contain embedded spaces");
        }
        
        return ValidationResult.valid();
    }
    
    /**
     * TTL 검증 및 보정
     * 
     * @param ttlSeconds 요청된 TTL
     * @return 보정된 TTL
     */
    private long validateTtl(Long ttlSeconds) {
        long defaultTtl = properties.getCache().getDefaultTtl();
        long minTtl = properties.getCache().getMinTtl();
        long maxTtl = properties.getCache().getMaxTtl();
        
        if (ttlSeconds == null || ttlSeconds <= 0) {
            return defaultTtl;
        }
        
        // 최소/최대 TTL 제한
        if (ttlSeconds < minTtl) {
            return minTtl;
        }
        
        if (ttlSeconds > maxTtl) {
            return maxTtl;
        }
        
        return ttlSeconds;
    }
    
    /**
     * 검증 결과 클래스
     */
    private static class ValidationResult {
        private final boolean valid;
        private final String message;
        
        private ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }
        
        static ValidationResult valid() {
            return new ValidationResult(true, null);
        }
        
        static ValidationResult invalid(String message) {
            return new ValidationResult(false, message);
        }
        
        boolean isValid() {
            return valid;
        }
        
        String getMessage() {
            return message;
        }
    }
}
