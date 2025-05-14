package org.nms.api.handlers;

import io.vertx.ext.web.RoutingContext;
import org.nms.api.Utility;
import org.nms.constants.Queries;
import org.nms.database.DbUtility;

public class MetricResult
{
    public static void getAllPolledData(RoutingContext ctx)
    {
        DbUtility.sendQueryExecutionRequest(Queries.PollingResult.GET_ALL).onComplete(asyncResult ->
        {
            if (asyncResult.succeeded())
            {
                var polledData = asyncResult.result();

                Utility.sendSuccess(ctx, 200, "Polled Data", polledData);
            }
            else
            {
                Utility.sendFailure(ctx, 500, "Something Went Wrong");
            }
        });
    }
}