package com.raisetimeline.post;

import com.raisetimeline.post.dto.CreatePostRequest;
import com.raisetimeline.post.dto.PostResponse;
import com.raisetimeline.post.dto.UpdatePostRequest;
import com.raisetimeline.user.User;
import com.raisetimeline.user.UserMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    /** コンストラクタ。Spring が PostMapper と UserMapper を自動的に注入する。 */
    public PostService(PostMapper postMapper, UserMapper userMapper) {
        this.postMapper = postMapper;
        this.userMapper = userMapper;
    }

    /**
     * ポストをページネーション付きで新しい順に取得する（タイムライン）。 認証済みユーザーなら誰でも閲覧できる。
     *
     * @param page 0 始まりのページ番号
     * @param size 1 ページあたりの件数
     * @return PostResponse のリスト（created_at 降順）
     */
    @Transactional(readOnly = true)
    public List<PostResponse> getPage(int page, int size) {
        int offset = page * size;
        return postMapper.findPageOrderByCreatedAtDesc(offset, size);
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
     * 新しいポストを作成する。 SecurityContextHolder から取得したメールアドレスでユーザーを特定し、 userId を posts テーブルに保存する。
     *
     * @param req 投稿内容（content）を含むリクエスト DTO
     * @param email JwtAuthFilter が SecurityContextHolder にセットしたメールアドレス
     * @return 作成した PostResponse（DB 生成のタイムスタンプ含む）
     */
    @Transactional
    public PostResponse createPost(CreatePostRequest req, String email) {
        // メールアドレスからユーザーを取得して userId を確定する
        User user =
                userMapper
                        .findByEmail(email)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.UNAUTHORIZED, "ユーザーが見つかりません"));

        Post post = new Post();
        post.setUserId(user.getId());
        post.setContent(req.content());
        postMapper.insert(post);

        // INSERT 後に DB が生成したタイムスタンプ（NOW()）を取得するために再検索する
        Post saved =
                postMapper
                        .findById(post.getId())
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.INTERNAL_SERVER_ERROR, "投稿の取得に失敗しました"));

        log.info("ポスト作成: postId={}, userId={}", saved.getId(), user.getId());
        return toPostResponse(saved, user);
    }

    /**
     * ポストを編集する。投稿者本人のみ実行できる。
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

        // 対象ポストの存在確認
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

        post.setContent(req.content());
        postMapper.update(post);

        // UPDATE 後に DB のタイムスタンプを取得するために再検索する
        Post updated =
                postMapper
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.INTERNAL_SERVER_ERROR, "投稿の取得に失敗しました"));

        log.info("ポスト更新: postId={}, userId={}", updated.getId(), user.getId());
        return toPostResponse(updated, user);
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

        // 対象ポストの存在確認
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

    /**
     * Post エンティティと User から PostResponse を組み立てる内部メソッド。 createPost・updatePost の戻り値を統一するために使う。
     *
     * @param post 保存済みポスト（id・content・createdAt・updatedAt が確定済み）
     * @param user 投稿者
     * @return PostResponse
     */
    private PostResponse toPostResponse(Post post, User user) {
        return new PostResponse(
                post.getId(),
                post.getContent(),
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                post.getCreatedAt(),
                post.getUpdatedAt());
    }
}
