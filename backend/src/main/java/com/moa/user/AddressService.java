package com.moa.user;

import com.moa.auth.AddressRequest;
import com.moa.global.ApiException;
import com.moa.global.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 배송지를 바꾸는 요청은 모두 트랜잭션 처음에 users 행을 FOR UPDATE로 잡는다 (D-34).
// 같은 사용자의 요청이 한 줄로 서서 "기본 정확히 1개"와 "최대 10개"가 동시 요청에도 지켜진다.
// 락을 먼저 잡은 뒤에 배송지를 읽으므로, 앞 요청이 커밋한 결과를 보고 판단한다
@Service
public class AddressService {

    public static final int MAX_ADDRESS_COUNT = 10;

    private final UserRepository userRepository;
    private final AddressRepository addressRepository;

    public AddressService(UserRepository userRepository, AddressRepository addressRepository) {
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> getAddresses(Long userId) {
        return addressRepository.findByUserIdOrderByIsDefaultDescIdAsc(userId).stream()
                .map(AddressResponse::from)
                .toList();
    }

    @Transactional
    public AddressResponse addAddress(Long userId, AddressCreateRequest request) {
        User user = lockUser(userId);
        if (addressRepository.countByUserId(userId) >= MAX_ADDRESS_COUNT) {
            throw new ApiException(ErrorCode.ADDRESS_LIMIT_EXCEEDED);
        }
        if (request.setDefault()) {
            addressRepository.clearDefault(userId);
        }
        Address address = addressRepository.save(Address.create(user, request.recipientName(),
                request.recipientPhone(), request.zipCode(), request.address1(), request.address2(),
                request.deliveryNote(), request.setDefault()));
        return AddressResponse.from(address);
    }

    // 진행 중 당첨이 있어도 막지 않는다. 주문 주소는 입금완료 순간의 기본 배송지로 정해진다 (D-24)
    @Transactional
    public AddressResponse changeAddress(Long userId, Long addressId, AddressRequest request) {
        lockUser(userId);
        Address address = findMyAddress(userId, addressId);
        address.change(request.recipientName(), request.recipientPhone(), request.zipCode(),
                request.address1(), request.address2(), request.deliveryNote());
        return AddressResponse.from(address);
    }

    // 기본 배송지는 못 지운다. 배송지가 1개뿐이면 그게 기본이라 마지막 1개도 같이 막힌다
    @Transactional
    public void deleteAddress(Long userId, Long addressId) {
        lockUser(userId);
        Address address = findMyAddress(userId, addressId);
        if (address.isDefault()) {
            throw new ApiException(ErrorCode.DEFAULT_ADDRESS_NOT_DELETABLE);
        }
        addressRepository.delete(address);
    }

    // 같은 요청을 여러 번 보내도 결과가 같다 (PUT, 멱등)
    @Transactional
    public List<AddressResponse> changeDefaultAddress(Long userId, Long addressId) {
        lockUser(userId);
        Address address = findMyAddress(userId, addressId);
        if (!address.isDefault()) {
            addressRepository.changeDefault(userId, addressId);
        }
        return getAddresses(userId);
    }

    private Address findMyAddress(Long userId, Long addressId) {
        return addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.ADDRESS_NOT_FOUND));
    }

    private User lockUser(Long userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new IllegalStateException("로그인한 회원이 없음: " + userId));
    }
}
