package com.example.smartkid.feature.student.course;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.example.smartkid.R;
import com.example.smartkid.common.util.AppConstants;
import com.example.smartkid.common.util.AppLogger;
import com.example.smartkid.common.util.SafeJson;
import com.example.smartkid.data.local.SessionManager;
import com.example.smartkid.data.model.FeatureItem;
import com.example.smartkid.data.model.LessonContent;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiError;
import com.example.smartkid.data.repository.CourseRepository;
import com.example.smartkid.common.ui.BaseActivity;
import com.example.smartkid.feature.student.ai.AITutorActivity;
import com.example.smartkid.feature.student.course.LessonDiscussionActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.json.JSONObject;

public class LessonPlayerActivity extends BaseActivity {
    /** Chế độ xem trước cho giáo viên: phát nội dung bài học, ẩn mọi thao tác học viên. */
    public static final String EXTRA_PREVIEW_MODE = "extra_lesson_preview";

    private MaterialToolbar toolbar;
    private ProgressBar loadingView;
    private TextView typeText;
    private TextView contentText;
    private TextView statusText;
    private android.view.TextureView videoView;
    private View videoContainer;
    private ImageView videoPlayButton;
    private ImageView videoToggleButton;
    private android.widget.SeekBar videoSeekBar;
    private TextView videoTimeText;
    private View videoControls;
    private final android.os.Handler videoProgressHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable videoProgressTick = new Runnable() {
        @Override
        public void run() {
            updateVideoProgress();
            videoProgressHandler.postDelayed(this, 500);
        }
    };
    private boolean userSeeking;
    private TextView videoSpeedButton;
    private float playbackSpeed = 1.0f;

    // ===== TRẠNG THÁI XEM TOÀN MÀN HÌNH =====
    private android.widget.FrameLayout fullscreenContainer;   // lớp phủ che kín màn hình
    private boolean fullscreen;
    private android.view.ViewGroup videoHomeParent;           // chỗ cũ của khung video trong trang
    private int videoHomeIndex;
    private android.view.ViewGroup.LayoutParams videoHomeParams;
    private int lastVideoWidth;                               // kích thước gốc của video, để tính lại tỉ lệ
    private int lastVideoHeight;
    private int resumePositionMs;                             // chỗ đang xem, giữ khi đổi chế độ
    private boolean resumeWasPlaying = true;
    private static final float[] SPEED_OPTIONS = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
    private android.media.MediaPlayer mediaPlayer;
    private String pendingVideoUrl;
    private WebView webVideo;
    private View webVideoContainer;
    private YouTubePlayerView youtubePlayerView;
    private View exercisesCard;
    private LinearLayout exercisesContainer;
    private View lessonPrevCard;
    private View lessonNextCard;
    private View lessonNavigationRow;
    private View lessonNavigationSpacer;
    private TextView introText;
    // Danh sách bài học của khóa, dùng để biết bài trước / bài sau là bài nào.
    private final java.util.List<com.example.smartkid.data.model.Lesson> courseLessons =
            new java.util.ArrayList<>();
    private Button completeButton;
    private CourseRepository repository;
    private LessonContent lessonContent;
    private String courseId;
    private String lessonId;
    private boolean previewMode;
    private boolean youtubeFallbackTried;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.course_activity_lesson_player);
            courseId = getIntent().getStringExtra(AppConstants.EXTRA_COURSE_ID);
            lessonId = getIntent().getStringExtra(AppConstants.EXTRA_LESSON_ID);
            previewMode = getIntent().getBooleanExtra(EXTRA_PREVIEW_MODE, false);
            if (!previewMode && (courseId == null || courseId.trim().isEmpty())) {
                showErrorDialog("Không tìm thấy khóa học của bài học");
                return;
            }
            if (previewMode && (lessonId == null || lessonId.trim().isEmpty())) {
                showErrorDialog("Không tìm thấy bài học cần xem trước");
                return;
            }
            repository = new CourseRepository(this);
            bindViews();
            toolbar.setNavigationOnClickListener(view -> finish());
            String title = getIntent().getStringExtra(AppConstants.EXTRA_LESSON_TITLE);
            toolbar.setTitle(title == null ? getString(R.string.lesson_content) : title);
            lessonPrevCard.setOnClickListener(view -> openSiblingLesson(-1));
            lessonNextCard.setOnClickListener(view -> openSiblingLesson(1));
            findViewById(R.id.buttonLessonVideoFullscreen)
                    .setOnClickListener(view -> toggleFullscreen());
            completeButton.setOnClickListener(view -> markCompleted(false));
            findViewById(R.id.buttonLessonAiTutor).setOnClickListener(view -> openAiTutor());
            findViewById(R.id.buttonLessonDiscussion).setOnClickListener(view -> openDiscussion());
            if (previewMode) {
                completeButton.setVisibility(View.GONE);
                findViewById(R.id.buttonLessonAiTutor).setVisibility(View.GONE);
                findViewById(R.id.buttonLessonDiscussion).setVisibility(View.GONE);
            }
            updateLessonNavigation();   // ẩn sẵn 2 nút chuyển bài cho tới khi biết bài này nằm ở đâu
            loadLesson();
            loadCourseLessons();        // thiếu lời gọi này thì danh sách bài luôn rỗng
        } catch (Exception exception) {
            AppLogger.error(this, "LessonPlayerActivity", "Không thể tạo trình phát bài học", exception);
            showErrorDialog("Không thể mở nội dung bài học: " + exception.getMessage());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Rời màn rồi quay lại: surface cũ đã bị hủy nên phải dựng lại trình phát.
        if (mediaPlayer == null && pendingVideoUrl != null) {
            prepareVideo(pendingVideoUrl);
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if (!previewMode && repository != null && lessonId != null && !lessonId.trim().isEmpty()) {
            loadExercises();
        }
    }

    @Override
    protected void onDestroy() {
        try {
            releasePlayer();
            if (webVideo != null) {
                webVideo.loadUrl("about:blank");
                webVideo.stopLoading();
                webVideo.destroy();
            }
            if (youtubePlayerView != null) {
                youtubePlayerView.release();
            }
            if (repository != null) {
                repository.close();
            }
        } catch (Exception exception) {
            AppLogger.error(this, "LessonPlayerActivity", "Không thể giải phóng multimedia", exception);
        }
        super.onDestroy();
    }

    private void bindViews() {
        toolbar = findViewById(R.id.toolbarLessonPlayer);
        loadingView = findViewById(R.id.progressLessonLoading);
        typeText = findViewById(R.id.textLessonContentType);
        contentText = findViewById(R.id.textLessonContent);
        statusText = findViewById(R.id.textLessonContentStatus);
        videoView = findViewById(R.id.videoLesson);
        videoContainer = findViewById(R.id.containerLessonVideo);
        videoPlayButton = findViewById(R.id.buttonLessonVideoPlay);
        videoToggleButton = findViewById(R.id.buttonLessonVideoToggle);
        videoSeekBar = findViewById(R.id.seekLessonVideo);
        videoTimeText = findViewById(R.id.textLessonVideoTime);
        videoControls = findViewById(R.id.barLessonVideoControls);
        videoSpeedButton = findViewById(R.id.buttonLessonVideoSpeed);
        fullscreenContainer = findViewById(R.id.containerLessonFullscreen);
        bindVideoControls();
        webVideo = findViewById(R.id.webLessonVideo);
        webVideoContainer = findViewById(R.id.containerLessonWebVideo);
        youtubePlayerView = findViewById(R.id.youtubeLessonPlayer);
        getLifecycle().addObserver(youtubePlayerView);
        exercisesCard = findViewById(R.id.cardLessonExercises);
        exercisesContainer = findViewById(R.id.containerLessonExercises);
        lessonPrevCard = findViewById(R.id.buttonLessonPrev);
        lessonNextCard = findViewById(R.id.buttonLessonNext);
        lessonNavigationRow = findViewById(R.id.rowLessonNavigation);
        lessonNavigationSpacer = findViewById(R.id.spacerLessonNavigation);
        introText = findViewById(R.id.textLessonIntro);
        // 4 thẻ thao tác dùng cùng một layout -> nạp icon và chữ cho từng thẻ
        bindActionCard(findViewById(R.id.buttonLessonDiscussion), R.drawable.role_ic_question,
                R.string.lesson_discussion, R.string.lesson_discussion_hint);
        bindActionCard(findViewById(R.id.buttonLessonAiTutor), R.drawable.ai_ic_tutor,
                R.string.ai_tutor, R.string.ai_tutor_hint);
        bindActionCard(lessonPrevCard, R.drawable.course_ic_arrow_left,
                R.string.lesson_prev, R.string.lesson_prev_hint);
        bindActionCard(lessonNextCard, R.drawable.course_ic_arrow_right,
                R.string.lesson_next, R.string.lesson_next_hint);
        completeButton = findViewById(R.id.buttonCompleteLesson);
    }

    private void loadLesson() {
        setLoading(true);
        if (previewMode) {
            repository.loadLessonPreview(lessonId, new ApiCallback<LessonContent>() {
                @Override
                public void onSuccess(LessonContent data) {
                    if (isFinishing() || isDestroyed()) return;
                    setLoading(false);
                    lessonContent = data;
                    bindContent(data);
                }

                @Override
                public void onError(ApiError error) {
                    if (isFinishing() || isDestroyed()) return;
                    setLoading(false);
                    handleApiError(error);
                }
            });
            return;
        }
        if (lessonId == null || lessonId.trim().isEmpty()) {
            loadLessonContent();
            return;
        }
        repository.checkLessonUnlock(lessonId, new ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject data) {
                if (isFinishing() || isDestroyed()) return;
                if (!SafeJson.bool(data, true, "can_unlock")) {
                    setLoading(false);
                    statusText.setText(SafeJson.string(data,
                            getString(R.string.lesson_locked), "reason", "detail"));
                    completeButton.setEnabled(false);
                    return;
                }
                loadLessonContent();
            }

            @Override
            public void onError(ApiError error) {
                if (isFinishing() || isDestroyed()) return;
                // Player backend van kiem tra enrollment; khong chan bai neu endpoint unlock loi.
                loadLessonContent();
            }
        });
    }

    private void loadLessonContent() {
        repository.loadLesson(courseId, lessonId, new ApiCallback<LessonContent>() {
            @Override
            public void onSuccess(LessonContent data) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                setLoading(false);
                lessonContent = data;
                lessonId = data.getId().isEmpty() ? lessonId : data.getId();
                bindContent(data);
                loadExercises();
            }

            @Override
            public void onError(ApiError error) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                setLoading(false);
                handleApiError(error);
            }
        });
    }

    private void bindContent(LessonContent content) {
        toolbar.setTitle(content.getTitle());
        typeText.setText(getString(R.string.content_type_format,
                contentTypeLabel(content.getContentType())));
        // Thẻ giới thiệu: dòng đầu là phần giới thiệu bài, dòng dưới là nội dung văn bản.
        String intro = content.getTextContent();
        if (introText != null) {
            introText.setText(content.getTitle());
            introText.setVisibility(content.getTitle().isEmpty() ? View.GONE : View.VISIBLE);
        }
        contentText.setText(intro.isEmpty() ? getString(R.string.no_text_content) : intro);
        if (previewMode) {
            statusText.setText("Chế độ xem trước của giáo viên");
        } else {
            statusText.setText(content.isCompleted()
                    ? R.string.lesson_completed : R.string.lesson_not_completed);
            completeButton.setEnabled(!content.isCompleted());
        }

        videoContainer.setVisibility(View.GONE);
        webVideo.setVisibility(View.GONE);
        webVideoContainer.setVisibility(View.GONE);
        youtubePlayerView.setVisibility(View.GONE);

        String videoUrl = content.getVideoUrl();
        if (videoUrl.isEmpty()) {
            return;
        }
        String youtubeId = youtubeVideoId(videoUrl);
        if (!youtubeId.isEmpty()) {
            prepareYoutube(youtubeId);
        } else if (isWebEmbed(videoUrl)) {
            prepareEmbeddedVideo(videoUrl);
        } else {
            prepareVideo(videoUrl);
        }
    }

    private void prepareYoutube(String videoId) {
        try {
            youtubeFallbackTried = false;
            youtubePlayerView.setVisibility(View.VISIBLE);
            IFramePlayerOptions options = new IFramePlayerOptions.Builder()
                    .controls(1)
                    .rel(0)
                    .build();
            youtubePlayerView.initialize(new AbstractYouTubePlayerListener() {
                @Override
                public void onReady(YouTubePlayer youTubePlayer) {
                    youTubePlayer.cueVideo(videoId, 0f);
                }

                @Override
                public void onError(YouTubePlayer youTubePlayer,
                                    PlayerConstants.PlayerError error) {
                    youtubePlayerView.setVisibility(View.GONE);
                    if (!youtubeFallbackTried) {
                        // Trình phát thư viện bị chặn: thử nhúng lại bằng WebView
                        // (nhiều video chỉ chặn iframe của thư viện, không chặn trang nhúng).
                        youtubeFallbackTried = true;
                        statusText.setText(R.string.video_retry_embed);
                        prepareEmbeddedVideo("https://www.youtube.com/watch?v=" + videoId);
                        return;
                    }
                    statusText.setText(R.string.video_embed_blocked);
                }
            }, options);
        } catch (Exception exception) {
            AppLogger.error(this, "LessonPlayerActivity",
                    "Không thể mở trình phát YouTube", exception);
            youtubePlayerView.setVisibility(View.GONE);
        }
    }

    private void prepareEmbeddedVideo(String videoUrl) {
        try {
            WebSettings settings = webVideo.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setMediaPlaybackRequiresUserGesture(false);
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
            webVideo.setWebChromeClient(new WebChromeClient());
            webVideo.setWebViewClient(new WebViewClient());
            webVideoContainer.setVisibility(View.VISIBLE);
            webVideo.setVisibility(View.VISIBLE);
            String embedUrl = toEmbedUrl(videoUrl);
            String host = embedUrl.contains("vimeo.com")
                    ? "https://player.vimeo.com" : "https://www.youtube.com";
            webVideo.loadDataWithBaseURL(host, embedHtml(embedUrl),
                    "text/html", "utf-8", null);
        } catch (Exception exception) {
            AppLogger.error(this, "LessonPlayerActivity",
                    "Không thể nhúng video web", exception);
            webVideoContainer.setVisibility(View.GONE);
            webVideo.setVisibility(View.GONE);
        }
    }

    private void prepareVideo(String videoUrl) {
        try {
            releasePlayer();
            pendingVideoUrl = videoUrl;
            statusText.setText(R.string.video_loading);
            applyVideoFrameSize();
            videoContainer.setVisibility(View.VISIBLE);
            videoPlayButton.setVisibility(View.GONE);
            if (videoControls != null) videoControls.setVisibility(View.GONE);
            // Đăng ký lắng nghe TRƯỚC rồi mới kiểm tra, tránh trường hợp surface
            // sẵn sàng đúng lúc giữa hai lệnh khiến không ai khởi động trình phát.
            videoView.setSurfaceTextureListener(new android.view.TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(android.graphics.SurfaceTexture texture,
                                                      int width, int height) {
                    startPlayer(videoUrl, new android.view.Surface(texture));
                }

                @Override
                public void onSurfaceTextureSizeChanged(android.graphics.SurfaceTexture texture,
                                                        int width, int height) { }

                @Override
                public boolean onSurfaceTextureDestroyed(android.graphics.SurfaceTexture texture) {
                    releasePlayer();
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(android.graphics.SurfaceTexture texture) { }
            });
            if (videoView.isAvailable() && videoView.getSurfaceTexture() != null) {
                startPlayer(videoUrl, new android.view.Surface(videoView.getSurfaceTexture()));
                return;
            }
            // Lưới an toàn: có máy không gọi lại onSurfaceTextureAvailable khi quay lại
            // màn hình, khiến video kẹt mãi ở "Đang tải video…". Sau 1,2 giây mà vẫn
            // chưa phát thì tự khởi động lại.
            videoView.postDelayed(this::retryPendingVideo, 1200);
        } catch (Exception exception) {
            AppLogger.error(this, "LessonPlayerActivity", "Không thể phát video", exception);
            statusText.setText(R.string.cannot_play_video);
        }
    }

    /** Khởi động lại video nếu surface đã sẵn sàng nhưng trình phát chưa chạy. */
    private void retryPendingVideo() {
        if (isFinishing() || isDestroyed()) return;
        if (mediaPlayer != null || pendingVideoUrl == null) return;
        if (videoView == null || !videoView.isAvailable()
                || videoView.getSurfaceTexture() == null) {
            videoView.postDelayed(this::retryPendingVideo, 1200);
            return;
        }
        startPlayer(pendingVideoUrl, new android.view.Surface(videoView.getSurfaceTexture()));
    }

    /** Phát video bằng MediaPlayer gắn vào TextureView đã sẵn sàng. */
    private void startPlayer(String videoUrl, android.view.Surface surface) {
        try {
            android.media.MediaPlayer player = new android.media.MediaPlayer();
            mediaPlayer = player;
            player.setSurface(surface);
            player.setAudioAttributes(new android.media.AudioAttributes.Builder()
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE)
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .build());
            // Chỉ gửi token cho backend của mình; CDN ngoài không cần header lạ.
            Map<String, String> headers = new HashMap<>();
            if (isOwnBackend(videoUrl)) {
                String accessToken = new SessionManager(this).getAccessToken();
                if (!accessToken.isEmpty()) {
                    headers.put("Authorization", "Bearer " + accessToken);
                }
            }
            player.setDataSource(this, Uri.parse(videoUrl), headers);
            player.setOnVideoSizeChangedListener((mediaPlayer, width, height) ->
                    fitVideoToFrame(width, height));
            player.setOnPreparedListener(prepared -> {
                if (isFinishing() || isDestroyed()) return;
                fitVideoToFrame(prepared.getVideoWidth(), prepared.getVideoHeight());
                statusText.setText(previewMode ? "Chế độ xem trước của giáo viên"
                        : (lessonContent != null && lessonContent.isCompleted()
                        ? getString(R.string.lesson_completed)
                        : getString(R.string.lesson_not_completed)));
                // Vào/ra toàn màn hình làm hủy surface nên phải dựng lại trình phát;
                // tua về đúng chỗ đang xem để học sinh không phải xem lại từ đầu.
                if (resumePositionMs > 0) {
                    prepared.seekTo(resumePositionMs);
                    resumePositionMs = 0;
                }
                prepared.start();
                if (!resumeWasPlaying) {
                    prepared.pause();
                    videoPlayButton.setVisibility(View.VISIBLE);
                }
                resumeWasPlaying = true;
                if (playbackSpeed != 1.0f) applySpeed(playbackSpeed);
                videoPlayButton.setVisibility(View.GONE);
                if (videoControls != null) videoControls.setVisibility(View.VISIBLE);
                videoProgressHandler.removeCallbacks(videoProgressTick);
                videoProgressHandler.post(videoProgressTick);
            });
            player.setOnCompletionListener(done -> {
                videoPlayButton.setVisibility(View.VISIBLE);
                markCompleted(true);
            });
            player.setOnErrorListener((failed, what, extra) -> {
                statusText.setText(getString(R.string.video_error_code, what, extra));
                    return true;
            });
            player.prepareAsync();
            // Bấm vào khung video để tạm dừng / phát tiếp.
            videoContainer.setOnClickListener(view -> togglePlayback());
            videoPlayButton.setOnClickListener(view -> togglePlayback());
        } catch (Exception exception) {
            AppLogger.error(this, "LessonPlayerActivity", "Không thể mở video", exception);
            statusText.setText(R.string.cannot_play_video);
        }
    }

    /** Thanh điều khiển: phát/dừng, lùi/tiến 10 giây, kéo tua. */
    private void bindVideoControls() {
        if (videoToggleButton == null || videoSeekBar == null) return;
        videoToggleButton.setOnClickListener(view -> togglePlayback());
        if (videoSpeedButton != null) {
            videoSpeedButton.setOnClickListener(view -> showSpeedMenu());
        }
        findViewById(R.id.buttonLessonVideoRewind).setOnClickListener(view -> seekBy(-10000));
        findViewById(R.id.buttonLessonVideoForward).setOnClickListener(view -> seekBy(10000));
        videoSeekBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar bar, int value, boolean fromUser) {
                if (fromUser && mediaPlayer != null && videoDurationMs() > 0) {
                    videoTimeText.setText(getString(R.string.video_time_format,
                            clock((long) videoDurationMs() * value / bar.getMax()),
                            clock(videoDurationMs())));
                }
            }

            @Override
            public void onStartTrackingTouch(android.widget.SeekBar bar) {
                userSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(android.widget.SeekBar bar) {
                userSeeking = false;
                if (mediaPlayer == null || videoDurationMs() <= 0) return;
                try {
                    mediaPlayer.seekTo((int) ((long) videoDurationMs() * bar.getProgress() / bar.getMax()));
                } catch (IllegalStateException ignored) {
                    // Trình phát chưa sẵn sàng.
                }
            }
        });
    }

    /** Menu chọn tốc độ phát: 0.5x đến 2x. */
    private void showSpeedMenu() {
        String[] labels = new String[SPEED_OPTIONS.length];
        for (int index = 0; index < SPEED_OPTIONS.length; index++) {
            labels[index] = getString(R.string.video_speed_format, trimSpeed(SPEED_OPTIONS[index]))
                    + (SPEED_OPTIONS[index] == 1.0f ? " (bình thường)" : "");
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.video_speed_title)
                .setItems(labels, (dialog, which) -> applySpeed(SPEED_OPTIONS[which]))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void applySpeed(float speed) {
        playbackSpeed = speed;
        if (videoSpeedButton != null) {
            videoSpeedButton.setText(getString(R.string.video_speed_format, trimSpeed(speed)));
        }
        if (mediaPlayer == null) return;
        try {
            boolean wasPlaying = mediaPlayer.isPlaying();
            mediaPlayer.setPlaybackParams(
                    mediaPlayer.getPlaybackParams().setSpeed(speed));
            // setPlaybackParams tự chuyển sang trạng thái phát; giữ đúng ý người dùng.
            if (!wasPlaying) mediaPlayer.pause();
        } catch (Exception exception) {
            AppLogger.error(this, "LessonPlayerActivity", "Không đổi được tốc độ phát", exception);
            showShortMessage("Thiết bị không hỗ trợ đổi tốc độ phát");
        }
    }

    /** 1.0 -> "1", 1.25 -> "1.25" cho nhãn gọn. */
    private String trimSpeed(float speed) {
        return speed == Math.rint(speed)
                ? String.valueOf((int) speed)
                : String.valueOf(speed).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private void seekBy(int deltaMs) {
        if (mediaPlayer == null) return;
        try {
            int target = Math.max(0, Math.min(videoDurationMs(),
                    mediaPlayer.getCurrentPosition() + deltaMs));
            mediaPlayer.seekTo(target);
            updateVideoProgress();
        } catch (IllegalStateException ignored) {
            // Bỏ qua khi trình phát chưa sẵn sàng.
        }
    }

    private int videoDurationMs() {
        try {
            return mediaPlayer == null ? 0 : Math.max(0, mediaPlayer.getDuration());
        } catch (IllegalStateException ignored) {
            return 0;
        }
    }

    private void updateVideoProgress() {
        if (mediaPlayer == null || videoSeekBar == null || videoTimeText == null) return;
        try {
            int duration = videoDurationMs();
            int position = mediaPlayer.getCurrentPosition();
            if (duration > 0 && !userSeeking) {
                videoSeekBar.setProgress((int) ((long) position * videoSeekBar.getMax() / duration));
            }
            videoTimeText.setText(getString(R.string.video_time_format,
                    clock(position), clock(duration)));
            videoToggleButton.setImageResource(mediaPlayer.isPlaying()
                    ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
        } catch (IllegalStateException ignored) {
            // Trình phát vừa được giải phóng.
        }
    }

    /** 125000ms -> "2:05" (hoặc "1:02:05" nếu dài hơn 1 giờ). */
    private String clock(long milliseconds) {
        long totalSeconds = Math.max(0, milliseconds) / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return hours > 0
                ? String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
                : String.format(Locale.US, "%d:%02d", minutes, seconds);
    }

    private void togglePlayback() {
        if (mediaPlayer == null) return;
        try {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                videoPlayButton.setVisibility(View.VISIBLE);
            } else {
                mediaPlayer.start();
                videoPlayButton.setVisibility(View.GONE);
            }
        } catch (IllegalStateException ignored) {
            // Trình phát chưa sẵn sàng: bỏ qua thao tác.
        }
    }

    private void releasePlayer() {
        videoProgressHandler.removeCallbacks(videoProgressTick);
        if (mediaPlayer == null) return;
        try {
            mediaPlayer.reset();
            mediaPlayer.release();
        } catch (Exception exception) {
            AppLogger.error(this, "LessonPlayerActivity", "Không thể giải phóng trình phát", exception);
        }
        mediaPlayer = null;
    }

    /** URL trỏ về backend của app (không phải CDN/bên thứ ba)? */
    private boolean isOwnBackend(String url) {
        try {
            String host = Uri.parse(url).getHost();
            String apiHost = Uri.parse(AppConstants.getApiBaseUrl()).getHost();
            return host != null && apiHost != null && host.equalsIgnoreCase(apiHost);
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Nạp icon, tiêu đề và dòng mô tả cho một thẻ thao tác. */
    /** "video" -> "Video", "text" -> "Văn bản"... cho nhãn loại nội dung. */
    private String contentTypeLabel(String type) {
        switch (type == null ? "" : type) {
            case "video": return "Video";
            case "text": return "Văn bản";
            case "pdf": return "Tài liệu PDF";
            case "document": return "Tài liệu";
            case "exercise": return "Bài luyện tập";
            case "": return "Văn bản";
            default: return type;
        }
    }

    private void bindActionCard(View card, int iconRes, int titleRes, int subtitleRes) {
        if (card == null) return;
        ((ImageView) card.findViewById(R.id.iconLessonAction)).setImageResource(iconRes);
        ((TextView) card.findViewById(R.id.textLessonActionTitle)).setText(titleRes);
        ((TextView) card.findViewById(R.id.textLessonActionSubtitle)).setText(subtitleRes);
    }

    /** Bấm nút phóng to: vào hoặc thoát chế độ xem toàn màn hình. */
    private void toggleFullscreen() {
        if (fullscreen) exitFullscreen();
        else enterFullscreen();
    }

    /**
     * Vào toàn màn hình: xoay ngang, ẩn thanh trạng thái và thanh điều hướng, rồi
     * GỠ khung video khỏi trang và gắn vào lớp phủ che kín màn hình. Trước đây hàm này
     * chỉ xoay ngang nên tiêu đề bài học vẫn còn và video vẫn nằm trong khung nhỏ.
     */
    private void enterFullscreen() {
        if (fullscreen || videoContainer == null || fullscreenContainer == null) return;
        android.view.ViewGroup parent = (android.view.ViewGroup) videoContainer.getParent();
        if (parent == null) return;

        videoHomeParent = parent;
        videoHomeIndex = parent.indexOfChild(videoContainer);
        videoHomeParams = videoContainer.getLayoutParams();

        rememberPlaybackPosition();
        parent.removeView(videoContainer);
        fullscreenContainer.addView(videoContainer, new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        fullscreenContainer.setVisibility(View.VISIBLE);

        // Bỏ bo góc và nền khung để video áp sát mép màn hình
        videoContainer.setBackground(null);
        videoContainer.setClipToOutline(false);
        stretchVideoView(android.view.ViewGroup.LayoutParams.MATCH_PARENT);

        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        showSystemBars(false);
        fullscreen = true;
        refitVideo();
    }

    /** Thoát toàn màn hình: trả khung video về đúng vị trí cũ trong trang. */
    private void exitFullscreen() {
        if (!fullscreen) return;
        fullscreen = false;

        if (videoContainer != null && fullscreenContainer != null && videoHomeParent != null) {
            rememberPlaybackPosition();
            fullscreenContainer.removeView(videoContainer);
            videoContainer.setBackgroundResource(R.drawable.course_bg_video_frame);
            videoContainer.setClipToOutline(true);
            if (videoHomeParams != null) videoContainer.setLayoutParams(videoHomeParams);
            videoHomeParent.addView(videoContainer, videoHomeIndex);
        }
        if (fullscreenContainer != null) fullscreenContainer.setVisibility(View.GONE);

        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        showSystemBars(true);
        applyVideoFrameSize();
        refitVideo();
    }

    /** Lưu chỗ đang xem và trạng thái phát trước khi surface bị hủy. */
    private void rememberPlaybackPosition() {
        if (mediaPlayer == null) return;
        try {
            resumePositionMs = mediaPlayer.getCurrentPosition();
            resumeWasPlaying = mediaPlayer.isPlaying();
        } catch (IllegalStateException ignored) {
            // Trình phát đã bị giải phóng, không có gì để lưu.
        }
    }

    /** Ẩn/hiện thanh trạng thái và thanh điều hướng của hệ thống. */
    private void showSystemBars(boolean show) {
        androidx.core.view.WindowInsetsControllerCompat controller =
                androidx.core.view.WindowCompat.getInsetsController(
                        getWindow(), getWindow().getDecorView());
        if (show) {
            controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars());
        } else {
            controller.setSystemBarsBehavior(androidx.core.view.WindowInsetsControllerCompat
                    .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars());
        }
    }

    /** Đặt chiều cao cho TextureView (số pixel cụ thể hoặc MATCH_PARENT khi toàn màn hình). */
    private void stretchVideoView(int height) {
        if (videoView == null) return;
        android.view.ViewGroup.LayoutParams params = videoView.getLayoutParams();
        params.height = height;
        videoView.setLayoutParams(params);
    }

    /** Tính lại ma trận sau khi khung đổi kích thước (xoay ngang, vào/ra toàn màn hình). */
    private void refitVideo() {
        if (videoView == null || lastVideoWidth <= 0 || lastVideoHeight <= 0) return;
        videoView.post(() -> fitVideoToFrame(lastVideoWidth, lastVideoHeight));
    }

    /** Nút Back khi đang toàn màn hình thì chỉ thoát toàn màn hình, không rời bài học. */
    @Override
    public void onBackPressed() {
        if (fullscreen) {
            exitFullscreen();
            return;
        }
        super.onBackPressed();
    }

    /** Xoay máy khi đang toàn màn hình: đo lại khung cho khớp kích thước mới. */
    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (!fullscreen) applyVideoFrameSize();
        refitVideo();
    }

    // ===== KHUNG VIDEO =====

    /** Khung video rộng hết màn hình, cao theo tỉ lệ 16:9 để không bị hụt hay thừa. */
    private void applyVideoFrameSize() {
        if (videoView == null || videoContainer == null) return;
        if (fullscreen) {                                  // toàn màn hình thì để video chiếm hết
            stretchVideoView(android.view.ViewGroup.LayoutParams.MATCH_PARENT);
            return;
        }
        int width = getResources().getDisplayMetrics().widthPixels
                - (int) (28 * getResources().getDisplayMetrics().density);  // trừ padding 14dp mỗi bên
        stretchVideoView(Math.round(width * 9f / 16f));
    }

    /**
     * TextureView mặc định kéo giãn video cho vừa khung nên hình bị méo. Hàm này
     * tính lại ma trận biến đổi để video giữ đúng tỉ lệ và lấp đầy khung
     * (phần thừa hai bên hoặc trên dưới bị cắt nhẹ, giống trình phát thường thấy).
     */
    private void fitVideoToFrame(int videoWidth, int videoHeight) {
        if (videoView == null || videoWidth <= 0 || videoHeight <= 0) return;
        lastVideoWidth = videoWidth;
        lastVideoHeight = videoHeight;
        int viewWidth = videoView.getWidth();
        int viewHeight = videoView.getHeight();
        if (viewWidth <= 0 || viewHeight <= 0) {
            videoView.post(() -> fitVideoToFrame(videoWidth, videoHeight));
            return;
        }
        float scaleX = (float) viewWidth / videoWidth;
        float scaleY = (float) viewHeight / videoHeight;
        // Trong trang: cắt nhẹ cho lấp đầy khung 16:9. Toàn màn hình: thu vừa để
        // không cắt mất chữ trên video (màn điện thoại thường dài hơn 16:9).
        float scale = fullscreen ? Math.min(scaleX, scaleY) : Math.max(scaleX, scaleY);
        float drawWidth = videoWidth * scale;
        float drawHeight = videoHeight * scale;

        android.graphics.Matrix matrix = new android.graphics.Matrix();
        matrix.setScale(drawWidth / viewWidth, drawHeight / viewHeight);
        matrix.postTranslate((viewWidth - drawWidth) / 2f, (viewHeight - drawHeight) / 2f);
        videoView.setTransform(matrix);
        videoView.invalidate();
    }

    // ===== CHUYỂN GIỮA CÁC BÀI HỌC =====

    /** Tải danh sách bài học của khóa để biết bài trước / bài sau. */
    private void loadCourseLessons() {
        if (previewMode || courseId == null || courseId.trim().isEmpty()) {
            updateLessonNavigation();
            return;
        }
        repository.loadCourseDetail(courseId,
                new ApiCallback<com.example.smartkid.data.model.CourseDetail>() {
                    @Override
                    public void onSuccess(com.example.smartkid.data.model.CourseDetail data) {
                        if (isFinishing() || isDestroyed()) return;
                        courseLessons.clear();
                        if (data != null && data.getLessons() != null) {
                            courseLessons.addAll(data.getLessons());
                        }
                        updateLessonNavigation();
                    }

                    @Override
                    public void onError(ApiError error) {
                        if (isFinishing() || isDestroyed()) return;
                        updateLessonNavigation();  // không tải được thì ẩn nút chuyển bài
                    }
                });
    }

    /** Vị trí bài đang xem trong danh sách, -1 nếu chưa xác định được. */
    private int currentLessonIndex() {
        if (lessonId == null) return -1;
        for (int index = 0; index < courseLessons.size(); index++) {
            if (lessonId.equals(courseLessons.get(index).getId())) return index;
        }
        return -1;
    }

    /** Chỉ ẩn ở hai đầu khóa; bài tiếp bị khóa vẫn hiện để giải thích khi học sinh bấm. */
    private void updateLessonNavigation() {
        if (lessonPrevCard == null || lessonNextCard == null) return;
        int index = currentLessonIndex();
        boolean hasPrev = !previewMode && index > 0;
        boolean hasNext = !previewMode && index >= 0
                && index < courseLessons.size() - 1;

        lessonPrevCard.setVisibility(hasPrev ? View.VISIBLE : View.GONE);
        lessonNextCard.setVisibility(hasNext ? View.VISIBLE : View.GONE);
        // Khoảng cách giữa hai thẻ chỉ cần khi cả hai cùng hiện.
        if (lessonNavigationSpacer != null) {
            lessonNavigationSpacer.setVisibility(hasPrev && hasNext ? View.VISIBLE : View.GONE);
        }
        // Không còn thẻ nào thì bỏ luôn cả hàng để không chừa khoảng trống.
        if (lessonNavigationRow != null) {
            lessonNavigationRow.setVisibility(hasPrev || hasNext ? View.VISIBLE : View.GONE);
        }
    }

    /** Mở bài liền trước (step = -1) hoặc liền sau (step = 1) trong cùng khóa học. */
    private void openSiblingLesson(int step) {
        int index = currentLessonIndex();
        if (index < 0) return;
        int target = index + step;
        if (target < 0) {
            showShortMessage(getString(R.string.lesson_is_first));
            return;
        }
        if (target >= courseLessons.size()) {
            showShortMessage(getString(R.string.lesson_is_last));
            return;
        }
        if (!isLessonUnlocked(target)) {
            showShortMessage(getString(R.string.lesson_locked));
            return;
        }
        com.example.smartkid.data.model.Lesson lesson = courseLessons.get(target);
        try {
            Intent intent = new Intent(this, LessonPlayerActivity.class);
            intent.putExtra(AppConstants.EXTRA_COURSE_ID, courseId);
            intent.putExtra(AppConstants.EXTRA_LESSON_ID, lesson.getId());
            intent.putExtra(AppConstants.EXTRA_LESSON_TITLE, lesson.getTitle());
            startActivity(intent);
            finish();  // không xếp chồng vô hạn khi chuyển nhiều bài liên tiếp
        } catch (Exception exception) {
            AppLogger.error(this, "LessonPlayerActivity", "Không thể mở bài học khác", exception);
            showErrorDialog("Không thể mở bài học kế tiếp");
        }
    }

    private boolean isLessonUnlocked(int targetIndex) {
        if (targetIndex < 0 || targetIndex >= courseLessons.size()) {
            return false;
        }
        for (int index = 0; index < targetIndex; index++) {
            if (!courseLessons.get(index).isCompleted()) {
                return false;
            }
        }
        return true;
    }

    private void openExternalContent() {
        if (lessonContent == null) {
            showShortMessage("Nội dung chưa sẵn sàng");
            return;
        }
        String url = preferredExternalUrl(lessonContent);
        if (url.isEmpty()) {
            showShortMessage("Bài học không có liên kết ngoài");
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            if (intent.resolveActivity(getPackageManager()) == null) {
                showErrorDialog("Thiết bị không có ứng dụng phù hợp để mở nội dung này");
                return;
            }
            startActivity(intent);
        } catch (Exception exception) {
            AppLogger.error(this, "LessonPlayerActivity", "Không thể mở nội dung ngoài", exception);
            showErrorDialog("Liên kết nội dung không hợp lệ");
        }
    }

    private void openAiTutor() {
        try {
            Intent intent = new Intent(this, AITutorActivity.class);
            intent.putExtra(AppConstants.EXTRA_LESSON_ID, lessonId);
            intent.putExtra(AppConstants.EXTRA_LESSON_TITLE,
                    lessonContent == null ? toolbar.getTitle() : lessonContent.getTitle());
            startActivity(intent);
        } catch (Exception exception) {
            AppLogger.error(this, "LessonPlayerActivity", "Không thể mở AI Tutor", exception);
            showErrorDialog("Không thể mở AI Tutor");
        }
    }

    private void openDiscussion() {
        if (lessonId == null || lessonId.trim().isEmpty()) {
            showShortMessage("Nội dung bài học chưa sẵn sàng");
            return;
        }
        try {
            Intent intent = new Intent(this, LessonDiscussionActivity.class);
            intent.putExtra(AppConstants.EXTRA_LESSON_ID, lessonId);
            intent.putExtra(AppConstants.EXTRA_LESSON_TITLE,
                    lessonContent == null ? toolbar.getTitle() : lessonContent.getTitle());
            startActivity(intent);
        } catch (Exception exception) {
            AppLogger.error(this, "LessonPlayerActivity", "Không thể mở hỏi đáp", exception);
            showErrorDialog("Không thể mở hỏi đáp bài học");
        }
    }

    private void markCompleted(boolean silent) {
        if (previewMode) return;
        if (lessonId == null || lessonId.trim().isEmpty()) {
            if (!silent) {
                showErrorDialog("Không tìm thấy mã bài học để cập nhật");
            }
            return;
        }
        completeButton.setEnabled(false);
        repository.markLessonCompleted(lessonId, new ApiCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean completed) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                // Bật lại nút: xem hết video sẽ tự gọi markCompleted(true), nếu không
                // bật lại thì học sinh không bấm được nút Đánh dấu hoàn thành nữa.
                completeButton.setEnabled(true);
                statusText.setText(Boolean.TRUE.equals(completed)
                        ? R.string.lesson_completed : R.string.progress_saved);
                if (!silent) {
                    showShortMessage("Đã lưu tiến độ bài học");
                }
                if (Boolean.TRUE.equals(completed)) {
                    loadCourseLessons();
                }
            }

            @Override
            public void onError(ApiError error) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                completeButton.setEnabled(true);
                if (!silent) {
                    handleApiError(error);
                } else {
                    statusText.setText(error.getMessage());
                }
            }
        });
    }

    private String preferredExternalUrl(LessonContent content) {
        if (!content.getDocumentUrl().isEmpty()) {
            return content.getDocumentUrl();
        }
        if (isWebEmbed(content.getVideoUrl())) {
            return content.getVideoUrl();
        }
        return content.getVideoUrl();
    }

    private String youtubeVideoId(String value) {
        String source = value == null ? "" : value.trim();
        if (source.isEmpty()) return "";
        try {
            Uri uri = Uri.parse(source);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (host.contains("youtu.be")) {
                String id = uri.getLastPathSegment();
                return id == null ? "" : id;
            }
            if (host.contains("youtube.com")) {
                String path = uri.getPath() == null ? "" : uri.getPath();
                if (path.contains("/embed/") || path.contains("/v/")) {
                    String id = uri.getLastPathSegment();
                    return id == null ? "" : id;
                }
                String id = uri.getQueryParameter("v");
                if ((id == null || id.isEmpty()) && uri.getPathSegments().size() > 1
                        && "shorts".equals(uri.getPathSegments().get(0))) {
                    id = uri.getPathSegments().get(1);
                }
                return id == null ? "" : id;
            }
        } catch (Exception exception) {
            AppLogger.error(this, "LessonPlayerActivity",
                    "Không thể tách mã video YouTube", exception);
        }
        return "";
    }

    private boolean isWebEmbed(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        return normalized.contains("youtube.com") || normalized.contains("youtu.be")
                || normalized.contains("vimeo.com");
    }

    private String toEmbedUrl(String value) {
        String source = value == null ? "" : value.trim();
        try {
            Uri uri = Uri.parse(source);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (host.contains("youtu.be")) {
                String id = uri.getLastPathSegment();
                if (id != null && !id.isEmpty()) return youtubeEmbed(id);
            }
            if (host.contains("youtube.com")) {
                if (uri.getPath() != null && uri.getPath().contains("/embed/")) return source;
                String id = uri.getQueryParameter("v");
                if ((id == null || id.isEmpty()) && uri.getPathSegments().size() > 1) {
                    List<String> parts = uri.getPathSegments();
                    if ("shorts".equals(parts.get(0))) id = parts.get(1);
                }
                if (id != null && !id.isEmpty()) return youtubeEmbed(id);
            }
            if (host.contains("vimeo.com")) {
                String id = uri.getLastPathSegment();
                if (id != null && !id.isEmpty()) {
                    return "https://player.vimeo.com/video/" + id;
                }
            }
        } catch (Exception exception) {
            AppLogger.error(this, "LessonPlayerActivity",
                    "Không thể chuẩn hóa URL video", exception);
        }
        return source;
    }

    private String youtubeEmbed(String id) {
        return "https://www.youtube.com/embed/" + id
                + "?playsinline=1&rel=0&modestbranding=1";
    }

    private String embedHtml(String embedUrl) {
        return "<!DOCTYPE html><html><head>"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<style>*{margin:0;padding:0}html,body{background:#000;height:100%;overflow:hidden}"
                + ".wrap{position:relative;width:100%;height:100%}"
                + ".wrap iframe{position:absolute;top:0;left:0;width:100%;height:100%;border:0}</style>"
                + "</head><body><div class=\"wrap\"><iframe src=\"" + embedUrl + "\" "
                + "allow=\"accelerometer;autoplay;encrypted-media;gyroscope;picture-in-picture\" "
                + "allowfullscreen></iframe></div></body></html>";
    }

    private void loadExercises() {
        if (lessonId == null || lessonId.trim().isEmpty()) return;
        repository.loadLessonExercises(lessonId, new ApiCallback<List<FeatureItem>>() {
            @Override
            public void onSuccess(List<FeatureItem> data) {
                if (isFinishing() || isDestroyed()) return;
                exercisesContainer.removeAllViews();
                exercisesCard.setVisibility(data == null || data.isEmpty()
                        ? View.GONE : View.VISIBLE);
                if (data == null) return;
                LayoutInflater inflater = LayoutInflater.from(LessonPlayerActivity.this);
                for (FeatureItem exercise : data) {
                    View row = inflater.inflate(R.layout.course_item_lesson_exercise,
                            exercisesContainer, false);
                    ((TextView) row.findViewById(R.id.textLessonExerciseTitle))
                            .setText(exercise.getTitle());
                    String meta = exercise.getSubtitle();
                    if (!exercise.getDetail().isEmpty()) meta += " • " + exercise.getDetail();
                    ((TextView) row.findViewById(R.id.textLessonExerciseMeta)).setText(meta);
                    row.setOnClickListener(view -> openExercise(exercise));
                    exercisesContainer.addView(row);
                }
            }

            @Override
            public void onError(ApiError error) {
                if (isFinishing() || isDestroyed()) return;
                exercisesCard.setVisibility(View.GONE);
            }
        });
    }

    private void openExercise(FeatureItem exercise) {
        if (exercise == null || exercise.getId().isEmpty()) return;
        try {
            Intent intent = new Intent(this, LessonExerciseActivity.class);
            intent.putExtra(LessonExerciseActivity.EXTRA_EXERCISE_ID, exercise.getId());
            intent.putExtra(LessonExerciseActivity.EXTRA_EXERCISE_TITLE, exercise.getTitle());
            intent.putExtra(LessonExerciseActivity.EXTRA_LESSON_ID, lessonId);
            startActivity(intent);
        } catch (Exception exception) {
            AppLogger.error(this, "LessonPlayerActivity",
                    "Không thể mở bài luyện tập", exception);
            showErrorDialog(getString(R.string.lesson_exercise_open_error));
        }
    }

    private void setLoading(boolean loading) {
        loadingView.setVisibility(loading ? View.VISIBLE : View.GONE);
        completeButton.setEnabled(!loading && lessonContent != null
                && !lessonContent.isCompleted());
    }
}
