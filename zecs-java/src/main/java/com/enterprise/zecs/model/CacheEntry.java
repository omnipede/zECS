package com.enterprise.zecs.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Arrays;

/**
 * zECS 캐시 엔트리 모델
 * 
 * CICS ZECSZFC/ZF-RECORD 구조를 Java 로 포팅
 * - 키: 1-255 바이트
 * - 밸류: 1 바이트 - 3.2MB
 * - ABS: 절대 시간 (CICS ABSTIME 대응)
 * - TTL: 생존 시간 (초)
 * - 세그먼트: 대용량 데이터 분할 저장용
 */
@Data
@Builder
public class CacheEntry {
    
    /**
     * 캐시 키 (1-255 바이트)
     */
    private String key;
    
    /**
     * 캐시 밸류 데이터 (최대 3.2MB)
     */
    private byte[] value;
    
    /**
     * 절대 시간 (CICS ABSTIME - 1970 년부터의 밀리초)
     * Java Instant 로 변환하여 사용
     */
    private Instant absoluteTime;
    
    /**
     * Time To Live (초 단위)
     * 300(5 분) ~ 86400(24 시간)
     */
    private long ttlSeconds;
    
    /**
     * 데이터 세그먼트 수
     * 대용량 데이터가 여러 세그먼트로 분할 저장될 때 사용
     */
    @Builder.Default
    private int segments = 1;
    
    /**
     * 미디어 타입 (MIME 타입)
     */
    private String mediaType;
    
    /**
     * 인스턴스 ID (ZC00, ZC01 등)
     */
    private String instanceId;
    
    /**
     * 생성 시간
     */
    private Instant createdAt;
    
    /**
     * 최종 수정 시간
     */
    private Instant updatedAt;
    
    /**
     * 키가 만료되었는지 확인
     * 
     * @return 만료되었으면 true
     */
    public boolean isExpired() {
        if (absoluteTime == null || ttlSeconds <= 0) {
            return false;
        }
        Instant expiryTime = absoluteTime.plusSeconds(ttlSeconds);
        return Instant.now().isAfter(expiryTime);
    }
    
    /**
     * 밸류가 세그먼트로 분할 필요한지 확인
     * 
     * @param segmentSize 세그먼트 크기
     * @return 분할 필요하면 true
     */
    public boolean requiresSegmentation(int segmentSize) {
        return value != null && value.length > segmentSize;
    }
    
    /**
     * 세그먼트 수 계산
     * 
     * @param segmentSize 세그먼트 크기
     * @return 필요한 세그먼트 수
     */
    public int calculateSegmentCount(int segmentSize) {
        if (value == null || value.length == 0) {
            return 0;
        }
        return (int) Math.ceil((double) value.length / segmentSize);
    }
    
    /**
     * 밸류를 세그먼트로 분할
     * 
     * @param segmentSize 세그먼트 크기
     * @return 분할된 세그먼트 배열
     */
    public byte[][] segmentValue(int segmentSize) {
        if (value == null) {
            return new byte[0][];
        }
        
        int segmentCount = calculateSegmentCount(segmentSize);
        byte[][] segments = new byte[segmentCount][];
        
        for (int i = 0; i < segmentCount; i++) {
            int offset = i * segmentSize;
            int length = Math.min(segmentSize, value.length - offset);
            segments[i] = Arrays.copyOfRange(value, offset, offset + length);
        }
        
        return segments;
    }
}
