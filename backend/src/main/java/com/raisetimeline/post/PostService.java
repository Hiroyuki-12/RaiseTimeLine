package com.raisetimeline.post;

import com.raisetimeline.post.dto.CreatePostRequest;
import com.raisetimeline.post.dto.PostResponse;
import com.raisetimeline.post.dto.UpdatePostRequest;
import com.raisetimeline.storage.FileStorageService;
import com.raisetimeline.user.User;
import com.raisetimeline.user.UserMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * ポストの CRUD ビジネスロジックを担当するサービスクラス。 コントローラーから呼び出し、HTTP の詳細とビジネスロジックを分離する。
 * 認可チェック（投稿者本人のみ編集・削除可）はこのクラスで行う。
 */
@Service
public class PostService {

    // SLF4J ロガー。クラス名をカテゴリとして使用する
    private static final Logger log = LoggerFactory.getLogger(PostService.class);

    private final PostMapper postMapper;
    private final UserMapper userMapper;
    private final FileStorageService fileStorageService;

    /** コンストラクタ。Spring が各依存を自動的に注入する。 */
    public PostService(
            PostMapper postMapper, UserMapper userMapper, FileStorageService fileStorageService) {
        this.postMapper = postMapper;
        this.userMapper = userMapper;
        this.fileStorageService = fileStorageService;
    }

    /**
     * ポストをページネーション付きで新しい順に取得する（タイムライン）。 認証済みユーザーなら誰でも閲覧できる。
     *
     * <p>timeline="all" のときは全員の投稿を返す。 timeline="following" のときはログイン中ユーザーがフォローしている人の投稿のみ返す。
     *
     * <p>N+1 対策: どちらも1SQL で likes/comments の COUNT と liked フラグを集計サブクエリで取得する。
     *
     * @param page 0 始まりのページ番号
     * @param size 1 ページあたりの件数
     * @param currentUserEmail 現在のユーザーのメールアドレス（liked 判定に使う）
     * @param timeline "all"（全員）または "following"（フォロー中）を指定する
     * @return PostResponse のリスト（created_at 降順）
     */
    @Transactional(readOnly = true)
    public List<PostResponse> getPage(
            int page, int size, String currentUserEmail, String timeline) {
        int offset = page * size;
        if ("following".equals(timeline)) {
            // フォロー中タイムライン: 現在のユーザー ID が必要なため findByEmail で取得する
            User user =
                    userMapper
                            .findByEmail(currentUserEmail)
                            .orElseThrow(
                                    () ->
                                            new ResponseStatusException(
                                                    HttpStatus.UNAUTHORIZED, "ユーザーが見つかりません"));
            return postMapper.findFollowingPageOrderByCreatedAtDesc(
                    user.getId(), offset, size, currentUserEmail);
        }
        // デフォルト（全員タイムライン）
        return postMapper.findPageOrderByCreatedAtDesc(offset, size, currentUserEmail);
    }

    /**
     * ID でポストを1件取得する（投稿詳細画面用）。 いいね数・コメント数・liked フラグを集計サブクエリで取得するため N+1 は発生しない。
     *
     * @param id 取得するポスト ID
     * @param currentUserEmail 現在のユーザーのメールアドレス（liked 判定に使う）
     * @return PostResponse
     */
    @Transactional(readOnly = true)
    public PostResponse getById(Long id, String currentUserEmail) {
        return postMapper
                .findByIdAsResponse(id, currentUserEmail)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ポストが見つかりません"));
    }

    /**
     * 指定日時より後に作成されたポスト件数を返す（ポーリングによる新着チェック用）。
     *
     * @param since この日時より後に作成されたものをカウントする
     * @return 新着件数
     */
    @Transactional(readOnly = true)
    public long countNewerThan(LocalDateTime since) {
        return postMapper.countNewerThan(since);
    }

    /**
     * 新しいポストを作成する。テキストと画像（任意）を受け取り、画像は S3 に保存する。
     *
     * <p>N+1 修正: insertAndReturn を使い INSERT ... RETURNING で1クエリにまとめることで、 従来の insert + findById
     * の2クエリを1クエリに削減した。
     *
     * @param req 投稿内容（content）を含むリクエスト DTO
     * @param image 添付画像ファイル（任意。null または空の場合は画像なしで投稿する）
     * @param email JwtAuthFilter が SecurityContextHolder にセットしたメールアドレス
     * @return 作成した PostResponse（DB 生成のタイムスタンプ含む）
     */
    @Transactional
    public PostResponse createPost(CreatePostRequest req, MultipartFile image, String email) {
        // メールアドレスからユーザーを取得して userId を確定する
        User user =
                userMapper
                        .findByEmail(email)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.UNAUTHORIZED, "ユーザーが見つかりません"));

        // 画像が添付されている場合は S3 にアップロードして URL を取得する（なければ null）
        String imageUrl = null;
        if (image != null && !image.isEmpty()) {
            imageUrl = fileStorageService.savePostImage(image);
        }

        // INSERT ... RETURNING で挿入と同時に PostResponse を取得する（N+1 修正）
        PostResponse saved =
                postMapper.insertAndReturn(user.getId(), req.content(), imageUrl, email);

        log.info("ポスト作成: postId={}, userId={}", saved.id(), user.getId());
        return saved;
    }

    /**
     * ポストを編集する。投稿者本人のみ実行できる。
     *
     * <p>N+1 修正: updateAndReturn を使い UPDATE ... RETURNING で1クエリにまとめることで、 従来の update + findById
     * の2クエリを1クエリに削減した。
     *
     * @param id 編集するポストの ID
     * @param req 更新内容（content）を含むリクエスト DTO
     * @param email JwtAuthFilter が SecurityContextHolder にセットしたメールアドレス
     * @return 更新後の PostResponse
     */
    @Transactional
    public PostResponse updatePost(Long id, UpdatePostRequest req, String email) {
        // リクエスト元のユーザーを取得する
        User user =
                userMapper
                        .findByEmail(email)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.UNAUTHORIZED, "ユーザーが見つかりません"));

        // 対象ポストの存在確認・投稿者本人チェック（削除前に user_id を確認する必要があるため findById は必須）
        Post post =
                postMapper
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "ポストが見つかりません"));

        // 投稿者本人かどうかを確認する（他ユーザーの投稿は編集不可）
        if (!post.getUserId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "このポストを編集する権限がありません");
        }

        // UPDATE ... RETURNING で更新と同時に PostResponse を取得する（N+1 修正）
        PostResponse updated = postMapper.updateAndReturn(id, req.content(), email);

        log.info("ポスト更新: postId={}, userId={}", id, user.getId());
        return updated;
    }

    /**
     * ポストを削除する。投稿者本人のみ実行できる。
     *
     * @param id 削除するポストの ID
     * @param email JwtAuthFilter が SecurityContextHolder にセットしたメールアドレス
     */
    @Transactional
    public void deletePost(Long id, String email) {
        // リクエスト元のユーザーを取得する
        User user =
                userMapper
                        .findByEmail(email)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.UNAUTHORIZED, "ユーザーが見つかりません"));

        // 対象ポストの存在確認・投稿者本人チェック
        Post post =
                postMapper
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "ポストが見つかりません"));

        // 投稿者本人かどうかを確認する
        if (!post.getUserId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "このポストを削除する権限がありません");
        }

        postMapper.deleteById(id);
        log.info("ポスト削除: postId={}, userId={}", id, user.getId());
    }
}
