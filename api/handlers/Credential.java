package org.nms.api.handlers;

import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.RoutingContext;
import org.nms.api.Utility;
import org.nms.api.Validators;
import org.nms.constants.Fields;
import org.nms.constants.Queries;
import org.nms.database.DbUtility;

public class Credential
{
    public static void getAllCredentials(RoutingContext ctx)
    {
        DbUtility.sendQueryExecutionRequest(Queries.Credential.GET_ALL).onComplete(asyncResult ->
        {
            if (asyncResult.succeeded())
            {
                var credentials = asyncResult.result();

                if (credentials.isEmpty())
                {
                    Utility.sendFailure(ctx, 404, "No credentials found");
                    return;
                }

                Utility.sendSuccess(ctx, 200, "Credentials found", credentials);
            }
            else
            {
                Utility.sendFailure(ctx, 500, "Something Went Wrong", asyncResult.cause().getMessage());
            }
        });
    }

    public static void getCredentialById(RoutingContext ctx)
    {
        var id = Validators.validateID(ctx);

        if(id == -1) { return; }

        var queryRequest = DbUtility.sendQueryExecutionRequest(Queries.Credential.GET_BY_ID, new JsonArray().add(id));

        queryRequest.onComplete(asyncResult ->
        {
            if (asyncResult.succeeded())
            {
                var credential = asyncResult.result();

                if (credential.isEmpty())
                {
                    Utility.sendFailure(ctx, 404, "Credential not found");

                    return;
                }
                Utility.sendSuccess(ctx, 200, "Credential found", credential);
            }
            else
            {
                Utility.sendFailure(ctx, 500, "Something Went Wrong", asyncResult.cause().getMessage());
            }
        });
    }

    public static void createCredential(RoutingContext ctx)
    {
        Validators.validateBody(ctx);

        if (
                Validators.validateInputFields(ctx, new String[]{
                                                        Fields.Credential.NAME,
                                                        Fields.Credential.USERNAME,
                                                        Fields.Credential.PASSWORD}, true)
        ) { return; }

        var name = ctx.body().asJsonObject().getString("name");

        var username = ctx.body().asJsonObject().getString("username");

        var password = ctx.body().asJsonObject().getString("password");

        DbUtility.sendQueryExecutionRequest(Queries.Credential.INSERT, new JsonArray()
                .add(name)
                .add(username)
                .add(password)
        ).onComplete(asyncResult ->
        {
            if (asyncResult.succeeded())
            {
                var credential = asyncResult.result();
                if (credential.isEmpty())
                {
                    Utility.sendFailure(ctx, 400, "Cannot create credential");
                    return;
                }
                Utility.sendSuccess(ctx, 201, "Credential created", credential);
            }
            else
            {
                Utility.sendFailure(ctx, 500, "Something Went Wrong", asyncResult.cause().getMessage());
            }
        });
    }

    public static void updateCredential(RoutingContext ctx)
    {
        var id = Validators.validateID(ctx);

        if(id == -1) { return; }

        if(Validators.validateBody(ctx)) { return; }

        if (
                Validators.validateInputFields(ctx, new String[]{
                        Fields.Credential.NAME,
                        Fields.Credential.USERNAME,
                        Fields.Credential.PASSWORD}, false)
        ) { return; }

        var checkRequest = DbUtility.sendQueryExecutionRequest(Queries.Credential.GET_BY_ID, new JsonArray().add(id));

        checkRequest.onComplete(asyncResult ->
        {
            if (asyncResult.succeeded())
            {
                var credential = asyncResult.result();

                if (credential.isEmpty())
                {
                    Utility.sendFailure(ctx, 404, "Credential not found");

                    return;
                }

                var name = ctx.body().asJsonObject().getString("name");

                var username = ctx.body().asJsonObject().getString("username");

                var password = ctx.body().asJsonObject().getString("password");

                DbUtility.sendQueryExecutionRequest(Queries.Credential.UPDATE, new JsonArray()
                        .add(id)
                        .add(name)
                        .add(username)
                        .add(password)
                ).onComplete(updateResult ->
                {
                    if (updateResult.succeeded())
                    {
                        var res = updateResult.result();

                        Utility.sendSuccess(ctx, 200, "Credential updated successfully", res);
                    }
                    else
                    {
                        Utility.sendFailure(ctx, 500, updateResult.cause().getMessage());
                    }
                });
            }
            else
            {
                Utility.sendFailure(ctx, 500, "Something Went Wrong", asyncResult.cause().getMessage());
            }
        });
    }

    public static void deleteCredential(RoutingContext ctx)
    {
        var id = Validators.validateID(ctx);

        if(id == -1) { return; }

        DbUtility.sendQueryExecutionRequest(Queries.Credential.GET_BY_ID, new JsonArray().add(id)).onComplete(asyncResult ->
        {
            if (asyncResult.succeeded())
            {
                var credential = asyncResult.result();

                if (credential.isEmpty())
                {
                    Utility.sendFailure(ctx, 404, "Credential not found");

                    return;
                }

                var deleteRequest = DbUtility.sendQueryExecutionRequest(Queries.Credential.DELETE, new JsonArray().add(id));

                deleteRequest.onComplete(deleteResult ->
                {
                    if (asyncResult.succeeded())
                    {
                        var res = asyncResult.result();

                        Utility.sendSuccess(ctx, 200, "Credential deleted successfully", res);
                    }
                    else
                    {
                        Utility.sendFailure(ctx, 500, "Something Went Wrong", asyncResult.cause().getMessage());
                    }
                });
            }
            else
            {
                Utility.sendFailure(ctx, 500, "Something Went Wrong", asyncResult.cause().getMessage());
            }
        });
    }
}