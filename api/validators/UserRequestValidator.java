package org.nms.api.validators;

import io.vertx.ext.web.RoutingContext;
import org.nms.constants.Fields;

public class UserRequestValidator
{

    public static void getUserByIdRequestValidator(RoutingContext ctx)
    {
        if(Utility.validateID(ctx)) {  return; }

        ctx.next();
    }

    public static void registerRequestValidator(RoutingContext ctx)
    {

        if(Utility.validateBody(ctx)) { return; }

        if(Utility.validateInputFields(ctx, new String[]{Fields.User.NAME, Fields.User.PASSWORD}, true)) { return; }

        ctx.next();
    }

    public static void loginRequestValidator(RoutingContext ctx)
    {
        if(Utility.validateBody(ctx)) { return; }

        if(Utility.validateInputFields(ctx, new String[]{Fields.User.NAME, Fields.User.PASSWORD}, true)) { return; }

        ctx.next();
    }

    public static void updateUserRequestValidator(RoutingContext ctx)
    {

        if(Utility.validateID(ctx)) { return; }

        if(Utility.validateBody(ctx)) { return; }

        if(Utility.validateInputFields(ctx, new String[]{Fields.User.NAME, Fields.User.PASSWORD}, false)) { return; }

        ctx.next();
    }


    public static void deleteUserRequestValidator(RoutingContext ctx)
    {
        if(Utility.validateID(ctx)) { return; }

        ctx.next();
    }
}
