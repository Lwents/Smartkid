package com.example.smartkid.feature.course;

import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
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
import com.example.smartkid.feature.ai.AITutorActivity;
import com.example.smartkid.feature.course.LessonDiscussionActivity;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.json.JSONObject;

public class LessonPlayerActivity extends BaseActivity implements TextToSpeech.OnInitListener {
    private MaterialToolbar toolbar;
    private ProgressBar loadingView;
    private TextView typeText;
    private TextView contentText;
    private TextView statusText;
    private VideoView videoView;
    private WebView webVideo;
    private View webVideoContainer;
    private View exercisesCard;
    private LinearLayout exercisesContainer;
    private Button openExternalButton;
    private Button speakButton;
    private Button completeButton;
    private CourseRepository repository;
    private TextToSpeech textToSpeech;
    private boolean ttsReady;
    private LessonContent lessonContent;
    private String courseId;
    private String lessonId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.course_activity_lesson_player);
            courseId = getIntent().getStringExtra(AppConstants.EXTRA_COURSE_ID);
            lessonId = getIntent().getStringExtra(AppConstants.EXTRA_LESSON_ID);
            if (courseId == null || courseId.trim().isEmpty()) {
                showErrorDialog("Không tìm thấy khóa học của bài học");
                return;
            }
            repository = new CourseRepository(this);
            bindViews();
            toolbar.setNavigationOnClickListener(view -> finish());
            String title = getIntent().getStringExtra(AppConstants.EXTRA_LESSON_TITLE);
            toolbar.setTitle(title == null ? getString(R.string.lesson_content) : title);
            openExternalButton.setOnClickListener(view -> openExternalContent());
            speakButton.setOnClickListener(view -> speakLesson());
            completeButton.setOnClickListener(view -> markCompleted(false));
            findViewById(R.id.buttonLessonAiTutor).setOnClickListener(view -> openAiTutor());
            findViewById(R.id.buttonLessonDiscussion).setOnClickListener(view -> openDiscussion());
            textToSpeech = new TextToSpeech(this, this);
            loadLesson();
        } catch (Exception exception) {
            AppLogger.error(this, "LessonPlayerActivity", "Không thể tạo trình phát bài học", exception);
            showErrorDialog("Không thể mở nội dung bài học: " + exception.getMessage());
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if (repository != null && lessonId != null && !lessonId.trim().isEmpty()) {
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
            if (textToSpeech != null) {
                textToSpeech.stop();
                textToSpeech.shutdown();
            }
            if (repository != null) {
                repository.close();
            }
        } catch (Exception exception) {
            AppLogger.error(this, "LessonPlayerActivity", "Không thể giải phóng multimedia", exception);
        }
        super.onDestroy();
    }

    @Override
    public void onInit(int status) {
        try {
            if (status == TextToSpeech.SUCCESS && textToSpeech != null) {
                int result = textToSpeech.setLanguage(new Locale("vi", "VN"));
                ttsReady = result != TextToSpeech.LANG_MISSING_DATA
                        && result != TextToSpeech.LANG_NOT_SUPPORTED;
            } else {
                ttsReady = false;
            }
            if (speakButton != null) {
                speakButton.setEnabled(ttsReady && lessonContent != null);
            }
        } catch (Exception exception) {
            AppLogger.error(this, "LessonPlayerActivity", "Không thể khởi tạo đọc văn bản", exception);
            ttsReady = false;
        }
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
        exercisesCard = findViewById(R.id.cardLessonExercises);
        exercisesContainer = findViewById(R.id.containerLessonExercises);
        openExternalButton = findViewById(R.id.buttonOpenExternal);
        speakButton = findViewById(R.id.buttonSpeakLesson);
        completeButton = findViewById(R.id.buttonCompleteLesson);
    }

    private void loadLesson() {
        setLoading(true);
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
        statusText.setText(content.isCompleted()
                ? R.string.lesson_completed : R.string.lesson_not_completed);
        completeButton.setEnabled(!content.isCompleted());
        speakButton.setEnabled(ttsReady && !content.getTextContent().isEmpty());

        String externalUrl = preferredExternalUrl(content);
        openExternalButton.setVisibility(externalUrl.isEmpty() ? View.GONE : View.VISIBLE);
        videoView.setVisibility(View.GONE);
        webVideo.setVisibility(View.GONE);
        webVideoContainer.setVisibility(View.GONE);

        if (!content.getVideoUrl().isEmpty()) {
            if (isWebEmbed(content.getVideoUrl())) prepareEmbeddedVideo(content.getVideoUrl());
            else prepareVideo(content.getVideoUrl());
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
            webVideo.loadUrl(toEmbedUrl(videoUrl));
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

    private void speakLesson() {
        if (!ttsReady || textToSpeech == null || lessonContent == null) {
            showShortMessage("Chức năng đọc văn bản chưa sẵn sàng");
            return;
        }
        String text = lessonContent.getTextContent();
        if (text.isEmpty()) {
            showShortMessage("Bài học không có văn bản để đọc");
            return;
        }
        try {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null,
                    "smartkid_lesson_" + lessonContent.getId());
        } catch (Exception exception) {
            AppLogger.error(this, "LessonPlayerActivity", "Không thể đọc bài học", exception);
            showErrorDialog("Thiết bị không thể đọc nội dung này");
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
