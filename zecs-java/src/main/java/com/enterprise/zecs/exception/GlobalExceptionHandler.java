package com.enterprise.zecs.exception;

import com.enterprise.zecs.service.CacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 전역 예외 처리기
 * 
 * zECS 의 HTTP 상태 코드 매핑:
 * - 400: WEB RECEIVE 오류, 잘못된 URI, 키 길이 오류
 * - 401: 인증 실패
 * - 409: 중복 키 (POST)
 * - 507: ZCxxKEY/ZCxxFILE 오류 (서버 오류)
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    
    /**
     * IllegalArgumentException 처리
     * 
     * @param ex 예외 객체
     * @return ResponseEntity
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Invalid URI format",
                ex.getMessage()
        );
    }
    
    /**
     * 키 길이 초과 예외 처리
     * 
     * @param ex 예외 객체
     * @return ResponseEntity
     */
    @ExceptionHandler(KeyLengthExceededException.class)
    public ResponseEntity<Map<String, Object>> handleKeyLengthExceeded(KeyLengthExceededException ex) {
        log.warn("Key length exceeded: {}", ex.getMessage());
        
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Key exceeds maximum 255 bytes",
                ex.getMessage()
        );
    }
    
    /**
     * 키 길이 0 예외 처리
     * 
     * @param ex 예외 객체
     * @return ResponseEntity
     */
    @ExceptionHandler(KeyLengthZeroException.class)
    public ResponseEntity<Map<String, Object>> handleKeyLengthZero(KeyLengthZeroException ex) {
        log.warn("Key length zero: {}", ex.getMessage());
        
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Key must be greater than 0 bytes",
                ex.getMessage()
        );
    }
    
    /**
     * 인증 예외 처리
     * 
     * @param ex 예외 객체
     * @return ResponseEntity
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthentication(AuthenticationException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        
        return buildErrorResponse(
                HttpStatus.UNAUTHORIZED,
                "Basic Authentication failed",
                ex.getMessage()
        );
    }
    
    /**
     * 중복 키 예외 처리
     * 
     * @param ex 예외 객체
     * @return ResponseEntity
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateKey(DuplicateKeyException ex) {
        log.warn("Duplicate key: {}", ex.getMessage());
        
        return buildErrorResponse(
                HttpStatus.CONFLICT,
                "POST/PUT conflict with DELETE",
                ex.getMessage()
        );
    }
    
    /**
     * 캐시 저장소 예외 처리
     * 
     * @param ex 예외 객체
     * @return ResponseEntity
     */
    @ExceptionHandler(CacheRepositoryException.class)
    public ResponseEntity<Map<String, Object>> handleCacheRepository(CacheRepositoryException ex) {
        log.error("Cache repository error: {}", ex.getMessage(), ex);
        
        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "ZCxxFILE error",
                ex.getMessage()
        );
    }
    
    /**
     * WEB RECEIVE 예외 처리
     * 
     * @param ex 예외 객체
     * @return ResponseEntity
     */
    @ExceptionHandler(WebReceiveException.class)
    public ResponseEntity<Map<String, Object>> handleWebReceive(WebReceiveException ex) {
        log.error("WEB RECEIVE error: {}", ex.getMessage(), ex);
        
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "WEB RECEIVE error",
                ex.getMessage()
        );
    }
    
    /**
     * 일반 예외 처리
     * 
     * @param ex 예외 객체
     * @return ResponseEntity
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneralException(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        
        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal server error",
                ex.getMessage()
        );
    }
    
    /**
     * 오류 응답 생성
     * 
     * @param status HTTP 상태
     * @param message 오류 메시지
     * @param details 상세 정보
     * @return ResponseEntity
     */
    private ResponseEntity<Map<String, Object>> buildErrorResponse(
            HttpStatus status,
            String message,
            String details) {
        
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("timestamp", Instant.now().toString());
        errorResponse.put("status", status.value());
        errorResponse.put("error", status.getReasonPhrase());
        errorResponse.put("message", message);
        errorResponse.put("details", details);
        
        return ResponseEntity.status(status).body(errorResponse);
    }
    
    // ----------------------------------------------------------------
    // Custom Exception Classes
    // ----------------------------------------------------------------
    
    /**
     * 키 길이 초과 예외
     */
    public static class KeyLengthExceededException extends IllegalArgumentException {
        public KeyLengthExceededException(String message) {
            super(message);
        }
    }
    
    /**
     * 키 길이 0 예외
     */
    public static class KeyLengthZeroException extends IllegalArgumentException {
        public KeyLengthZeroException(String message) {
            super(message);
        }
    }
    
    /**
     * 인증 예외
     */
    public static class AuthenticationException extends RuntimeException {
        public AuthenticationException(String message) {
            super(message);
        }
    }
    
    /**
     * 중복 키 예외
     */
    public static class DuplicateKeyException extends RuntimeException {
        public DuplicateKeyException(String message) {
            super(message);
        }
    }
    
    /**
     * 캐시 저장소 예외
     */
    public static class CacheRepositoryException extends RuntimeException {
        public CacheRepositoryException(String message) {
            super(message);
        }
        
        public CacheRepositoryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * WEB RECEIVE 예외
     */
    public static class WebReceiveException extends RuntimeException {
        public WebReceiveException(String message) {
            super(message);
        }
        
        public WebReceiveException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
