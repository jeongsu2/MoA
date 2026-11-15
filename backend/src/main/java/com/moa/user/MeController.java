package com.moa.user;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 세션의 principal은 회원 id(Long). 요청 본문의 id는 받지 않아 남의 정보를 바꿀 수 없다
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final MeService meService;

    public MeController(MeService meService) {
        this.meService = meService;
    }

    @GetMapping
    public MeResponse getMe(@AuthenticationPrincipal Long userId) {
        return meService.getMe(userId);
    }

    @PutMapping("/seller-profile")
    public SellerProfileResponse changeSellerProfile(@AuthenticationPrincipal Long userId,
            @Valid @RequestBody SellerProfileRequest request) {
        return meService.changeSellerProfile(userId, request);
    }
}
