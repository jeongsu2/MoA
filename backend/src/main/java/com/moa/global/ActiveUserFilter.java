package com.moa.global;

import com.moa.user.UserRepository;
import com.moa.user.UserStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

// 로그인된 요청마다 DB의 status를 다시 본다. 세션에 예전 상태가 남아 있어도 정지 즉시 막힌다 (D-16)
public class ActiveUserFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final JsonErrorWriter jsonErrorWriter;

    public ActiveUserFilter(UserRepository userRepository, JsonErrorWriter jsonErrorWriter) {
        this.userRepository = userRepository;
        this.jsonErrorWriter = jsonErrorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Long userId) {
            Optional<UserStatus> status = userRepository.findStatusById(userId);
            if (status.isEmpty()) {
                // 세션은 있는데 회원 행이 없으면 비로그인으로 본다
                SecurityContextHolder.clearContext();
            } else if (status.get() == UserStatus.SUSPENDED) {
                jsonErrorWriter.write(response, ErrorCode.ACCOUNT_SUSPENDED);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
