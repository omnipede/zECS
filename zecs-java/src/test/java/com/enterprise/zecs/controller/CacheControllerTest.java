package com.enterprise.zecs.controller;

import com.enterprise.zecs.config.ZecsProperties;
import com.enterprise.zecs.security.UserAccessManager;
import com.enterprise.zecs.service.CacheService;
import com.enterprise.zecs.service.ReplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CacheController 통합 테스트
 */
@WebMvcTest(CacheController.class)
class CacheControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private CacheService cacheService;
    
    @MockBean
    private ReplicationService replicationService;
    
    @MockBean
    private UserAccessManager userAccessManager;
    
    @MockBean
    private ZecsProperties properties;
    
    @BeforeEach
    void setUp() {
        // Security 우회를 위한 모킹
        when(userAccessManager.getCurrentUserId()).thenReturn("TESTUSER");
    }
    
    @Test
    @DisplayName("GET 요청 - 성공")
    void testGetSuccess() throws Exception {
        // Given
        when(cacheService.get(eq("ZC00"), eq("test-key"), any()))
                .thenReturn(java.util.Optional.of(
                        com.enterprise.zecs.model.CacheEntry.builder()
                                .key("test-key")
                                .value("test-value".getBytes())
                                .build()));
        
        // When & Then
        mockMvc.perform(get("/resources/ecs/devops/sessionData/test-key")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
    
    @Test
    @DisplayName("GET 요청 - 키 없음")
    void testGetNotFound() throws Exception {
        // Given
        when(cacheService.get(eq("ZC00"), eq("non-existent-key"), any()))
                .thenReturn(java.util.Optional.empty());
        
        // When & Then
        mockMvc.perform(get("/resources/ecs/devops/sessionData/non-existent-key")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
    }
    
    @Test
    @DisplayName("POST 요청 - 생성")
    void testPostCreate() throws Exception {
        // Given
        when(cacheService.post(eq("ZC00"), eq("new-key"), any(), any(), any(), any()))
                .thenReturn(201);
        
        // When & Then
        mockMvc.perform(post("/resources/ecs/devops/sessionData/new-key")
                .content("test-value")
                .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().isCreated());
    }
    
    @Test
    @DisplayName("PUT 요청 - 수정")
    void testPutUpdate() throws Exception {
        // Given
        when(cacheService.put(eq("ZC00"), eq("existing-key"), any(), any(), any(), any()))
                .thenReturn(200);
        
        // When & Then
        mockMvc.perform(put("/resources/ecs/devops/sessionData/existing-key")
                .content("updated-value")
                .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().isOk());
    }
    
    @Test
    @DisplayName("DELETE 요청 - 성공")
    void testDeleteSuccess() throws Exception {
        // Given
        when(cacheService.delete(eq("ZC00"), eq("test-key"), any()))
                .thenReturn(200);
        
        // When & Then
        mockMvc.perform(delete("/resources/ecs/devops/sessionData/test-key")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
    
    @Test
    @DisplayName("DELETE 요청 - 전체 삭제")
    void testDeleteAll() throws Exception {
        // Given
        when(cacheService.clearAll(eq("ZC00"), any()))
                .thenReturn(100L);
        
        // When & Then
        mockMvc.perform(delete("/resources/ecs/devops/sessionData/any-key")
                .param("clear", "*")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}
