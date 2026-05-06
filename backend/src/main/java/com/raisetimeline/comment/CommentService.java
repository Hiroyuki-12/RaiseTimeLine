package com.raisetimeline.comment;

import com.raisetimeline.comment.dto.CommentResponse;
import com.raisetimeline.comment.dto.CreateCommentRequest;
import com.raisetimeline.comment.dto.UpdateCommentRequest;
import com.raisetimeline.user.User;
import com.raisetimeline.user.UserMapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** コメントの CRUD ビジネスロジックを担当するサービスクラス。 認可チェック（コメント投稿者本人のみ編集・削除可）はこのクラスで行う。 */
@Service
public class CommentService {

    private static final Logger log = LoggerFactory.getLogger(CommentService.class);

    private final CommentMapper commentMapper;
    private final UserMapper userMapper;

    /** コンストラクタ。Spring が CommentMapper と UserMapper を自動的に注入する。 */
    public CommentService(CommentMapper commentMapper, UserMapper userMapper) {
        this.commentMapper = commentMapper;
        this.userMapper = userMapper;
    }

    /**
     * 指定した投稿のコメント一覧を取得する。 N+1 対策: findByPostId は comments JOIN users の1クエリで取得する。
     *
     * @param postId コメントを取得する投稿の ID
     * @return CommentResponse のリスト（古い順）
     */
    @Transactional(readOnly = true)
    public List<CommentResponse> getComments(Long postId) {
        return commentMapper.findByPostId(postId);
    }

    /**
     * 新しいコメントを作成する。
     *
     * @param postId コメント対象の投稿 ID
     * @param req コメント本文を含むリクエスト DTO
     * @param email 現在のユーザーのメールアドレス
     * @return 作成した CommentResponse
     */
    @Transactional
    public CommentResponse createComment(Long postId, CreateCommentRequest req, String email) {
        // メールアドレスからユーザーを取得して userId を確定する
        User user =
                userMapper
                        .findByEmail(email)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.UNAUTHORIZED, "ユーザーが見つかりません"));

        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setUserId(user.getId());
        comment.setContent(req.content());
        commentMapper.insert(comment);

        // INSERT 後、作成した CommentResponse をコメント一覧から取得して返す
        // comments には updated_at がないため、findById してレスポンスを組み立てる
        Comment saved =
                commentMapper
                        .findById(comment.getId())
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.INTERNAL_SERVER_ERROR,
                                                "コメントの取得に失敗しました"));

        log.info("コメント作成: commentId={}, postId={}, userId={}", saved.getId(), postId, user.getId());
        return new CommentResponse(
                saved.getId(),
                saved.getPostId(),
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                saved.getContent(),
                saved.getCreatedAt());
    }

    /**
     * コメントを編集する。コメント投稿者本人のみ実行できる。
     *
     * @param id 編集するコメントの ID
     * @param req 更新内容を含むリクエスト DTO
     * @param email 現在のユーザーのメールアドレス
     * @return 更新後の CommentResponse
     */
    @Transactional
    public CommentResponse updateComment(Long id, UpdateCommentRequest req, String email) {
        User user =
                userMapper
                        .findByEmail(email)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.UNAUTHORIZED, "ユーザーが見つかりません"));

        Comment comment =
                commentMapper
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "コメントが見つかりません"));

        // コメント投稿者本人かどうかを確認する
        if (!comment.getUserId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "このコメントを編集する権限がありません");
        }

        commentMapper.update(id, req.content());
        log.info("コメント更新: commentId={}, userId={}", id, user.getId());

        // 更新後のデータを返す（content のみ変更、他は既存の値を使う）
        return new CommentResponse(
                comment.getId(),
                comment.getPostId(),
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                req.content(),
                comment.getCreatedAt());
    }

    /**
     * コメントを削除する。コメント投稿者本人のみ実行できる。
     *
     * @param id 削除するコメントの ID
     * @param email 現在のユーザーのメールアドレス
     */
    @Transactional
    public void deleteComment(Long id, String email) {
        User user =
                userMapper
                        .findByEmail(email)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.UNAUTHORIZED, "ユーザーが見つかりません"));

        Comment comment =
                commentMapper
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "コメントが見つかりません"));

        // コメント投稿者本人かどうかを確認する
        if (!comment.getUserId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "このコメントを削除する権限がありません");
        }

        commentMapper.deleteById(id);
        log.info("コメント削除: commentId={}, userId={}", id, user.getId());
    }
}
