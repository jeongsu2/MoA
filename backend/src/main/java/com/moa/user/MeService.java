package com.moa.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MeService {

    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final SellerProfileRepository sellerProfileRepository;

    public MeService(UserRepository userRepository, AddressRepository addressRepository,
            SellerProfileRepository sellerProfileRepository) {
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.sellerProfileRepository = sellerProfileRepository;
    }

    @Transactional(readOnly = true)
    public MeResponse getMe(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("로그인한 회원이 없음: " + userId));
        AddressResponse defaultAddress = addressRepository.findByUserIdAndIsDefaultTrue(userId)
                .map(AddressResponse::from)
                .orElse(null);
        SellerProfileResponse sellerProfile = null;
        if (user.getRole() == Role.SELLER) {
            sellerProfile = sellerProfileRepository.findById(userId)
                    .map(SellerProfileResponse::from)
                    .orElse(null);
        }
        return new MeResponse(user.getId(), user.getLoginId(), user.getRole(), user.getNickname(),
                user.getRealName(), user.getPhone(), defaultAddress, sellerProfile);
    }

    // 역할 확인은 SecurityConfig(hasRole SELLER)에서 한다. 판매자는 가입 때 계좌가 꼭 생긴다
    @Transactional
    public SellerProfileResponse changeSellerProfile(Long userId, SellerProfileRequest request) {
        SellerProfile sellerProfile = sellerProfileRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("판매자 계좌가 없음: " + userId));
        sellerProfile.changeAccount(request.bankName(), request.accountNumber(), request.accountHolder());
        return SellerProfileResponse.from(sellerProfile);
    }
}
