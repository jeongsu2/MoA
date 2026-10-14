package com.moa.auth;

import com.moa.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// 로그아웃(POST /api/auth/logout)은 SecurityConfig의 logout 설정이 처리한다
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final SessionLoginHandler sessionLoginHandler;

    public AuthController(AuthService authService, SessionLoginHandler sessionLoginHandler) {
        this.authService = authService;
        this.sessionLoginHandler = sessionLoginHandler;
    }

    // 가입 API를 역할별로 나눠 요청 본문의 role 값을 믿지 않는다
    @PostMapping("/signup/buyer")
    @ResponseStatus(HttpStatus.CREATED)
    public SignupResponse signupBuyer(@Valid @RequestBody BuyerSignupRequest request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        User user = authService.signupBuyer(request);
        sessionLoginHandler.login(user, httpRequest, httpResponse);
        return SignupResponse.from(user);
    }

    @PostMapping("/signup/seller")
    @ResponseStatus(HttpStatus.CREATED)
    public SignupResponse signupSeller(@Valid @RequestBody SellerSignupRequest request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        User user = authService.signupSeller(request);
        sessionLoginHandler.login(user, httpRequest, httpResponse);
        return SignupResponse.from(user);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        User user = authService.authenticate(request);
        sessionLoginHandler.login(user, httpRequest, httpResponse);
        return LoginResponse.from(user);
    }

    // 토큰을 읽는 순간 XSRF-TOKEN 쿠키도 같이 내려간다
    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken csrfToken) {
        return new CsrfResponse(csrfToken.getHeaderName(), csrfToken.getToken());
    }
}
