package com.moa.comment;

import com.moa.post.ProductKey;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

// "정확히 기재" 판정 (04 문서 Q-1 A, 정수 확인 전). 정규화한 뒤 "번호 + 이름"과 완전히 같아야 한다.
// 정규화: 모든 공백 제거, 영문 소문자로. 번호 바로 뒤의 점 하나는 있어도 없어도 된다.
// 그 외 글자가 하나라도 더 있으면 실패 ("1 니트 구매할게요"는 NORMAL)
public final class OrderMatcher {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private OrderMatcher() {
    }

    // 맞는 상품이 정확히 하나일 때만 돌려준다. 둘 이상이면 어느 쪽인지 알 수 없어 일반 댓글로 본다
    public static Optional<ProductKey> match(String content, List<ProductKey> products) {
        String target = normalize(content);
        List<ProductKey> matched = products.stream()
                .filter(product -> isExactOrder(target, product))
                .toList();
        if (matched.size() != 1) {
            return Optional.empty();
        }
        return Optional.of(matched.get(0));
    }

    private static boolean isExactOrder(String target, ProductKey product) {
        String itemNo = normalize(product.itemNo());
        String name = normalize(product.name());
        if (itemNo.isEmpty() || name.isEmpty()) {
            return false;
        }
        return target.equals(itemNo + name) || target.equals(itemNo + "." + name);
    }

    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return WHITESPACE.matcher(text).replaceAll("").toLowerCase(Locale.ROOT);
    }
}
