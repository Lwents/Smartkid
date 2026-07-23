package com.example.smartkid.ui.student;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.example.smartkid.R;
import com.example.smartkid.core.AppLogger;
import com.example.smartkid.data.local.CartManager;
import com.example.smartkid.data.model.Course;
import com.example.smartkid.data.model.FeatureItem;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiError;
import com.example.smartkid.data.repository.CourseRepository;
import com.example.smartkid.ui.common.BaseActivity;
import com.example.smartkid.ui.common.FeatureItemAdapter;
import com.google.android.material.appbar.MaterialToolbar;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Giỏ hàng khóa học, luôn đồng bộ lại giá và trạng thái bằng catalog API. */
public class CartActivity extends BaseActivity {
    private CartManager cartManager;
    private CourseRepository repository;
    private FeatureItemAdapter adapter;
    private ProgressBar progressBar;
    private TextView totalText;
    private TextView statusText;
    private View checkoutButton;
    private final List<Course> currentItems = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_cart);
            cartManager = new CartManager(this);
            repository = new CourseRepository(this);
            bindViews();
            MaterialToolbar toolbar = findViewById(R.id.toolbarCart);
            ListView list = findViewById(R.id.listCart);
            TextView empty = findViewById(R.id.textCartEmpty);
            if (toolbar == null || list == null || empty == null) {
                throw new IllegalStateException("Giao diện giỏ hàng thiếu thành phần bắt buộc");
            }
            toolbar.setNavigationOnClickListener(view -> finish());
            adapter = new FeatureItemAdapter(this);
            list.setAdapter(adapter);
            list.setEmptyView(empty);
            list.setOnItemClickListener((parent, row, position, id) -> {
                FeatureItem item = adapter.getItem(position);
                if (item != null) confirmRemove(item);
            });
            findViewById(R.id.buttonCartRefresh).setOnClickListener(view -> syncSafely());
            checkoutButton.setOnClickListener(view -> checkoutSafely());
            bindCart(cartManager.getItems());
        } catch (Exception exception) {
            AppLogger.error(this, "CartActivity", "Không thể tạo giỏ hàng", exception);
            showErrorDialog("Không thể mở giỏ hàng");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (cartManager != null && repository != null) syncSafely();
    }

    private void bindViews() {
        progressBar = findViewById(R.id.progressCart);
        totalText = findViewById(R.id.textCartTotal);
        statusText = findViewById(R.id.textCartStatus);
        checkoutButton = findViewById(R.id.buttonCartCheckout);
        if (progressBar == null || totalText == null || statusText == null
                || checkoutButton == null) {
            throw new IllegalStateException("Giao diện giỏ hàng chưa đầy đủ");
        }
    }

    private void syncSafely() {
        List<Course> saved = cartManager.getItems();
        if (saved.isEmpty()) {
            bindCart(saved);
            showStatus("Giỏ hàng đang trống");
            return;
        }
        setLoading(true);
        repository.loadCatalog("", new ApiCallback<List<Course>>() {
            @Override
            public void onSuccess(List<Course> catalog) {
                if (!isUsable()) return;
                try {
                    Map<String, Course> byId = new HashMap<>();
                    if (catalog != null) {
                        for (Course course : catalog) byId.put(course.getId(), course);
                    }
                    List<Course> valid = new ArrayList<>();
                    for (Course oldItem : saved) {
                        Course fresh = byId.get(oldItem.getId());
                        if (fresh != null && !fresh.isEnrolled() && fresh.getPrice() > 0) {
                            valid.add(fresh);
                        }
                    }
                    cartManager.replace(valid);
                    bindCart(valid);
                    setLoading(false);
                    showStatus("Giá và trạng thái khóa học đã đồng bộ từ server");
                } catch (Exception exception) {
                    AppLogger.error(CartActivity.this, "CartActivity",
                            "Không thể đồng bộ giỏ hàng", exception);
                    setLoading(false);
                    bindCart(saved);
                    showStatus("Không thể đồng bộ; chưa thay đổi dữ liệu giỏ hàng");
                }
            }

            @Override
            public void onError(ApiError error) {
                if (!isUsable()) return;
                setLoading(false);
                bindCart(saved);
                showStatus(error == null ? getString(R.string.unknown_error) : error.getMessage());
            }
        });
    }

    private void bindCart(List<Course> courses) {
        currentItems.clear();
        if (courses != null) currentItems.addAll(courses);
        List<FeatureItem> display = new ArrayList<>();
        double total = 0;
        NumberFormat money = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
        for (Course course : currentItems) {
            total += course.getPrice();
            JSONObject source = new JSONObject();
            try {
                source.put("id", course.getId());
                source.put("title", course.getTitle());
                source.put("price", course.getPrice());
            } catch (Exception exception) {
                AppLogger.error(this, "CartActivity", "Không thể tạo mục giỏ", exception);
            }
            display.add(new FeatureItem(course.getId(), course.getTitle(),
                    course.getSubject() + (course.getGrade().isEmpty() ? "" : " • " + course.getGrade()),
                    course.getTeacherName(), money.format(course.getPrice()), source));
        }
        adapter.setItems(display);
        totalText.setText(getString(R.string.cart_total_format, money.format(total)));
        checkoutButton.setEnabled(!currentItems.isEmpty());
    }

    private void confirmRemove(FeatureItem item) {
        new AlertDialog.Builder(this).setTitle("Xóa khỏi giỏ hàng")
                .setMessage("Xóa “" + item.getTitle() + "” khỏi giỏ hàng?")
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton("Xóa", (dialog, which) -> {
                    cartManager.remove(item.getId());
                    bindCart(cartManager.getItems());
                    showStatus("Đã xóa khỏi giỏ hàng");
                }).show();
    }

    private void checkoutSafely() {
        if (currentItems.isEmpty()) {
            showStatus("Giỏ hàng đang trống");
            return;
        }
        try {
            double total = 0;
            JSONArray ids = new JSONArray();
            JSONArray titles = new JSONArray();
            for (Course course : currentItems) {
                total += course.getPrice();
                ids.put(course.getId());
                titles.put(course.getTitle());
            }
            Intent intent = new Intent(this, PaymentActivity.class);
            intent.putExtra(PaymentActivity.EXTRA_AMOUNT, total);
            intent.putExtra(PaymentActivity.EXTRA_DESCRIPTION,
                    "Thanh toán " + currentItems.size() + " khóa học SmartKid");
            intent.putExtra(PaymentActivity.EXTRA_COURSE_IDS_JSON, ids.toString());
            intent.putExtra(PaymentActivity.EXTRA_COURSE_TITLES_JSON, titles.toString());
            startActivity(intent);
        } catch (Exception exception) {
            AppLogger.error(this, "CartActivity", "Không thể chuyển sang thanh toán", exception);
            showErrorDialog("Không thể chuẩn bị dữ liệu thanh toán");
        }
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        checkoutButton.setEnabled(!loading && !currentItems.isEmpty());
    }

    private void showStatus(String message) {
        statusText.setText(message == null ? getString(R.string.unknown_error) : message);
        statusText.setVisibility(View.VISIBLE);
    }

    private boolean isUsable() { return !isFinishing() && !isDestroyed(); }
}
