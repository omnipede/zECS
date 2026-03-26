package com.enterprise.zecs.security;

import com.enterprise.zecs.config.ZecsProperties;
import com.enterprise.zecs.model.SecurityDefinition;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 사용자 접근 권한 관리자
 * 
 * CICS 의 보안 정의 (ZC##SD) 를 Java 로 포팅
 * - HTTP Basic 인증 처리
 * - 사용자별 권한 (SELECT, UPDATE, DELETE) 관리
 */
@Slf4j
@Component
public class UserAccessManager extends OncePerRequestFilter {
    
    private final ZecsProperties properties;
    
    /**
     * 사용자缓存 (성능 향상을 위한 인메모리 캐시)
     */
    private final Map<String, UserContext> userCache = new ConcurrentHashMap<>();
    
    public UserAccessManager(ZecsProperties properties) {
        this.properties = properties;
        
        // 사용자 정보 캐시에 로드
        loadUsersToCache();
    }
    
    /**
     * 사용자 정보를 캐시에 로드
     */
    private void loadUsersToCache() {
        for (ZecsProperties.UserConfig userConfig : properties.getSecurity().getUsers()) {
            SecurityDefinition securityDef = SecurityDefinition.parseAccessTypes(userConfig.getAccess());
            UserContext userContext = new UserContext(
                userConfig.getUsername(),
                securityDef
            );
            userCache.put(userConfig.getUsername(), userContext);
            log.debug("Loaded user: {} with access: {}", userConfig.getUsername(), userConfig.getAccess());
        }
    }
    
    /**
     * 사용자의 SecurityDefinition 조회
     * 
     * @param userId 사용자 ID
     * @return SecurityDefinition (없으면 null)
     */
    public SecurityDefinition getUserSecurity(String userId) {
        UserContext userContext = userCache.get(userId);
        return userContext != null ? userContext.securityDefinition() : null;
    }
    
    /**
     * 사용자의 권한 확인
     * 
     * @param userId 사용자 ID
     * @param accessType 권한 타입
     * @return 권한이 있으면 true
     */
    public boolean hasAccess(String userId, SecurityDefinition.AccessType accessType) {
        SecurityDefinition securityDef = getUserSecurity(userId);
        return securityDef != null && securityDef.hasAccess(accessType);
    }
    
    /**
     * 현재 인증된 사용자 ID 조회
     * 
     * @return 인증된 사용자 ID (없으면 null)
     */
    public String getCurrentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }
        return null;
    }
    
    /**
     * 현재 인증된 사용자의 SecurityDefinition 조회
     * 
     * @return SecurityDefinition (없으면 null)
     */
    public SecurityDefinition getCurrentUserSecurity() {
        String userId = getCurrentUserId();
        return userId != null ? getUserSecurity(userId) : null;
    }
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain)
            throws ServletException, IOException {
        
        // Basic Auth 헤더 확인
        String header = request.getHeader("Authorization");
        
        if (header != null && header.startsWith("Basic ")) {
            try {
                // Base64 디코딩
                String base64Credentials = header.substring("Basic".length()).trim();
                String credentials = new String(Base64.getDecoder().decode(base64Credentials));
                
                // "username:password" 형식 파싱
                int colonIndex = credentials.indexOf(':');
                if (colonIndex > 0) {
                    String username = credentials.substring(0, colonIndex);
                    String password = credentials.substring(colonIndex + 1);
                    
                    // 사용자 확인
                    if (userCache.containsKey(username)) {
                        // 비밀번호 확인 (단순 비교 - 실제 구현에서는 PasswordEncoder 사용)
                        ZecsProperties.UserConfig userConfig = properties.getSecurity().getUsers().stream()
                                .filter(u -> u.getUsername().equals(username))
                                .findFirst()
                                .orElse(null);
                        
                        if (userConfig != null) {
                            String storedPassword = userConfig.getPassword();
                            boolean authenticated = false;
                            
                            // BCrypt 또는 평문 확인
                            if (storedPassword.startsWith("{bcrypt}")) {
                                // BCrypt - 실제 구현에서는 BCryptPasswordEncoder 사용
                                authenticated = password.equals(storedPassword);
                            } else {
                                // 평문 비교 (테스트용)
                                authenticated = password.equals(storedPassword);
                            }
                            
                            if (authenticated) {
                                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                                authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
                                
                                UsernamePasswordAuthenticationToken authentication = 
                                        new UsernamePasswordAuthenticationToken(username, password, authorities);
                                
                                SecurityContextHolder.getContext().setAuthentication(authentication);
                                log.debug("Authenticated user: {}", username);
                            } else {
                                log.debug("Authentication failed for user: {}", username);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Basic authentication parsing failed: {}", e.getMessage());
            }
        }
        
        filterChain.doFilter(request, response);
    }
    
    /**
     * 사용자 컨텍스트 레코드
     */
    public record UserContext(String userId, SecurityDefinition securityDefinition) {}
}
