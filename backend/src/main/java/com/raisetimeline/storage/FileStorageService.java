package com.raisetimeline.storage;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * AWS S3 へのファイルアップロードを担当するサービスクラス。
 *
 * <p>プロフィール画像は media/avatars/、投稿画像は media/posts/ プレフィックス付きで保存する。 ファイル名は UUID で生成して重複・予測を防ぐ。
 * バリデーション（ファイルサイズ・MIME タイプ）もここで一元管理する。
 *
 * <p>配信は CloudFront 経由（{@code app.media.base-url}）を基本とする。S3 バケットは非公開（OAC 経由のみ） のため直リンク S3 URL は 403
 * になる。未設定時はローカル開発を想定し直リンク S3 URL にフォールバックする。
 */
@Service
public class FileStorageService {

    /** 許可する MIME タイプ（JPEG・PNG のみ） */
    private static final List<String> ALLOWED_CONTENT_TYPES =
            Arrays.asList("image/jpeg", "image/png");

    /** アップロード可能な最大ファイルサイズ（2MB = 2 * 1024 * 1024 バイト） */
    private static final long MAX_FILE_SIZE = 2L * 1024 * 1024;

    private final S3Client s3Client;

    /** S3 バケット名（application.properties の aws.s3.bucket-name） */
    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    /** S3 バケットのリージョン（直リンク S3 URL の生成に使う） */
    @Value("${aws.s3.region}")
    private String region;

    /**
     * 画像配信のベース URL（末尾スラッシュなし）。
     *
     * <p>本番では CloudFront のドメイン（例: https://xxxx.cloudfront.net）を設定する。 S3 バケットは非公開（OAC 経由のみ） なので直リンク
     * S3 URL は 403 になる。そのため CloudFront 経由の URL を返す必要がある。
     *
     * <p>未設定（空文字）の場合はローカル開発などを想定し、従来どおり直リンクの S3 URL を組み立てる。
     */
    @Value("${app.media.base-url:}")
    private String mediaBaseUrl;

    /** コンストラクタ。Spring が S3Client を自動的に注入する。 */
    public FileStorageService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    /**
     * プロフィール画像を S3 の avatars/ フォルダにアップロードする。
     *
     * @param file アップロードするファイル（JPEG または PNG、2MB 以下）
     * @return 配信 URL（CloudFront 設定時は https://cdn/media/avatars/uuid.jpg）
     */
    public String saveAvatar(MultipartFile file) {
        // ファイルの種類・サイズを検証する
        validate(file);
        // media/avatars/ プレフィックス付きでアップロードする。
        // SPA のルート（/users/:username 等）と衝突しないよう media/ 配下にまとめる。
        return upload(file, "media/avatars");
    }

    /**
     * 投稿画像を S3 の posts/ フォルダにアップロードする。
     *
     * @param file アップロードするファイル（JPEG または PNG、2MB 以下）
     * @return 配信 URL（CloudFront 設定時は https://cdn/media/posts/uuid.jpg）
     */
    public String savePostImage(MultipartFile file) {
        // ファイルの種類・サイズを検証する
        validate(file);
        // media/posts/ プレフィックス付きでアップロードする。
        // SPA のルート（/posts/:postId）と衝突しないよう media/ 配下にまとめる。
        return upload(file, "media/posts");
    }

    /**
     * ファイルを S3 にアップロードして公開 URL を返す内部メソッド。
     *
     * <p>ファイル名は UUID + 元の拡張子で生成する（例: uuid.jpg）。 UUID にすることでファイル名の衝突と外部からの予測を防ぐ。
     *
     * @param file アップロードするファイル
     * @param prefix S3 内のフォルダ名（media/avatars または media/posts）
     * @return 配信 URL
     */
    private String upload(MultipartFile file, String prefix) {
        // 元のファイル名から拡張子を取り出す（例: photo.jpg → .jpg）
        String originalFilename = file.getOriginalFilename();
        String extension =
                (originalFilename != null && originalFilename.contains("."))
                        ? originalFilename.substring(originalFilename.lastIndexOf("."))
                        : ".jpg";

        // UUID でランダムなファイル名を生成する
        String key = prefix + "/" + UUID.randomUUID() + extension;

        try {
            // S3 の PutObject リクエストを組み立てる
            PutObjectRequest request =
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(key)
                            .contentType(file.getContentType())
                            .contentLength(file.getSize())
                            .build();

            // S3 にアップロードする（同期アップロード）
            s3Client.putObject(
                    request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "ファイルのアップロードに失敗しました");
        }

        // 配信 URL を組み立てて返す。
        // 本番（CloudFront 設定あり）: https://{cloudfront-domain}/{key}
        //   → バケットは非公開のまま CloudFront(OAC) 経由で配信するため、直リンク S3 URL は使わない。
        // ローカル等（未設定）: https://{bucket}.s3.{region}.amazonaws.com/{key}
        //   → 従来どおり直リンク S3 URL にフォールバックする。
        if (mediaBaseUrl != null && !mediaBaseUrl.isBlank()) {
            // 末尾スラッシュの有無に関わらず正しく結合できるよう、余分なスラッシュを除去する。
            String base =
                    mediaBaseUrl.endsWith("/")
                            ? mediaBaseUrl.substring(0, mediaBaseUrl.length() - 1)
                            : mediaBaseUrl;
            return base + "/" + key;
        }
        return "https://" + bucketName + ".s3." + region + ".amazonaws.com/" + key;
    }

    /**
     * ファイルのサイズと MIME タイプを検証する。
     *
     * <p>不正なファイルは 400 Bad Request で弾く。 クライアント側のバリデーションだけでは不十分なため、サーバー側でも必ず検証する。
     *
     * @param file 検証するファイル
     */
    private void validate(MultipartFile file) {
        // 空ファイルチェック
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ファイルが選択されていません");
        }
        // ファイルサイズチェック（2MB 以下）
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "ファイルサイズが大きすぎます（2MB 以下にしてください）");
        }
        // MIME タイプチェック（JPEG・PNG のみ許可）
        // getContentType() はクライアントが送った値なので偽装可能だが、学習用アプリでは十分
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "対応していないファイル形式です（JPEG または PNG のみ）");
        }
    }
}
