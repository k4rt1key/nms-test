package org.nms.api;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.auth.KeyStoreOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;

import org.nms.App;
import static org.nms.App.logger;
import org.nms.api.handlers.*;
import org.nms.constants.Config;


public class Server extends AbstractVerticle
{
    public static final String CREDENTIALS_ENDPOINT = "/api/v1/credential/*";

    public static final String DISCOVERY_ENDPOINT = "/api/v1/discovery/*";

    public static final String PROVISION_ENDPOINT = "/api/v1/provision/*";

    public static final String POLLING_ENDPOINT = "/api/v1/polling/*";

    public static final String USER_ENDPOINT = "/api/v1/user/*";

    private static final JWTAuthOptions config = new JWTAuthOptions()
                                                    .setKeyStore(new KeyStoreOptions().setPath(Config.KEYSTORE_PATH).setPassword(Config.JWT_SECRET));

    public static final JWTAuth jwtAuth = JWTAuth.create(App.vertx, config);

    public static final JWTAuthHandler jwtAuthHandler = JWTAuthHandler.create(Server.jwtAuth);

    @Override
    public void start(Promise<Void> startPromise)
    {
        // ===== Configure HttpServer =====
        var server = vertx.createHttpServer(new HttpServerOptions().setReuseAddress(true));

        // ===== Configure Routes ======
        var router = Router.router(App.vertx);

        router.route().handler(BodyHandler.create());

        router.route().handler(ctx ->
        {
            ctx.response().putHeader("Content-Type", "application/json");
            ctx.next();
        });

        // ===== Configure User Routes =====
        setupUserRoutes(router);

        router.route()
                .handler(jwtAuthHandler)
                .handler(this::authenticate);

        // ===== Configure Credential Routes =====
        setupCredentialRoutes(router);

        // ===== Configure Discovery Routes =====
        setupDiscoveryRoutes(router);

        // ===== Configure Provision Routes =====
        setupProvisionRoutes(router);

        // ===== Configure Polling Routes =====
        setupPollingRoutes(router);

        // ===== Configure Router To Server =====
        server.requestHandler(router);

        // ===== Listen on Port =====
        server.listen(Config.HTTP_PORT, http ->
        {
            if(http.succeeded())
            {
                logger.info("✅ HTTP Server Started On Port => " + Config.HTTP_PORT + " On Thread [ " + Thread.currentThread().getName() + " ] ");
                startPromise.complete();
            }
            else
            {
                logger.error("Failed To Start HTTP Server => " + http.cause());
                startPromise.fail(http.cause());
            }
        });
    }

    private void setupUserRoutes(Router router)
    {
        var userRouter = Router.router(vertx);

        userRouter.get("/")
                .handler(org.nms.api.handlers.User::getUsers);

        userRouter.get("/:id")
                .handler(jwtAuthHandler)
                .handler(this::authenticate)
                .handler(org.nms.api.handlers.User::getUserById);

        userRouter.post("/login")
                .handler(org.nms.api.handlers.User::login);

        userRouter.post("/register")
                .handler(org.nms.api.handlers.User::register);

        userRouter.patch("/:id")
                .handler(jwtAuthHandler)
                .handler(this::authenticate)
                .handler(org.nms.api.handlers.User::updateUser);

        userRouter.delete("/:id")
                .handler(jwtAuthHandler)
                .handler(this::authenticate)
                .handler(org.nms.api.handlers.User::deleteUser);

        router.route(USER_ENDPOINT).subRouter(userRouter);
    }

    private void setupProvisionRoutes(Router router)
    {
        var provisionRouter = Router.router(App.vertx);

        provisionRouter.get("/")
                .handler(org.nms.api.handlers.Provision::getAllProvisions);

        provisionRouter.get("/:id")
                .handler(org.nms.api.handlers.Provision::getProvisionById);

        provisionRouter.post("/")
                .handler(org.nms.api.handlers.Provision::createProvision);

        provisionRouter.patch("/:id")
                .handler(org.nms.api.handlers.Provision::updateMetric);

        provisionRouter.delete("/:id")
                .handler(org.nms.api.handlers.Provision::deleteProvision);

        router.route(PROVISION_ENDPOINT).subRouter(provisionRouter);
    }

    private void setupDiscoveryRoutes(Router router)
    {
        var discoveryRouter = Router.router(vertx);

        discoveryRouter.get("/results/:id")
                .handler(org.nms.api.handlers.Discovery::getDiscoveryResultsById);

        discoveryRouter.get("/results")
                .handler(org.nms.api.handlers.Discovery::getDiscoveryResults);

        discoveryRouter.get("/")
                .handler(org.nms.api.handlers.Discovery::getAllDiscoveries);

        discoveryRouter.get("/:id")
                .handler(org.nms.api.handlers.Discovery::getDiscoveryById);

        discoveryRouter.post("/")
                .handler(org.nms.api.handlers.Discovery::createDiscovery);

        discoveryRouter.post("/run/:id")
                .handler(org.nms.api.handlers.Discovery::runDiscovery);

        discoveryRouter.patch("/:id")
                .handler(org.nms.api.handlers.Discovery::updateDiscovery);

        discoveryRouter.patch("/credential/:id")
                .handler(org.nms.api.handlers.Discovery::updateDiscoveryCredentials);

        discoveryRouter.delete("/:id")
                .handler(org.nms.api.handlers.Discovery::deleteDiscovery);

        router.route(DISCOVERY_ENDPOINT).subRouter(discoveryRouter);
    }

    private void setupCredentialRoutes(Router router)
    {
        var credentialRouter = Router.router(vertx);

        credentialRouter.get("/")
                .handler(org.nms.api.handlers.Credential::getAllCredentials);

        credentialRouter.get("/:id")
                .handler(org.nms.api.handlers.Credential::getCredentialById);

        credentialRouter.post("/")
                .handler(org.nms.api.handlers.Credential::createCredential);

        credentialRouter.patch("/:id")
                .handler(org.nms.api.handlers.Credential::updateCredential);

        credentialRouter.delete("/:id")
                .handler(org.nms.api.handlers.Credential::deleteCredential);

        router.route(CREDENTIALS_ENDPOINT).subRouter(credentialRouter);
    }

    private void setupPollingRoutes(Router router)
    {
        var pollingRouter = Router.router(vertx);

        pollingRouter.get("/")
                .handler(MetricResult::getAllPolledData);

        router.route(POLLING_ENDPOINT).subRouter(pollingRouter);
    }

    private void authenticate(RoutingContext ctx)
    {
        if(ctx.user() == null || ctx.user().principal().isEmpty() || ctx.user().expired())
        {
            Utility.sendFailure(ctx, 401, "Please login to continue");
            return;
        }

        ctx.next();
    }

    @Override
    public void stop()
    {
        logger.info("\uD83D\uDED1 Http Server Stopped");
    }
}