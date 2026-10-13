package com.moa.user;

import com.moa.auth.AddressRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// 경로에 userId가 없다. 사용자는 세션에서 꺼내므로 남의 배송지 목록을 요청할 방법이 없다
@RestController
@RequestMapping("/api/me/addresses")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping
    public List<AddressResponse> getAddresses(@AuthenticationPrincipal Long userId) {
        return addressService.getAddresses(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AddressResponse addAddress(@AuthenticationPrincipal Long userId,
            @Valid @RequestBody AddressCreateRequest request) {
        return addressService.addAddress(userId, request);
    }

    @PutMapping("/{addressId}")
    public AddressResponse changeAddress(@AuthenticationPrincipal Long userId, @PathVariable Long addressId,
            @Valid @RequestBody AddressRequest request) {
        return addressService.changeAddress(userId, addressId, request);
    }

    @DeleteMapping("/{addressId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAddress(@AuthenticationPrincipal Long userId, @PathVariable Long addressId) {
        addressService.deleteAddress(userId, addressId);
    }
}
