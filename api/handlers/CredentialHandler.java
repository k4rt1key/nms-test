package org.nms.api.handlers;

import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.RoutingContext;
import org.nms.api.helpers.HttpResponse;
import org.nms.constants.Queries;
import org.nms.database.helpers.DbEventBus;

public class CredentialHandler
{
    public static void getAllCredentials(RoutingContext ctx)
    {
        var queryRequest = DbEventBus.sendQueryExecutionRequest(Queries.Credential.GET_ALL);
        queryRequest.onComplete(ar ->
        {
            if (ar.succeeded())
            {
                var credentials = ar.result();
                if (credentials.isEmpty())
                {
                    HttpResponse.sendFailure(ctx, 404, "No credentials found");
                    return;
                }
                HttpResponse.sendSuccess(ctx, 200, "Credentials found", credentials);
            }
            else
            {
                HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", ar.cause().getMessage());
            }
        });
    }

    public static void getCredentialById(RoutingContext ctx)
    {
        var id = Integer.parseInt(ctx.request().getParam("id"));
        var queryRequest = DbEventBus.sendQueryExecutionRequest(Queries.Credential.GET_BY_ID, new JsonArray().add(id));
        queryRequest.onComplete(ar ->
        {
            if (ar.succeeded())
            {
                var credential = ar.result();
                if (credential.isEmpty())
                {
                    HttpResponse.sendFailure(ctx, 404, "Credential not found");
                    return;
                }
                HttpResponse.sendSuccess(ctx, 200, "Credential found", credential);
            }
            else
            {
                HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", ar.cause().getMessage());
            }
        });
    }

    public static void createCredential(RoutingContext ctx)
    {
        var name = ctx.body().asJsonObject().getString("name");
        var username = ctx.body().asJsonObject().getString("username");
        var password = ctx.body().asJsonObject().getString("password");
        var insertRequest = DbEventBus.sendQueryExecutionRequest(Queries.Credential.INSERT, new JsonArray()
                .add(name)
                .add(username)
                .add(password)
        );
        insertRequest.onComplete(ar ->
        {
            if (ar.succeeded())
            {
                var credential = ar.result();
                if (credential.isEmpty())
                {
                    HttpResponse.sendFailure(ctx, 400, "Cannot create credential");
                    return;
                }
                HttpResponse.sendSuccess(ctx, 201, "Credential created", credential);
            }
            else
            {
                HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", ar.cause().getMessage());
            }
        });
    }

    public static void updateCredential(RoutingContext ctx)
    {
        var id = Integer.parseInt(ctx.request().getParam("id"));
        var checkRequest = DbEventBus.sendQueryExecutionRequest(Queries.Credential.GET_BY_ID, new JsonArray().add(id));
        checkRequest.onComplete(ar ->
        {
            if (ar.succeeded())
            {
                var credential = ar.result();
                if (credential.isEmpty())
                {
                    HttpResponse.sendFailure(ctx, 404, "Credential not found");
                    return;
                }

                var name = ctx.body().asJsonObject().getString("name");
                var username = ctx.body().asJsonObject().getString("username");
                var password = ctx.body().asJsonObject().getString("password");
                var updateRequest = DbEventBus.sendQueryExecutionRequest(Queries.Credential.UPDATE, new JsonArray()
                        .add(id)
                        .add(name)
                        .add(username)
                        .add(password)
                );
                updateRequest.onComplete(updateAr ->
                {
                    if (updateAr.succeeded())
                    {
                        var res = updateAr.result();
                        HttpResponse.sendSuccess(ctx, 200, "Credential updated successfully", res);
                    }
                    else
                    {
                        HttpResponse.sendFailure(ctx, 500, updateAr.cause().getMessage());
                    }
                });
            }
            else
            {
                HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", ar.cause().getMessage());
            }
        });
    }

    public static void deleteCredential(RoutingContext ctx)
    {
        var id = Integer.parseInt(ctx.request().getParam("id"));
        var checkRequest = DbEventBus.sendQueryExecutionRequest(Queries.Credential.GET_BY_ID, new JsonArray().add(id));
        checkRequest.onComplete(ar ->
        {
            if (ar.succeeded())
            {
                var credential = ar.result();
                if (credential.isEmpty())
                {
                    HttpResponse.sendFailure(ctx, 404, "Credential not found");
                    return;
                }

                var deleteRequest = DbEventBus.sendQueryExecutionRequest(Queries.Credential.DELETE, new JsonArray().add(id));
                deleteRequest.onComplete(delAr ->
                {
                    if (delAr.succeeded())
                    {
                        var res = delAr.result();
                        HttpResponse.sendSuccess(ctx, 200, "Credential deleted successfully", res);
                    }
                    else
                    {
                        HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", delAr.cause().getMessage());
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