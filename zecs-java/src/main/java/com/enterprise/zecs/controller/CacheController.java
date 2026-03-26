package com.enterprise.zecs.controller;

import com.enterprise.zecs.model.CacheEntry;
import com.enterprise.zecs.security.UserAccessManager;
import com.enterprise.zecs.service.CacheService;
import com.enterprise.zecs.service.ReplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.Optional;

/**
 * 캐시 REST API 컨트롤러
 * 
 * zECS 의 RESTful 서비스 구현
 * - GET: 키/밸류 조회
 * - POST: 키/밸류 생성 (중복 시 오류)
 * - PUT: 키/밸류 생성/수정
 * - DELETE: 키 삭제
 */
@Slf4j
@RestController
@RequestMapping("/resources/ecs")
@Tag(name = "zECS Cache API", description = "Enterprise Caching Service REST API")
public class CacheController {
    
    private final CacheService cacheService;
    private final ReplicationService replicationService;
    private final UserAccessManager userAccessManager;
    
    /**
     * URL 경로 패턴
     */
    private static final String PATH_PATTERN = "/resources/ecs/{org}/{app}/{key}";
    
    public CacheController(CacheService cacheService,
                          ReplicationService replicationService,
                          UserAccessManager userAccessManager) {
        this.cacheService = cacheService;
        this.replicationService = replicationService;
        this.userAccessManager = userAccessManager;
    }
    
    /**
     * 캐시 엔트리 조회 (GET)
     * 
     * @param org 조직 ID
     * @param app 애플리케이션 ID
     * @param key 캐시 키
     * @param request HTTP 요청
     * @return 캐시 엔트리 (바이트 배열)
     */
    @GetMapping("/{org}/{app}/**")
    @Operation(
        summary = "Retrieve cache entry",
        description = "Retrieve a value from the zECS instance by key"
    )
    public ResponseEntity<byte[]> get(
            @Parameter(description = "Organization ID") @PathVariable String org,
            @Parameter(description = "Application ID") @PathVariable String app,
            HttpServletRequest request) {
        
        // URL 에서 키 추출
        String key = extractKeyFromRequest(request);
        log.debug("GET request: org={}, app={}, key={}", org, app, key);
        
        // 인스턴스 ID 생성
        String instanceId = buildInstanceId(org, app);
        
        // 사용자 ID 조회
        String userId = userAccessManager.getCurrentUserId();
        
        // 캐시 엔트리 조회
        Optional<CacheEntry> entryOpt = cacheService.get(instanceId, key, userId);
        
        if (entryOpt.isEmpty()) {
            // 204 No Content - 레코드 없음
            return ResponseEntity.noContent().build();
        }
        
        CacheEntry entry = entryOpt.get();
        
        // HTTP 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        
        // 미디어 타입 설정
        if (entry.getMediaType() != null) {
            headers.setContentType(MediaType.parseMediaType(entry.getMediaType()));
        } else {
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        }
        
        // CORS 헤더 (CICS 와 동일)
        headers.setAccessControlAllowOrigin("*");
        
        // TTL 정보 헤더 (선택적)
        // headers.set("X-TTL-Seconds", String.valueOf(entry.getTtlSeconds()));
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(entry.getValue());
    }
    
    /**
     * 캐시 엔트리 생성 (POST)
     * 
     * @param org 조직 ID
     * @param app 애플리케이션 ID
     * @param key 캐시 키
     * @param value 밸류 데이터
     * @param ttlSeconds TTL (초)
     * @param request HTTP 요청
     * @return HTTP 상태 코드
     */
    @PostMapping("/{org}/{app}/**")
    @Operation(
        summary = "Create cache entry",
        description = "Write a key/value to the zECS instance. Creates new keys or returns conflict for existing keys."
    )
    public ResponseEntity<Void> post(
            @Parameter(description = "Organization ID") @PathVariable String org,
            @Parameter(description = "Application ID") @PathVariable String app,
            HttpServletRequest request,
            @Parameter(description = "Time to live in seconds (300-86400)")
            @RequestParam(required = false) Long ttl,
            @RequestBody byte[] value) {
        
        String key = extractKeyFromRequest(request);
        log.debug("POST request: org={}, app={}, key={}, size={} bytes", 
                org, app, key, value != null ? value.length : 0);
        
        String instanceId = buildInstanceId(org, app);
        String userId = userAccessManager.getCurrentUserId();
        String mediaType = request.getContentType();
        
        int statusCode = cacheService.post(instanceId, key, value, ttl, userId, mediaType);
        
        // POST 생성/수정 후 복제
        if (statusCode == HttpStatus.OK.value() || statusCode == HttpStatus.CREATED.value()) {
            replicationService.replicateWrite(key, value, 
                    ttl != null ? ttl : 1800, instanceId);
        }
        
        return ResponseEntity.status(statusCode).build();
    }
    
    /**
     * 캐시 엔트리 생성/수정 (PUT)
     * 
     * @param org 조직 ID
     * @param app 애플리케이션 ID
     * @param key 캐시 키
     * @param value 밸류 데이터
     * @param ttlSeconds TTL (초)
     * @param request HTTP 요청
     * @return HTTP 상태 코드
     */
    @PutMapping("/{org}/{app}/**")
    @Operation(
        summary = "Update cache entry",
        description = "Write a key/value to the zECS instance. Creates new keys or updates existing keys."
    )
    public ResponseEntity<Void> put(
            @Parameter(description = "Organization ID") @PathVariable String org,
            @Parameter(description = "Application ID") @PathVariable String app,
            HttpServletRequest request,
            @Parameter(description = "Time to live in seconds (300-86400)")
            @RequestParam(required = false) Long ttl,
            @RequestBody byte[] value) {
        
        String key = extractKeyFromRequest(request);
        log.debug("PUT request: org={}, app={}, key={}, size={} bytes", 
                org, app, key, value != null ? value.length : 0);
        
        String instanceId = buildInstanceId(org, app);
        String userId = userAccessManager.getCurrentUserId();
        String mediaType = request.getContentType();
        
        int statusCode = cacheService.put(instanceId, key, value, ttl, userId, mediaType);
        
        // PUT 생성/수정 후 복제
        if (statusCode == HttpStatus.OK.value()) {
            replicationService.replicateWrite(key, value, 
                    ttl != null ? ttl : 1800, instanceId);
        }
        
        return ResponseEntity.status(statusCode).build();
    }
    
    /**
     * 캐시 엔트리 삭제 (DELETE)
     * 
     * @param org 조직 ID
     * @param app 애플리케이션 ID
     * @param key 캐시 키
     * @param clear 전체 삭제 플래그 (clear=*)
     * @param request HTTP 요청
     * @return HTTP 상태 코드
     */
    @DeleteMapping("/{org}/{app}/**")
    @Operation(
        summary = "Delete cache entry",
        description = "Delete a key from the zECS instance. Use ?clear=* to delete all keys."
    )
    public ResponseEntity<Void> delete(
            @Parameter(description = "Organization ID") @PathVariable String org,
            @Parameter(description = "Application ID") @PathVariable String app,
            HttpServletRequest request,
            @Parameter(description = "Clear all keys when set to '*'")
            @RequestParam(required = false) String clear) {
        
        String instanceId = buildInstanceId(org, app);
        String userId = userAccessManager.getCurrentUserId();
        
        // 전체 삭제 (clear=*)
        if ("*".equals(clear)) {
            log.info("Clear all request: org={}, app={}", org, app);
            long deletedCount = cacheService.clearAll(instanceId, userId);
            log.info("Cleared {} keys", deletedCount);
            return ResponseEntity.ok().build();
        }
        
        // 단일 키 삭제
        String key = extractKeyFromRequest(request);
        log.debug("DELETE request: org={}, app={}, key={}", org, app, key);
        
        int statusCode = cacheService.delete(instanceId, key, userId);
        
        // DELETE 후 복제
        if (statusCode == HttpStatus.OK.value()) {
            replicationService.replicateDelete(key, instanceId);
        }
        
        return ResponseEntity.status(statusCode).build();
    }
    
    /**
     * URL 경로에서 키 추출
     * 
     * @param request HTTP 요청
     * @return 캐시 키
     */
    private String extractKeyFromRequest(HttpServletRequest request) {
        // Spring MVC 가 매칭한 경로에서 키 부분 추출
        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String bestMatch = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        
        // bestMatch: /resources/ecs/{org}/{app}/**
        // path: /resources/ecs/{org}/{app}/actual/key/here
        
        if (path != null && bestMatch != null) {
            // 프리픽스 제거 (/resources/ecs/{org}/{app}/)
            int prefixEnd = bestMatch.lastIndexOf("/**");
            if (prefixEnd > 0) {
                String prefix = bestMatch.substring(0, prefixEnd);
                // 실제 경로에서 프리픽스 길이만큼 잘라냄
                String actualPrefix = path.substring(0, Math.min(path.length(), 
                        bestMatch.indexOf("/**")));
                
                // org/app 경로 찾기
                int orgIndex = actualPrefix.indexOf("/", 1); // 첫 번째 / 이후
                int appIndex = actualPrefix.indexOf("/", orgIndex + 1);
                
                if (appIndex > 0) {
                    return path.substring(appIndex + 1);
                }
            }
        }
        
        // fallback: URL 디코딩
        String requestUri = request.getRequestURI();
        int lastSlash = requestUri.lastIndexOf('/');
        if (lastSlash > 0 && lastSlash < requestUri.length() - 1) {
            return requestUri.substring(lastSlash + 1);
        }
        
        return "";
    }
    
    /**
     * 인스턴스 ID 생성
     * 
     * @param org 조직 ID
     * @param app 애플리케이션 ID
     * @return 인스턴스 ID
     */
    private String buildInstanceId(String org, String app) {
        // org + app 조합으로 인스턴스 ID 생성
        // 예: devops + sessionData = ZC00
        // 실제 구현에서는 설정에서 매핑
        return "ZC00";
    }
}
