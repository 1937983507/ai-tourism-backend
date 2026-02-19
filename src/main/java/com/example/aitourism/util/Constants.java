package com.example.aitourism.util;

public class Constants {

    // 通用状态码
    public static final int STATUS_SUCCESS = 0; // 接口响应成功

    // 用户状态
    public static final int USER_STATUS_INACTIVE = 0;
    public static final int USER_STATUS_ACTIVE = 1;

    // 错误码
    public static final int ERROR_CODE_ACCOUNT_OR_PASSWORD_INVALID = 1001;
    public static final int ERROR_CODE_TOKEN_EXPIRED = 1101;
    public static final int ERROR_CODE_BAD_REQUEST = 4000;
    public static final int ERROR_CODE_SERVER_ERROR = 5000; // 通用服务端错误（对应 HTTP 500）
}
