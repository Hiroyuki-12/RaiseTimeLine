package com.raisetimeline.post;

import com.raisetimeline.post.dto.CreatePostRequest;
import com.raisetimeline.post.dto.PostResponse;
import com.raisetimeline.post.dto.UpdatePostRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * ポストの CRUD に関する HTTP リクエストを受け付けるコントローラー。 SecurityConfig の anyRequest().authenticated() により、
 * すべてのエンドポイントで JWT 認証が必須になる。
 */
@Tag(name = "Post", description = "投稿の作成・取得・更新・削除")
@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;

    /** コンストラクタ。Spring が PostService を自動的に注入する。 */
    public PostController(PostService postService) {
        this.postService = postService;
    }

    /**
     * タイムライン取得エンドポイント（ページネーション付き）。 GET /api/posts?page=0&size=20 ポストを新しい順で返す。クエリパラメータで取得範囲を指定する。
     *
     * @param page 0 始まりのページ番号（デフォルト: 0）
     * @param size 1 ページあたりの件数（デフォルト: 20）
     * @return 200 OK + PostResponse のリスト
     */
    @Operation(
            summary = "タイムライン取得",
            description =
                    "投稿一覧をページネーション付きで返す。timeline=following でフォロー中ユーザーの投稿のみ取得できる。"
                            + "各投稿には isLiked / likeCount / commentCount を含む。")
    @ApiResponse(responseCode = "200", description = "取得成功")
    @GetMapping
    public ResponseEntity<List<PostResponse>> getTimeline(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "all") String timeline) {
        // liked フラグの判定に現在ユーザーのメールアドレスが必要なため SecurityContextHolder から取得する
        String email =
                (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        // timeline="following" のときはフォロー中ユーザーの投稿のみ返す
        return ResponseEntity.ok(postService.getPage(page, size, email, timeline));
    }

    /**
     * 投稿詳細取得エンドポイント。 GET /api/posts/{id} いいね数・コメント数・liked フラグを含む PostResponse を返す。
     *
     * @param id パスパラメータ（取得するポストの ID）
     * @return 200 OK + PostResponse
     */
    @Operation(summary = "投稿詳細取得", description = "ID 指定で投稿1件を取得する。")
    @ApiResponse(responseCode = "200", description = "取得成功")
    @ApiResponse(responseCode = "404", description = "投稿が存在しない")
    @GetMapping("/{id}")
    public ResponseEntity<PostResponse> getPost(@PathVariable Long id) {
        String email =
                (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(postService.getById(id, email));
    }

    /**
     * 新着件数確認エンドポイント（ポーリング用）。 GET /api/posts/new-count?since=2024-01-01T00:00:00 フロントエンドが 30
     * 秒ごとに呼び出して新着投稿の有無を確認する。
     *
     * @param since この日時より後に作成された投稿件数を返す（ISO_LOCAL_DATE_TIME 形式）
     * @return 200 OK + { "count": N }
     */
    @Operation(
            summary = "新着件数取得",
            description = "since 以降に作成された投稿の件数を返す。フロントエンドが30秒ごとにポーリングして「新着あり」バッジを表示するために使う。")
    @ApiResponse(responseCode = "200", description = "取得成功")
    @GetMapping("/new-count")
    public ResponseEntity<Map<String, Long>> getNewCount(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since) {
        long count = postService.countNewerThan(since);
        return ResponseEntity.ok(Map.of("count", count));
    }

    /**
     * ポスト作成エンドポイント。 POST /api/posts テキストと任意の画像ファイルを受け取り、投稿を作成する。
     *
     * <p>multipart/form-data で送信する。"content" フィールドにテキスト、"image" フィールドに画像ファイルを入れる。
     * 画像は任意（指定しない場合は画像なし投稿になる）。
     *
     * @param content 投稿テキスト（1〜280文字）
     * @param image 添付画像ファイル（任意、JPEG または PNG、2MB 以下）
     * @return 201 Created + 作成した PostResponse
     */
    @Operation(
            summary = "投稿作成",
            description =
                    "テキスト (1〜280文字) と任意の画像を multipart/form-data で受け取り投稿を作成する。"
                            + "画像は JPEG / PNG・2MB 以下。S3 に保存され、URL が PostResponse.imageUrl に入る。")
    @ApiResponse(responseCode = "201", description = "作成成功")
    @ApiResponse(responseCode = "400", description = "バリデーションエラー（文字数超過・画像形式不正など）")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PostResponse> createPost(
            @RequestParam("content") String content,
            @RequestParam(value = "image", required = false) MultipartFile image) {
        // content のバリデーション（1〜280 文字）
        if (content == null || content.isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "投稿内容を入力してください");
        }
        if (content.length() > 280) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "投稿内容は280文字以内にしてください");
        }
        // JwtAuthFilter が SecurityContextHolder にセットしたメールアドレスを取得する
        String email =
                (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        // CreatePostRequest に変換してサービスに渡す
        CreatePostRequest request = new CreatePostRequest(content);
        PostResponse response = postService.createPost(request, image, email);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * ポスト編集エンドポイント。 PUT /api/posts/{id} 投稿者本人のみ編集できる。他ユーザーが試みた場合は 403 を返す。
     *
     * @param id パスパラメータ（編集するポストの ID）
     * @param request 更新内容を含むリクエスト DTO（@Valid でバリデーション実行）
     * @return 200 OK + 更新後の PostResponse
     */
    @Operation(summary = "投稿更新", description = "投稿者本人のみ編集可能。他ユーザーが試みた場合は 403 を返す。")
    @ApiResponse(responseCode = "200", description = "更新成功")
    @ApiResponse(responseCode = "403", description = "投稿者本人ではない")
    @ApiResponse(responseCode = "404", description = "投稿が存在しない")
    @PutMapping("/{id}")
    public ResponseEntity<PostResponse> updatePost(
            @PathVariable Long id, @Valid @RequestBody UpdatePostRequest request) {
        String email =
                (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        PostResponse response = postService.updatePost(id, request, email);
        return ResponseEntity.ok(response);
    }

    /**
     * ポスト削除エンドポイント。 DELETE /api/posts/{id} 投稿者本人のみ削除できる。他ユーザーが試みた場合は 403 を返す。
     *
     * @param id パスパラメータ（削除するポストの ID）
     * @return 204 No Content
     */
    @Operation(summary = "投稿削除", description = "投稿者本人のみ削除可能。他ユーザーが試みた場合は 403 を返す。")
    @ApiResponse(responseCode = "204", description = "削除成功")
    @ApiResponse(responseCode = "403", description = "投稿者本人ではない")
    @ApiResponse(responseCode = "404", description = "投稿が存在しない")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePost(@PathVariable Long id) {
        String email =
                (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        postService.deletePost(id, email);
        return ResponseEntity.noContent().build();
    }
}
