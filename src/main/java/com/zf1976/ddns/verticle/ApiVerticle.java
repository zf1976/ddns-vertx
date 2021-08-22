package com.zf1976.ddns.verticle;

import com.zf1976.ddns.cache.AbstractMemoryLogCache;
import com.zf1976.ddns.cache.MemoryLogCache;
import com.zf1976.ddns.config.DnsConfig;
import com.zf1976.ddns.config.SecureConfig;
import com.zf1976.ddns.config.ServerJMessage;
import com.zf1976.ddns.enums.DnsProviderType;
import com.zf1976.ddns.enums.DnsRecordType;
import com.zf1976.ddns.enums.WebhookProviderType;
import com.zf1976.ddns.pojo.DataResult;
import com.zf1976.ddns.pojo.DnsRecordLog;
import com.zf1976.ddns.util.*;
import com.zf1976.ddns.verticle.handler.WebhookHandler;
import com.zf1976.ddns.verticle.provider.RedirectAuthenticationProvider;
import com.zf1976.ddns.verticle.provider.SecureProvider;
import com.zf1976.ddns.verticle.provider.UsernamePasswordAuthenticationProvider;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.CookieSameSite;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.Json;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.*;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import io.vertx.ext.web.handler.sockjs.SockJSHandlerOptions;
import io.vertx.ext.web.sstore.LocalSessionStore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author mac
 * 2021/7/6
 */
public class ApiVerticle extends TemplateVerticle implements SecureProvider, WebhookHandler {

    private final Logger log = LogManager.getLogger("[ApiVerticle]");
    private final AbstractMemoryLogCache<DnsProviderType, DnsRecordLog> cache = MemoryLogCache.getInstance();
    private WebClient webClient;

    @Override
    public void start(Promise<Void> startPromise) {
        final var serverPort = serverPort();
        final var router = getRouter();
        final var sessionStore = LocalSessionStore.create(vertx, ApiConstants.SESSION_NAME);
        final var sessionHandler = SessionHandler.create(sessionStore)
                                                 .setLazySession(true)
                                                 // Same site strategy using strict mode
                                                 .setCookieSameSite(CookieSameSite.STRICT);
        final var formLoginHandler = FormLoginHandler.create(new UsernamePasswordAuthenticationProvider(this))
                                                     .setDirectLoggedInOKURL(ApiConstants.INDEX_PATH)
                                                     .setReturnURLParam(ApiConstants.INDEX_PATH);
        SockJSHandlerOptions options = new SockJSHandlerOptions()
                .setRegisterWriteHandler(true)
                .setHeartbeatInterval(2000);
        SockJSHandler sockJSHandler = SockJSHandler.create(vertx, options);
        router.mountSubRouter("/api/logs", this.dnsRecordLogHandler(sockJSHandler));

        // all routes use session
        router.route()
              .handler(this::allowWanAccessHandler)
              .handler(XFrameHandler.create(XFrameHandler.DENY))
              .handler(sessionHandler)
              .failureHandler(this::routeErrorHandler);
        final var redirectAuthHandler = RedirectAuthHandler.create(
                new RedirectAuthenticationProvider(),
                ApiConstants.LOGIN_PATH
        );
        // redirect authentication
        router.route("/api/*")
              .handler(redirectAuthHandler);
        router.route("/index.html")
              .handler(redirectAuthHandler);
        // the page must have POST form login data
        router.post("/login")
              .handler(BodyHandler.create())
              .handler(formLoginHandler);
        // sign out
        router.post("/logout")
              .handler(redirectAuthHandler)
              .handler(this::logoutHandler);
        // store DNS service provider key
        router.post("/api/store/dns/config")
              .consumes("application/json")
              .handler(BodyHandler.create())
              .blockingHandler(this::storeDnsConfigHandle);
        // sava secure config
        router.post("/api/store/secure/config")
              .consumes("application/json")
              .handler(BodyHandler.create())
              .handler(this::storeSecureConfigHandler);
        // query DNS service provider's domain name resolution record
        router.post("/api/dns/record/list")
              .handler(this::findDnsRecordsHandler);
        // delete analysis record
        router.delete("/api/dns/record")
              .handler(this::deleteDnsRecordHandler);
        // resolve dns record
        router.post("/api/dns/record/resolve")
              .handler(this::resolveDnsRecordHandler);
        // clear resolve dns record log
        router.delete("/api/dns/record/log")
              .handler(this::clearDnsRecordLogHandler);
        // store webhook config
        router.post("/api/webhook/config")
              .consumes("application/json")
              .handler(BodyHandler.create())
              .handler(this::storeWebhookConfig);
        // send test webhook
        router.post("/api/webhook/test")
              .handler(this::sendWebhookTest);
        // obtain the RSA public key
        router.get("/common/rsa/public_key")
              .handler(this::readRsaPublicKeyHandler);
        // initial configuration
        this.initConfig(vertx)
            .compose(v -> vertx.createHttpServer()
                               .exceptionHandler(Throwable::printStackTrace)
                               .requestHandler(router)
                               .listen(serverPort)
            )
            .onSuccess(event -> {
                log.info("Vertx web server initialized with port(s): {}(http)", serverPort);
                log.info("DDNS-Vertx is running at http://localhost:{}", serverPort);
                try {
                    super.start(startPromise);
                } catch (Exception e) {
                    startPromise.fail(e);
                }
            })
            .onFailure(startPromise::fail);
    }

    @Override
    public void start() throws Exception {
        this.webClient = WebClient.create(vertx);
        this.vertx.deployVerticle(new PeriodicVerticle(this.dnsRecordService, this), event -> {
            if (event.succeeded()) {
                context.put(ApiConstants.VERTICLE_PERIODIC_DEPLOY_ID, event.result());
                log.info("PeriodicVerticle deploy complete!");
            } else {
                final var err = event.cause();
                err.printStackTrace();
                log.error("Class：" + err.getClass() + " => Message：" + err.getMessage());
            }
        });
    }

    @Override
    public void stop(Promise<Void> stopPromise) {
        final var o = context.get(ApiConstants.VERTICLE_PERIODIC_DEPLOY_ID);
        String periodicDeployId = (String) o;
        this.vertx.undeploy(periodicDeployId, event -> {
            if (event.succeeded()) {
                try {
                    super.stop(stopPromise);
                } catch (Exception e) {
                    log.error(e.getMessage(), e.getCause());
                }
            } else {
                log.error(event.cause());
            }
        });
    }

    protected void storeWebhookConfig(RoutingContext ctx) {
        final var request = ctx.request();
        try {
            final var providerType = WebhookProviderType.checkType(request.getParam("type"));
            switch (providerType) {
                case SERVER_J -> {
                    final var serverJMessage = ctx.getBodyAsJson()
                                                  .mapTo(ServerJMessage.class);
                    Validator.of(serverJMessage)
                             .withValidated(v -> !HttpUtil.isURL(v.getUrl()), "this is not url")
                             .withValidated(v -> !StringUtil.isEmpty(v.getTitle()), "title cannot been empty")
                             .withValidated(v -> !StringUtil.isEmpty(v.getContent()), "content cannot been empty");
                    this.routeSuccessHandler(ctx);
                }
                case DING_DING -> {
                    this.routeSuccessHandler(ctx);
                }
            }
        } catch (Exception e) {
            this.routeBadRequestHandler(ctx, e.getMessage());
        }
    }

    /**
     * Webhook test
     *
     * @param ctx router context
     */
    protected void sendWebhookTest(RoutingContext ctx) {
        final var request = ctx.request();
        try {
            final var url = AesUtil.decodeByCBC(request.getParam("url"), aesKey.getKey(), aesKey.getIv());
            this.webClient.postAbs(url)
                          .send()
                          .onSuccess(event -> {
                              this.routeSuccessHandler(ctx, "消息已发送成功，请注意查收");
                          })
                          .onFailure(err -> {
                              this.routeErrorHandler(ctx, err.getMessage());
                          });
        } catch (Exception e) {
            this.routeBadRequestHandler(ctx, e.getMessage());
        }
    }

    /**
     * sockJS handler
     *
     * @param sockJSHandler sockJs
     * @return {@link Router}
     */
    private Router dnsRecordLogHandler(SockJSHandler sockJSHandler) {
        return sockJSHandler.socketHandler(socket -> {
            vertx.sharedData()
                 .getLocalAsyncMap(ApiConstants.SHARE_MAP_ID)
                 .compose(shareMap -> {
                     socket.handler(providerType -> {
                               final DnsProviderType dnsProviderType;
                               try {
                                   dnsProviderType = DnsProviderType.checkType(providerType.toString());
                               } catch (Exception e) {
                                   socket.write(e.getMessage());
                                   return;
                               }
                               final var completableFuture = this.cache.get(dnsProviderType);
                               Future.fromCompletionStage(completableFuture, vertx.getOrCreateContext())
                                     .compose(collection -> shareMap.put(ApiConstants.SOCKJS_SELECT_PROVIDER_TYPE, dnsProviderType)
                                                                    .compose(v -> socket.write(Json.encodePrettily(collection))))
                                     .onFailure(err -> log.error(err.getMessage(), err.getCause()));
                           })
                           .exceptionHandler(log::error);
                     if (socket.writeHandlerID() != null) {
                         return shareMap.put(ApiConstants.SOCKJS_WRITE_HANDLER_ID, socket.writeHandlerID());
                     }
                     return Future.failedFuture("No authentication, please log in for authentication");
                 })
                 .onFailure(err -> log.error(err.getMessage(), err.getCause()));
        });
    }

    /**
     * not allow wan access handler
     *
     * @param ctx routing context
     */
    protected void allowWanAccessHandler(RoutingContext ctx) {
        HttpServerRequest request = ctx.request();
        String ipAddress = HttpUtil.getIpAddress(request);
        if (HttpUtil.isInnerIp(ipAddress)) {
            ctx.next();
        } else {
            this.routeBadRequestHandler(ctx, "Prohibit WAN access！");
        }
    }

    /**
     * logout handler
     *
     * @param ctx routing context
     */
    protected void logoutHandler(RoutingContext ctx) {
        if (ctx.user() != null) {
            final var session = ctx.session();
            final var id = session.id();
            for (Map.Entry<String, Cookie> cookieEntry : ctx.cookieMap()
                                                            .entrySet()) {
                final var cookie = cookieEntry.getValue();
                if (ObjectUtil.nullSafeEquals(id, cookie.getValue())) {
                    ctx.clearUser();
                    this.routeSuccessHandler(ctx, "Sign out successfully！");
                    break;
                }
            }
        } else {
            this.routeBadRequestHandler(ctx, "Invalid request");
        }
    }

    /**
     * get rsa public key handler
     *
     * @param ctx routing context
     */
    protected void readRsaPublicKeyHandler(RoutingContext ctx) {
        this.readRsaKeyPair()
            .onSuccess(rsaKeyPair -> this.routeSuccessHandler(ctx, rsaKeyPair.getPublicKey()))
            .onFailure(err -> this.routeErrorHandler(ctx, err));
    }

    /**
     * find DDNS records handler
     *
     * @param ctx routing context
     */
    protected void findDnsRecordsHandler(RoutingContext ctx) {
        try {
            final var request = ctx.request();
            final var dnsRecordType = DnsRecordType.checkType(request.getParam(ApiConstants.DNS_RECORD_TYPE));
            final var dnsProviderType = DnsProviderType.checkType(request.getParam(ApiConstants.DDNS_PROVIDER_TYPE));
            final var domain = request.getParam(ApiConstants.DOMAIN);
            this.dnsRecordService.findRecordListAsync(dnsProviderType, domain, dnsRecordType)
                                 .onSuccess(bool -> this.routeSuccessHandler(ctx, bool))
                                 .onFailure(err -> this.routeBadRequestHandler(ctx, err));
        } catch (Exception e) {
            this.routeErrorHandler(ctx, "Parameter error");
        }
    }

    /**
     * delete DDNS record handler
     *
     * @param ctx routing context
     */
    protected void deleteDnsRecordHandler(RoutingContext ctx) {
        try {
            final var request = ctx.request();
            final var recordId = request.getParam(ApiConstants.RECORD_ID);
            final var dnsProviderType = DnsProviderType.checkType(request.getParam(ApiConstants.DDNS_PROVIDER_TYPE));
            final var domain = request.getParam(ApiConstants.DOMAIN);
            this.dnsRecordService.deleteRecordAsync(dnsProviderType, recordId, domain)
                                 .onSuccess(bool -> this.routeSuccessHandler(ctx, bool))
                                 .onFailure(err -> this.routeBadRequestHandler(ctx, err));
        } catch (Exception e) {
            this.routeBadRequestHandler(ctx, "Parameter error");
        }
    }


    protected void clearDnsRecordLogHandler(RoutingContext ctx) {
        final var type = ctx.request()
                            .getParam("type");
        try {
            final var dnsProviderType = DnsProviderType.checkType(type);
            final var completableFuture = this.cache.get(dnsProviderType);
            Future.fromCompletionStage(completableFuture, vertx.getOrCreateContext())
                  .onSuccess(event -> {
                      event.clear();
                      this.routeSuccessHandler(ctx);
                  })
                  .onFailure(err -> this.routeErrorHandler(ctx, err.getMessage()));
        } catch (Exception e) {
            this.routeBadRequestHandler(ctx, e.getMessage());
        }

    }

    protected void resolveDnsRecordHandler(RoutingContext ctx) {
        this.dnsRecordService.update();
        this.routeSuccessHandler(ctx);
    }

    /**
     * store secure config handler
     *
     * @param ctx routing context
     */
    protected void storeSecureConfigHandler(RoutingContext ctx) {
        SecureConfig secureConfig;
        try {
            secureConfig = ctx.getBodyAsJson()
                              .mapTo(SecureConfig.class);
            Validator.of(secureConfig)
                     .withValidated(v -> !StringUtil.hasLength(v.getUsername()), "username cannot been empty!")
                     .withValidated(v -> StringUtil.hasLength(v.getPassword()), "username cannot been empty!");
            this.secureConfigDecryptHandler(secureConfig)
                .compose(this::storeSecureConfig)
                .onSuccess(success -> {
                    ctx.clearUser();
                    // return login url
                    this.routeSuccessHandler(ctx, DataResult.success(ApiConstants.LOGIN_PATH));
                })
                .onFailure(err -> this.routeErrorHandler(ctx, err));
        } catch (Exception e) {
            this.routeErrorHandler(ctx, new RuntimeException("Parameter abnormal"));
        }
    }

    /**
     * store DDNS config handler
     *
     * @param ctx routing context
     */
    protected void storeDnsConfigHandle(RoutingContext ctx) {
        try {
            final var dnsConfig = ctx.getBodyAsJson().mapTo(DnsConfig.class);
            switch (dnsConfig.getDnsProviderType()) {
                case ALIYUN:
                case HUAWEI:
                case DNSPOD:
                    Validator.of(dnsConfig)
                             .withValidated(v -> !StringUtil.isEmpty(v.getId()) && !v.getId().isBlank(), "ID cannot be empty")
                             .withValidated(v -> !StringUtil.isEmpty(v.getSecret()) && !v.getSecret().isBlank(), "The Secret cannot be empty");
                    break;
                case CLOUDFLARE:
                    Validator.of(dnsConfig)
                             .withValidated(v -> !StringUtil.isEmpty(v.getSecret()) && !v.getSecret().isBlank(), "The Secret cannot be empty");
                default:
            }
            this.dnsConfigDecryptHandler(dnsConfig)
                .compose(this::storeDnsConfig)
                .onSuccess(success -> this.routeSuccessHandler(ctx))
                .onFailure(err -> this.routeErrorHandler(ctx, err));
        } catch (Exception exception) {
            this.routeBadRequestHandler(ctx, "Parameter abnormal");
        }
    }

    /**
     * store DDNS config to file
     *
     * @param dnsConfig DDNS config
     * @return {@link Future<Void>}
     */
    private Future<Void> storeDnsConfig(DnsConfig dnsConfig) {
        final var fileSystem = vertx.fileSystem();
        final String absolutePath = this.toAbsolutePath(workDir, DNS_CONFIG_FILENAME);
        return this.readDnsConfig(fileSystem)
                   .compose(configList -> this.writeDnsConfig(configList, dnsConfig, absolutePath)
                                              .compose(v -> newDnsRecordService(configList)));
    }

    /**
     * store secure config to file
     *
     * @param secureConfig secure config
     * @return {@link Future<Void>}
     */
    private Future<Void> storeSecureConfig(SecureConfig secureConfig) {
        String absolutePath = this.toAbsolutePath(workDir, SECURE_CONFIG_FILENAME);
        return this.writeJsonToFile(absolutePath, Json.encodePrettily(secureConfig))
                .compose(v -> {
                    this.notAllowWanAccess = secureConfig.getNotAllowWanAccess() == null? Boolean.TRUE : Boolean.FALSE;
                    return Future.succeededFuture();
                });
    }

    /**
     * write DDNS config
     *
     * @param dnsConfigList DDNS config list
     * @param dnsConfig new DDNS config
     * @param absolutePath file absolute path
     * @return {@link Future<Void>}
     */
    private Future<Void> writeDnsConfig(List<DnsConfig> dnsConfigList, DnsConfig dnsConfig, String absolutePath) {
        // 读取配置为空
        if (CollectionUtil.isEmpty(dnsConfigList)) {
            List<DnsConfig> newDnsConfigList = new ArrayList<>();
            newDnsConfigList.add(dnsConfig);
            return this.writeJsonToFile(absolutePath, Json.encodePrettily(newDnsConfigList));
        } else {
            try {
                dnsConfigList.removeIf(config -> dnsConfig.getDnsProviderType().equals(config.getDnsProviderType()));
                dnsConfigList.add(dnsConfig);
                return this.writeJsonToFile(absolutePath, Json.encodePrettily(dnsConfigList));
            } catch (Exception e) {
                return Future.failedFuture(new RuntimeException("Server Error"));
            }
        }
    }

    /**
     * write config to file
     *
     * @param absolutePath path
     * @param json         JSON
     * @return {@link Future<Void>
     */
    private Future<Void> writeJsonToFile(String absolutePath, String json) {
        return vertx.fileSystem()
                    .writeFile(absolutePath, Buffer.buffer(json));
    }

}
