package org.nms.api.handlers;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.nms.api.Server;
import org.nms.api.helpers.HttpResponse;
import org.nms.database.helpers.DbEventBus;
import org.nms.constants.Queries.*;

public class UserHandler
{
    public static void getUsers(RoutingContext ctx)
    {
        var queryRequest = DbEventBus.sendQueryExecutionRequest(User.GET_ALL);
        queryRequest.onComplete(ar ->
        {
            if (ar.succeeded())
            {
                var users = ar.result();
                if (users.isEmpty())
                {
                    HttpResponse.sendFailure(ctx, 404, "No users found");
                    return;
                }
                HttpResponse.sendSuccess(ctx, 200, "Users found", users);
            }
            else
            {
                HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", ar.cause().getMessage());
            }
        });
    }

    public static void getUserById(RoutingContext ctx)
    {
        var id = Integer.parseInt(ctx.request().getParam("id"));
        var queryRequest = DbEventBus.sendQueryExecutionRequest(User.GET_BY_ID, new JsonArray().add(id));
        queryRequest.onComplete(ar ->
        {
            if (ar.succeeded())
            {
                var user = ar.result();
                if (user.isEmpty())
                {
                    HttpResponse.sendFailure(ctx, 404, "User not found");
                    return;
                }
                HttpResponse.sendSuccess(ctx, 200, "User found", user);
            }
            else
            {
                HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", ar.cause().getMessage());
            }
        });
    }

    public static void register(RoutingContext ctx)
    {
        var checkRequest = DbEventBus.sendQueryExecutionRequest(User.GET_BY_NAME, new JsonArray().add(ctx.body().asJsonObject().getString("name")));
        checkRequest.compose(user ->
        {
            if (user != null && !user.isEmpty())
            {
                HttpResponse.sendFailure(ctx, 409, "User already exists");
                return Future.failedFuture(new Exception("User Already Exist"));
            }
            return Future.succeededFuture();
        })
                .compose(v ->
                    DbEventBus.sendQueryExecutionRequest(User.INSERT, new JsonArray()
                            .add(ctx.body().asJsonObject().getString("name"))
                            .add(ctx.body().asJsonObject().getString("password"))
                    ))
                .onComplete(ar ->
                 {
                    if (ar.succeeded())
                    {
                        var user = ar.result();
                        if (user.isEmpty())
                        {
                            HttpResponse.sendFailure(ctx, 400, "Cannot register user");
                            return;
                        }
                        var jwtToken = Server.jwtAuth
                                .generateToken(new JsonObject().put("id", user.getJsonObject(0).getInteger("id")));
                        user.getJsonObject(0).put("jwt", jwtToken);
                        HttpResponse.sendSuccess(ctx, 201, "User registered", user);
                    }
                    else
                    {
                        HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", ar.cause().getMessage());
                    }
                 });
    }

    public static void login(RoutingContext ctx)
    {
        var username = ctx.body().asJsonObject().getString("name");
        var queryRequest = DbEventBus.sendQueryExecutionRequest(User.GET_BY_NAME, new JsonArray().add(username));
        queryRequest.onComplete(ar ->
        {
            if (ar.succeeded())
            {
                var user = ar.result();
                if (user == null || user.isEmpty())
                {
                    HttpResponse.sendFailure(ctx, 404, "User not found");
                    return;
                }

                var password = ctx.body().asJsonObject().getString("password");
                if (!user.getJsonObject(0).getString("password").equals(password))
                {
                    HttpResponse.sendFailure(ctx, 401, "Invalid password");
                    return;
                }

                var jwtToken = Server.jwtAuth.generateToken(new JsonObject()
                        .put("id", user.getJsonObject(0).getInteger("id")));
                user.getJsonObject(0).put("jwt", jwtToken);
                HttpResponse.sendSuccess(ctx, 200, "User logged in", user);
            }
            else
            {
                HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", ar.cause().getMessage());
            }
        });
    }

    public static void updateUser(RoutingContext ctx)
    {
        var id = Integer.parseInt(ctx.request().getParam("id"));
        var username = ctx.body().asJsonObject().getString("name");
        var password = ctx.body().asJsonObject().getString("password");
        var updateRequest = DbEventBus.sendQueryExecutionRequest(User.UPDATE, new JsonArray()
                .add(id)
                .add(username)
                .add(password)
        );
        updateRequest.onComplete(ar ->
        {
            if (ar.succeeded())
            {
                var res = ar.result();
                HttpResponse.sendSuccess(ctx, 200, "User updated successfully", res);
            }
            else
            {
                HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", ar.cause().getMessage());
            }
        });
    }

    public static void deleteUser(RoutingContext ctx)
    {
        var id = Integer.parseInt(ctx.request().getParam("id"));
        var checkRequest = DbEventBus.sendQueryExecutionRequest(User.GET_BY_ID, new JsonArray().add(id));
        checkRequest.onComplete(ar ->
        {
            if (ar.succeeded())
            {
                var res = ar.result();
                if (res.isEmpty())
                {
                    HttpResponse.sendFailure(ctx, 404, "User not found");
                    return;
                }

                var loggedInUser = ctx.user().principal().getInteger("id");
                if (id != loggedInUser)
                {
                    HttpResponse.sendFailure(ctx, 403, "You are not authorized to delete this user");
                    return;
                }

                var deleteRequest = DbEventBus.sendQueryExecutionRequest(User.DELETE, new JsonArray().add(id));
                deleteRequest.onComplete(delAr ->
                {
                    if (delAr.succeeded())
                    {
                        var delRes = delAr.result();
                        HttpResponse.sendSuccess(ctx, 200, "User deleted Successfully", delRes);
                    }
                    else
                    {
                        HttpResponse.sendFailure(ctx, 500, "Error deleting user: " + delAr.cause().getMessage());
                    }
                });
            }
            else
            {
                HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", ar.cause().getMessage());
            }
        });
    }
}