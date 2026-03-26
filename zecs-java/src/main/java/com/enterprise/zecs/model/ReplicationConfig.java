package com.enterprise.zecs.model;

import lombok.Builder;
import lombok.Data;

/**
 * 복제 설정 모델
 * 
 * CICS ZC##DC 문서 템플릿을 Java 로 포팅
 */
@Data
@Builder
public class ReplicationConfig {
    
    /**
     * 복제 모드 (A1, AA, AS)
     */
    @Builder.Default
    private ReplicationMode mode = ReplicationMode.A1;
    
    /**
     * 복제 활성화 여부
     */
    @Builder.Default
    private boolean enabled = false;
    
    /**
     * 파트너 데이터센터 호스트
     */
    private String partnerHost;
    
    /**
     * 파트너 데이터센터 포트
     */
    private int partnerPort;
    
    /**
     * 파트너 데이터센터 스키마 (http/https)
     */
    @Builder.Default
    private String partnerScheme = "http";
    
    /**
     * 복제 포트
     */
    private int replicationPort;
    
    /**
     * 복제 URL 생성
     * 
     * @return 복제 URL 문자열
     */
    public String getPartnerUrl() {
        if (partnerHost == null || partnerHost.trim().isEmpty()) {
            return null;
        }
        return String.format("%s://%s:%d", 
                partnerScheme != null ? partnerScheme : "http",
                partnerHost, 
                partnerPort);
    }
    
    /**
     * 복제 활성화 확인
     * 
     * @return Active/Active 또는 Active/Standby 모드이면 true
     */
    public boolean isReplicationActive() {
        return enabled && (mode == ReplicationMode.AA || mode == ReplicationMode.AS);
    }
    
    /**
     * 양방향 복제인지 확인
     * 
     * @return Active/Active 모드이면 true
     */
    public boolean isActiveActive() {
        return mode == ReplicationMode.AA;
    }
    
    /**
     * 단방향 복제인지 확인
     * 
     * @return Active/Standby 모드이면 true
     */
    public boolean isActiveStandby() {
        return mode == ReplicationMode.AS;
    }
}
