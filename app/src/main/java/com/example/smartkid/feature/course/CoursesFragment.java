package com.example.smartkid.feature.course;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.smartkid.R;
import com.example.smartkid.common.util.AppConstants;
import com.example.smartkid.common.util.AppLogger;
import com.example.smartkid.data.model.Course;
import com.example.smartkid.data.model.CourseListResult;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiError;
import com.example.smartkid.data.repository.CourseRepository;
import com.example.smartkid.common.ui.BaseActivity;

public class CoursesFragment extends Fragment {
    private ProgressBar progressBar;
    private EditText searchInput;
    private Button sortButton;
    private Button refreshButton;
    private ListView listView;
    private TextView emptyText;
    private TextView noticeText;
    private CourseAdapter adapter;
    private CourseRepository repository;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.course_fragment_courses, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        try {
            repository = new CourseRepository(requireContext());
            bindViews(view);
            adapter = new CourseAdapter(requireContext());
            listView.setAdapter(adapter);
            listView.setEmptyView(emptyText);
            listView.setOnItemClickListener((parent, row, position, id) ->
                    openCourse(adapter.getItem(position)));
            sortButton.setOnClickListener(clicked -> toggleSort());
            refreshButton.setOnClickListener(clicked -> safeLoadCourses());
            searchInput.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence text, int start, int count, int after) {
                    // Không cần xử lý trước khi thay đổi.
                }

                @Override
                public void onTextChanged(CharSequence text, int start, int before, int count) {
                    try {
                        adapter.filter(text == null ? "" : text.toString());
                    } catch (Exception exception) {
                        AppLogger.error(getContext(), "CoursesFragment", "Không thể tìm kiếm", exception);
                    }
                }

                @Override
                public void afterTextChanged(Editable editable) {
                    // Không cần xử lý sau khi thay đổi.
                }
            });
            safeLoadCourses();
        } catch (Exception exception) {
            AppLogger.error(getContext(), "CoursesFragment", "Không thể tạo danh sách khóa học", exception);
            showErrorState("Không thể khởi tạo danh sách khóa học");
        }
    }

    @Override
    public void onDestroyView() {
        if (repository != null) {
            repository.close();
        }
        super.onDestroyView();
    }

    private void bindViews(View view) {
        progressBar = view.findViewById(R.id.progressCourses);
        searchInput = view.findViewById(R.id.inputSearchCourse);
        sortButton = view.findViewById(R.id.buttonSortCourses);
        refreshButton = view.findViewById(R.id.buttonRefreshCourses);
        listView = view.findViewById(R.id.listCourses);
        emptyText = view.findViewById(R.id.textCoursesEmpty);
        noticeText = view.findViewById(R.id.textCourseNotice);
    }

    private void safeLoadCourses() {
        try {
            loadCourses();
        } catch (Exception exception) {
            AppLogger.error(getContext(), "CoursesFragment", "Không thể tải khóa học", exception);
            showErrorState("Không thể tải khóa học");
        }
    }

    private void loadCourses() {
        setLoading(true);
        repository.loadMyCourses(new ApiCallback<CourseListResult>() {
            @Override
            public void onSuccess(CourseListResult result) {
                if (!isAdded() || getView() == null) {
                    return;
                }
                setLoading(false);
                adapter.setCourses(result.getCourses());
                emptyText.setText(result.getCourses().isEmpty()
                        ? R.string.no_courses : R.string.no_matching_courses);
                if (result.getNotice().isEmpty()) {
                    noticeText.setVisibility(View.GONE);
                } else {
                    noticeText.setText(result.getNotice());
                    noticeText.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(ApiError error) {
                if (!isAdded() || getView() == null) {
                    return;
                }
                setLoading(false);
                if (error.isSessionExpired() && getActivity() instanceof BaseActivity) {
                    ((BaseActivity) getActivity()).handleApiError(error);
                } else {
                    showErrorState(error.getMessage());
                }
            }
        });
    }

    private void toggleSort() {
        try {
            boolean ascending = adapter.toggleSort();
            sortButton.setText(ascending ? R.string.sort_az : R.string.sort_za);
        } catch (Exception exception) {
            AppLogger.error(getContext(), "CoursesFragment", "Không thể sắp xếp", exception);
        }
    }

    private void openCourse(Course course) {
        if (course == null || course.getId().isEmpty()) {
            showErrorState("Khóa học này không có mã hợp lệ");
            return;
        }
        try {
            Intent intent = new Intent(requireContext(), CourseDetailActivity.class);
            intent.putExtra(AppConstants.EXTRA_COURSE_ID, course.getId());
            intent.putExtra(AppConstants.EXTRA_COURSE_TITLE, course.getTitle());
            startActivity(intent);
        } catch (Exception exception) {
            AppLogger.error(getContext(), "CoursesFragment", "Không thể mở khóa học", exception);
            showErrorState("Không thể mở chi tiết khóa học");
        }
    }

    private void setLoading(boolean loading) {
        if (progressBar != null) {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        if (refreshButton != null) {
            refreshButton.setEnabled(!loading);
        }
    }

    private void showErrorState(String message) {
        if (emptyText != null) {
            emptyText.setText(message);
            emptyText.setVisibility(View.VISIBLE);
        }
        if (noticeText != null) {
            noticeText.setText(message);
            noticeText.setVisibility(View.VISIBLE);
        }
    }
}
