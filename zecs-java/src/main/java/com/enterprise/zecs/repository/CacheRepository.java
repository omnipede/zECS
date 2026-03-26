package com.enterprise.zecs.repository;

import com.enterprise.zecs.config.ZecsProperties;
import com.enterprise.zecs.model.CacheEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 캐시 데이터 저장소
 * 
 * CICS VSAM/RLS (ZFAM 파일) 를 Redis 로 포팅
 * - 키/밸류 저장
 * - TTL 기반 만료
 * - 대용량 데이터 세그먼트 처리
 */
@Slf4j
@Repository
public class CacheRepository {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final ZecsProperties properties;
    
    /**
     * 키 접두사 (인스턴스별 네임스페이스)
     */
    private static final String KEY_PREFIX = "zecs:";
    private static final String KEY_DATA_PREFIX = "data:";
    private static final String KEY_META_PREFIX = "meta:";
    private static final String KEY_SEGMENT_PREFIX = "seg:";
    
    /**
     * 세그먼트 키 구분자
     */
    private static final String SEGMENT_SEPARATOR = ":";
    
    public CacheRepository(RedisTemplate<String, Object> redisTemplate, ZecsProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }
    
    /**
     * 캐시 키 생성
     * 
     * @param instanceId 인스턴스 ID
     * @param key 사용자 키
     * @return 전체 Redis 키
     */
    private String buildKey(String instanceId, String key) {
        return KEY_PREFIX + instanceId + ":" + key;
    }
    
    /**
     * 메타데이터 키 생성
     * 
     * @param instanceId 인스턴스 ID
     * @param key 사용자 키
     * @return 메타데이터 Redis 키
     */
    private String buildMetaKey(String instanceId, String key) {
        return KEY_PREFIX + instanceId + KEY_META_PREFIX + key;
    }
    
    /**
     * 세그먼트 키 생성
     * 
     * @param instanceId 인스턴스 ID
     * @param key 사용자 키
     * @param segmentIndex 세그먼트 인덱스
     * @return 세그먼트 Redis 키
     */
    private String buildSegmentKey(String instanceId, String key, int segmentIndex) {
        return KEY_PREFIX + instanceId + KEY_SEGMENT_PREFIX + key + SEGMENT_SEPARATOR + segmentIndex;
    }
    
    /**
     * 캐시 엔트리 저장
     * 
     * @param instanceId 인스턴스 ID
     * @param entry 저장할 캐시 엔트리
     * @return 성공 여부
     */
    public boolean save(String instanceId, CacheEntry entry) {
        try {
            String fullKey = buildKey(instanceId, entry.getKey());
            String metaKey = buildMetaKey(instanceId, entry.getKey());
            
            log.debug("Saving key: {}, instance: {}, fullKey: {}", entry.getKey(), instanceId, fullKey);

            // 밸류 크기 확인
            if (entry.getValue() == null || entry.getValue().length == 0) {
                log.warn("Empty value for key: {}", entry.getKey());
                return false;
            }

            int segmentSize = properties.getCache().getSegmentSize();

            if (entry.requiresSegmentation(segmentSize)) {
                // 대용량 데이터 - 세그먼트로 분할 저장
                return saveSegmented(instanceId, entry, fullKey, metaKey, segmentSize);
            } else {
                // 단일 키로 저장
                return saveSingle(instanceId, entry, fullKey, metaKey);
            }
        } catch (Exception e) {
            log.error("Error saving cache entry: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 단일 키로 저장 (32KB 미만)
     */
    private boolean saveSingle(String instanceId, CacheEntry entry, String fullKey, String metaKey) {
        try {
            // 바이트 배열을 문자열로 변환하여 저장 (Base64 인코딩)
            String encodedValue = Base64.getEncoder().encodeToString(entry.getValue());
            redisTemplate.opsForValue().set(fullKey, encodedValue);
            
            // 메타데이터 저장
            saveMetadata(metaKey, entry);
            
            // TTL 설정
            long ttl = entry.getTtlSeconds();
            if (ttl > 0) {
                redisTemplate.expire(fullKey, ttl, TimeUnit.SECONDS);
                redisTemplate.expire(metaKey, ttl, TimeUnit.SECONDS);
            }
            
            log.debug("Saved single key: {}, size: {} bytes, TTL: {}s, Redis keys: {}, {}", 
                    entry.getKey(), entry.getValue().length, ttl, fullKey, metaKey);
            return true;
        } catch (Exception e) {
            log.error("Error saving single key: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 세그먼트로 분할 저장 (32KB 이상)
     */
    private boolean saveSegmented(String instanceId, CacheEntry entry, String fullKey, String metaKey, int segmentSize) {
        try {
            byte[][] segments = entry.segmentValue(segmentSize);
            
            // 각 세그먼트 저장
            for (int i = 0; i < segments.length; i++) {
                String segmentKey = buildSegmentKey(instanceId, entry.getKey(), i);
                redisTemplate.opsForValue().set(segmentKey, segments[i]);
                
                // 세그먼트에도 TTL 설정
                long ttl = entry.getTtlSeconds();
                if (ttl > 0) {
                    redisTemplate.expire(segmentKey, ttl, TimeUnit.SECONDS);
                }
            }
            
            // 메타데이터 저장 (세그먼트 수 포함)
            entry.setSegments(segments.length);
            saveMetadata(metaKey, entry);
            
            log.debug("Saved segmented key: {}, segments: {}, total size: {} bytes", 
                    entry.getKey(), segments.length, entry.getValue().length);
            return true;
        } catch (Exception e) {
            log.error("Error saving segmented key: {}", e.getMessage(), e);
            // 롤백 - 저장된 세그먼트 삭제
            deleteSegments(instanceId, entry.getKey());
            return false;
        }
    }
    
    /**
     * 메타데이터 저장
     */
    private void saveMetadata(String metaKey, CacheEntry entry) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("key", entry.getKey());
        
        if (entry.getAbsoluteTime() != null) {
            metadata.put("absoluteTime", String.valueOf(entry.getAbsoluteTime().toEpochMilli()));
        }
        
        metadata.put("ttlSeconds", String.valueOf(entry.getTtlSeconds()));
        metadata.put("segments", String.valueOf(entry.getSegments()));
        
        if (entry.getMediaType() != null) {
            metadata.put("mediaType", entry.getMediaType());
        }
        
        metadata.put("createdAt", String.valueOf(
            entry.getCreatedAt() != null ? 
            entry.getCreatedAt().toEpochMilli() : System.currentTimeMillis()));
        metadata.put("updatedAt", String.valueOf(System.currentTimeMillis()));

        redisTemplate.opsForHash().putAll(metaKey, metadata);
    }
    
    /**
     * 캐시 엔트리 조회
     * 
     * @param instanceId 인스턴스 ID
     * @param key 조회할 키
     * @return CacheEntry 객체 (없으면 null)
     */
    public CacheEntry get(String instanceId, String key) {
        try {
            String fullKey = buildKey(instanceId, key);
            String metaKey = buildMetaKey(instanceId, key);
            
            log.debug("Getting key: {}, fullKey: {}, metaKey: {}", key, instanceId, fullKey, metaKey);

            // 메타데이터 먼저 확인
            Map<Object, Object> metadata = redisTemplate.opsForHash().entries(metaKey);
            if (metadata.isEmpty()) {
                log.debug("Metadata not found for key: {}", key);
                return null;
            }
            
            log.debug("Metadata found: {}", metadata);

            // 세그먼트 수 확인
            Object segmentsObj = metadata.get("segments");
            Integer segments = segmentsObj != null ? 
                (segmentsObj instanceof Integer ? (Integer) segmentsObj : Integer.parseInt(segmentsObj.toString())) : 1;
            byte[] value;

            if (segments != null && segments > 1) {
                // 세그먼트로 저장된 데이터 조립
                value = getSegmentedValue(instanceId, key, segments);
            } else {
                // 단일 키에서 조회 (Base64 디코딩 필요)
                Object obj = redisTemplate.opsForValue().get(fullKey);
                log.debug("Retrieved value object: {} (type: {})", obj, obj != null ? obj.getClass().getName() : "null");
                
                if (obj instanceof String) {
                    // Base64 디코딩
                    value = Base64.getDecoder().decode((String) obj);
                } else if (obj instanceof byte[]) {
                    value = (byte[]) obj;
                } else if (obj != null) {
                    value = obj.toString().getBytes();
                } else {
                    value = null;
                }
            }

            if (value == null) {
                log.debug("Data value is null for key: {}", key);
                return null;
            }
            
            log.debug("Successfully retrieved key: {}, value size: {} bytes", key, value.length);

            // CacheEntry 객체 생성
            return buildCacheEntryFromMetadata(key, value, metadata);
        } catch (Exception e) {
            log.error("Error getting cache entry: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 세그먼트로 저장된 값 조립
     */
    private byte[] getSegmentedValue(String instanceId, String key, int segmentCount) {
        List<Byte> byteList = new ArrayList<>();
        
        for (int i = 0; i < segmentCount; i++) {
            String segmentKey = buildSegmentKey(instanceId, key, i);
            Object obj = redisTemplate.opsForValue().get(segmentKey);
            byte[] segment = obj instanceof byte[] ? (byte[]) obj : 
                    (obj != null ? obj.toString().getBytes() : new byte[0]);
            
            for (byte b : segment) {
                byteList.add(b);
            }
        }
        
        // List<Byte> to byte[]
        byte[] result = new byte[byteList.size()];
        for (int i = 0; i < byteList.size(); i++) {
            result[i] = byteList.get(i);
        }
        
        return result;
    }
    
    /**
     * 메타데이터로 CacheEntry 객체 생성
     */
    private CacheEntry buildCacheEntryFromMetadata(String key, byte[] value, Map<Object, Object> metadata) {
        CacheEntry.CacheEntryBuilder builder = CacheEntry.builder()
                .key(key)
                .value(value);

        // absoluteTime 처리
        Object absoluteTimeObj = metadata.get("absoluteTime");
        if (absoluteTimeObj != null) {
            try {
                Long absoluteTimeMillis = absoluteTimeObj instanceof Long ? 
                    (Long) absoluteTimeObj : Long.parseLong(absoluteTimeObj.toString());
                builder.absoluteTime(Instant.ofEpochMilli(absoluteTimeMillis));
            } catch (Exception e) {
                log.warn("Failed to parse absoluteTime: {}", absoluteTimeObj);
            }
        }

        // ttlSeconds 처리
        Object ttlObj = metadata.get("ttlSeconds");
        if (ttlObj != null) {
            try {
                Long ttlSeconds = ttlObj instanceof Long ? 
                    (Long) ttlObj : Long.parseLong(ttlObj.toString());
                builder.ttlSeconds(ttlSeconds);
            } catch (Exception e) {
                log.warn("Failed to parse ttlSeconds: {}", ttlObj);
            }
        }

        // segments 처리
        Object segmentsObj = metadata.get("segments");
        if (segmentsObj != null) {
            try {
                Integer segments = segmentsObj instanceof Integer ? 
                    (Integer) segmentsObj : Integer.parseInt(segmentsObj.toString());
                builder.segments(segments);
            } catch (Exception e) {
                log.warn("Failed to parse segments: {}", segmentsObj);
            }
        }

        String mediaType = (String) metadata.get("mediaType");
        if (mediaType != null) {
            builder.mediaType(mediaType);
        }

        return builder.build();
    }
    
    /**
     * 캐시 엔트리 삭제
     * 
     * @param instanceId 인스턴스 ID
     * @param key 삭제할 키
     * @return 성공 여부
     */
    public boolean delete(String instanceId, String key) {
        try {
            String fullKey = buildKey(instanceId, key);
            String metaKey = buildMetaKey(instanceId, key);
            
            log.debug("Deleting key: {}, fullKey: {}, metaKey: {}", key, instanceId, fullKey, metaKey);

            // 메타데이터에서 세그먼트 수 확인
            Map<Object, Object> metadata = redisTemplate.opsForHash().entries(metaKey);
            
            Integer segments = null;
            Object segmentsObj = metadata.get("segments");
            if (segmentsObj != null) {
                try {
                    segments = segmentsObj instanceof Integer ? 
                        (Integer) segmentsObj : Integer.parseInt(segmentsObj.toString());
                } catch (Exception e) {
                    log.warn("Failed to parse segments: {}", segmentsObj);
                }
            }

            // 세그먼트 삭제 (있는 경우)
            if (segments != null && segments > 1) {
                deleteSegments(instanceId, key);
            }

            // 메인 키 및 메타데이터 삭제
            redisTemplate.delete(fullKey);
            redisTemplate.delete(metaKey);
            
            log.debug("Deleted key: {}", key);
            return true;
        } catch (Exception e) {
            log.error("Error deleting cache entry: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 세그먼트 삭제
     */
    private void deleteSegments(String instanceId, String key) {
        String metaKey = buildMetaKey(instanceId, key);
        Map<Object, Object> metadata = redisTemplate.opsForHash().entries(metaKey);
        Integer segments = (Integer) metadata.get("segments");
        
        if (segments != null) {
            for (int i = 0; i < segments; i++) {
                String segmentKey = buildSegmentKey(instanceId, key, i);
                redisTemplate.delete(segmentKey);
            }
        }
    }
    
    /**
     * 키 존재 여부 확인
     * 
     * @param instanceId 인스턴스 ID
     * @param key 확인할 키
     * @return 존재하면 true
     */
    public boolean exists(String instanceId, String key) {
        String metaKey = buildMetaKey(instanceId, key);
        Boolean exists = redisTemplate.hasKey(metaKey);
        return exists != null && exists;
    }
    
    /**
     * 만료된 키들 조회 (삭제용)
     * 
     * @param instanceId 인스턴스 ID
     * @param limit 최대 조회 수
     * @return 만료된 키 목록
     */
    public Set<String> getExpiredKeys(String instanceId, int limit) {
        Set<String> expiredKeys = new HashSet<>();

        try {
            // 모든 메타데이터 키 스캔
            String pattern = KEY_PREFIX + instanceId + KEY_META_PREFIX + "*";
            Set<String> keys = redisTemplate.keys(pattern);

            if (keys != null && !keys.isEmpty()) {
                for (String metaKey : keys) {
                    if (expiredKeys.size() >= limit) {
                        break;
                    }

                    Map<Object, Object> metadata = redisTemplate.opsForHash().entries(metaKey);
                    
                    // absoluteTime 처리
                    Object absoluteTimeObj = metadata.get("absoluteTime");
                    Long absoluteTimeMillis = null;
                    if (absoluteTimeObj != null) {
                        try {
                            absoluteTimeMillis = absoluteTimeObj instanceof Long ? 
                                (Long) absoluteTimeObj : Long.parseLong(absoluteTimeObj.toString());
                        } catch (Exception e) {
                            log.warn("Failed to parse absoluteTime: {}", absoluteTimeObj);
                        }
                    }
                    
                    // ttlSeconds 처리
                    Object ttlObj = metadata.get("ttlSeconds");
                    Long ttlSeconds = null;
                    if (ttlObj != null) {
                        try {
                            ttlSeconds = ttlObj instanceof Long ? 
                                (Long) ttlObj : Long.parseLong(ttlObj.toString());
                        } catch (Exception e) {
                            log.warn("Failed to parse ttlSeconds: {}", ttlObj);
                        }
                    }

                    if (absoluteTimeMillis != null && ttlSeconds != null && ttlSeconds > 0) {
                        Instant expiryTime = Instant.ofEpochMilli(absoluteTimeMillis)
                                .plusSeconds(ttlSeconds);
                        if (Instant.now().isAfter(expiryTime)) {
                            // 메타데이터 키에서 실제 키 추출
                            String key = metaKey.substring((KEY_PREFIX + instanceId + KEY_META_PREFIX).length());
                            expiredKeys.add(key);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error getting expired keys: {}", e.getMessage(), e);
        }

        return expiredKeys;
    }
    
    /**
     * 인스턴스의 모든 키 삭제
     * 
     * @param instanceId 인스턴스 ID
     * @return 삭제된 키 수
     */
    public long clearAll(String instanceId) {
        long count = 0;
        
        try {
            String pattern = KEY_PREFIX + instanceId + "*";
            Set<String> keys = redisTemplate.keys(pattern);
            
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                count = keys.size();
                log.info("Cleared {} keys for instance: {}", count, instanceId);
            }
        } catch (Exception e) {
            log.error("Error clearing all keys: {}", e.getMessage(), e);
        }
        
        return count;
    }
}
