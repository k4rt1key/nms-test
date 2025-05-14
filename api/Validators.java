package org.nms.api;

import inet.ipaddr.IPAddressString;
import io.vertx.ext.web.RoutingContext;
import org.nms.constants.Config;
import org.nms.constants.Fields;

import java.util.ArrayList;
import java.util.List;

import static org.nms.App.logger;
import static org.nms.constants.Fields.Discovery.ADD_CREDENTIALS;
import static org.nms.constants.Fields.Discovery.REMOVE_CREDENTIALS;
import static org.nms.constants.Fields.PluginPollingRequest.CREDENTIALS;
import static org.nms.constants.Fields.PluginPollingRequest.METRIC_GROUPS;

public class Validators
{
    private static final String ID = "id";

    public static final String PORT = "port";

    public static int validateID(RoutingContext ctx)
    {
        var id = ctx.request().getParam(ID);

        if (id == null || id.isEmpty())
        {
            Utility.sendFailure(ctx, 400,"Missing or empty 'id' parameter");

            return -1;
        }

        try
        {
            return Integer.parseInt(id);
        }
        catch (IllegalArgumentException exception)
        {
            Utility.sendFailure(ctx, 400,"Invalid 'id' parameter. Must be a positive integer");

            return -1;
        }
    }

    public static boolean validateBody(RoutingContext ctx)
    {
        try
        {
            var body = ctx.body().asJsonObject();

            if (body == null || body.isEmpty())
            {
                Utility.sendFailure(ctx, 400, "Missing or empty request body");

                return true;
            }
        }
        catch (Exception exception)
        {
            Utility.sendFailure(ctx, 400, "Invalid Json Body");

            return true;
        }

        return false;
    }

    public static boolean validateInputFields(RoutingContext ctx, String[] requiredFields, boolean isAllRequired)
    {

        try {

            var body = ctx.body().asJsonObject();

            var missingFields = new StringBuilder();

            var isAllMissing = true;

            for (var requiredField : requiredFields)
            {
                if (body.getString(requiredField) == null)
                {
                    missingFields.append(requiredField).append(", ");
                }
                else
                {
                    isAllMissing = false;
                }
            }

            if (isAllRequired && !missingFields.isEmpty())
            {
                Utility.sendFailure(ctx, 400, "Missing required fields: " + missingFields);

                return true;
            }

            if (!isAllRequired && isAllMissing)
            {
                Utility.sendFailure(ctx, 400, "Missing required fields, Provide any of " + missingFields);

                return true;
            }

        }
        catch (Exception exception)
        {
            Utility.sendFailure(ctx, 400, "Invalid Json");

            return true;
        }

        return false;
    }

    public static boolean validatePort(RoutingContext ctx)
    {
        var port = ctx.body().asJsonObject().getInteger(PORT);

        if (port != null && (port < 1 || port > 65535))
        {
            Utility.sendFailure(ctx, 400, "Port must be between 1 and 65535");

            return false;
        }

        return true;
    }

    public static boolean validateIpWithIpType(String ip, String ipType)
    {
        if (ip == null || ipType == null)
        {
            return true;
        }

        try
        {
            ipType = ipType.toUpperCase();

            switch (ipType)
            {
                case "SINGLE":

                    var singleAddr = new IPAddressString(ip).getAddress();

                    return ! (singleAddr != null && singleAddr.isIPv4() && singleAddr.getCount().longValue() == 1 );

                case "RANGE":

                    if (!ip.contains("-"))
                    {
                        return true;
                    }

                    var ipParts = ip.split("-");

                    if (ipParts.length != 2)
                    {
                        return true;
                    }

                    var startIp = new IPAddressString(ipParts[0].trim()).getAddress();

                    var endIp = new IPAddressString(ipParts[1].trim()).getAddress();

                    if (startIp == null || endIp == null || !startIp.isIPv4() || !endIp.isIPv4())
                    {
                        return true;
                    }

                    if (startIp.compareTo(endIp) > 0)
                    {
                        return true;
                    }

                    var range = startIp.toSequentialRange(endIp);

                    return ! ( range.getCount().longValue() <= Config.MAX_IP_COUNT );

                case "CIDR":

                    if (!ip.contains("/"))
                    {
                        return true;
                    }

                    var cidr = new IPAddressString(ip).getAddress();

                    if (cidr == null || !cidr.isIPv4() || !cidr.isPrefixed())
                    {
                        return true;
                    }

                    return ! ( cidr.getCount().longValue() <= Config.MAX_IP_COUNT );

                default:
                    return true;
            }
        }
        catch (Exception exception)
        {
            logger.error("Failed to validate ip and ipType, error: " + exception.getMessage() );

            return true;
        }
    }

    public static boolean validateProvisionCreation(RoutingContext ctx)
    {
        final String IPS = "ips";

        final String DISCOVERY_ID = "discovery_id";

        if(Validators.validateBody(ctx)) { return false; }

        if(Validators.validateInputFields(ctx, new String[]{IPS, DISCOVERY_ID}, true)) { return false; }

        var body = ctx.body().asJsonObject();

        var ips = body.getJsonArray(IPS);

        var invalidIps = new StringBuilder();

        for(var ip: ips)
        {
            if(validateIpWithIpType(ip.toString(), "SINGLE"))
            {
                invalidIps.append(ip).append(", ");
            }
        }

        if(!invalidIps.isEmpty())
        {
            ctx.response().setStatusCode(400).end("Invalid IPs: " + invalidIps);

            return true;
        }

        if(body.getString(DISCOVERY_ID).isEmpty())
        {
            Utility.sendFailure(ctx, 400, "Discovery ID must be at least 1 character");

            try
            {
                Integer.parseInt(body.getString(DISCOVERY_ID));

                return false;
            }
            catch (Exception exception)
            {
                Utility.sendFailure(ctx, 400, "Discovery ID must be integer");

                return true;
            }
        }

        return false;
    }

    public static boolean validateProvisionUpdation(RoutingContext ctx)
    {
        if(Validators.validateBody(ctx)) { return true; }

        var id = Validators.validateID(ctx);

        if( id == -1 ){ return true; }

        if(Validators.validateInputFields(ctx, new String[]{METRIC_GROUPS}, true)) { return true; }

        var body = ctx.body().asJsonObject();

        var metrics = body.getJsonArray(METRIC_GROUPS);

        if(metrics != null && !metrics.isEmpty())
        {
            for(var i = 0; i < metrics.size(); i++)
            {
                var type = metrics.getJsonObject(i).getString(Fields.MetricGroup.NAME);

                var metricType = new ArrayList<>(List.of("CPUINFO", "CPUUSAGE", "DISK", "MEMORY", "DISK", "UPTIME", "PROCESS", "NETWORK", "SYSTEMINFO"));

                if(!metricType.contains(type))
                {
                    Utility.sendFailure(ctx, 400,"Invalid Metric Type " + type);

                    return true;
                }

                var interval = metrics.getJsonObject(i).getInteger(Fields.MetricGroup.POLLING_INTERVAL);

                var enable = metrics.getJsonObject(i).getBoolean(Fields.MetricGroup.IS_ENABLED);

                if(type == null)
                {
                    Utility.sendFailure(ctx, 400,"Provide Valid Metric Type");

                    return true;
                }

                if( ( (interval == null || interval < 60 || interval % 60 != 0) & enable == null ) )
                {
                    Utility.sendFailure(ctx, 400,"Provide Polling Interval ( Multiple of 60 ) Or Enable ( true or false )");

                    return true;
                }

            }
        }

        return false;
    }

    public static boolean validateCreateDiscovery(RoutingContext ctx)
    {

        if(Validators.validateBody(ctx)) { return true; }

        if(Validators.validateInputFields(ctx, new String[]{Fields.Discovery.IP, Fields.Discovery.IP_TYPE, Fields.Discovery.NAME, Fields.Discovery.PORT}, true)) { return true; }

        var body = ctx.body().asJsonObject();

        var credentials = body.getJsonArray(CREDENTIALS);

        if (credentials == null || credentials.isEmpty())
        {
            Utility.sendFailure(ctx, 400, "Missing or empty 'credentials' field");

            return true;
        }

        for (var credential : credentials)
        {
            try
            {
                Integer.parseInt(credential.toString());
            }
            catch (NumberFormatException exception)
            {
                Utility.sendFailure(ctx, 400, "Invalid credential ID: " + credential);

                return true;
            }
        }

        var port = body.getInteger(Fields.Discovery.PORT);

        if (port < 1 || port > 65535)
        {
            Utility.sendFailure(ctx, 400, "Port must be between 1 and 65535");
            return true;
        }

        var ipType = body.getString(Fields.Discovery.IP_TYPE);

        if (!ipType.equals("SINGLE") && !ipType.equals("RANGE") && !ipType.equals("CIDR"))
        {
            Utility.sendFailure(ctx, 400, "Invalid IP type. Only 'SINGLE', 'RANGE' and 'CIDR' are supported");

            return true;
        }

        var ip = body.getString(Fields.Discovery.IP);

        if (validateIpWithIpType(ip, ipType))
        {
            Utility.sendFailure(ctx, 400, "Invalid IP address or mismatch Ip and IpType: " + ip + ", " + ipType);

            return true;
        }

        return false;
    }

    public static boolean validateUpdateDiscovery(RoutingContext ctx)
    {
        if(Validators.validateBody(ctx)) { return true; }

        if(Validators.validateInputFields(ctx, new String[]{Fields.Discovery.IP, Fields.Discovery.IP_TYPE, Fields.Discovery.NAME, Fields.Discovery.PORT}, false)) { return true; }

        if(! Validators.validatePort(ctx)) { return true; }

        var ipType = ctx.body().asJsonObject().getString(Fields.Discovery.IP_TYPE);

        if (ipType != null && !ipType.equals("SINGLE") && !ipType.equals("RANGE") && !ipType.equals("CIDR"))
        {
            Utility.sendFailure(ctx, 400, "Invalid IP type. Only 'SINGLE', 'RANGE' and 'CIDR' are supported");
        }

        var ip = ctx.body().asJsonObject().getString(Fields.Discovery.IP);

        if (ip != null && ipType != null && validateIpWithIpType(ip, ipType))
        {
            Utility.sendFailure(ctx, 400, "Invalid IP address or mismatch Ip and IpType: " + ip + ", " + ipType);

            return true;
        }

        return false;
    }

    public static boolean validateUpdateDiscoveryCredential(RoutingContext ctx)
    {
        if(Validators.validateBody(ctx)) { return true; }

        var body = ctx.body().asJsonObject();

        var add_credentials = body.getJsonArray(ADD_CREDENTIALS);

        var remove_credentials = body.getJsonArray(REMOVE_CREDENTIALS);

        if (
                (add_credentials == null || add_credentials.isEmpty()) &&
                        (remove_credentials == null || remove_credentials.isEmpty())
        )
        {
            Utility.sendFailure(ctx, 400, "Missing or empty ' " + ADD_CREDENTIALS  + " ' and ' " + REMOVE_CREDENTIALS + " ' fields");

            return true;
        }

        if (add_credentials != null && !add_credentials.isEmpty())
        {
            for (var credential : add_credentials)
            {
                try
                {
                    Integer.parseInt(credential.toString());
                }
                catch (NumberFormatException exception)
                {
                    Utility.sendFailure(ctx, 400, "Invalid credential ID in " + ADD_CREDENTIALS + " : " + credential);

                    return true;
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
                catch (NumberFormatException exception)
                {
                    Utility.sendFailure(ctx, 400, "Invalid credential ID in " + REMOVE_CREDENTIALS + " : " + credential);

                    return true;
                }
            }
        }

        return false;
    }
}
