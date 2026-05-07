package com.raisetimeline.follow;

import com.raisetimeline.user.User;
import com.raisetimeline.user.UserMapper;
import com.raisetimeline.user.dto.UserSummary;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** フォロー/アンフォローのビジネスロジックを担当するサービスクラス。 */
@Service
public class FollowService {

    private static final Logger log = LoggerFactory.getLogger(FollowService.class);

    private final FollowMapper followMapper;
    private final UserMapper userMapper;

    /** コンストラクタ。Spring が各 Mapper を自動的に注入する。 */
    public FollowService(FollowMapper followMapper, UserMapper userMapper) {
        this.followMapper = followMapper;
        this.userMapper = userMapper;
    }

    /**
     * 指定ユーザーをフォローする。 自分自身へのフォロー・重複フォローはアプリケーション層で弾く。
     *
     * @param followeeId フォローするユーザーの ID
     * @param currentEmail 現在ログイン中のユーザーのメールアドレス
     */
    @Transactional
    public void follow(Long followeeId, String currentEmail) {
        User follower =
                userMapper
                        .findByEmail(currentEmail)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.UNAUTHORIZED, "ユーザーが見つかりません"));

        // フォロー対象ユーザーの存在確認
        userMapper
                .findById(followeeId)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ユーザーが見つかりません"));

        // 自分自身へのフォローは禁止する
        if (follower.getId().equals(followeeId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "自分自身をフォローすることはできません");
        }

        // 既にフォロー済みの場合は 409 Conflict を返す
        if (followMapper.exists(follower.getId(), followeeId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "既にフォロー済みです");
        }

        Follow follow = new Follow();
        follow.setFollowerId(follower.getId());
        follow.setFolloweeId(followeeId);
        followMapper.insert(follow);

        log.info("フォロー: followerId={}, followeeId={}", follower.getId(), followeeId);
    }

    /**
     * 指定ユーザーのフォローを解除する。 フォロー関係が存在しない場合は 404 を返す。
     *
     * @param followeeId アンフォローするユーザーの ID
     * @param currentEmail 現在ログイン中のユーザーのメールアドレス
     */
    @Transactional
    public void unfollow(Long followeeId, String currentEmail) {
        User follower =
                userMapper
                        .findByEmail(currentEmail)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.UNAUTHORIZED, "ユーザーが見つかりません"));

        // フォロー関係が存在しない場合は 404 を返す
        if (!followMapper.exists(follower.getId(), followeeId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "フォロー関係が見つかりません");
        }

        followMapper.delete(follower.getId(), followeeId);
        log.info("アンフォロー: followerId={}, followeeId={}", follower.getId(), followeeId);
    }

    /**
     * 指定ユーザーがフォローしているユーザー一覧を取得する。
     *
     * @param username フォロー中一覧を取得したいユーザーの @handle
     * @param currentEmail 現在ログイン中のユーザーのメールアドレス（isFollowing フラグに使う）
     * @return フォロー中ユーザーの一覧
     */
    @Transactional(readOnly = true)
    public List<UserSummary> getFollowing(String username, String currentEmail) {
        User target =
                userMapper
                        .findByUsername(username)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "ユーザーが見つかりません"));

        User currentUser =
                userMapper
                        .findByEmail(currentEmail)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.UNAUTHORIZED, "ユーザーが見つかりません"));

        return followMapper.findFollowing(target.getId(), currentUser.getId());
    }

    /**
     * 指定ユーザーをフォローしているユーザー一覧を取得する。
     *
     * @param username フォロワー一覧を取得したいユーザーの @handle
     * @param currentEmail 現在ログイン中のユーザーのメールアドレス（isFollowing フラグに使う）
     * @return フォロワーユーザーの一覧
     */
    @Transactional(readOnly = true)
    public List<UserSummary> getFollowers(String username, String currentEmail) {
        User target =
                userMapper
                        .findByUsername(username)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "ユーザーが見つかりません"));

        User currentUser =
                userMapper
                        .findByEmail(currentEmail)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.UNAUTHORIZED, "ユーザーが見つかりません"));

        return followMapper.findFollowers(target.getId(), currentUser.getId());
    }
}
