package com.moa.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moa.IntegrationTestSupport;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

class PostIntegrationTest extends IntegrationTestSupport {

    private static final String POSTS = "/api/posts";

    @Test
    @DisplayName("AC-1, AC-2 글, 사진, 상품이 함께 생기고 DRAFT, 사진은 보낸 순서, 상품은 ON_SALE, 배송비는 서버 값")
    void createPost() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        String first = uploadImage(seller);
        String second = uploadImage(seller);
        Map<String, Object> body = postBody(List.of(second, first), List.of(product("1", "니트", 15000),
                product("1-2", "니트 아이보리", 16000)));
        body.put("shippingFee", 0);

        String response = mockMvc.perform(jsonPost(POSTS, body).session(seller))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        Long postId = jsonMapper.readTree(response).get("postId").asLong();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select status, shipping_fee, remote_shipping_fee, opened_at from sale_post where id = ?", postId);
        assertThat(row.get("status")).isEqualTo("DRAFT");
        assertThat(row.get("shipping_fee")).isEqualTo(3500);
        assertThat(row.get("remote_shipping_fee")).isEqualTo(6000);
        assertThat(row.get("opened_at")).isNull();
        assertThat(jdbcTemplate.queryForList(
                "select image_key from post_image where post_id = ? order by sort_order", String.class, postId))
                .containsExactly(second, first);
        assertThat(jdbcTemplate.queryForList(
                "select status from product where post_id = ?", String.class, postId))
                .containsExactly("ON_SALE", "ON_SALE");
    }

    @Test
    @DisplayName("AC-3 구매자는 글을 쓸 수 없다 403")
    void buyerCannotCreatePost() throws Exception {
        MockHttpSession buyer = signupBuyer("buyer01", "구매자1");

        mockMvc.perform(jsonPost(POSTS, postBody(List.of(), List.of(product("1", "니트", 15000)))).session(buyer))
                .andExpect(status().isForbidden());

        assertThat(countRows("sale_post")).isZero();
    }

    @Test
    @DisplayName("AC-5 같은 글 안에서 번호가 겹치면 400, 글도 상품도 안 생긴다")
    void rejectDuplicateItemNo() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        Map<String, Object> body = postBody(List.of(), List.of(product("1", "니트", 15000),
                product("1", "치마", 20000)));

        mockMvc.perform(jsonPost(POSTS, body).session(seller))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DUPLICATE_ITEM_NO"));

        assertThat(countRows("sale_post")).isZero();
        assertThat(countRows("product")).isZero();
    }

    @Test
    @DisplayName("AC-5 대소문자만 다른 번호는 DB 유니크가 막아 400, 글은 롤백")
    void uniqueConstraintIsLastDefense() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        Map<String, Object> body = postBody(List.of(), List.of(product("a", "니트", 15000),
                product("A", "치마", 20000)));

        mockMvc.perform(jsonPost(POSTS, body).session(seller))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DUPLICATE_ITEM_NO"));

        assertThat(countRows("sale_post")).isZero();
    }

    @Test
    @DisplayName("AC-6 가격 0, 가격 1.5, 빈 제목, 빈 상품 이름은 400과 틀린 필드")
    void rejectInvalidFields() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        Map<String, Object> body = postBody(List.of(), List.of(product("1", " ", 0)));
        body.put("title", "");

        mockMvc.perform(jsonPost(POSTS, body).session(seller))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists())
                .andExpect(jsonPath("$.fieldErrors['products[0].price']").exists())
                .andExpect(jsonPath("$.fieldErrors['products[0].name']").exists());

        mockMvc.perform(jsonPost(POSTS, postBody(List.of(), List.of(product("1", "니트", 1.5)))).session(seller))
                .andExpect(status().isBadRequest());

        assertThat(countRows("sale_post")).isZero();
    }

    @Test
    @DisplayName("AC-17 올리지 않은 이미지 키나 10장 초과는 400")
    void rejectInvalidImageKeys() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        String notUploaded = UUID.randomUUID() + ".png";

        mockMvc.perform(jsonPost(POSTS, postBody(List.of(notUploaded), List.of())).session(seller))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMAGE_NOT_UPLOADED"));

        List<String> elevenKeys = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            elevenKeys.add(UUID.randomUUID() + ".png");
        }
        mockMvc.perform(jsonPost(POSTS, postBody(elevenKeys, List.of())).session(seller))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMAGE_LIMIT_EXCEEDED"));

        assertThat(countRows("sale_post")).isZero();
    }

    @Test
    @DisplayName("AC-7 DRAFT는 제목, 본문, 사진, 상품을 모두 바꾼다. 번호를 서로 바꿔도 된다")
    void updateDraft() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        Long postId = createPost(seller, List.of(uploadImage(seller)),
                List.of(product("1", "니트", 15000), product("2", "치마", 20000)));
        String newImage = uploadImage(seller);

        Map<String, Object> body = postBody(List.of(newImage),
                List.of(product("2", "니트", 15000), product("1", "치마", 18000), product("3", "모자", 5000)));
        body.put("title", "새 제목");
        mockMvc.perform(updateRequest(postId, body).session(seller))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("새 제목"))
                .andExpect(jsonPath("$.images.length()").value(1))
                .andExpect(jsonPath("$.images[0].imageKey").value(newImage))
                .andExpect(jsonPath("$.products.length()").value(3))
                .andExpect(jsonPath("$.products[1].itemNo").value("1"))
                .andExpect(jsonPath("$.products[1].price").value(18000));

        assertThat(countRows("product")).isEqualTo(3);
        assertThat(countRows("post_image")).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-8 OPEN 뒤 상품을 보내면 409, 상품은 그대로")
    void cannotChangeProductsAfterOpen() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        Long postId = createPost(seller, List.of(), List.of(product("1", "니트", 15000)));
        open(seller, postId);

        mockMvc.perform(updateRequest(postId, postBody(List.of(), List.of(product("1", "니트", 1000))))
                        .session(seller))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCTS_LOCKED"));

        assertThat(jdbcTemplate.queryForObject("select price from product where post_id = ?", Integer.class,
                postId)).isEqualTo(15000);
    }

    @Test
    @DisplayName("AC-8 OPEN 뒤에도 상품을 빼고 보내면 제목, 본문, 사진은 바뀐다")
    void changeTextAfterOpen() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        Long postId = createPost(seller, List.of(), List.of(product("1", "니트", 15000)));
        open(seller, postId);
        String image = uploadImage(seller);

        Map<String, Object> body = postBody(List.of(image), null);
        body.put("title", "오타 고친 제목");
        mockMvc.perform(updateRequest(postId, body).session(seller))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("오타 고친 제목"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.images[0].imageKey").value(image))
                .andExpect(jsonPath("$.products.length()").value(1));
    }

    @Test
    @DisplayName("AC-9, AC-10 상품 0개면 오픈 400, 오픈은 다시 눌러도 200이고 opened_at 그대로")
    void openPost() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        Long emptyPostId = createPost(seller, List.of(), List.of());
        mockMvc.perform(withCsrf(post(POSTS + "/" + emptyPostId + "/open")).session(seller))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCT_REQUIRED"));

        Long postId = createPost(seller, List.of(), List.of(product("1", "니트", 15000)));
        String firstOpenedAt = open(seller, postId);
        String secondOpenedAt = open(seller, postId);

        assertThat(secondOpenedAt).isEqualTo(firstOpenedAt);
        assertThat(jdbcTemplate.queryForObject("select status from sale_post where id = ?", String.class, postId))
                .isEqualTo("OPEN");
    }

    @Test
    @DisplayName("Q-2 판매 종료는 OPEN에서만. DRAFT는 409, OPEN은 CLOSED, 다시 눌러도 200")
    void closePost() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        Long postId = createPost(seller, List.of(), List.of(product("1", "니트", 15000)));
        mockMvc.perform(withCsrf(post(POSTS + "/" + postId + "/close")).session(seller))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("POST_STATUS_CONFLICT"));

        open(seller, postId);
        mockMvc.perform(withCsrf(post(POSTS + "/" + postId + "/close")).session(seller))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
        mockMvc.perform(withCsrf(post(POSTS + "/" + postId + "/close")).session(seller))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
        mockMvc.perform(withCsrf(post(POSTS + "/" + postId + "/open")).session(seller))
                .andExpect(status().isConflict());

        assertThat(jdbcTemplate.queryForObject("select status from sale_post where id = ?", String.class, postId))
                .isEqualTo("CLOSED");
    }

    @Test
    @DisplayName("AC-7 남의 글 수정, 오픈, 종료는 없는 글과 같은 404이고 글은 그대로")
    void otherSellerGetsNotFound() throws Exception {
        MockHttpSession owner = signupSeller("seller01", "판매자1");
        Long postId = createPost(owner, List.of(), List.of(product("1", "니트", 15000)));
        MockHttpSession other = signupSeller("seller02", "판매자2");

        mockMvc.perform(updateRequest(postId, postBody(List.of(), null)).session(other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
        mockMvc.perform(withCsrf(post(POSTS + "/" + postId + "/open")).session(other))
                .andExpect(status().isNotFound());
        mockMvc.perform(withCsrf(post(POSTS + "/" + postId + "/close")).session(other))
                .andExpect(status().isNotFound());

        Map<String, Object> row = jdbcTemplate.queryForMap("select title, status from sale_post where id = ?",
                postId);
        assertThat(row.get("title")).isEqualTo("봄 옷 정리");
        assertThat(row.get("status")).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("AC-13 DRAFT는 작성자만 본다. 다른 판매자와 비로그인은 404")
    void draftIsHiddenFromOthers() throws Exception {
        MockHttpSession owner = signupSeller("seller01", "판매자1");
        Long postId = createPost(owner, List.of(), List.of(product("1", "니트", 15000)));
        MockHttpSession other = signupSeller("seller02", "판매자2");

        mockMvc.perform(get(POSTS + "/" + postId).session(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
        mockMvc.perform(get(POSTS + "/" + postId).session(other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
        mockMvc.perform(get(POSTS + "/" + postId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("AC-12, AC-15 비로그인도 OPEN 글을 본다. 계좌와 실명은 없다")
    void anonymousCanReadOpenPost() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        String first = uploadImage(seller);
        String second = uploadImage(seller);
        Long postId = createPost(seller, List.of(first, second),
                List.of(product("1", "니트", 15000), product("2", "치마", 20000)));
        open(seller, postId);

        String response = mockMvc.perform(get(POSTS + "/" + postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postId").value(postId))
                .andExpect(jsonPath("$.title").value("봄 옷 정리"))
                .andExpect(jsonPath("$.content").value("사이즈는 모두 free"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.openedAt").exists())
                .andExpect(jsonPath("$.sellerNickname").value("판매자1"))
                .andExpect(jsonPath("$.images[0].url").value("/images/" + first))
                .andExpect(jsonPath("$.images[0].sortOrder").value(0))
                .andExpect(jsonPath("$.images[1].url").value("/images/" + second))
                .andExpect(jsonPath("$.products[0].itemNo").value("1"))
                .andExpect(jsonPath("$.products[0].name").value("니트"))
                .andExpect(jsonPath("$.products[0].price").value(15000))
                .andExpect(jsonPath("$.products[0].status").value("ON_SALE"))
                .andExpect(jsonPath("$.products[0].productId").exists())
                .andExpect(jsonPath("$.shippingFee").value(3500))
                .andExpect(jsonPath("$.remoteShippingFee").value(6000))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain("123-456-7890", "국민은행", "홍길동");
    }

    @Test
    @DisplayName("AC-14 내 글 목록은 내 글만 최신순, 상품 수 포함")
    void myPosts() throws Exception {
        MockHttpSession me = signupSeller("seller01", "판매자1");
        Long older = createPost(me, List.of(), List.of(product("1", "니트", 15000)));
        Long newer = createPost(me, List.of(), List.of(product("1", "니트", 15000), product("2", "치마", 20000)));
        MockHttpSession other = signupSeller("seller02", "판매자2");
        createPost(other, List.of(), List.of());

        String response = mockMvc.perform(get("/api/me/posts").session(me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].productCount").value(2))
                .andExpect(jsonPath("$[0].status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();

        List<Long> postIds = new ArrayList<>();
        for (JsonNode post : jsonMapper.readTree(response)) {
            postIds.add(post.get("postId").asLong());
        }
        assertThat(postIds).containsExactly(newer, older);
    }

    private Map<String, Object> postBody(List<String> imageKeys, List<Map<String, Object>> products) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "봄 옷 정리");
        body.put("content", "사이즈는 모두 free");
        body.put("imageKeys", imageKeys);
        if (products != null) {
            body.put("products", products);
        }
        return body;
    }

    private Map<String, Object> product(String itemNo, String name, Number price) {
        Map<String, Object> product = new LinkedHashMap<>();
        product.put("itemNo", itemNo);
        product.put("name", name);
        product.put("price", price);
        return product;
    }

    private Long createPost(MockHttpSession seller, List<String> imageKeys, List<Map<String, Object>> products)
            throws Exception {
        String response = mockMvc.perform(jsonPost(POSTS, postBody(imageKeys, products)).session(seller))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(response).get("postId").asLong();
    }

    private String open(MockHttpSession seller, Long postId) throws Exception {
        String response = mockMvc.perform(withCsrf(post(POSTS + "/" + postId + "/open")).session(seller))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(response).get("openedAt").asString();
    }

    private MockHttpServletRequestBuilder updateRequest(Long postId, Map<String, Object> body) throws Exception {
        return withCsrf(put(POSTS + "/" + postId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(body));
    }
}
