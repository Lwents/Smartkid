package com.example.smartkid.data.remote;

import android.net.Uri;

/** A content URI mapped to one multipart file field. */
public final class MultipartFilePart {
    private final String fieldName;
    private final Uri uri;

    public MultipartFilePart(String fieldName, Uri uri) {
        this.fieldName = fieldName == null ? "" : fieldName.trim();
        this.uri = uri;
    }

    public String getFieldName() {
        return fieldName;
    }

    public Uri getUri() {
        return uri;
    }
}
