package org.nms.api.validators;

import io.vertx.ext.web.RoutingContext;
import org.nms.api.helpers.HttpResponse;
import org.nms.api.helpers.Ip;
import org.nms.constants.Fields;

public class DiscoveryRequestValidator
{

    private static final String INPUT_CREDENTIALS_FIELD = "credentials";

    public static final String INPUT_ADD_CREDENTIALS_FIELD = "add_credentials";

    public static final String INPUT_REMOVE_CREDENTIALS_FIELD = "remove_credentials";

    public static void getDiscoveryByIdRequestValidator(RoutingContext ctx)
    {
        if(Utility.validateID(ctx)) { return; }
        
        ctx.next();
    }

    public static void createDiscoveryRequestValidator(RoutingContext ctx)
    {

        if(Utility.validateBody(ctx)) { return; }

        if(Utility.validateInputFields(ctx, new String[]{Fields.Discovery.IP, Fields.Discovery.IP_TYPE, Fields.Discovery.NAME, Fields.Discovery.PORT}, true)) { return; }

        var body = ctx.body().asJsonObject();

        var credentials = body.getJsonArray(INPUT_CREDENTIALS_FIELD);

        if (credentials == null || credentials.isEmpty())
        {
            HttpResponse.sendFailure(ctx, 400, "Missing or empty 'credentials' field");
            return;
        }

        for (var credential : credentials)
        {
            try
            {
                Integer.parseInt(credential.toString());
            }
            catch (NumberFormatException e)
            {
                HttpResponse.sendFailure(ctx, 400, "Invalid credential ID: " + credential);
                return;
            }
        }

        var port = body.getInteger(Fields.Discovery.PORT);

        if (port < 1 || port > 65535)
        {
            HttpResponse.sendFailure(ctx, 400, "Port must be between 1 and 65535");
            return;
        }

        var ipType = body.getString(Fields.Discovery.IP_TYPE);

        if (!ipType.equals("SINGLE") && !ipType.equals("RANGE") && !ipType.equals("SUBNET"))
        {
            HttpResponse.sendFailure(ctx, 400, "Invalid IP type. Only 'SINGLE', 'RANGE' and 'SUBNET' are supported");
            return;
        }

        var ip = body.getString(Fields.Discovery.IP);

        if (Ip.isValidIpAndType(ip, ipType))
        {
            HttpResponse.sendFailure(ctx, 400, "Invalid IP address or mismatch Ip and IpType: " + ip + ", " + ipType);
            return;
        }

        ctx.next();
    }

    public static void updateDiscoveryRequestValidator(RoutingContext ctx)
    {
        if(Utility.validateID(ctx)) { return; }

        if(Utility.validateBody(ctx)) { return; }

        if(Utility.validateInputFields(ctx, new String[]{Fields.Discovery.IP, Fields.Discovery.IP_TYPE, Fields.Discovery.NAME, Fields.Discovery.PORT}, false)) { return; }

        if(! Utility.validatePort(ctx)) { return; }

        var ipType = ctx.body().asJsonObject().getString(Fields.Discovery.IP_TYPE);

        if (ipType != null && !ipType.equals("SINGLE") && !ipType.equals("RANGE") && !ipType.equals("CIDR"))
        {
            HttpResponse.sendFailure(ctx, 400, "Invalid IP type. Only 'SINGLE', 'RANGE' and 'SUBNET' are supported");
        }

        var ip = ctx.body().asJsonObject().getString(Fields.Discovery.IP);

        if (ip != null && ipType != null && Ip.isValidIpAndType(ip, ipType))
        {
            HttpResponse.sendFailure(ctx, 400, "Invalid IP address or mismatch Ip and IpType: " + ip + ", " + ipType);
            return;
        }

        ctx.next();
    }

    public static void updateDiscoveryCredentialsRequestValidator(RoutingContext ctx)
    {
        if(Utility.validateID(ctx)) { return; }

        if(Utility.validateBody(ctx)) { return; }

        var body = ctx.body().asJsonObject();

        var add_credentials = body.getJsonArray(INPUT_ADD_CREDENTIALS_FIELD);

        var remove_credentials = body.getJsonArray(INPUT_REMOVE_CREDENTIALS_FIELD);

        if (
                (add_credentials == null || add_credentials.isEmpty()) &&
                (remove_credentials == null || remove_credentials.isEmpty())
        )
        {
            HttpResponse.sendFailure(ctx, 400, "Missing or empty ' " + INPUT_ADD_CREDENTIALS_FIELD  + " ' and ' " + INPUT_REMOVE_CREDENTIALS_FIELD + " ' fields");
            return;
        }

        if (add_credentials != null && !add_credentials.isEmpty())
        {
            for (var credential : add_credentials)
            {
                try
                {
                    Integer.parseInt(credential.toString());
                }
                catch (NumberFormatException e)
                {
                    HttpResponse.sendFailure(ctx, 400, "Invalid credential ID in " + INPUT_ADD_CREDENTIALS_FIELD + " : " + credential);
                    return;
                }
            }
        }

        if (remove_credentials != null && !remove_credentials.isEmpty())
        {
            for (var credential : remove_credentials)
            {
                try
                {
                    Integer.parseInt(credential.toString());
                }
                catch (NumberFormatException e)
                {
                    HttpResponse.sendFailure(ctx, 400, "Invalid credential ID in " + INPUT_REMOVE_CREDENTIALS_FIELD + " : " + credential);
                    return;
                }
            }
        }

        ctx.next();
    }

    public static void runDiscoveryRequestValidator(RoutingContext ctx)
    {
        if(Utility.validateID(ctx)) { return; }

        ctx.next();
    }

    public static void deleteDiscoveryRequestValidator(RoutingContext ctx)
    {
        if(Utility.validateID(ctx)) { return; }

        ctx.next();
    }
}