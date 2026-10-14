package com.moa.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "users",
        uniqueConstraints = {
            // 동시 가입의 최종 방어선. 이름으로 어느 값이 겹쳤는지 구분한다
            @UniqueConstraint(name = User.UK_LOGIN_ID, columnNames = "login_id"),
            @UniqueConstraint(name = User.UK_NICKNAME, columnNames = "nickname")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    public static final String UK_LOGIN_ID = "uk_users_login_id";
    public static final String UK_NICKNAME = "uk_users_nickname";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String loginId;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false, length = 20)
    private String nickname;

    @Column(nullable = false, length = 50)
    private String realName;

    @Column(nullable = false, length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(nullable = false)
    private LocalDateTime privacyAgreedAt;

    @Column(nullable = false)
    private LocalDateTime thirdPartyAgreedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 동의 시각과 가입 시각은 요청 값이 아니라 서버 시각을 쓴다 (D-14)
    public User(String loginId, String passwordHash, Role role, String nickname,
            String realName, String phone, LocalDateTime now) {
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.role = role;
        this.nickname = nickname;
        this.realName = realName;
        this.phone = phone;
        this.status = UserStatus.ACTIVE;
        this.privacyAgreedAt = now;
        this.thirdPartyAgreedAt = now;
        this.createdAt = now;
    }

    public boolean isSuspended() {
        return status == UserStatus.SUSPENDED;
    }
}
