package com.moa.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.moa.IntegrationTestSupport;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

// 세션 쿠키는 MockMvc에서 안 나오므로 실제 톰캣을 띄우고 쿠키를 직접 주고받는다
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SessionCookieTest extends IntegrationTestSupport {

    private static final String SESSION_COOKIE = "JSESSIONID";

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private String csrfToken;

    @BeforeEach
    void fetchCsrfToken() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/api/auth/csrf")).GET(), null);
        csrfToken = cookieValue(response, CSRF_COOKIE);
        assertThat(csrfToken).isNotBlank();
    }

    @Test
    @DisplayName("L-01 로그인하면 HttpOnly, SameSite=Lax 세션 쿠키를 받는다")
    void loginSetsSecureCookie() throws Exception {
        post("/api/auth/signup/buyer", buyerBody("buyer01", "구매자1"), null);

        HttpResponse<String> response = post("/api/auth/login", loginBody(), null);

        assertThat(response.statusCode()).isEqualTo(200);
        String setCookie = setCookieHeader(response, SESSION_COOKIE);
        assertThat(setCookie).contains("HttpOnly").containsIgnoringCase("SameSite=Lax").contains("Max-Age=604800");
    }

    @Test
    @DisplayName("L-02 로그인하면 세션 id가 바뀌고, 예전 세션 id로는 401")
    void loginChangesSessionId() throws Exception {
        HttpResponse<String> signup = post("/api/auth/signup/buyer", buyerBody("buyer01", "구매자1"), null);
        String oldSessionId = cookieValue(signup, SESSION_COOKIE);

        HttpResponse<String> login = post("/api/auth/login", loginBody(), oldSessionId);
        String newSessionId = cookieValue(login, SESSION_COOKIE);

        assertThat(newSessionId).isNotNull().isNotEqualTo(oldSessionId);
        assertThat(getMe(oldSessionId).statusCode()).isEqualTo(401);
        assertThat(getMe(newSessionId).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("L-07 로그아웃하면 204, 같은 쿠키로 다시 요청하면 401")
    void logoutInvalidatesSession() throws Exception {
        HttpResponse<String> signup = post("/api/auth/signup/buyer", buyerBody("buyer01", "구매자1"), null);
        String sessionId = cookieValue(signup, SESSION_COOKIE);
        assertThat(getMe(sessionId).statusCode()).isEqualTo(200);

        HttpResponse<String> logout = post("/api/auth/logout", null, sessionId);

        assertThat(logout.statusCode()).isEqualTo(204);
        HttpResponse<String> me = getMe(sessionId);
        assertThat(me.statusCode()).isEqualTo(401);
        assertThat(me.body()).contains("UNAUTHORIZED");
    }

    private Map<String, Object> loginBody() {
        return Map.of("loginId", "buyer01", "password", PASSWORD);
    }

    private HttpResponse<String> getMe(String sessionId) throws IOException, InterruptedException {
        return send(HttpRequest.newBuilder(uri("/api/me")).GET(), sessionId);
    }

    private HttpResponse<String> post(String path, Object body, String sessionId)
            throws IOException, InterruptedException {
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(body));
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .header(CSRF_HEADER, csrfToken)
                .POST(publisher);
        return send(builder, sessionId);
    }

    private HttpResponse<String> send(HttpRequest.Builder builder, String sessionId)
            throws IOException, InterruptedException {
        StringBuilder cookie = new StringBuilder();
        if (csrfToken != null) {
            cookie.append(CSRF_COOKIE).append('=').append(csrfToken);
        }
        if (sessionId != null) {
            if (!cookie.isEmpty()) {
                cookie.append("; ");
            }
            cookie.append(SESSION_COOKIE).append('=').append(sessionId);
        }
        if (!cookie.isEmpty()) {
            builder.header("Cookie", cookie.toString());
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private String setCookieHeader(HttpResponse<String> response, String name) {
        List<String> headers = response.headers().allValues("Set-Cookie");
        for (String header : headers) {
            if (header.startsWith(name + "=")) {
                return header;
            }
        }
        return null;
    }

    private String cookieValue(HttpResponse<String> response, String name) {
        String header = setCookieHeader(response, name);
        if (header == null) {
            return null;
        }
        return header.substring(name.length() + 1, header.indexOf(';'));
    }
}
