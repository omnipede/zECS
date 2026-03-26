package com.enterprise.zecs.model;

import lombok.Builder;
import lombok.Data;

/**
 * 복제 모드 열거형
 * 
 * CICS ZC##DC 문서 템플릿의 replication type 을 Java 로 포팅
 */
public enum ReplicationMode {
    /**
     * Standalone - 단일 사이트 운영
     * 복제 없음
     */
    A1("Standalone"),
    
    /**
     * Active/Active - 멀티사이트 활성 복제
     * 양방향 복제
     */
    AA("Active/Active"),
    
    /**
     * Active/Standby - DR 장애조치 구성
     * 단방향 복제 (주 → 대기)
     */
    AS("Active/Standby");
    
    private final String description;
    
    ReplicationMode(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * 문자열 코드로부터 ReplicationMode 조회
     * 
     * @param code 모드 코드 (A1, AA, AS)
     * @return ReplicationMode 열거형
     * @throws IllegalArgumentException 유효하지 않은 코드
     */
    public static ReplicationMode fromCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return A1; // 기본값
        }
        
        try {
            return ReplicationMode.valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return A1;
        }
    }
}
