package com.example.smartkid.ui.courses;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.example.smartkid.R;
import com.example.smartkid.core.AppConstants;
import com.example.smartkid.core.AppLogger;
import com.example.smartkid.data.model.Course;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiError;
import com.example.smartkid.data.repository.CourseRepository;
import com.example.smartkid.ui.common.BaseActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;

/** Danh mục khóa học công khai lấy từ PostgreSQL qua API. */
public class CatalogActivity extends BaseActivity {
    private TextInputEditText searchInput;
    private Button searchButton;
    private ProgressBar progressBar;
    private TextView emptyText;
    private CourseAdapter adapter;
    private CourseRepository repository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_catalog);
            repository = new CourseRepository(this);
            MaterialToolbar toolbar = findViewById(R.id.toolbarCatalog);
            searchInput = findViewById(R.id.inputCatalogSearch);
            searchButton = findViewById(R.id.buttonCatalogSearch);
            progressBar = findViewById(R.id.progressCatalog);
            emptyText = findViewById(R.id.textCatalogEmpty);
            ListView listView = findViewById(R.id.listCatalog);
            if (toolbar == null || searchInput == null || searchButton == null
                    || progressBar == null || emptyText == null || listView == null) {
                throw new IllegalStateException("Giao diện danh mục chưa đầy đủ");
            }
            toolbar.setNavigationOnClickListener(view -> finish());
            adapter = new CourseAdapter(this);
            listView.setAdapter(adapter);
            listView.setEmptyView(emptyText);
            listView.setOnItemClickListener((parent, view, position, id) ->
                    openCourse(adapter.getItem(position)));
            searchButton.setOnClickListener(view -> loadSafely());
            searchInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (adapter != null) adapter.filter(s == null ? "" : s.toString());
                }
                @Override public void afterTextChanged(Editable s) { }
            });
            loadSafely();
        } catch (Exception exception) {
            AppLogger.error(this, "CatalogActivity", "Không thể tạo danh mục", exception);
            showErrorDialog("Không thể mở danh mục khóa học");
        }
    }

    private void loadSafely() {
        try {
            setLoading(true);
            String query = searchInput.getText() == null ? "" : searchInput.getText().toString();
            repository.loadCatalog(query, new ApiCallback<List<Course>>() {
                @Override
                public void onSuccess(List<Course> data) {
                    if (!isUsable()) return;
                    setLoading(false);
                    adapter.setCourses(data);
                    emptyText.setText(R.string.no_catalog_courses);
                }

                @Override
                public void onError(ApiError error) {
                    if (!isUsable()) return;
                    setLoading(false);
                    handleApiError(error);
                }
            });
        } catch (Exception exception) {
            AppLogger.error(this, "CatalogActivity", "Không thể tải danh mục", exception);
            setLoading(false);
            showErrorDialog("Không thể tải danh mục khóa học");
        }
    }

    private void openCourse(Course course) {
        if (course == null || course.getId().isEmpty()) {
            showErrorDialog("Khóa học không có mã hợp lệ");
            return;
        }
        try {
            Intent intent = new Intent(this, CourseDetailActivity.class);
            intent.putExtra(AppConstants.EXTRA_COURSE_ID, course.getId());
            intent.putExtra(AppConstants.EXTRA_COURSE_TITLE, course.getTitle());
            startActivity(intent);
        } catch (Exception exception) {
            AppLogger.error(this, "CatalogActivity", "Không thể mở khóa học", exception);
            showErrorDialog("Không thể mở chi tiết khóa học");
        }
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        searchButton.setEnabled(!loading);
    }

    private boolean isUsable() { return !isFinishing() && !isDestroyed(); }
}
