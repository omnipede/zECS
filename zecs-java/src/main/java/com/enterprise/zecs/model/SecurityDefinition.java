package com.enterprise.zecs.model;

import lombok.Builder;
import lombok.Data;

import java.util.EnumSet;
import java.util.Set;

/**
 * 보안 정의 모델
 * 
 * CICS ZC##SD 문서 템플릿의 사용자 권한 정의를 Java 로 포팅
 * - SELECT: 읽기 권한
 * - UPDATE: 쓰기/수정 권한
 * - DELETE: 삭제 권한
 */
@Data
@Builder
public class SecurityDefinition {
    
    /**
     * 사용자 ID
     */
    private String userId;
    
    /**
     * 접근 권한 세트
     */
    @Builder.Default
    private Set<AccessType> accessTypes = EnumSet.noneOf(AccessType.class);
    
    /**
     * 접근 권한 타입
     */
    public enum AccessType {
        /**
         * 읽기 권한 (GET)
         */
        SELECT,
        
        /**
         * 쓰기/수정 권한 (POST, PUT)
         */
        UPDATE,
        
        /**
         * 삭제 권한 (DELETE)
         */
        DELETE
    }
    
    /**
     * 특정 권한이 있는지 확인
     * 
     * @param type 권한 타입
     * @return 권한이 있으면 true
     */
    public boolean hasAccess(AccessType type) {
        return accessTypes != null && accessTypes.contains(type);
    }
    
    /**
     * GET 연산 권한이 있는지 확인
     * 
     * @return SELECT 권한이 있으면 true
     */
    public boolean canRead() {
        return hasAccess(AccessType.SELECT);
    }
    
    /**
     * POST/PUT 연산 권한이 있는지 확인
     * 
     * @return UPDATE 권한이 있으면 true
     */
    public boolean canWrite() {
        return hasAccess(AccessType.UPDATE);
    }
    
    /**
     * DELETE 연산 권한이 있는지 확인
     * 
     * @return DELETE 권한이 있으면 true
     */
    public boolean canDelete() {
        return hasAccess(AccessType.DELETE);
    }
    
    /**
     * 권한 문자열 파싱 (예: "SELECT,UPDATE,DELETE")
     * 
     * @param accessString 권한 문자열
     * @return SecurityDefinition 객체
     */
    public static SecurityDefinition parseAccessTypes(String accessString) {
        Set<AccessType> accessTypes = EnumSet.noneOf(AccessType.class);
        
        if (accessString != null && !accessString.trim().isEmpty()) {
            String[] parts = accessString.split(",");
            for (String part : parts) {
                try {
                    accessTypes.add(AccessType.valueOf(part.trim().toUpperCase()));
                } catch (IllegalArgumentException e) {
                    // 유효하지 않은 권한 타입 무시
                }
            }
        }
        
        return SecurityDefinition.builder()
                .accessTypes(accessTypes)
                .build();
    }
}
