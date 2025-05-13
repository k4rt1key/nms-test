package org.nms.api.handlers;

import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.RoutingContext;
import org.nms.api.helpers.HttpResponse;
import org.nms.constants.Queries;
import org.nms.database.DbEngine;

public class CredentialHandler
{
    public static void getAllCredentials(RoutingContext ctx)
    {
        DbEngine.execute(Queries.Credential.GET_ALL)
                .onSuccess(credentials ->
                {
                    // Credentials not found
                    if(credentials.isEmpty())
                    {
                        HttpResponse.sendFailure(ctx, 404, "No credentials found");
                        return;
                    }

                    // Credentials Found
                    HttpResponse.sendSuccess(ctx, 200, "Credentials found", credentials);
                })
                .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));
    }

    public static void getCredentialById(RoutingContext ctx)
    {
        int id = Integer.parseInt(ctx.request().getParam("id"));

        DbEngine.execute(Queries.Credential.GET_BY_ID, new JsonArray().add(id))
                .onSuccess(credential ->
                {
                    // Credential not found
                    if (credential.isEmpty())
                    {
                        HttpResponse.sendFailure(ctx, 404, "Credential not found");
                        return;
                    }

                    // Credential found
                    HttpResponse.sendSuccess(ctx, 200, "Credential found", credential);
                })
                .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));
    }

    public static void createCredential(RoutingContext ctx)
    {
        var name = ctx.body().asJsonObject().getString("name");
        var username = ctx.body().asJsonObject().getString("username");
        var password = ctx.body().asJsonObject().getString("password");

                DbEngine.execute(Queries.Credential.INSERT, new JsonArray()
                        .add(name)
                        .add(username)
                        .add(password)
                )
                .onSuccess(credential ->
                {
                    // Credential in response not found
                    if (credential.isEmpty())
                    {
                        HttpResponse.sendFailure(ctx, 400, "Cannot create credential");
                        return;
                    }

                    HttpResponse.sendSuccess(ctx, 201, "Credential created", credential);
                })
                .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));
    }

    public static void updateCredential(RoutingContext ctx)
    {
        var id = Integer.parseInt(ctx.request().getParam("id"));

        // First check if credential exists
        DbEngine.execute(Queries.Credential.GET_BY_ID, new JsonArray().add(id))
                .onSuccess(credential ->
                {
                    // Credential not found
                    if (credential.isEmpty())
                    {
                        HttpResponse.sendFailure(ctx, 404, "Credential not found");
                        return;
                    }

                    // Credential found, proceed with update
                    var name = ctx.body().asJsonObject().getString("name");
                    var username = ctx.body().asJsonObject().getString("username");
                    var password = ctx.body().asJsonObject().getString("password");

                    DbEngine.execute(Queries.Credential.UPDATE, new JsonArray()
                                    .add(id)
                                    .add(name)
                                    .add(username)
                                    .add(password)
                            )
                            .onSuccess(res -> HttpResponse.sendSuccess(ctx, 200, "Credential updated successfully", res))
                            .onFailure(err -> HttpResponse.sendFailure(ctx, 500, err.getMessage()));
                })
                .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));
    }

    public static void deleteCredential(RoutingContext ctx)
    {
        var id = Integer.parseInt(ctx.request().getParam("id"));

        // First check if credential exists
        DbEngine.execute(Queries.Credential.GET_BY_ID, new JsonArray().add(id))
                .onSuccess(credential ->
                {
                    // Credential not found
                    if (credential.isEmpty())
                    {
                        HttpResponse.sendFailure(ctx, 404, "Credential not found");
                        return;
                    }

                    // Credential found, proceed with delete
                    DbEngine.execute(Queries.Credential.DELETE, new JsonArray().add(id))
                            .onSuccess(res -> HttpResponse.sendSuccess(ctx, 200, "Credential deleted successfully", credential))
                            .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));
                })
                .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));
    }
}