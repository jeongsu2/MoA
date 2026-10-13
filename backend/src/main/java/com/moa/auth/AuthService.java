package com.moa.auth;

import com.moa.global.ApiException;
import com.moa.global.ErrorCode;
import com.moa.user.Address;
import com.moa.user.AddressRepository;
import com.moa.user.Role;
import com.moa.user.SellerProfile;
import com.moa.user.SellerProfileRepository;
import com.moa.user.User;
import com.moa.user.UserRepository;
import java.time.LocalDateTime;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final SellerProfileRepository sellerProfileRepository;
    private final AddressRepository addressRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, SellerProfileRepository sellerProfileRepository,
            AddressRepository addressRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.sellerProfileRepository = sellerProfileRepository;
        this.addressRepository = addressRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // users와 기본 배송지는 한 트랜잭션. 하나라도 실패하면 둘 다 안 생긴다
    @Transactional
    public User signupBuyer(BuyerSignupRequest request) {
        User user = saveUser(request.loginId(), request.password(), Role.BUYER,
                request.nickname(), request.realName(), request.phone());
        saveDefaultAddress(user, request.address());
        return user;
    }

    @Transactional
    public User signupSeller(SellerSignupRequest request) {
        User user = saveUser(request.loginId(), request.password(), Role.SELLER,
                request.nickname(), request.realName(), request.phone());
        sellerProfileRepository.save(new SellerProfile(user, request.bankName(),
                request.accountNumber(), request.accountHolder()));
        saveDefaultAddress(user, request.address());
        return user;
    }

    private User saveUser(String loginId, String password, Role role, String nickname,
            String realName, String phone) {
        User user = new User(loginId, passwordEncoder.encode(password), role, nickname,
                realName, phone, LocalDateTime.now());
        // 먼저 조회해서 막지 않는다. 조회와 저장 사이에 다른 요청이 끼어들 수 있어서
        // DB 유니크 제약을 최종 방어선으로 두고 그 예외를 409로 바꾼다
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(duplicateErrorCode(e));
        }
    }

    private ErrorCode duplicateErrorCode(DataIntegrityViolationException e) {
        String message = String.valueOf(e.getMostSpecificCause().getMessage());
        if (message.contains(User.UK_NICKNAME)) {
            return ErrorCode.DUPLICATE_NICKNAME;
        }
        if (message.contains(User.UK_LOGIN_ID)) {
            return ErrorCode.DUPLICATE_LOGIN_ID;
        }
        throw e;
    }

    private void saveDefaultAddress(User user, AddressRequest request) {
        addressRepository.save(Address.defaultOf(user, request.recipientName(), request.recipientPhone(),
                request.zipCode(), request.address1(), request.address2(), request.deliveryNote()));
    }
}
