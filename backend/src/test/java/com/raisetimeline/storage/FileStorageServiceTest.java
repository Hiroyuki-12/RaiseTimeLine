package com.raisetimeline.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * FileStorageService の単体テスト。
 *
 * <pre>
 * 対象: FileStorageService (saveAvatar / savePostImage / validate / URL 生成)
 * 技法: 分岐網羅 (URL: CloudFront 設定あり/なし) + 同値分割/境界値 (サイズ・MIME) + 状態 (空ファイル)
 *
 * URL 生成:
 *   [WB-1] app.media.base-url 設定あり  → CloudFront ルートの URL（media/ プレフィックス配下）を返す
 *   [WB-2] app.media.base-url 設定なし  → 直リンク S3 URL にフォールバックする
 *   [WB-3] base-url 末尾スラッシュあり  → スラッシュ重複なく結合する
 *   [EP ] saveAvatar/savePostImage     → それぞれ media/avatars, media/posts 配下に保存する
 *
 * validate:
 *   [EP-1] 空ファイル        → 400
 *   [BV-1] サイズ超過(2MB+1) → 400（境界値）
 *   [EP-2] 非対応 MIME       → 400
 * </pre>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FileStorageService の単体テスト")
class FileStorageServiceTest {

    @Mock private S3Client s3Client;

    private FileStorageService service;

    // テスト用の S3 設定値。@Value はテストでは注入されないため ReflectionTestUtils で直接埋める。
    private static final String BUCKET = "test-bucket";
    private static final String REGION = "ap-northeast-1";

    @BeforeEach
    void setUp() {
        service = new FileStorageService(s3Client);
        ReflectionTestUtils.setField(service, "bucketName", BUCKET);
        ReflectionTestUtils.setField(service, "region", REGION);
    }

    /** JPEG の正常なテストファイルを作る小道具。 */
    private MultipartFile validJpeg() {
        return new MockMultipartFile("image", "photo.jpg", "image/jpeg", new byte[] {1, 2, 3});
    }

    @Nested
    @DisplayName("URL 生成")
    class UrlGeneration {

        @Test
        @DisplayName("[WB-1] base-url 設定あり: CloudFront ルートの URL を media/posts 配下で返す")
        void cloudFrontUrlForPost() {
            ReflectionTestUtils.setField(service, "mediaBaseUrl", "https://cdn.example.net");

            String url = service.savePostImage(validJpeg());

            // CloudFront ドメイン配下・media/posts プレフィックス・UUID + 拡張子であること
            assertThat(url).startsWith("https://cdn.example.net/media/posts/");
            assertThat(url).endsWith(".jpg");
            // 直リンク S3 URL になっていないこと（403 になる経路を返さない）
            assertThat(url).doesNotContain(".s3.");
            // 実際に S3 へ PutObject したこと
            verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        }

        @Test
        @DisplayName("[EP] saveAvatar は media/avatars 配下に保存する")
        void cloudFrontUrlForAvatar() {
            ReflectionTestUtils.setField(service, "mediaBaseUrl", "https://cdn.example.net");

            String url = service.saveAvatar(validJpeg());

            assertThat(url).startsWith("https://cdn.example.net/media/avatars/");
        }

        @Test
        @DisplayName("[WB-2] base-url 設定なし: 直リンク S3 URL にフォールバックする")
        void fallbackToDirectS3Url() {
            ReflectionTestUtils.setField(service, "mediaBaseUrl", "");

            String url = service.savePostImage(validJpeg());

            assertThat(url)
                    .startsWith(
                            "https://" + BUCKET + ".s3." + REGION + ".amazonaws.com/media/posts/");
        }

        @Test
        @DisplayName("[WB-3] base-url 末尾スラッシュあり: スラッシュが重複しない")
        void trailingSlashIsNormalized() {
            ReflectionTestUtils.setField(service, "mediaBaseUrl", "https://cdn.example.net/");

            String url = service.savePostImage(validJpeg());

            assertThat(url).startsWith("https://cdn.example.net/media/posts/");
            assertThat(url).doesNotContain("//media");
        }
    }

    @Nested
    @DisplayName("バリデーション")
    class Validation {

        @Test
        @DisplayName("[EP-1] 空ファイルは 400")
        void emptyFileRejected() {
            MultipartFile empty =
                    new MockMultipartFile("image", "empty.jpg", "image/jpeg", new byte[0]);

            assertThatThrownBy(() -> service.savePostImage(empty))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting("statusCode")
                    .isEqualTo(HttpStatus.BAD_REQUEST);
            // 検証で弾かれた場合は S3 へアップロードしないこと
            verify(s3Client, never())
                    .putObject(any(PutObjectRequest.class), any(RequestBody.class));
        }

        @Test
        @DisplayName("[BV-1] 2MB を 1 バイト超えると 400（境界値）")
        void oversizeFileRejected() {
            byte[] tooBig = new byte[2 * 1024 * 1024 + 1];
            MultipartFile big = new MockMultipartFile("image", "big.jpg", "image/jpeg", tooBig);

            assertThatThrownBy(() -> service.savePostImage(big))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting("statusCode")
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("[EP-2] 非対応 MIME（gif）は 400")
        void unsupportedMimeRejected() {
            MultipartFile gif =
                    new MockMultipartFile("image", "a.gif", "image/gif", new byte[] {1});

            assertThatThrownBy(() -> service.savePostImage(gif))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting("statusCode")
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
}
