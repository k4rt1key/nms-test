package org.nms.api;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.auth.KeyStoreOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;

import org.nms.App;
import org.nms.ConsoleLogger;
import org.nms.api.handlers.*;
import org.nms.api.auth.AuthMiddleware;
import org.nms.api.validators.CredentialRequestValidator;
import org.nms.api.validators.DiscoveryRequestValidator;
import org.nms.api.validators.ProvisionRequestValidator;
import org.nms.api.validators.UserRequestValidator;
import org.nms.constants.Config;


public class Server extends AbstractVerticle
{
    public static final String CREDENTIALS_ENDPOINT = "/api/v1/credential/*";
    public static final String DISCOVERY_ENDPOINT = "/api/v1/discovery/*";
    public static final String PROVISION_ENDPOINT = "/api/v1/provision/*";
    public static final String POLLING_ENDPOINT = "/api/v1/polling/*";
    public static final String USER_ENDPOINT = "/api/v1/user/*";
    public static final int HTTP_PORT = 8080;

    private static final JWTAuthOptions config = new JWTAuthOptions()
                                                    .setKeyStore(new KeyStoreOptions().setPath(Config.KEYSTORE_PATH).setPassword(Config.JWT_SECRET));

    public static final JWTAuth jwtAuth = JWTAuth.create(App.vertx, config);

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
                .handler(JWTAuthHandler.create(jwtAuth))
                .handler(AuthMiddleware::authenticate);

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

        // ===== Listen on Port... =====
        server.listen(HTTP_PORT, http ->
        {
            if(http.succeeded())
            {
                ConsoleLogger.info("✅ HTTP Server Started On Port => " + HTTP_PORT + " On Thread [ " + Thread.currentThread().getName() + " ] ");
                startPromise.complete();
            }
            else
            {
                ConsoleLogger.error("Failed To Start HTTP Server => " + http.cause());
                startPromise.fail(http.cause());
            }
        });
    }

    private void setupUserRoutes(Router router)
    {
        var userRouter = Router.router(vertx);

        userRouter.get("/")
                .handler(UserHandler::getUsers);

        userRouter.get("/:id")
                .handler(JWTAuthHandler.create(Server.jwtAuth))
                .handler(AuthMiddleware::authenticate)
                .handler(UserRequestValidator::getUserByIdRequestValidator)
                .handler(UserHandler::getUserById);

        userRouter.post("/login")
                .handler(UserRequestValidator::loginRequestValidator)
                .handler(UserHandler::login);

        userRouter.post("/register")
                .handler(UserRequestValidator::registerRequestValidator)
                .handler(UserHandler::register);

        userRouter.patch("/:id")
                .handler(JWTAuthHandler.create(Server.jwtAuth))
                .handler(AuthMiddleware::authenticate)
                .handler(UserRequestValidator::updateUserRequestValidator)
                .handler(UserHandler::updateUser);

        userRouter.delete("/:id")
                .handler(JWTAuthHandler.create(Server.jwtAuth))
                .handler(AuthMiddleware::authenticate)
                .handler(UserRequestValidator::deleteUserRequestValidator)
                .handler(UserHandler::deleteUser);

        router.route(USER_ENDPOINT).subRouter(userRouter);
    }

    private void setupProvisionRoutes(Router router)
    {
        var provisionRouter = Router.router(App.vertx);

        provisionRouter.get("/")
                .handler(JWTAuthHandler.create(jwtAuth))
                .handler(ProvisionHandler::getAllProvisions);

        provisionRouter.get("/:id")
                .handler(ProvisionRequestValidator::getProvisionByIdRequestValidator)
                .handler(ProvisionHandler::getProvisionById);

        provisionRouter.post("/")
                .handler(ProvisionRequestValidator::createProvisionRequestValidator)
                .handler(ProvisionHandler::createProvision);

        provisionRouter.patch("/:id")
                .handler(ProvisionRequestValidator::updateProvisionRequestValidator)
                .handler(ProvisionHandler::updateMetric);

        provisionRouter.delete("/:id")
                .handler(ProvisionRequestValidator::deleteProvisionRequestValidator)
                .handler(ProvisionHandler::deleteProvision);

        router.route(PROVISION_ENDPOINT).subRouter(provisionRouter);
    }

    private void setupDiscoveryRoutes(Router router)
    {
        var discoveryRouter = Router.router(vertx);

        discoveryRouter.get("/results/:id")
                .handler(DiscoveryRequestValidator::getDiscoveryByIdRequestValidator)
                .handler(DiscoveryHandler::getDiscoveryResultsById);

        discoveryRouter.get("/results")
                .handler(DiscoveryHandler::getDiscoveryResults);

        discoveryRouter.get("/")
                .handler(DiscoveryHandler::getAllDiscoveries);

        discoveryRouter.get("/:id")
                .handler(DiscoveryRequestValidator::getDiscoveryByIdRequestValidator)
                .handler(DiscoveryHandler::getDiscoveryById);

        discoveryRouter.post("/")
                .handler(DiscoveryRequestValidator::createDiscoveryRequestValidator)
                .handler(DiscoveryHandler::createDiscovery);

        discoveryRouter.post("/run/:id")
                .handler(DiscoveryRequestValidator::runDiscoveryRequestValidator)
                .handler(DiscoveryHandler::runDiscovery);

        discoveryRouter.patch("/:id")
                .handler(DiscoveryRequestValidator::updateDiscoveryRequestValidator)
                .handler(DiscoveryHandler::updateDiscovery);

        discoveryRouter.patch("/credential/:id")
                .handler(DiscoveryRequestValidator::updateDiscoveryCredentialsRequestValidator)
                .handler(DiscoveryHandler::updateDiscoveryCredentials);

        discoveryRouter.delete("/:id")
                .handler(DiscoveryRequestValidator::deleteDiscoveryRequestValidator)
                .handler(DiscoveryHandler::deleteDiscovery);

        router.route(DISCOVERY_ENDPOINT).subRouter(discoveryRouter);
    }

    private void setupCredentialRoutes(Router router)
    {
        var credentialRouter = Router.router(vertx);

        credentialRouter.get("/")
                .handler(CredentialHandler::getAllCredentials);

        credentialRouter.get("/:id")
                .handler(CredentialRequestValidator::getCredentialByIdRequestValidator)
                .handler(CredentialHandler::getCredentialById);

        credentialRouter.post("/")
                .handler(CredentialRequestValidator::createCredentialRequestValidator)
                .handler(CredentialHandler::createCredential);

        credentialRouter.patch("/:id")
                .handler(CredentialRequestValidator::updateCredentialByIdRequestValidator)
                .handler(CredentialHandler::updateCredential);

        credentialRouter.delete("/:id")
                .handler(CredentialRequestValidator::deleteCredentialByIdRequestValidator)
                .handler(CredentialHandler::deleteCredential);

        router.route(CREDENTIALS_ENDPOINT).subRouter(credentialRouter);
    }

    private void setupPollingRoutes(Router router)
    {
        var pollingRouter = Router.router(vertx);

        pollingRouter.get("/")
                .handler(MetricResultHandler::getAllPolledData);

        router.route(POLLING_ENDPOINT).subRouter(pollingRouter);
    }

    @Override
    public void stop()
    {
        ConsoleLogger.info("Http Server Stopped");
    }
}