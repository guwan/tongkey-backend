package com.tongkey.push;

/** 推送目标鉴权方式（规格文档 6.1）。 */
public enum PushAuthType {
    NONE,
    BASIC,
    BEARER,
    /** 自定义 Header HMAC-SHA256 签名 */
    HMAC_SIGNATURE
}
