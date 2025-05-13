package org.nms.api.validators;

import io.vertx.ext.web.RoutingContext;
import org.nms.api.helpers.HttpResponse;

public class Utility
{

    private static final String ID = "id";

    public static final String PORT = "port";

    public static boolean validateID(RoutingContext ctx)
    {
        var id = ctx.request().getParam(ID);

        if (id == null || id.isEmpty())
        {
            HttpResponse.sendFailure(ctx, 400,"Missing or empty 'id' parameter");
            return true;
        }

        try
        {
            Integer.parseInt(id);
        }
        catch (IllegalArgumentException e)
        {
            HttpResponse.sendFailure(ctx, 400,"Invalid 'id' parameter. Must be a positive integer");
            return true;
        }

        return false;
    }

    public static boolean validateBody(RoutingContext ctx)
    {
        try
        {
            var body = ctx.body().asJsonObject();

            if (body == null || body.isEmpty())
            {
                HttpResponse.sendFailure(ctx, 400, "Missing or empty request body");
                return true;
            }
        }
        catch (Exception e)
        {
            HttpResponse.sendFailure(ctx, 400, "Invalid Json");
            return true;
        }

        return false;
    }

    public static boolean validateInputFields(RoutingContext ctx, String[] requiredFields, boolean isAllRequired)
    {

        try {

            var body = ctx.body().asJsonObject();

            var missingFields = new StringBuilder();

            boolean isAllMissing = true;

            for (String requiredField : requiredFields)
            {
                if (body.getString(requiredField) == null)
                {
                    missingFields
                            .append(requiredField)
                            .append(", ");
                }
                else
                {
                    isAllMissing = false;
                }
            }

            if (isAllRequired && !missingFields.isEmpty())
            {
                HttpResponse.sendFailure(ctx, 400, "Missing required fields: " + missingFields);
                return true;
            }

            if (!isAllRequired && isAllMissing)
            {
                HttpResponse.sendFailure(ctx, 400, "Missing required fields, Provide any of " + missingFields);
                return true;
            }

        }
        catch (Exception e)
        {
            HttpResponse.sendFailure(ctx, 400, "Invalid Json");
            return true;
        }

        return false;
    }

    public static boolean validatePort(RoutingContext ctx)
    {
        var port = ctx.body().asJsonObject().getInteger(PORT);

        if (port != null && (port < 1 || port > 65535))
        {
            HttpResponse.sendFailure(ctx, 400, "Port must be between 1 and 65535");
            return false;
        }

        return true;
    }
}
