package com.tongkey.oauth2;

/**
 * OAuth2 端点错误：携带 RFC 6749 §5.2 错误码与 HTTP 状态。
 */
public class OAuthErrorException extends RuntimeException {

    private final String errorCode;
    private final int httpStatus;

    public OAuthErrorException(int httpStatus, String errorCode, String description) {
        super(description);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
