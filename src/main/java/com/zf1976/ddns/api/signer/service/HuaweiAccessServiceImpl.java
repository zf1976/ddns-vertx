package com.zf1976.ddns.api.signer.service;

import com.zf1976.ddns.api.auth.BasicCredentials;
import com.zf1976.ddns.api.enums.MethodType;
import com.zf1976.ddns.api.signer.HuaweiRequest;
import com.zf1976.ddns.api.signer.HuaweiSigner;
import org.apache.http.Header;
import org.apache.http.client.methods.*;
import org.apache.http.entity.StringEntity;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * @author mac
 * @date 2021/7/24
 */
public class HuaweiAccessServiceImpl extends HuaweiAccessService {

    public HuaweiAccessServiceImpl(String ak, String sk) {
        super(ak, sk);
    }

    @SuppressWarnings("SameParameterValue")
    private static HttpRequestBase createRequest(String url, Header header, String content, MethodType httpMethod) {
        HttpRequestBase httpRequest;
        StringEntity entity;
        if (httpMethod == MethodType.POST) {
            HttpPost postMethod = new HttpPost(url);
            if (content != null) {
                entity = new StringEntity(content, StandardCharsets.UTF_8);
                postMethod.setEntity(entity);
            }

            httpRequest = postMethod;
        } else if (httpMethod == MethodType.PUT) {
            HttpPut putMethod = new HttpPut(url);
            httpRequest = putMethod;
            if (content != null) {
                entity = new StringEntity(content, StandardCharsets.UTF_8);
                putMethod.setEntity(entity);
            }
        } else if (httpMethod == MethodType.PATCH) {
            HttpPatch patchMethod = new HttpPatch(url);
            httpRequest = patchMethod;
            if (content != null) {
                entity = new StringEntity(content, StandardCharsets.UTF_8);
                patchMethod.setEntity(entity);
            }
        } else if (httpMethod == MethodType.GET) {
            httpRequest = new HttpGet(url);
        } else if (httpMethod == MethodType.DELETE) {
            httpRequest = new HttpDelete(url);
        } else if (httpMethod == MethodType.OPTIONS) {
            httpRequest = new HttpOptions(url);
        } else {
            if (httpMethod != MethodType.HEAD) {
                throw new RuntimeException("Unknown HTTP method name: " + httpMethod);
            }

            httpRequest = new HttpHead(url);
        }

        httpRequest.addHeader(header);
        return httpRequest;
    }

    public HttpRequestBase access(String url,
                                  Map<String, String> headers,
                                  String content,
                                  MethodType httpMethod) throws Exception {
        HuaweiRequest request = HuaweiRequest.newBuilder(new BasicCredentials(this.ak, this.sk))
                                             .setMethod(httpMethod)
                                             .setUrl(url);

        for (String k : headers.keySet()) {
            request.addHeader(k, headers.get(k));
        }

        request.setBody(content);
        HuaweiSigner signer = new HuaweiSigner();
        signer.sign(request);
        HttpRequestBase httpRequestBase = createRequest(url, null, content, httpMethod);
        Map<String, String> requestHeaders = request.getHeaders();

        for (String key : requestHeaders.keySet()) {
            if (!key.equalsIgnoreCase("Content-Length")) {
                String value = requestHeaders.get(key);
                httpRequestBase.addHeader(key, new String(value.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1));
            }
        }

        return httpRequestBase;
    }

    public HttpRequestBase access(String url,
                                  Map<String, String> headers,
                                  InputStream content,
                                  Long contentLength,
                                  MethodType httpMethod) throws Exception {
        String body = "";
        if (content != null) {
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];

            int length;
            while ((length = content.read(buffer)) != -1) {
                result.write(buffer, 0, length);
            }

            body = result.toString(StandardCharsets.UTF_8);
        }

        return this.access(url, headers, body, httpMethod);
    }
}