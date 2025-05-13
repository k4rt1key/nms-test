package org.nms.api.handlers;

import io.vertx.ext.web.RoutingContext;
import org.nms.api.helpers.HttpResponse;
import org.nms.constants.Queries;
import org.nms.database.helpers.DbEventBus;

public class MetricResultHandler
{
    public static void getAllPolledData(RoutingContext ctx)
    {
        var queryRequest = DbEventBus.sendQueryExecutionRequest(Queries.PollingResult.GET_ALL);

        queryRequest.onComplete(ar ->
        {
            if (ar.succeeded())
            {
                var polledData = ar.result();
                HttpResponse.sendSuccess(ctx, 200, "Polled Data", polledData);
            }
            else
            {
                HttpResponse.sendFailure(ctx, 500, "Something Went Wrong");
            }
        });
    }
}