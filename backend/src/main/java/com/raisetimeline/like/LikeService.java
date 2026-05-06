package com.raisetimeline.like;

import com.raisetimeline.user.User;
import com.raisetimeline.user.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** いいねの追加・削除ビジネスロジックを担当するサービスクラス。 1ユーザー1投稿1いいねの制約チェックと、存在確認をここで行う。 */
@Service
public class LikeService {

    private static final Logger log = LoggerFactory.getLogger(LikeService.class);

    private final LikeMapper likeMapper;
    private final UserMapper userMapper;

    /** コンストラクタ。Spring が LikeMapper と UserMapper を自動的に注入する。 */
    public LikeService(LikeMapper likeMapper, UserMapper userMapper) {
        this.likeMapper = likeMapper;
        this.userMapper = userMapper;
    }

    /**
     * いいねを追加する。 既にいいね済みの場合は 409 Conflict を返す（DB の UNIQUE 制約でも防ぐが、明示的なエラーレスポンスのため先に確認する）。
     *
     * @param postId いいね対象の投稿 ID
     * @param email 現在のユーザーのメールアドレス
     */
    @Transactional
    public void addLike(Long postId, String email) {
        User user =
                userMapper
                        .findByEmail(email)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.UNAUTHORIZED, "ユーザーが見つかりません"));

        // 既にいいね済みの場合はエラー（DB の ON CONFLICT DO NOTHING だけでは 409 を返せないため先に確認）
        if (likeMapper.exists(postId, user.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "既にいいね済みです");
        }

        Like like = new Like();
        like.setPostId(postId);
        like.setUserId(user.getId());
        likeMapper.insert(like);
        log.info("いいね追加: postId={}, userId={}", postId, user.getId());
    }

    /**
     * いいねを取り消す。 いいねが存在しない場合は 404 Not Found を返す。
     *
     * @param postId いいね対象の投稿 ID
     * @param email 現在のユーザーのメールアドレス
     */
    @Transactional
    public void removeLike(Long postId, String email) {
        User user =
                userMapper
                        .findByEmail(email)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.UNAUTHORIZED, "ユーザーが見つかりません"));

        // いいねが存在しない場合はエラー
        if (!likeMapper.exists(postId, user.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "いいねが見つかりません");
        }

        likeMapper.delete(postId, user.getId());
        log.info("いいね削除: postId={}, userId={}", postId, user.getId());
    }
}
