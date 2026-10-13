package com.moa.auth;

import com.moa.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

// 인증이 끝난 회원을 서버 세션에 로그인 상태로 저장한다 (D-16)
@Component
public class SessionLoginHandler {

    private final SecurityContextRepository securityContextRepository;

    public SessionLoginHandler(SecurityContextRepository securityContextRepository) {
        this.securityContextRepository = securityContextRepository;
    }

    public void login(User user, HttpServletRequest request, HttpServletResponse response) {
        // 세션 고정 방지. 로그인 전 세션이 있으면 id를 새로 바꿔 예전 id를 못 쓰게 한다
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        // 세션에는 회원 id와 역할만 둔다. 정지 여부는 요청마다 DB에서 본다 (ActiveUserFilter)
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                user.getId(), null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
