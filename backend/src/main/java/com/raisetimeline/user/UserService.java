package com.raisetimeline.user;

import com.raisetimeline.follow.FollowMapper;
import com.raisetimeline.post.PostMapper;
import com.raisetimeline.post.dto.PostResponse;
import com.raisetimeline.storage.FileStorageService;
import com.raisetimeline.user.dto.UpdateProfileRequest;
import com.raisetimeline.user.dto.UserProfileResponse;
import com.raisetimeline.user.dto.UserSummary;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** ユーザープロフィールの取得・更新ビジネスロジックを担当するサービスクラス。 */
@Service
public class UserService {

    private final UserMapper userMapper;
    private final FollowMapper followMapper;
    private final PostMapper postMapper;
    private final FileStorageService fileStorageService;

    /** コンストラクタ。Spring が各 Mapper と FileStorageService を自動的に注入する。 */
    public UserService(
            UserMapper userMapper,
            FollowMapper followMapper,
            PostMapper postMapper,
            FileStorageService fileStorageService) {
        this.userMapper = userMapper;
        this.followMapper = followMapper;
        this.postMapper = postMapper;
        this.fileStorageService = fileStorageService;
    }

    /**
     * ユーザー名（@handle）でプロフィールを取得する。 フォロー数・フォロワー数・フォロー状態・自分のプロフィールかどうかを含む。
     *
     * @param username プロフィールを取得したいユーザーの @handle
     * @param currentEmail 現在ログイン中のユーザーのメールアドレス
     * @return UserProfileResponse
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(String username, String currentEmail) {
        // プロフィール対象ユーザーを取得する
        User target =
                userMapper
                        .findByUsername(username)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "ユーザーが見つかりません"));

        // 現在ログイン中のユーザーを取得する（isFollowing / isOwnProfile の判定に使う）
        User currentUser =
                userMapper
                        .findByEmail(currentEmail)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.UNAUTHORIZED, "ユーザーが見つかりません"));

        long followingCount = followMapper.countFollowing(target.getId());
        long followerCount = followMapper.countFollowers(target.getId());
        boolean isFollowing = followMapper.exists(currentUser.getId(), target.getId());
        boolean isOwnProfile = currentUser.getId().equals(target.getId());

        return new UserProfileResponse(
                target.getId(),
                target.getUsername(),
                target.getDisplayName(),
                target.getAvatarUrl(),
                target.getBio(),
                followingCount,
                followerCount,
                isFollowing,
                isOwnProfile);
    }

    /**
     * ユーザープロフィールを更新する（自分のプロフィールのみ更新可）。 ユーザー名の重複チェックを自分以外に対して行う。
     *
     * @param req 更新内容（username, displayName, bio）
     * @param currentEmail 現在ログイン中のユーザーのメールアドレス
     * @return 更新後の UserProfileResponse
     */
    @Transactional
    public UserProfileResponse updateProfile(UpdateProfileRequest req, String currentEmail) {
        User user =
                userMapper
                        .findByEmail(currentEmail)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.UNAUTHORIZED, "ユーザーが見つかりません"));

        // 自分以外に同じユーザー名が存在するか確認する
        if (userMapper.existsByUsernameExcludingSelf(req.username(), user.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "このユーザー名は既に使用されています");
        }

        user.setUsername(req.username());
        user.setDisplayName(req.displayName());
        // 空文字は null に変換して保存する（DB では NULL として扱う）
        user.setBio(req.bio() == null || req.bio().isBlank() ? null : req.bio());
        userMapper.update(user);

        // 更新後のプロフィールを再取得して返す（フォロー数など最新の状態を含める）
        return getProfile(user.getUsername(), currentEmail);
    }

    /**
     * キーワードでユーザーを部分一致検索する（ユーザー検索画面用）。
     *
     * <p>username に対して大文字小文字を区別せずに部分一致検索する（PostgreSQL の ILIKE）。 自分自身は結果から除外する。isFollowing
     * フラグも1クエリで取得する。
     *
     * @param q 検索キーワード（1文字以上）
     * @param currentEmail 現在ログイン中のユーザーのメールアドレス
     * @return UserSummary のリスト（最大 20 件、ユーザー名順）
     */
    @Transactional(readOnly = true)
    public List<UserSummary> searchUsers(String q, String currentEmail) {
        User currentUser =
                userMapper
                        .findByEmail(currentEmail)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.UNAUTHORIZED, "ユーザーが見つかりません"));
        return userMapper.findByUsernameContaining(q, currentUser.getId());
    }

    /**
     * アバター画像を S3 にアップロードし、ユーザーの avatar_url を更新する。
     *
     * <p>ファイルのバリデーション（サイズ・MIME タイプ）は FileStorageService で行う。 S3 への保存が成功してから DB を更新するため、 アップロード失敗時は
     * DB が汚染されない。
     *
     * @param file アップロードする画像ファイル（JPEG または PNG、2MB 以下）
     * @param currentEmail 現在ログイン中のユーザーのメールアドレス
     * @return 更新後の UserProfileResponse
     */
    @Transactional
    public UserProfileResponse uploadAvatar(MultipartFile file, String currentEmail) {
        User user =
                userMapper
                        .findByEmail(currentEmail)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.UNAUTHORIZED, "ユーザーが見つかりません"));

        // S3 にアップロードして公開 URL を取得する
        String avatarUrl = fileStorageService.saveAvatar(file);

        // DB の avatar_url を更新する
        userMapper.updateAvatarUrl(user.getId(), avatarUrl);

        // 更新後のプロフィールを返す
        return getProfile(user.getUsername(), currentEmail);
    }

    /**
     * 指定ユーザーの投稿一覧を取得する（プロフィールページの投稿タブ用）。
     *
     * @param username 投稿を取得したいユーザーの @handle
     * @param currentEmail 現在ログイン中のユーザーのメールアドレス（liked フラグに使う）
     * @return PostResponse のリスト（新しい順）
     */
    @Transactional(readOnly = true)
    public List<PostResponse> getUserPosts(String username, String currentEmail) {
        User target =
                userMapper
                        .findByUsername(username)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "ユーザーが見つかりません"));

        return postMapper.findByUserIdOrderByCreatedAtDesc(target.getId(), currentEmail);
    }
}
