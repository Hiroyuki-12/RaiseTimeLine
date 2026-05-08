package com.raisetimeline.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * AWS S3 クライアントの設定クラス。
 *
 * <p>application.properties の aws.s3.region プロパティを読み込み、 S3Client Bean を生成して Spring コンテナに登録する。
 * FileStorageService がこの Bean を使って S3 にファイルをアップロードする。
 *
 * <p>認証情報は DefaultCredentialsProvider（AWS SDK の自動認証チェーン）を使う。 以下の順で認証情報を自動検出する（最初に見つかったものを使う）: 1.
 * 環境変数（AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY） 2. ~/.aws/credentials ファイル（aws configure で設定したもの）
 * 3. EC2/ECS の IAM ロール（本番環境で推奨） この方式により、認証情報をコードやプロパティファイルに直接書かなくて済むため安全。
 */
@Configuration
public class S3Config {

    /** S3 バケットのリージョン（application.properties の aws.s3.region） */
    @Value("${aws.s3.region}")
    private String region;

    /**
     * S3Client Bean を生成する。
     *
     * <p>DefaultCredentialsProvider により、~/.aws/credentials や環境変数から認証情報を自動取得する。 リージョンは
     * application.properties の aws.s3.region を使う（例: ap-northeast-1 = 東京リージョン）。
     *
     * @return 設定済みの S3Client
     */
    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(region))
                // DefaultCredentialsProvider: 環境変数 → ~/.aws/credentials → IAM ロールの順に認証情報を探す
                // 認証情報をコードに直接書かなくてよいため、誤ってコミットするリスクがない
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
