package com.moa.post;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// 수량 입력은 없다 (D-12). 번호에 공백을 막아 "1"과 "1 "이 다른 상품이 되지 않게 한다
public record ProductRequest(
        @NotBlank @Pattern(regexp = "^\\S{1,20}$", message = "공백 없이 20자 이하") String itemNo,
        @NotBlank @Size(max = 100) String name,
        @NotNull @Positive Integer price) {

    // 요청 안 중복은 먼저 400으로 알려 준다. DB 유니크는 그래도 최종 방어선으로 남긴다
    static boolean hasDuplicateItemNo(List<ProductRequest> products) {
        Set<String> itemNos = new HashSet<>();
        for (ProductRequest product : products) {
            if (!itemNos.add(product.itemNo())) {
                return true;
            }
        }
        return false;
    }
}
