package com.example.smartkid.feature.student.profile;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.smartkid.R;
import com.example.smartkid.common.util.AppConstants;
import com.example.smartkid.common.util.AppLogger;
import com.example.smartkid.data.local.SessionManager;
import com.example.smartkid.data.model.User;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiError;
import com.example.smartkid.data.repository.AuthRepository;
import com.example.smartkid.feature.shared.auth.LoginActivity;
import com.example.smartkid.common.ui.BaseActivity;
import com.example.smartkid.feature.student.course.CatalogActivity;
import com.example.smartkid.feature.shared.notification.FeatureListActivity;
import com.example.smartkid.feature.student.ai.AITutorActivity;
import com.example.smartkid.feature.student.ai.LearningAnalysisActivity;
import com.example.smartkid.feature.shared.profile.ChangePasswordActivity;
import com.example.smartkid.feature.shared.profile.ProfileEditActivity;

public class StudentProfileFragment extends Fragment {
    private TextView avatarText;
    private TextView fullNameText;
    private TextView usernameText;
    private TextView emailText;
    private TextView classText;
    private TextView roleText;
    private TextView serverText;
    private TextView statusText;
    private ProgressBar progressBar;
    private TextView studentCodeText;
    private SwipeRefreshLayout refreshLayout;
    private View logoutButton;
    private AuthRepository repository;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.profile_fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        try {
            repository = new AuthRepository(requireContext());
            bindViews(view);
            bindUser(repository.getSessionManager().getUser());
            serverText.setText(getString(R.string.server_format, AppConstants.getApiBaseUrl()));
            refreshLayout.setOnRefreshListener(this::safelyLoadProfile);
            logoutButton.setOnClickListener(clicked -> confirmLogout());
            bindFeatureNavigation(view);
            safelyLoadProfile();
        } catch (Exception exception) {
            AppLogger.error(getContext(), "StudentProfileFragment", "Không thể tạo hồ sơ", exception);
            showStatus("Không thể khởi tạo màn hình hồ sơ");
        }
    }

    private void bindFeatureNavigation(View view) {
        View rowEdit = view.findViewById(R.id.rowEditProfile);
        if (rowEdit != null) {
            rowEdit.setOnClickListener(clicked -> openActivity(ProfileEditActivity.class));
        }
        View rowChange = view.findViewById(R.id.rowChangePassword);
        if (rowChange != null) {
            rowChange.setOnClickListener(clicked -> openActivity(ChangePasswordActivity.class));
        }
        View buttonEdit = view.findViewById(R.id.buttonEditProfile);
        if (buttonEdit != null) {
            buttonEdit.setOnClickListener(clicked -> openActivity(ProfileEditActivity.class));
        }
        View buttonPassword = view.findViewById(R.id.buttonChangePassword);
        if (buttonPassword != null) {
            buttonPassword.setOnClickListener(clicked -> openActivity(ChangePasswordActivity.class));
        }
        View buttonParent = view.findViewById(R.id.buttonParentInfo);
        if (buttonParent != null) {
            buttonParent.setOnClickListener(clicked -> openActivity(ParentActivity.class));
        }
        View buttonCat = view.findViewById(R.id.buttonCatalog);
        if (buttonCat != null) {
            buttonCat.setOnClickListener(clicked -> openActivity(CatalogActivity.class));
        }
        View buttonPath = view.findViewById(R.id.buttonLearningPath);
        if (buttonPath != null) {
            buttonPath.setOnClickListener(clicked -> openFeature(FeatureListActivity.MODE_LEARNING_PATH));
        }
        View buttonAi = view.findViewById(R.id.buttonAiTutor);
        if (buttonAi != null) {
            buttonAi.setOnClickListener(clicked -> openActivity(AITutorActivity.class));
        }
        View buttonAnalysis = view.findViewById(R.id.buttonLearningAnalysis);
        if (buttonAnalysis != null) {
            buttonAnalysis.setOnClickListener(clicked -> openActivity(LearningAnalysisActivity.class));
        }
        View buttonCerts = view.findViewById(R.id.buttonCertificates);
        if (buttonCerts != null) {
            buttonCerts.setOnClickListener(clicked -> openFeature(FeatureListActivity.MODE_CERTIFICATES));
        }
        View buttonNotif = view.findViewById(R.id.buttonNotifications);
        if (buttonNotif != null) {
            buttonNotif.setOnClickListener(clicked -> openFeature(FeatureListActivity.MODE_NOTIFICATIONS));
        }
    }

    private void openFeature(String mode) {
        if (!isAdded()) return;
        try {
            Intent intent = new Intent(requireContext(), FeatureListActivity.class);
            intent.putExtra(FeatureListActivity.EXTRA_MODE, mode);
            startActivity(intent);
        } catch (Exception exception) {
            AppLogger.error(getContext(), "StudentProfileFragment", "Không thể mở chức năng", exception);
            showStatus("Không thể mở chức năng đã chọn");
        }
    }

    private void openActivity(Class<?> destination) {
        if (!isAdded()) return;
        try {
            startActivity(new Intent(requireContext(), destination));
        } catch (Exception exception) {
            AppLogger.error(getContext(), "StudentProfileFragment", "Không thể chuyển trang", exception);
            showStatus("Không thể mở chức năng đã chọn");
        }
    }

    private void bindViews(View view) {
        avatarText = view.findViewById(R.id.textProfileAvatar);
        fullNameText = view.findViewById(R.id.textProfileName);
        usernameText = view.findViewById(R.id.textProfileUsername);
        studentCodeText = view.findViewById(R.id.textProfileStudentCode);
        emailText = view.findViewById(R.id.textProfileEmail);
        classText = view.findViewById(R.id.textProfileClass);
        roleText = view.findViewById(R.id.textProfileRole);
        serverText = view.findViewById(R.id.textProfileServer);
        statusText = view.findViewById(R.id.textProfileStatus);
        progressBar = view.findViewById(R.id.progressProfile);
        refreshLayout = view.findViewById(R.id.refreshProfile);
        logoutButton = view.findViewById(R.id.buttonLogout);

        if (avatarText == null || fullNameText == null || usernameText == null
                || emailText == null || classText == null || roleText == null
                || serverText == null || statusText == null || progressBar == null
                || logoutButton == null) {
            throw new IllegalStateException("Giao diện hồ sơ thiếu thành phần bắt buộc");
        }
    }

    private void safelyLoadProfile() {
        try {
            setLoading(true);
            repository.loadProfile(new ApiCallback<User>() {
                @Override
                public void onSuccess(User user) {
                    if (!isAdded() || getView() == null) {
                        return;
                    }
                    setLoading(false);
                    bindUser(user);
                    showStatus(getString(R.string.profile_updated));
                }

                @Override
                public void onError(ApiError error) {
                    if (!isAdded() || getView() == null) {
                        return;
                    }
                    setLoading(false);
                    if (error != null && error.isSessionExpired()
                            && getActivity() instanceof BaseActivity) {
                        ((BaseActivity) getActivity()).handleApiError(error);
                    } else {
                        showStatus(error == null
                                ? getString(R.string.unknown_error) : error.getMessage());
                    }
                }
            });
        } catch (Exception exception) {
            AppLogger.error(getContext(), "StudentProfileFragment", "Không thể tải hồ sơ", exception);
            setLoading(false);
            showStatus("Không thể tải hồ sơ");
        }
    }

    private void bindUser(User user) {
        User safeUser = user == null
                ? new User("", "", "", "", "student", "") : user;
        String displayName = safeUser.getFullName().isEmpty()
                ? getString(R.string.student_default_name) : safeUser.getFullName();
        avatarText.setText(initialOf(displayName));
        fullNameText.setText(displayName);

        String username = valueOrUpdating(safeUser.getUsername());
        usernameText.setText("Mã học sinh: " + username);
        if (studentCodeText != null) {
            studentCodeText.setText(username);
        }
        emailText.setText(valueOrUpdating(safeUser.getEmail()));
        classText.setText(valueOrUpdating(safeUser.getClassName()));
        if (roleText != null) {
            roleText.setText(safeUser.getRole().isEmpty() ? "student" : safeUser.getRole());
        }
    }

    private String initialOf(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            return "S";
        }
        return normalized.substring(0, 1).toUpperCase(java.util.Locale.getDefault());
    }

    private String valueOrUpdating(String value) {
        return value == null || value.trim().isEmpty()
                ? getString(R.string.updating) : value.trim();
    }

    private void confirmLogout() {
        if (!isAdded()) {
            return;
        }
        try {
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.logout)
                    .setMessage(R.string.logout_confirmation)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.logout, (dialog, which) -> safelyLogout())
                    .show();
        } catch (Exception exception) {
            AppLogger.error(getContext(), "StudentProfileFragment",
                    "Không thể hiện xác nhận đăng xuất", exception);
            showStatus("Không thể mở hộp thoại xác nhận");
        }
    }

    private void safelyLogout() {
        try {
            setLoading(true);
            repository.logout(new ApiCallback<Boolean>() {
                @Override
                public void onSuccess(Boolean ignored) {
                    if (!isAdded()) {
                        return;
                    }
                    openLogin();
                }

                @Override
                public void onError(ApiError error) {
                    // Repository luôn xóa phiên cục bộ; nhánh này là hàng rào an toàn cuối.
                    Context context = getContext();
                    if (context != null) {
                        new SessionManager(context).clear();
                    }
                    openLogin();
                }
            });
        } catch (Exception exception) {
            AppLogger.error(getContext(), "StudentProfileFragment", "Không thể đăng xuất", exception);
            if (isAdded()) {
                new SessionManager(requireContext()).clear();
                openLogin();
            }
        }
    }

    private void openLogin() {
        if (!isAdded() || getActivity() == null) {
            return;
        }
        try {
            Intent intent = new Intent(requireContext(), LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            requireActivity().finish();
        } catch (Exception exception) {
            AppLogger.error(getContext(), "StudentProfileFragment",
                    "Không thể quay về đăng nhập", exception);
            showStatus("Đã xóa phiên nhưng chưa thể mở màn hình đăng nhập");
        }
    }

    private void setLoading(boolean loading) {
        if (!loading && refreshLayout != null) {
            refreshLayout.setRefreshing(false);
        }
        boolean swiping = loading && refreshLayout != null && refreshLayout.isRefreshing();
        if (progressBar != null) {
            progressBar.setVisibility(loading && !swiping ? View.VISIBLE : View.GONE);
        }
        if (logoutButton != null) {
            logoutButton.setEnabled(!loading);
        }
    }

    private void showStatus(String message) {
        if (statusText != null) {
            statusText.setText(message == null ? getString(R.string.unknown_error) : message);
            statusText.setVisibility(View.VISIBLE);
        }
    }
}
