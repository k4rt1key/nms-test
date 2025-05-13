package org.nms.api.validators;

import io.vertx.ext.web.RoutingContext;
import org.nms.api.helpers.HttpResponse;
import org.nms.api.helpers.Ip;
import org.nms.constants.Fields;

import java.util.ArrayList;
import java.util.List;

public class ProvisionRequestValidator
{
    private static final String INPUT_IPS_FIELD = "ips";

    private static final String INPUT_DISCOVERY_FIELD = "discovery_id";

    private static final String INPUT_METRICS_FIELD = "metrics";

    public static void getProvisionByIdRequestValidator(RoutingContext ctx)
    {
        if(Utility.validateBody(ctx)) { return; }

        if(Utility.validateID(ctx)) { return; }

        ctx.next();
    }

    public static void createProvisionRequestValidator(RoutingContext ctx)
    {
        if(Utility.validateBody(ctx)) { return; }

        if(Utility.validateInputFields(ctx, new String[]{INPUT_IPS_FIELD, INPUT_DISCOVERY_FIELD}, true)) { return; }

        var body = ctx.body().asJsonObject();

        var ips = body.getJsonArray("ips");

        var invalidIps = new StringBuilder();

        for(var ip: ips)
        {
            if(!Ip.isValidIp(ip.toString()))
            {
                invalidIps.append(ip).append(", ");
            }
        }

        if(!invalidIps.isEmpty())
        {
            ctx.response().setStatusCode(400).end("Invalid IPs: " + invalidIps);
            return;
        }

        if(body.getString(INPUT_DISCOVERY_FIELD).isEmpty())
        {
            HttpResponse.sendFailure(ctx, 400, "Discovery ID must be at least 1 character");

            try
            {
                Integer.parseInt(body.getString(INPUT_DISCOVERY_FIELD));
            }
            catch (Exception e)
            {
                HttpResponse.sendFailure(ctx, 400, "Discovery ID must be integer");
            }

            return;
        }

        ctx.next();
    }

    public static void updateProvisionRequestValidator(RoutingContext ctx)
    {
        if(Utility.validateBody(ctx)) { return; }

        if(Utility.validateID(ctx)) { return; }

        if(Utility.validateInputFields(ctx, new String[]{INPUT_METRICS_FIELD}, true)) { return; }

        var body = ctx.body().asJsonObject();

        var metrics = body.getJsonArray(INPUT_METRICS_FIELD);

        if(metrics != null && !metrics.isEmpty())
        {
            for(var i = 0; i < metrics.size(); i++)
            {
                var type = metrics.getJsonObject(i).getString(Fields.MetricGroup.NAME);

                var metricType = new ArrayList<>(List.of("CPUINFO", "CPUUSAGE", "DISK", "MEMORY", "DISK", "UPTIME", "PROCESS", "NETWORK", "SYSTEMINFO"));

                if(!metricType.contains(type))
                {
                    HttpResponse.sendFailure(ctx, 400,"Invalid Metric Type " + type);
                    return;
                }

                var interval = metrics.getJsonObject(i).getInteger(Fields.MetricGroup.POLLING_INTERVAL);

                var enable = metrics.getJsonObject(i).getBoolean(Fields.MetricGroup.IS_ENABLED);

                if(type == null)
                {
                    HttpResponse.sendFailure(ctx, 400,"Provide Valid Metric Type");
                    return;
                }

                if( ( (interval == null || interval < 60 || interval % 60 != 0) & enable == null ) )
                {
                    HttpResponse.sendFailure(ctx, 400,"Provide Polling Interval ( Multiple of 60 ) Or Enable ( true or false )");
                    return;
                }

            }
        }

        ctx.next();
    }

    public static void deleteProvisionRequestValidator(RoutingContext ctx)
    {
        if(Utility.validateID(ctx)) { return; }

        ctx.next();
    }
}
