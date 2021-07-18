package com.zf1976.ddns.api;

import com.zf1976.ddns.api.auth.DnsApiCredentials;
import com.zf1976.ddns.api.auth.TokenCredentials;
import com.zf1976.ddns.api.enums.DNSRecordType;
import com.zf1976.ddns.api.enums.MethodType;
import com.zf1976.ddns.pojo.CloudflareDataResult;
import com.zf1976.ddns.util.Assert;
import com.zf1976.ddns.util.CollectionUtil;
import com.zf1976.ddns.util.StringUtil;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

/**
 * cloudflare DNS
 *
 * @author ant
 * Create by Ant on 2021/7/17 1:24 上午
 */
@SuppressWarnings({"FieldCanBeLocal"})
public class CloudflareDnsApi extends AbstractDnsApi{

    private final Logger log = LogManager.getLogger("[CloudflareDnsApi]");
    private final String api = "https://api.cloudflare.com/client/v4/zones";
    private final String zoneId;
    private final DnsApiCredentials dnsApiCredentials;

    public CloudflareDnsApi(String token) {
        this(new TokenCredentials(token));
    }


    public CloudflareDnsApi(DnsApiCredentials dnsApiCredentials) {
        final var request = HttpRequest.newBuilder()
                                       .GET()
                                       .uri(URI.create(api))
                                       .header("Authorization","Bearer " + dnsApiCredentials.getAccessKeySecret())
                                       .build();
        try {
            final var body = super.httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
            final var cloudflareDataResult = Json.decodeValue(body, CloudflareDataResult.class);
            Assert.notNull(cloudflareDataResult, "result cannot been null");
            if (!cloudflareDataResult.getSuccess()) {
                throw new RuntimeException(Json.encodePrettily(cloudflareDataResult.getErrors()));
            }
            @SuppressWarnings("unchecked") final var zoneId = ((List<LinkedHashMap<String, String>>) cloudflareDataResult.getResult()).get(0).get("id");
            Assert.notNull(zoneId, "cloudflare zone id cannot been null!");
            this.zoneId = zoneId;
            this.dnsApiCredentials = dnsApiCredentials;
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e.getCause());
        }
    }

    public CloudflareDataResult<List<CloudflareDataResult.Result>> findDnsRecords(DNSRecordType dnsRecordType) {
        final var queryParam = getQueryParam(dnsRecordType);
        final var url = this.toUrl(queryParam, null);
        final var request = this.requestBuild(url, null, MethodType.GET);
        return  this.sendRequest(request);
    }

    public CloudflareDataResult<CloudflareDataResult.Result> addDnsRecord(String domain, String ip, DNSRecordType dnsRecordType) {
        this.checkIp(ip);
        final var queryParam = this.getQueryParam(dnsRecordType);
        queryParam.put("name", domain);
        queryParam.put("content", ip);
        queryParam.put("ttl", "120");
        final var url = toUrl(queryParam, null);
        final var httpRequest = this.requestBuild(url, queryParam, MethodType.POST);
        return this.sendRequest(httpRequest);
    }

    public CloudflareDataResult<CloudflareDataResult.Result> updateDnsRecord(String identifier, String domain, String ip, DNSRecordType dnsRecordType) {
        this.checkIp(ip);
        this.checkDomain(domain);
        final var queryParam = this.getQueryParam(dnsRecordType);
        queryParam.put("name", domain);
        queryParam.put("content", ip);
        queryParam.put("ttl", "120");
        final var url = this.toUrl(queryParam, identifier);
        final var httpRequest = this.requestBuild(url, queryParam, MethodType.PUT);
        return this.sendRequest(httpRequest);
    }

    @SuppressWarnings("unchecked")
    private <T> CloudflareDataResult<T>  sendRequest(HttpRequest request) {
        try {
            final var body = httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
            final var cloudflareDataResult = Json.decodeValue(body, CloudflareDataResult.class);
            if (cloudflareDataResult.getResult() instanceof ArrayList) {
                ArrayList<?> result = (ArrayList<?>) cloudflareDataResult.getResult();
                List<CloudflareDataResult.Result> targetList = new ArrayList<>();
                for (Object o : result) {
                    final var target = JsonObject.mapFrom(o).mapTo(CloudflareDataResult.Result.class);
                    targetList.add(target);
                }
                return cloudflareDataResult.setResult(targetList);
            } else {
                final var result = cloudflareDataResult.getResult();
                return cloudflareDataResult.setResult(JsonObject.mapFrom(result).mapTo(CloudflareDataResult.Result.class));
            }
        } catch (IOException | InterruptedException e) {
            log.error(e.getMessage(), e.getCause());
        }
        return null;
    }

    private HttpRequest requestBuild(String url,Object data, MethodType methodType) {
        final var builder = HttpRequest.newBuilder();
        builder.uri(URI.create(url))
               .header("Authorization", this.getBearerToken())
               .header("Content-type", "application/json");
        switch (methodType) {
            case GET:
                builder.GET();
                break;
            case PUT:
                builder.PUT(HttpRequest.BodyPublishers.ofString(Json.encodePrettily(data)));
                break;
            case POST:
                builder.POST(HttpRequest.BodyPublishers.ofString(Json.encodePrettily(data)));
                break;
            case DELETE:
                builder.DELETE();
                break;
            default:
        }
        return builder.build();
    }

    private String getBearerToken() {
        final var token = this.dnsApiCredentials.getAccessKeySecret();
        return "Bearer " + token;
    }

    @SuppressWarnings("SameParameterValue")
    private String toUrl(Map<String, String> queryParam, String identifier) {
        if (!CollectionUtil.isEmpty(queryParam)) {
            final var query = new StringBuilder();
            final var array = queryParam.keySet().toArray(new String[]{});
            for (String key : array) {
                query.append("&")
                     .append(key)
                     .append("=")
                     .append(queryParam.get(key));
            }
            return this.getBaseUrl() + (StringUtil.isEmpty(identifier) ? "?" : "/" + identifier + "?") + query.substring(1);
        }
        return this.getBaseUrl() + (StringUtil.isEmpty(identifier) ? "?" : "/" + identifier);
    }

    private String getBaseUrl() {
        return this.api + "/" + this.zoneId + "/dns_records";
    }

    public Map<String, String> getQueryParam(DNSRecordType dnsRecordType) {
        Map<String, String> queryParam = new HashMap<>();
        queryParam.put("match", "all");
        queryParam.put("type", dnsRecordType.name());
        queryParam.put("per_page", "100");
        return queryParam;
    }

}
