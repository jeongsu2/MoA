package com.moa.global;

import com.moa.user.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, UserRepository userRepository,
            JsonMapper jsonMapper) throws Exception {
        JsonErrorWriter jsonErrorWriter = new JsonErrorWriter(jsonMapper);

        http
                // 프론트는 XSRF-TOKEN 쿠키(또는 GET /api/auth/csrf 응답)의 값을 X-XSRF-TOKEN 헤더로 보낸다
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .securityContext(context -> context.securityContextRepository(securityContextRepository()))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                // 비로그인 요청을 세션에 저장해 두지 않는다 (리다이렉트가 없는 API 서버)
                .requestCache(cache -> cache.disable())
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/error").permitAll()
                        // 구매자는 인스타 링크로 들어와 로그인 없이 글과 사진을 본다 (DRAFT는 서비스가 404로 막는다)
                        .requestMatchers(HttpMethod.GET, "/images/*", "/api/posts/*").permitAll()
                        // 댓글 주문은 구매자, 판매자 모두 (D-34). 아래 판매자 전용 규칙보다 먼저 둔다
                        .requestMatchers(HttpMethod.POST, "/api/posts/*/comments").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/images").hasRole("SELLER")
                        .requestMatchers("/api/posts/**", "/api/me/posts").hasRole("SELLER")
                        .requestMatchers(HttpMethod.PUT, "/api/me/seller-profile").hasRole("SELLER")
                        .anyRequest().authenticated())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, e) ->
                                jsonErrorWriter.write(response, ErrorCode.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, e) ->
                                jsonErrorWriter.write(response, ErrorCode.FORBIDDEN)))
                .addFilterBefore(new ActiveUserFilter(userRepository, jsonErrorWriter), AuthorizationFilter.class);
        return http.build();
    }

    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
