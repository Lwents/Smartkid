package com.example.smartkid.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.smartkid.core.AppLogger;
import com.example.smartkid.core.SafeJson;
import com.example.smartkid.data.model.Course;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Giỏ hàng cục bộ theo tài khoản; chỉ lưu khóa học thật đã lấy từ API. */
public class CartManager {
    private static final String PREF_NAME = "smartkid_cart";
    private final Context appContext;
    private final SharedPreferences preferences;
    private final String cartKey;

    public CartManager(Context context) {
        if (context == null) throw new IllegalArgumentException("Context không được để trống");
        appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SessionManager session = new SessionManager(appContext);
        String owner = session.getUser().getId();
        if (owner == null || owner.trim().isEmpty()) owner = session.getUser().getUsername();
        cartKey = "items_" + (owner == null ? "" : owner.trim());
    }

    public synchronized boolean add(Course course) {
        if (course == null || course.getId().isEmpty() || course.isEnrolled()
                || course.getPrice() <= 0) return false;
        try {
            List<Course> items = getItems();
            for (Course item : items) {
                if (course.getId().equals(item.getId())) return false;
            }
            items.add(course);
            save(items);
            return true;
        } catch (Exception exception) {
            AppLogger.error(appContext, "CartManager", "Không thể thêm vào giỏ", exception);
            return false;
        }
    }

    public synchronized void replace(List<Course> courses) {
        try { save(courses == null ? new ArrayList<>() : courses); }
        catch (Exception exception) {
            AppLogger.error(appContext, "CartManager", "Không thể đồng bộ giỏ", exception);
        }
    }

    public synchronized void remove(String courseId) {
        try {
            String id = courseId == null ? "" : courseId.trim();
            List<Course> items = getItems();
            items.removeIf(item -> id.equals(item.getId()));
            save(items);
        } catch (Exception exception) {
            AppLogger.error(appContext, "CartManager", "Không thể xóa khỏi giỏ", exception);
        }
    }

    public synchronized List<Course> getItems() {
        List<Course> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(preferences.getString(cartKey, "[]"));
            for (int index = 0; index < array.length(); index++) {
                JSONObject item = array.optJSONObject(index);
                if (item == null) continue;
                Course course = fromJson(item);
                if (!course.getId().isEmpty()) result.add(course);
            }
        } catch (Exception exception) {
            AppLogger.error(appContext, "CartManager", "Không thể đọc giỏ hàng", exception);
        }
        return result;
    }

    private void save(List<Course> courses) {
        try {
            JSONArray array = new JSONArray();
            for (Course course : courses) {
                if (course != null && !course.getId().isEmpty()) array.put(toJson(course));
            }
            preferences.edit().putString(cartKey, array.toString()).apply();
        } catch (Exception exception) {
            AppLogger.error(appContext, "CartManager", "Không thể lưu giỏ hàng", exception);
        }
    }

    private JSONObject toJson(Course course) throws Exception {
        JSONObject value = new JSONObject();
        value.put("id", course.getId());
        value.put("title", course.getTitle());
        value.put("grade", course.getGrade());
        value.put("subject", course.getSubject());
        value.put("teacher", course.getTeacherName());
        value.put("lessons", course.getLessonsCount());
        value.put("price", course.getPrice());
        value.put("thumbnail", course.getThumbnailUrl());
        value.put("description", course.getDescription());
        return value;
    }

    private Course fromJson(JSONObject value) {
        return new Course(
                SafeJson.string(value, "", "id"),
                SafeJson.string(value, "", "title"),
                SafeJson.string(value, "", "grade"),
                SafeJson.string(value, "", "subject"),
                SafeJson.string(value, "", "teacher"),
                SafeJson.integer(value, 0, "lessons"), 0,
                SafeJson.decimal(value, 0, "price"),
                SafeJson.string(value, "", "thumbnail"),
                SafeJson.string(value, "", "description"), false);
    }
}
