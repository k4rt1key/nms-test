package org.nms.api.handlers;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.nms.api.Server;
import org.nms.api.Utility;
import org.nms.api.Validators;
import org.nms.constants.Fields;
import org.nms.constants.Queries;
import org.nms.database.DbUtility;

public class User
{
    public static void getUsers(RoutingContext ctx)
    {
        DbUtility.sendQueryExecutionRequest(Queries.User.GET_ALL).onComplete(asyncResult ->
        {
            if (asyncResult.succeeded())
            {
                var users = asyncResult.result();

                if (users.isEmpty())
                {
                    Utility.sendFailure(ctx, 404, "No users found");

                    return;
                }
                Utility.sendSuccess(ctx, 200, "Users found", users);
            }
            else
            {
                Utility.sendFailure(ctx, 500, "Something Went Wrong", asyncResult.cause().getMessage());
            }
        });
    }

    public static void getUserById(RoutingContext ctx)
    {
        var id = Validators.validateID(ctx);

        if(id == -1) { return; }

        DbUtility.sendQueryExecutionRequest(Queries.User.GET_BY_ID, new JsonArray().add(id)).onComplete(asyncResult ->
        {
            if (asyncResult.succeeded())
            {
                var user = asyncResult.result();

                if (user.isEmpty())
                {
                    Utility.sendFailure(ctx, 404, "User not found");
                    return;
                }

                Utility.sendSuccess(ctx, 200, "User found", user);
            }
            else
            {
                Utility.sendFailure(ctx, 500, "Something Went Wrong", asyncResult.cause().getMessage());
            }
        });
    }

    public static void register(RoutingContext ctx)
    {
        if(Validators.validateBody(ctx)) { return; }

        if (
                Validators.validateInputFields(ctx, new String[]{
                        Fields.User.NAME,
                        Fields.User.PASSWORD}, true)
        ) { return; }

        DbUtility.sendQueryExecutionRequest(Queries.User.GET_BY_NAME, new JsonArray().add(ctx.body().asJsonObject().getString("name")))

                .compose(user ->
                {
                    if (user != null && !user.isEmpty())
                    {
                        Utility.sendFailure(ctx, 409, "User already exists");

                        return Future.failedFuture(new Exception("User Already Exist"));
                    }

                    return Future.succeededFuture();
                })

                .compose(useExist ->

                    DbUtility.sendQueryExecutionRequest(Queries.User.INSERT, new JsonArray()
                            .add(ctx.body().asJsonObject().getString("name"))
                            .add(ctx.body().asJsonObject().getString("password"))
                    ))

                .onComplete(userInsertion ->
                 {
                    if (userInsertion.succeeded())
                    {
                        var user = userInsertion.result();

                        if (user.isEmpty())
                        {
                            Utility.sendFailure(ctx, 400, "Cannot register user");

                            return;
                        }

                        var jwtToken = Server.jwtAuth
                                .generateToken(new JsonObject().put("id", user.getJsonObject(0).getInteger("id")));

                        user.getJsonObject(0).put("jwt", jwtToken);

                        Utility.sendSuccess(ctx, 201, "User registered", user);
                    }
                    else
                    {
                        Utility.sendFailure(ctx, 500, "Something Went Wrong", userInsertion.cause().getMessage());
                    }
                 });
    }

    public static void login(RoutingContext ctx)
    {

        if(Validators.validateBody(ctx)) { return; }

        if (
                Validators.validateInputFields(ctx, new String[]{
                        Fields.User.NAME,
                        Fields.User.PASSWORD}, true)
        ) { return; }

        var username = ctx.body().asJsonObject().getString("name");

        DbUtility.sendQueryExecutionRequest(Queries.User.GET_BY_NAME, new JsonArray().add(username)).onComplete(asyncResult ->
        {
            if (asyncResult.succeeded())
            {
                var user = asyncResult.result();

                if (user == null || user.isEmpty())
                {
                    Utility.sendFailure(ctx, 404, "User not found");

                    return;
                }

                var password = ctx.body().asJsonObject().getString("password");

                if (!user.getJsonObject(0).getString("password").equals(password))
                {
                    Utility.sendFailure(ctx, 401, "Invalid password");

                    return;
                }

                var jwtToken = Server.jwtAuth.generateToken(new JsonObject()
                        .put("id", user.getJsonObject(0).getInteger("id")));

                user.getJsonObject(0).put("jwt", jwtToken);

                Utility.sendSuccess(ctx, 200, "User logged in", user);
            }
            else
            {
                Utility.sendFailure(ctx, 500, "Something Went Wrong", asyncResult.cause().getMessage());
            }
        });
    }

    public static void updateUser(RoutingContext ctx)
    {
        var id = Validators.validateID(ctx);

        if(id == -1) { return; }

        if(Validators.validateBody(ctx)) { return; }

        if (
                Validators.validateInputFields(ctx, new String[]{
                        Fields.User.NAME,
                        Fields.User.PASSWORD}, false)
        ) { return; }

        var username = ctx.body().asJsonObject().getString("name");

        var password = ctx.body().asJsonObject().getString("password");

        DbUtility.sendQueryExecutionRequest(Queries.User.UPDATE, new JsonArray()
                .add(id)
                .add(username)
                .add(password)
        ).onComplete(asyncResult ->
        {
            if (asyncResult.succeeded())
            {
                var updatedUser = asyncResult.result();

                Utility.sendSuccess(ctx, 200, "User updated successfully", updatedUser);
            }
            else
            {
                Utility.sendFailure(ctx, 500, "Something Went Wrong", asyncResult.cause().getMessage());
            }
        });
    }

    public static void deleteUser(RoutingContext ctx)
    {
        var id = Validators.validateID(ctx);

        if(id == -1) { return; }

        DbUtility.sendQueryExecutionRequest(Queries.User.GET_BY_ID, new JsonArray().add(id)).onComplete(asyncResult ->
        {
            if (asyncResult.succeeded())
            {
                var user = asyncResult.result();

                if (user.isEmpty())
                {
                    Utility.sendFailure(ctx, 404, "User not found");

                    return;
                }

                var loggedInUser = ctx.user().principal().getInteger("id");

                if (id != loggedInUser)
                {
                    Utility.sendFailure(ctx, 403, "You are not authorized to delete this user");

                    return;
                }

                DbUtility.sendQueryExecutionRequest(Queries.User.DELETE, new JsonArray().add(id)).onComplete(userDeletion ->
                {
                    if (userDeletion.succeeded())
                    {
                        var deletedUser = userDeletion.result();

                        Utility.sendSuccess(ctx, 200, "User deleted Successfully", deletedUser);
                    }
                    else
                    {
                        Utility.sendFailure(ctx, 500, "Error deleting user: " + userDeletion.cause().getMessage());
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