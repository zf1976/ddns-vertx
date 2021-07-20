package com.zf1976.ddns.api.signature;

import com.zf1976.ddns.api.auth.DnsApiCredentials;

/**
 * @author mac
 * @date 2021/7/19
 */
public class HmacSHA256Signer extends Signer {

    public static final String ALGORITHM_NAME = "HmacSHA256";

    @Override
    public String signString(String stringToSign, DnsApiCredentials credentials) {
        return signString(stringToSign, credentials.getAccessKeySecret());
    }

    @Override
    public String signString(String stringToSign, String accessKeySecret) {
        return sign(stringToSign, accessKeySecret, ALGORITHM_NAME);
    }

    public String getSignerName() {
        return "HMAC-SHA256";
    }

    public String getSignerVersion() {
        return "1.0";
    }

    @Override
    public String getSignerType() {
        return null;
    }
}
