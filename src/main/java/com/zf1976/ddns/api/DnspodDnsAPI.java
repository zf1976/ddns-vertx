package com.zf1976.ddns.api;

import com.zf1976.ddns.api.auth.BasicCredentials;
import com.zf1976.ddns.api.auth.DnsApiCredentials;
import com.zf1976.ddns.api.enums.DNSRecordType;
import com.zf1976.ddns.api.enums.MethodType;
import com.zf1976.ddns.api.signature.rpc.DnspodSignatureComposer;
import com.zf1976.ddns.api.signature.rpc.RpcAPISignatureComposer;
import com.zf1976.ddns.pojo.DnspodDataResult;
import com.zf1976.ddns.util.HttpUtil;
import io.vertx.core.json.Json;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 腾讯云DNS
 *
 * @author ant
 * Create by Ant on 2021/7/17 1:23 上午
 */
@SuppressWarnings("FieldCanBeLocal")
public class DnspodDnsAPI extends AbstractDnsAPI {

    private final String api = "https://dnspod.tencentcloudapi.com";
    private final RpcAPISignatureComposer composer = DnspodSignatureComposer.getComposer();

    public DnspodDnsAPI(String id, String secret) {
        this(new BasicCredentials(id, secret));
    }

    public DnspodDnsAPI(DnsApiCredentials dnsApiCredentials) {
        super(dnsApiCredentials);
    }

    public DnspodDataResult findDnsRecords(String domain, DNSRecordType dnsRecordType) {
        this.checkDomain(domain);
        final var queryParam = this.getQueryParam("DescribeRecordList");
        queryParam.put("RecordType", dnsRecordType.name());
        queryParam.put("Domain", domain);
        final var url = composer.toUrl(this.dnsApiCredentials.getAccessKeySecret(), this.api, MethodType.GET, queryParam);
        final var httpRequest = this.requestBuild(url);
        return this.sendRequest(httpRequest);
    }

    public DnspodDataResult addDnsRecord(String domain, String ip, DNSRecordType dnsRecordType) {
        this.checkIp(ip);
        this.checkDomain(domain);
        final var queryParam = this.getQueryParam("CreateRecord");
        final var extractDomain = HttpUtil.extractDomain(domain);
        queryParam.put("Domain", extractDomain[0]);
        queryParam.put("SubDomain", "".equals(extractDomain[1]) ? "@" : extractDomain[1]);
        queryParam.put("RecordType", dnsRecordType.name());
        queryParam.put("RecordLine", "默认");
        queryParam.put("Value", ip);
        final var url = this.composer.toUrl(this.dnsApiCredentials.getAccessKeySecret(), this.api, MethodType.GET, queryParam);
        final var httpRequest = this.requestBuild(url);
        return this.sendRequest(httpRequest);
    }

    private HttpRequest requestBuild(String url) {
        return HttpRequest.newBuilder()
                          .uri(URI.create(url))
                          .GET()
                          .build();
    }

    private DnspodDataResult sendRequest(HttpRequest request) {
        try {
            final var body = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                                            .body();
            return Json.decodeValue(body, DnspodDataResult.class);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    private Map<String, Object> getQueryParam(String action) {
        Map<String, Object> params = new HashMap<>();
        params.put("Nonce", new Random().nextInt(java.lang.Integer.MAX_VALUE));
        params.put("Timestamp", String.valueOf(System.currentTimeMillis() / 1000));
        params.put("SecretId", this.dnsApiCredentials.getAccessKeyId());
        params.put("Action", action);
        params.put("Version", "2021-03-23");
        params.put("SignatureMethod", "HmacSHA256");
        return params;
    }
}
