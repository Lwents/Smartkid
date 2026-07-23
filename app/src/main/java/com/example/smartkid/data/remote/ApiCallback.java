package com.example.smartkid.data.remote;

public interface ApiCallback<T> {
    void onSuccess(T data);

    void onError(ApiError error);
}
