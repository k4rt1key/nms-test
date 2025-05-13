package org.nms.api.auth;

import io.vertx.ext.web.RoutingContext;
import org.nms.api.helpers.HttpResponse;

public class AuthMiddleware
{
    public static void authenticate(RoutingContext ctx)
    {
        if(ctx.user() == null)
        {
            HttpResponse.sendFailure(ctx, 401, "Unauthorized");
            return;
        }

        if(ctx.user().principal().isEmpty())
        {
            HttpResponse.sendFailure(ctx, 401, "Unauthorized");
            return;
        }

        if(ctx.user().expired())
        {
            HttpResponse.sendFailure(ctx, 401, "Unauthorized");
        }

        ctx.next();
    }
}
