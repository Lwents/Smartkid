package com.example.smartkid.feature.game;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.smartkid.R;
import com.example.smartkid.common.util.AppLogger;
import com.example.smartkid.data.model.FeatureItem;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiError;
import com.example.smartkid.data.repository.GameRepository;
import com.example.smartkid.common.ui.BaseActivity;
import com.example.smartkid.common.ui.FeatureItemAdapter;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;

public class GamesFragment extends Fragment {
    private ProgressBar progressBar;
    private TextView emptyText;
    private View refreshButton;
    private FeatureItemAdapter adapter;
    private GameRepository repository;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.common_fragment_feature_items, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        try {
            repository = new GameRepository(requireContext());
            progressBar = view.findViewById(R.id.progressFragmentFeatures);
            emptyText = view.findViewById(R.id.textFragmentFeaturesEmpty);
            refreshButton = view.findViewById(R.id.buttonFragmentFeaturesRefresh);
            TextInputEditText search = view.findViewById(R.id.inputFragmentFeatureSearch);
            ListView list = view.findViewById(R.id.listFragmentFeatures);
            if (progressBar == null || emptyText == null || refreshButton == null
                    || search == null || list == null) {
                throw new IllegalStateException("Giao diện trò chơi chưa đầy đủ");
            }
            emptyText.setText(R.string.no_games);
            adapter = new FeatureItemAdapter(requireContext());
            list.setAdapter(adapter);
            list.setEmptyView(emptyText);
            list.setOnItemClickListener((parent, row, position, id) ->
                    openGame(adapter.getItem(position)));
            refreshButton.setOnClickListener(clicked -> loadSafely());
            search.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    adapter.filter(s == null ? "" : s.toString());
                }
                @Override public void afterTextChanged(Editable s) { }
            });
            loadSafely();
        } catch (Exception exception) {
            AppLogger.error(getContext(), "GamesFragment", "Không thể tạo danh sách", exception);
            if (emptyText != null) emptyText.setText(R.string.unknown_error);
        }
    }

    private void loadSafely() {
        try {
            setLoading(true);
            repository.loadGames(new ApiCallback<List<FeatureItem>>() {
                @Override
                public void onSuccess(List<FeatureItem> data) {
                    if (!isUsable()) return;
                    setLoading(false);
                    adapter.setItems(data);
                }

                @Override
                public void onError(ApiError error) {
                    if (!isUsable()) return;
                    setLoading(false);
                    if (getActivity() instanceof BaseActivity) {
                        ((BaseActivity) getActivity()).handleApiError(error);
                    }
                }
            });
        } catch (Exception exception) {
            AppLogger.error(getContext(), "GamesFragment", "Không thể tải game", exception);
            setLoading(false);
        }
    }

    private void openGame(FeatureItem item) {
        if (item == null || item.getId().isEmpty() || !isAdded()) return;
        try {
            Intent intent = new Intent(requireContext(), GameActivity.class);
            intent.putExtra(GameActivity.EXTRA_GAME_ID, item.getId());
            intent.putExtra(GameActivity.EXTRA_GAME_TITLE, item.getTitle());
            startActivity(intent);
        } catch (Exception exception) {
            AppLogger.error(getContext(), "GamesFragment", "Không thể mở game", exception);
        }
    }

    private void setLoading(boolean loading) {
        if (progressBar != null) progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (refreshButton != null) refreshButton.setEnabled(!loading);
    }

    private boolean isUsable() { return isAdded() && getView() != null; }
}
