package com.example.smartkid.ui.profile;

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

import com.example.smartkid.R;
import com.example.smartkid.core.AppConstants;
import com.example.smartkid.core.AppLogger;
import com.example.smartkid.data.local.SessionManager;
import com.example.smartkid.data.model.User;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiError;
import com.example.smartkid.data.repository.AuthRepository;
import com.example.smartkid.ui.auth.LoginActivity;
import com.example.smartkid.ui.common.BaseActivity;
import com.example.smartkid.ui.courses.CatalogActivity;
import com.example.smartkid.ui.student.FeatureListActivity;
import com.example.smartkid.ui.student.PaymentActivity;
import com.example.smartkid.ui.student.AITutorActivity;
import com.example.smartkid.ui.student.CartActivity;
import com.example.smartkid.ui.student.LearningAnalysisActivity;

public class ProfileFragment extends Fragment {
    private TextView avatarText;
    private TextView fullNameText;
    private TextView usernameText;
    private TextView emailText;
    private TextView classText;
    private TextView roleText;
    private TextView serverText;
    private TextView statusText;
    private ProgressBar progressBar;
    private Button refreshButton;
    private Button logoutButton;
    private AuthRepository repository;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        try {
            repository = new AuthRepository(requireContext());
            bindViews(view);
            bindUser(repository.getSessionManager().getUser());
            serverText.setText(getString(R.string.server_format, AppConstants.API_BASE_URL));
            refreshButton.setOnClickListener(clicked -> safelyLoadProfile());
            logoutButton.setOnClickListener(clicked -> confirmLogout());
            bindFeatureNavigation(view);
            safelyLoadProfile();
        } catch (Exception exception) {
            AppLogger.error(getContext(), "ProfileFragment", "Không thể tạo hồ sơ", exception);
            showStatus("Không thể khởi tạo màn hình hồ sơ");
        }
    }

    private void bindFeatureNavigation(View view) {
        view.findViewById(R.id.buttonEditProfile).setOnClickListener(clicked ->
                openActivity(ProfileEditActivity.class));
        view.findViewById(R.id.buttonChangePassword).setOnClickListener(clicked ->
                openActivity(ChangePasswordActivity.class));
        view.findViewById(R.id.buttonParentInfo).setOnClickListener(clicked ->
                openActivity(ParentActivity.class));
        view.findViewById(R.id.buttonCatalog).setOnClickListener(clicked ->
                openActivity(CatalogActivity.class));
        view.findViewById(R.id.buttonLearningPath).setOnClickListener(clicked ->
                openFeature(FeatureListActivity.MODE_LEARNING_PATH));
        view.findViewById(R.id.buttonAiTutor).setOnClickListener(clicked ->
                openActivity(AITutorActivity.class));
        view.findViewById(R.id.buttonLearningAnalysis).setOnClickListener(clicked ->
                openActivity(LearningAnalysisActivity.class));
        view.findViewById(R.id.buttonCertificates).setOnClickListener(clicked ->
                openFeature(FeatureListActivity.MODE_CERTIFICATES));
        view.findViewById(R.id.buttonPayments).setOnClickListener(clicked ->
                openActivity(PaymentActivity.class));
        view.findViewById(R.id.buttonCart).setOnClickListener(clicked ->
                openActivity(CartActivity.class));
        view.findViewById(R.id.buttonNotifications).setOnClickListener(clicked ->
                openFeature(FeatureListActivity.MODE_NOTIFICATIONS));
    }

    private void openFeature(String mode) {
        if (!isAdded()) return;
        try {
            Intent intent = new Intent(requireContext(), FeatureListActivity.class);
            intent.putExtra(FeatureListActivity.EXTRA_MODE, mode);
            startActivity(intent);
        } catch (Exception exception) {
            AppLogger.error(getContext(), "ProfileFragment", "Không thể mở chức năng", exception);
            showStatus("Không thể mở chức năng đã chọn");
        }
    }

    private void openActivity(Class<?> destination) {
        if (!isAdded()) return;
        try {
            startActivity(new Intent(requireContext(), destination));
        } catch (Exception exception) {
            AppLogger.error(getContext(), "ProfileFragment", "Không thể chuyển trang", exception);
            showStatus("Không thể mở chức năng đã chọn");
        }
    }

    private void bindViews(View view) {
        avatarText = view.findViewById(R.id.textProfileAvatar);
        fullNameText = view.findViewById(R.id.textProfileName);
        usernameText = view.findViewById(R.id.textProfileUsername);
        emailText = view.findViewById(R.id.textProfileEmail);
        classText = view.findViewById(R.id.textProfileClass);
        roleText = view.findViewById(R.id.textProfileRole);
        serverText = view.findViewById(R.id.textProfileServer);
        statusText = view.findViewById(R.id.textProfileStatus);
        progressBar = view.findViewById(R.id.progressProfile);
        refreshButton = view.findViewById(R.id.buttonRefreshProfile);
        logoutButton = view.findViewById(R.id.buttonLogout);

        if (avatarText == null || fullNameText == null || usernameText == null
                || emailText == null || classText == null || roleText == null
                || serverText == null || statusText == null || progressBar == null
                || refreshButton == null || logoutButton == null) {
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
            AppLogger.error(getContext(), "ProfileFragment", "Không thể tải hồ sơ", exception);
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
        usernameText.setText(getString(R.string.username_format,
                valueOrUpdating(safeUser.getUsername())));
        emailText.setText(getString(R.string.email_format,
                valueOrUpdating(safeUser.getEmail())));
        classText.setText(getString(R.string.class_format,
                valueOrUpdating(safeUser.getClassName())));
        roleText.setText(getString(R.string.role_format,
                safeUser.getRole().isEmpty() ? "student" : safeUser.getRole()));
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
            AppLogger.error(getContext(), "ProfileFragment",
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
                    new SessionManager(requireContext()).clear();
                    openLogin();
                }
            });
        } catch (Exception exception) {
            AppLogger.error(getContext(), "ProfileFragment", "Không thể đăng xuất", exception);
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
            AppLogger.error(getContext(), "ProfileFragment",
                    "Không thể quay về đăng nhập", exception);
            showStatus("Đã xóa phiên nhưng chưa thể mở màn hình đăng nhập");
        }
    }

    private void setLoading(boolean loading) {
        if (progressBar != null) {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        if (refreshButton != null) {
            refreshButton.setEnabled(!loading);
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
