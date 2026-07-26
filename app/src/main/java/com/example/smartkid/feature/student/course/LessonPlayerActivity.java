package com.example.smartkid.feature.student.course;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.VideoView;
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
    private VideoView videoView;
    private WebView webVideo;
    private View webVideoContainer;
    private YouTubePlayerView youtubePlayerView;
    private View exercisesCard;
    private LinearLayout exercisesContainer;
    private Button openExternalButton;
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
            openExternalButton.setOnClickListener(view -> openExternalContent());
            completeButton.setOnClickListener(view -> markCompleted(false));
            findViewById(R.id.buttonLessonAiTutor).setOnClickListener(view -> openAiTutor());
            findViewById(R.id.buttonLessonDiscussion).setOnClickListener(view -> openDiscussion());
            if (previewMode) {
                completeButton.setVisibility(View.GONE);
                findViewById(R.id.buttonLessonAiTutor).setVisibility(View.GONE);
                findViewById(R.id.buttonLessonDiscussion).setVisibility(View.GONE);
            }
            loadLesson();
        } catch (Exception exception) {
            AppLogger.error(this, "LessonPlayerActivity", "Không thể tạo trình phát bài học", exception);
            showErrorDialog("Không thể mở nội dung bài học: " + exception.getMessage());
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
            if (videoView != null) {
                videoView.stopPlayback();
            }
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
        webVideo = findViewById(R.id.webLessonVideo);
        webVideoContainer = findViewById(R.id.containerLessonWebVideo);
        youtubePlayerView = findViewById(R.id.youtubeLessonPlayer);
        getLifecycle().addObserver(youtubePlayerView);
        exercisesCard = findViewById(R.id.cardLessonExercises);
        exercisesContainer = findViewById(R.id.containerLessonExercises);
        openExternalButton = findViewById(R.id.buttonOpenExternal);
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
                content.getContentType().isEmpty() ? "text" : content.getContentType()));
        contentText.setText(content.getTextContent().isEmpty()
                ? getString(R.string.no_text_content) : content.getTextContent());
        if (previewMode) {
            statusText.setText("Chế độ xem trước của giáo viên");
        } else {
            statusText.setText(content.isCompleted()
                    ? R.string.lesson_completed : R.string.lesson_not_completed);
            completeButton.setEnabled(!content.isCompleted());
        }

        String externalUrl = preferredExternalUrl(content);
        openExternalButton.setVisibility(externalUrl.isEmpty() ? View.GONE : View.VISIBLE);
        videoView.setVisibility(View.GONE);
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
                    openExternalButton.setVisibility(View.VISIBLE);
                }
            }, options);
        } catch (Exception exception) {
            AppLogger.error(this, "LessonPlayerActivity",
                    "Không thể mở trình phát YouTube", exception);
            youtubePlayerView.setVisibility(View.GONE);
            openExternalButton.setVisibility(View.VISIBLE);
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
            openExternalButton.setVisibility(View.VISIBLE);
        }
    }

    private void prepareVideo(String videoUrl) {
        try {
            Uri uri = Uri.parse(videoUrl);
            MediaController controller = new MediaController(this);
            controller.setAnchorView(videoView);
            videoView.setMediaController(controller);
            Map<String, String> headers = new HashMap<>();
            String accessToken = new SessionManager(this).getAccessToken();
            if (!accessToken.isEmpty()) {
                headers.put("Authorization", "Bearer " + accessToken);
            }
            videoView.setVideoURI(uri, headers);
            videoView.setVisibility(View.VISIBLE);
            videoView.setOnPreparedListener(player -> {
                player.setOnVideoSizeChangedListener((mediaPlayer, width, height) ->
                        controller.setAnchorView(videoView));
                videoView.start();
            });
            videoView.setOnCompletionListener(player -> markCompleted(true));
            videoView.setOnErrorListener((player, what, extra) -> {
                statusText.setText(getString(R.string.video_error_code, what, extra));
                openExternalButton.setVisibility(View.VISIBLE);
                return true;
            });
        } catch (Exception exception) {
            AppLogger.error(this, "LessonPlayerActivity", "Không thể phát video", exception);
            statusText.setText(R.string.cannot_play_video);
            openExternalButton.setVisibility(View.VISIBLE);
        }
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
                statusText.setText(Boolean.TRUE.equals(completed)
                        ? R.string.lesson_completed : R.string.progress_saved);
                if (!silent) {
                    showShortMessage("Đã lưu tiến độ bài học");
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
