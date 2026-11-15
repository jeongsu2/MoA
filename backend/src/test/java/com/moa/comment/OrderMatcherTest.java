package com.moa.comment;

import static org.assertj.core.api.Assertions.assertThat;

import com.moa.post.ProductKey;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OrderMatcherTest {

    private static final ProductKey BAG = new ProductKey(1L, "1-2", "보라색에코백");
    private static final ProductKey DRESS = new ProductKey(2L, "1", "빈티지 원피스");
    private static final ProductKey SHIRT = new ProductKey(3L, "3", "Oxford Shirt");
    private static final List<ProductKey> PRODUCTS = List.of(BAG, DRESS, SHIRT);

    @ParameterizedTest
    @ValueSource(strings = {"1-2 보라색에코백", " 1-2. 보라색 에코백 ", "1-2보라색에코백", "1-2.보라색에코백",
        "1-2\t보라색\n에코백"})
    @DisplayName("M-01 공백, 번호 뒤 점은 무시하고 일치")
    void matchWithSpacesAndDot(String content) {
        assertThat(OrderMatcher.match(content, PRODUCTS)).contains(BAG);
    }

    @Test
    @DisplayName("M-01 상품 이름 안 공백도 무시한다. \"1. 빈티지원피스\" = \"1빈티지 원피스\"")
    void matchIgnoringSpacesInName() {
        assertThat(OrderMatcher.match("1. 빈티지원피스", PRODUCTS)).contains(DRESS);
        assertThat(OrderMatcher.match("1빈티지 원피스", PRODUCTS)).contains(DRESS);
    }

    @Test
    @DisplayName("M-01 NBSP(U+00A0)도 공백으로 본다. 복사해 붙인 댓글에 자주 섞인다")
    void matchWithNoBreakSpace() {
        assertThat(OrderMatcher.match("1-2\u00A0보라색\u00A0에코백", PRODUCTS)).contains(BAG);
    }

    @Test
    @DisplayName("M-01 전각 공백(U+3000)도 공백으로 본다. 한글 입력기에서 들어온다")
    void matchWithIdeographicSpace() {
        assertThat(OrderMatcher.match("1.\u3000빈티지\u3000원피스", PRODUCTS)).contains(DRESS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"3 oxford shirt", "3 OXFORD SHIRT", "3.oXfOrDsHiRt"})
    @DisplayName("M-01 영문 대소문자는 무시")
    void matchIgnoringCase(String content) {
        assertThat(OrderMatcher.match(content, PRODUCTS)).contains(SHIRT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1-2 보라색에코백 구매할게요", "1-2", "1-2.", "1-3 보라색에코백", "보라색에코백",
        "1-2 보라색에코백.", "1-2..보라색에코백", "", "   "})
    @DisplayName("M-02 글자가 더 있거나, 번호나 이름만 있거나, 번호가 다르면 불일치")
    void notMatch(String content) {
        assertThat(OrderMatcher.match(content, PRODUCTS)).isEmpty();
    }

    @Test
    @DisplayName("M-02 null 내용은 불일치")
    void nullContent() {
        assertThat(OrderMatcher.match(null, PRODUCTS)).isEmpty();
    }

    @Test
    @DisplayName("정규화 결과가 두 상품에 다 맞으면 어느 쪽인지 몰라 불일치")
    void ambiguousIsNotOrder() {
        ProductKey first = new ProductKey(10L, "1", "2니트");
        ProductKey second = new ProductKey(11L, "12", "니트");

        assertThat(OrderMatcher.match("12니트", List.of(first, second))).isEmpty();
        assertThat(OrderMatcher.match("1.2니트", List.of(first, second))).contains(first);
    }
}
