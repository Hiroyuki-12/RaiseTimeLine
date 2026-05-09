package com.raisetimeline.auth;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raisetimeline.auth.dto.AuthResponse;
import com.raisetimeline.auth.dto.LoginRequest;
import com.raisetimeline.auth.dto.RegisterRequest;
import com.raisetimeline.exception.GlobalExceptionHandler;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

/**
 * AuthController のスライステスト。
 *
 * <pre>
 * 対象: AuthController (/api/auth/{register, login, refresh, logout})
 * 技法: 同値分割 + デシジョンテーブル + 状態遷移 (Cookie 発行/破棄)
 *
 * register():
 *   [BB-1] 必須欠落 (email)            → 400 + errors マップ
 *   [BB-2] バリデーション通過 + Service 成功 → 201 + AuthResponse + Set-Cookie
 *   [BB-3] Service が ResponseStatusException → 該当ステータス + message JSON
 *
 * login():
 *   [BB-1] 認証成功 → 200 + Cookie (httpOnly / Path=/api/auth)
 *   [BB-2] Service が 401 → 401 + message
 *
 * refresh() (状態遷移):
 *   [ST-1] Cookie なし → 401
 *   [ST-2] Cookie あり + Service 成功 → 200 + 新 Cookie
 *
 * logout():
 *   [BB-1] Cookie あり → 204 + Max-Age=0 の破棄 Cookie + Service に値が渡る
 *   [BB-2] Cookie なし → 204 + Service に null が渡る
 *
 * 注: Spring Boot 4.0 で @WebMvcTest / @AutoConfigureMockMvc が削除されたため、
 *     MockMvcBuilders.standaloneSetup() で Controller を直接組み立てる方式を採る。
 *     これにより Boot の auto-config に依存せず、テストが軽量・高速になる。
 * </pre>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController")
class AuthControllerTest {

    @Mock private AuthService authService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // 7日 = 604800000ms 相当のリフレッシュトークン有効期限を渡す。
        AuthController controller = new AuthController(authService, 604_800_000L);
        mockMvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
    }

    @Nested
    @DisplayName("POST /api/auth/register")
    class Register {

        @Test
        @DisplayName("必須項目欠落のとき 400 を返し errors マップに該当フィールドが含まれる")
        void バリデーションエラーで400() throws Exception {
            // email を欠落させた不正な JSON
            String body =
                    "{\"displayName\":\"アリス\",\"username\":\"alice\","
                            + "\"password\":\"Pass1234\",\"passwordConfirm\":\"Pass1234\"}";

            mockMvc.perform(
                            post("/api/auth/register")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.email").exists());
            verify(authService, never()).register(any());
        }

        @Test
        @DisplayName("正常リクエストで 201 + AuthResponse + Set-Cookie を返す")
        void 正常登録で201とCookie() throws Exception {
            RegisterRequest req =
                    new RegisterRequest(
                            "アリス", "alice", "alice@example.com", "Pass1234", "Pass1234");
            when(authService.register(any(RegisterRequest.class)))
                    .thenReturn(
                            new AuthService.AuthResult(
                                    new AuthResponse("access-jwt", 1L, "alice", "アリス"),
                                    "refresh-token-value"));

            mockMvc.perform(
                            post("/api/auth/register")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.accessToken").value("access-jwt"))
                    .andExpect(jsonPath("$.userId").value(1))
                    .andExpect(jsonPath("$.username").value("alice"))
                    .andExpect(cookie().exists("refresh_token"))
                    .andExpect(cookie().value("refresh_token", "refresh-token-value"))
                    .andExpect(cookie().httpOnly("refresh_token", true))
                    .andExpect(cookie().path("refresh_token", "/api/auth"));
        }

        @Test
        @DisplayName("Service がメール重複で 400 を投げると 400 + message が返る")
        void Service例外で400() throws Exception {
            RegisterRequest req =
                    new RegisterRequest("アリス", "alice", "dup@example.com", "Pass1234", "Pass1234");
            when(authService.register(any(RegisterRequest.class)))
                    .thenThrow(
                            new ResponseStatusException(
                                    HttpStatus.BAD_REQUEST, "このメールアドレスは既に使用されています"));

            mockMvc.perform(
                            post("/api/auth/register")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", containsString("メールアドレス")));
        }
    }

    @Nested
    @DisplayName("POST /api/auth/login")
    class Login {

        @Test
        @DisplayName("正常ログインで 200 + Cookie")
        void 正常() throws Exception {
            LoginRequest req = new LoginRequest("alice@example.com", "Pass1234");
            when(authService.login(any(LoginRequest.class)))
                    .thenReturn(
                            new AuthService.AuthResult(
                                    new AuthResponse("access-jwt", 1L, "alice", "アリス"),
                                    "refresh-token-value"));

            mockMvc.perform(
                            post("/api/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("access-jwt"))
                    .andExpect(cookie().exists("refresh_token"));
        }

        @Test
        @DisplayName("認証失敗時は 401 + message を返す")
        void 認証失敗で401() throws Exception {
            LoginRequest req = new LoginRequest("alice@example.com", "wrong");
            when(authService.login(any(LoginRequest.class)))
                    .thenThrow(
                            new ResponseStatusException(
                                    HttpStatus.UNAUTHORIZED, "メールアドレスまたはパスワードが正しくありません"));

            mockMvc.perform(
                            post("/api/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").exists());
        }
    }

    @Nested
    @DisplayName("POST /api/auth/refresh")
    class Refresh {

        @Test
        @DisplayName("Cookie がないとき 401 (Service は呼ばれない)")
        void Cookieなしで401() throws Exception {
            mockMvc.perform(post("/api/auth/refresh")).andExpect(status().isUnauthorized());
            verify(authService, never()).refresh(any());
        }

        @Test
        @DisplayName("Cookie があるとき Service を呼び 200 + 新 Cookie")
        void Cookieありで成功() throws Exception {
            when(authService.refresh("old-token"))
                    .thenReturn(
                            new AuthService.AuthResult(
                                    new AuthResponse("new-access", 1L, "alice", "アリス"),
                                    "new-refresh"));

            mockMvc.perform(
                            post("/api/auth/refresh")
                                    .cookie(new Cookie("refresh_token", "old-token")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("new-access"))
                    .andExpect(cookie().value("refresh_token", "new-refresh"));
        }
    }

    @Nested
    @DisplayName("POST /api/auth/logout")
    class Logout {

        @Test
        @DisplayName("Cookie あり: 204 + Max-Age=0 の破棄 Cookie")
        void Cookieありで204() throws Exception {
            mockMvc.perform(post("/api/auth/logout").cookie(new Cookie("refresh_token", "abc")))
                    .andExpect(status().isNoContent())
                    .andExpect(cookie().maxAge("refresh_token", 0))
                    .andExpect(cookie().value("refresh_token", ""));
            verify(authService).logout("abc");
        }

        @Test
        @DisplayName("Cookie なし: 204 + Service に null が渡る")
        void Cookieなしで204() throws Exception {
            mockMvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent());
            verify(authService).logout(null);
        }
    }
}
