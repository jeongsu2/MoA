package com.moa.auth;

import com.moa.user.Role;
import com.moa.user.User;

public record LoginResponse(Long userId, Role role, String nickname) {

    static LoginResponse from(User user) {
        return new LoginResponse(user.getId(), user.getRole(), user.getNickname());
    }
}
