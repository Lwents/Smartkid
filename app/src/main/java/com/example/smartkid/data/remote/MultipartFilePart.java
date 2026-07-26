package com.example.smartkid.data.remote;

import android.net.Uri;

/** A content URI mapped to one multipart file field. */
public final class MultipartFilePart {
    private final String fieldName;
    private final Uri uri;

    /** Gắn một file người dùng chọn vào tên trường mà server chờ nhận. */
    public MultipartFilePart(String fieldName, Uri uri) {
        this.fieldName = fieldName == null ? "" : fieldName.trim();
        this.uri = uri;
    }

    /** Tên trường phía server, ví dụ video_file hay thumbnail. */
    public String getFieldName() {
        return fieldName;
    }

    /** Đường dẫn nội bộ tới file trên máy. */
    public Uri getUri() {
        return uri;
    }
}
