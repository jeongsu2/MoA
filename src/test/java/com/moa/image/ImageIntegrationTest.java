package com.moa.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moa.IntegrationTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import tools.jackson.databind.JsonNode;

class ImageIntegrationTest extends IntegrationTestSupport {

    private static final int TEN_MB = 10 * 1024 * 1024;

    @Test
    @DisplayName("AC-16 판매자가 올린 이미지를 받은 URL로 로그인 없이 그대로 받는다")
    void uploadAndGetImage() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        byte[] png = pngBytes(100);

        String response = mockMvc.perform(withCsrf(multipart(IMAGES).file(imageFile("a.png", png))).session(seller))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode body = jsonMapper.readTree(response);
        String imageKey = body.get("imageKey").asString();
        assertThat(imageKey).matches("[0-9a-f-]{36}\\.png");
        assertThat(body.get("url").asString()).isEqualTo("/images/" + imageKey);

        byte[] downloaded = mockMvc.perform(get("/images/" + imageKey))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(downloaded).isEqualTo(png);
    }

    @Test
    @DisplayName("AC-17 jpg, png, webp가 아니면 이름을 .png로 바꿔 와도 400")
    void rejectInvalidType() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");
        byte[] gif = "GIF89a-not-allowed".getBytes();

        mockMvc.perform(withCsrf(multipart(IMAGES).file(imageFile("fake.png", gif))).session(seller))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMAGE_INVALID_TYPE"));
    }

    @Test
    @DisplayName("AC-17 10MB를 넘으면 400")
    void rejectTooLarge() throws Exception {
        MockHttpSession seller = signupSeller("seller01", "판매자1");

        mockMvc.perform(withCsrf(multipart(IMAGES).file(imageFile("big.png", pngBytes(TEN_MB + 1))))
                        .session(seller))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMAGE_TOO_LARGE"));
    }

    @Test
    @DisplayName("구매자는 이미지를 올릴 수 없다 403")
    void buyerCannotUpload() throws Exception {
        MockHttpSession buyer = signupBuyer("buyer01", "구매자1");

        mockMvc.perform(withCsrf(multipart(IMAGES).file(imageFile("a.png", pngBytes(100)))).session(buyer))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("없는 키나 형식이 틀린 키는 404")
    void missingImage() throws Exception {
        mockMvc.perform(get("/images/" + UUID.randomUUID() + ".png"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("IMAGE_NOT_FOUND"));
        mockMvc.perform(get("/images/..%2Fapplication.yaml"))
                .andExpect(status().is4xxClientError());
    }
}
