package com.enterprise.zecs;

import com.enterprise.zecs.model.CacheEntry;
import com.enterprise.zecs.repository.CacheRepository;
import com.enterprise.zecs.service.CacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CacheService 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
class CacheServiceTest {
    
    @Mock
    private CacheRepository cacheRepository;
    
    private CacheService cacheService;
    
    @BeforeEach
    void setUp() {
        // TODO: UserAccessManager 와 ZecsProperties 모킹 추가 필요
        // 현재는 간단히 repository 만 테스트
    }
    
    @Test
    @DisplayName("캐시 엔트리 생성 테스트")
    void testCreateCacheEntry() {
        // Given
        String instanceId = "ZC00";
        String key = "test-key";
        byte[] value = "test-value".getBytes(StandardCharsets.UTF_8);
        long ttlSeconds = 1800;
        
        CacheEntry entry = CacheEntry.builder()
                .key(key)
                .value(value)
                .absoluteTime(Instant.now())
                .ttlSeconds(ttlSeconds)
                .build();
        
        // When & Then
        assertNotNull(entry.getKey());
        assertEquals(key, entry.getKey());
        assertArrayEquals(value, entry.getValue());
        assertEquals(ttlSeconds, entry.getTtlSeconds());
        assertFalse(entry.isExpired());
    }
    
    @Test
    @DisplayName("캐시 만료 테스트")
    void testCacheExpiration() {
        // Given
        String key = "expired-key";
        byte[] value = "test".getBytes(StandardCharsets.UTF_8);
        
        // 1 초 전의 시간에 1 초 TTL 설정 (이미 만료됨)
        CacheEntry entry = CacheEntry.builder()
                .key(key)
                .value(value)
                .absoluteTime(Instant.now().minusSeconds(2))
                .ttlSeconds(1)
                .build();
        
        // When & Then
        assertTrue(entry.isExpired());
    }
    
    @Test
    @DisplayName("세그먼트 계산 테스트")
    void testSegmentCalculation() {
        // Given
        int segmentSize = 100; // 100 바이트 세그먼트
        
        // 250 바이트 데이터 (3 세그먼트 필요)
        byte[] largeValue = new byte[250];
        
        CacheEntry entry = CacheEntry.builder()
                .key("large-key")
                .value(largeValue)
                .ttlSeconds(1800)
                .build();
        
        // When
        int segmentCount = entry.calculateSegmentCount(segmentSize);
        
        // Then
        assertEquals(3, segmentCount);
        assertTrue(entry.requiresSegmentation(segmentSize));
    }
}
