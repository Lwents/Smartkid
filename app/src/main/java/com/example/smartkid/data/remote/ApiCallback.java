package com.example.smartkid.data.remote;

/**
 * Kết quả trả về của một lần gọi API.
 * 
 * Gọi mạng không trả giá trị ngay được (mạng chậm, không được chặn giao diện), nên
 * nơi gọi truyền vào đây phần "xong thì làm gì": onSuccess khi có dữ liệu,
 * onError khi thất bại.
 */
public interface ApiCallback<T> {
    /** Chạy khi server trả về dữ liệu hợp lệ. */
    void onSuccess(T data);

    /** Chạy khi lỗi mạng hoặc server trả mã lỗi. */
    void onError(ApiError error);
}
