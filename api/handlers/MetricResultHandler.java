package org.nms.api.handlers;

import io.vertx.ext.web.RoutingContext;
import org.nms.api.helpers.HttpResponse;
import org.nms.constants.Queries;
import org.nms.database.DbEngine;

public class MetricResultHandler
{
    public static void getAllPolledData(RoutingContext ctx)
    {
        DbEngine.execute(Queries.PollingResult.GET_ALL)
                .onSuccess((polledData -> HttpResponse.sendSuccess(ctx, 200,"Polled Data", polledData)))
                .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong"));
    }
}
