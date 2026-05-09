package com.raisetimeline.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.raisetimeline.auth.dto.LoginRequest;
import com.raisetimeline.auth.dto.RegisterRequest;
import com.raisetimeline.support.TestFixtures;
import com.raisetimeline.user.User;
import com.raisetimeline.user.UserMapper;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

/**
 * AuthService の単体テスト。
 *
 * <pre>
 * 対象: AuthService (register / login / refresh / logout)
 * 技法: 分岐網羅 (ホワイトボックス) + 状態遷移 (ブラックボックス) + デシジョンテーブル
 *
 * register():
 *   [WB-1] パスワード不一致 → 400
 *   [WB-2] メール重複 → 400
 *   [WB-3] ユーザー名重複 → 400
 *   [WB-4] 全条件OK → INSERT 実行 + AuthResult 返却 (パスワードハッシュ化を verify)
 *
 * login():
 *   [BB-1] メール不在 → 401 (列挙攻撃対策で同一メッセージ)
 *   [BB-2] パスワード不一致 → 401 (同上)
 *   [BB-3] 認証成功 → AuthResult 返却 (ローテーション)
 *
 * refresh() (状態遷移):
 *   [ST-1] トークン不在 → 401
 *   [ST-2] トークン期限切れ → 401 + 期限切れトークン削除
 *   [ST-3] ユーザー不在 (整合性異常) → 401
 *   [ST-4] 有効トークン → 旧トークン削除 + 新トークン発行
 *
 * logout():
 *   [BB-1] null → 何もしない (NPE 等が起きないこと)
 *   [BB-2] 空文字 → 何もしない
 *   [BB-3] 通常文字列 → deleteByToken 実行
 * </pre>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService")
class AuthServiceTest {

    @Mock private UserMapper userMapper;
    @Mock private RefreshTokenMapper refreshTokenMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;

    private AuthService sut;

    @BeforeEach
    void setUp() {
        // AuthService のコンストラクタに primitive long が含まれるため @InjectMocks が失敗する。
        // 手動で構築して、テスト用のリフレッシュトークン有効期限 (7日 = 604800000ms) を渡す。
        sut =
                new AuthService(
                        userMapper, refreshTokenMapper, passwordEncoder, jwtUtil, 604_800_000L);
    }

    // ============================== register ==============================
    @Nested
    @DisplayName("register")
    class Register {

        private RegisterRequest validRequest() {
            return new RegisterRequest("アリス", "alice", "alice@example.com", "Pass1234", "Pass1234");
        }

        @Test
        @DisplayName("パスワードと確認用が不一致のとき 400 を投げる")
        void パスワード不一致で400() {
            RegisterRequest req =
                    new RegisterRequest(
                            "アリス", "alice", "alice@example.com", "Pass1234", "Different1234");

            assertThatThrownBy(() -> sut.register(req))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
            verify(userMapper, never()).insert(any());
        }

        @Test
        @DisplayName("メールアドレスが既に存在するとき 400 を投げる")
        void メール重複で400() {
            RegisterRequest req = validRequest();
            when(userMapper.existsByEmail(req.email())).thenReturn(true);

            assertThatThrownBy(() -> sut.register(req))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
            verify(userMapper, never()).insert(any());
        }

        @Test
        @DisplayName("ユーザー名が既に存在するとき 400 を投げる")
        void ユーザー名重複で400() {
            RegisterRequest req = validRequest();
            when(userMapper.existsByEmail(req.email())).thenReturn(false);
            when(userMapper.existsByUsername(req.username())).thenReturn(true);

            assertThatThrownBy(() -> sut.register(req))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
            verify(userMapper, never()).insert(any());
        }

        @Test
        @DisplayName("バリデーション通過時はパスワードをハッシュ化し INSERT して AuthResult を返す")
        void 正常系で登録成功() {
            RegisterRequest req = validRequest();
            when(userMapper.existsByEmail(req.email())).thenReturn(false);
            when(userMapper.existsByUsername(req.username())).thenReturn(false);
            when(passwordEncoder.encode(req.password())).thenReturn("$2a$10$hashed");
            // userMapper.insert で id が採番される挙動を再現する
            doAnswerSetId(1L).when(userMapper).insert(any(User.class));
            when(jwtUtil.generateToken("alice@example.com")).thenReturn("access-token");
            when(jwtUtil.generateRefreshToken()).thenReturn("refresh-token");

            AuthService.AuthResult result = sut.register(req);

            assertThat(result.response().accessToken()).isEqualTo("access-token");
            assertThat(result.response().username()).isEqualTo("alice");
            assertThat(result.refreshToken()).isEqualTo("refresh-token");
            // 平文ではなく BCrypt でハッシュ化された値が DB に渡されること
            verify(passwordEncoder).encode("Pass1234");
            verify(refreshTokenMapper).insert(any(RefreshToken.class));
        }

        // userMapper.insert 呼び出し時に User#setId(id) を疑似的に行うためのヘルパー
        private static org.mockito.stubbing.Stubber doAnswerSetId(long id) {
            return org.mockito.Mockito.doAnswer(
                    invocation -> {
                        User u = invocation.getArgument(0);
                        u.setId(id);
                        return null;
                    });
        }
    }

    // ============================== login ==============================
    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("メールが存在しないとき 401 を投げる (列挙攻撃対策で同一メッセージ)")
        void メール不在で401() {
            LoginRequest req = new LoginRequest("nobody@example.com", "Pass1234");
            when(userMapper.findByEmail(req.email())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> sut.login(req))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("パスワードが不一致のとき 401 を投げる (列挙攻撃対策で同一メッセージ)")
        void パスワード不一致で401() {
            LoginRequest req = new LoginRequest("alice@example.com", "WrongPass");
            User user = TestFixtures.aliceUser();
            when(userMapper.findByEmail(req.email())).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("WrongPass", user.getPasswordHash())).thenReturn(false);

            assertThatThrownBy(() -> sut.login(req))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
            // パスワードが一致しないときはトークン生成を行わないこと
            verify(jwtUtil, never()).generateToken(anyString());
        }

        @Test
        @DisplayName("認証成功時は AuthResult を返しリフレッシュトークンを保存する")
        void 認証成功() {
            LoginRequest req = new LoginRequest("alice@example.com", "Pass1234");
            User user = TestFixtures.aliceUser();
            when(userMapper.findByEmail(req.email())).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("Pass1234", user.getPasswordHash())).thenReturn(true);
            when(jwtUtil.generateToken(user.getEmail())).thenReturn("access-token");
            when(jwtUtil.generateRefreshToken()).thenReturn("refresh-token");

            AuthService.AuthResult result = sut.login(req);

            assertThat(result.response().accessToken()).isEqualTo("access-token");
            assertThat(result.response().userId()).isEqualTo(1L);
            assertThat(result.refreshToken()).isEqualTo("refresh-token");
            verify(refreshTokenMapper).insert(any(RefreshToken.class));
        }
    }

    // ============================== refresh ==============================
    @Nested
    @DisplayName("refresh")
    class Refresh {

        @Test
        @DisplayName("トークンが DB に存在しないとき 401 を投げる")
        void トークン不在で401() {
            when(refreshTokenMapper.findByToken("token")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> sut.refresh("token"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("トークンが期限切れのとき 401 を投げ、当該トークンを DB から削除する")
        void 期限切れで401_かつ削除() {
            // 期限切れ = 現在より過去
            RefreshToken expired =
                    TestFixtures.refreshToken(1L, "expired", LocalDateTime.now().minusDays(1));
            when(refreshTokenMapper.findByToken("expired")).thenReturn(Optional.of(expired));

            assertThatThrownBy(() -> sut.refresh("expired"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
            // 期限切れトークンは破棄されること (再利用攻撃対策)
            verify(refreshTokenMapper).deleteByToken("expired");
            verify(jwtUtil, never()).generateToken(anyString());
        }

        @Test
        @DisplayName("トークンに紐づくユーザーが存在しないとき 401 を投げる (整合性異常)")
        void ユーザー不在で401() {
            RefreshToken rt =
                    TestFixtures.refreshToken(99L, "valid", LocalDateTime.now().plusDays(7));
            when(refreshTokenMapper.findByToken("valid")).thenReturn(Optional.of(rt));
            when(userMapper.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> sut.refresh("valid"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("有効なトークンのとき、旧トークンを削除して新トークンを発行する (ローテーション)")
        void 有効トークンでローテーション() {
            RefreshToken rt = TestFixtures.refreshToken(1L, "old", LocalDateTime.now().plusDays(7));
            when(refreshTokenMapper.findByToken("old")).thenReturn(Optional.of(rt));
            when(userMapper.findById(1L)).thenReturn(Optional.of(TestFixtures.aliceUser()));
            when(jwtUtil.generateToken("alice@example.com")).thenReturn("new-access");
            when(jwtUtil.generateRefreshToken()).thenReturn("new-refresh");

            AuthService.AuthResult result = sut.refresh("old");

            assertThat(result.response().accessToken()).isEqualTo("new-access");
            assertThat(result.refreshToken()).isEqualTo("new-refresh");
            // 旧トークンは確実に削除される
            verify(refreshTokenMapper).deleteByToken("old");
            // 新トークンは新規 INSERT される
            verify(refreshTokenMapper).insert(any(RefreshToken.class));
        }
    }

    // ============================== logout ==============================
    @Nested
    @DisplayName("logout")
    class Logout {

        @Test
        @DisplayName("null のときは Mapper を呼ばない")
        void null時は無処理() {
            sut.logout(null);
            verify(refreshTokenMapper, never()).deleteByToken(anyString());
        }

        @Test
        @DisplayName("空文字のときは Mapper を呼ばない")
        void 空文字時は無処理() {
            sut.logout("   ");
            verify(refreshTokenMapper, never()).deleteByToken(anyString());
        }

        @Test
        @DisplayName("通常文字列のときは deleteByToken を1回呼ぶ")
        void 通常時は削除() {
            sut.logout("token-x");
            verify(refreshTokenMapper, times(1)).deleteByToken("token-x");
        }
    }
}
