package org.nms.discovery;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Tuple;
import org.nms.constants.Fields;
import org.nms.constants.Queries;
import org.nms.database.DbUtility;

import java.util.ArrayList;

public class CrudHelpers
{
    public static Future<JsonObject> fetchDiscoveryDetails(int id)
    {
        Promise<JsonObject> promise = Promise.promise();

        DbUtility.sendQueryExecutionRequest(Queries.Discovery.GET_BY_ID, new JsonArray().add(id))
                .onComplete(asyncResult ->
                {
                    if (asyncResult.succeeded())
                    {
                        var discoveryArray = asyncResult.result();

                        if (discoveryArray.isEmpty())
                        {
                            promise.fail("Discovery not found");
                        }
                        else
                        {
                            promise.complete(discoveryArray.getJsonObject(0));
                        }
                    }
                    else
                    {
                        promise.fail(asyncResult.cause());
                    }
                });

        return promise.future();
    }

    public static Future<Void> updateDiscoveryStatus(int id, String status)
    {
        Promise<Void> promise = Promise.promise();

        DbUtility.sendQueryExecutionRequest(
                        Queries.Discovery.UPDATE_STATUS,
                        new JsonArray().add(id).add(status)
                )
                .onComplete(asyncResult ->
                {
                    if (asyncResult.succeeded())
                    {
                        promise.complete();
                    }
                    else
                    {
                        promise.fail(asyncResult.cause());
                    }
                });

        return promise.future();
    }

    public static JsonArray processPingResults(int id, JsonArray pingResults)
    {
        var pingCheckPassedIps = new JsonArray();

        var pingCheckFailedIps = new ArrayList<Tuple>();

        for (int i = 0; i < pingResults.size(); i++)
        {
            var result = pingResults.getJsonObject(i);

            var success = result.getBoolean(Fields.Discovery.SUCCESS);

            var ip = result.getString(Fields.Discovery.IP);

            if (success)
            {
                pingCheckPassedIps.add(ip);
            }
            else
            {
                pingCheckFailedIps.add(Tuple.of(id, null, ip, result.getString(Fields.DiscoveryResult.MESSAGE), Fields.Discovery.FAILED_STATUS));
            }
        }

        // Insert failed IPs if any
        if (!pingCheckFailedIps.isEmpty())
        {
            DbUtility.sendQueryExecutionRequest(Queries.Discovery.INSERT_RESULT, pingCheckFailedIps);
        }

        return pingCheckPassedIps;
    }

    public static JsonArray processPortCheckResults(int id, JsonArray portCheckResults)
    {
        var portCheckPassedIps = new JsonArray();

        var portCheckFailedIps = new ArrayList<Tuple>();

        for (int i = 0; i < portCheckResults.size(); i++)
        {
            var result = portCheckResults.getJsonObject(i);

            var success = result.getBoolean(Fields.Discovery.SUCCESS);

            var ip = result.getString(Fields.Discovery.IP);

            if (success)
            {
                portCheckPassedIps.add(ip);
            }
            else
            {
                portCheckFailedIps.add(Tuple.of(id, null, ip, result.getString(Fields.DiscoveryResult.MESSAGE), Fields.DiscoveryResult.FAILED_STATUS));
            }
        }

        // Insert failed IPs if any
        if (!portCheckFailedIps.isEmpty())
        {
            DbUtility.sendQueryExecutionRequest(Queries.Discovery.INSERT_RESULT, portCheckFailedIps);
        }

        return portCheckPassedIps;
    }

    public static Future<Void> processCredentialCheckResults(int id, JsonArray credentialCheckResults)
    {
        var credentialCheckFailedIps = new ArrayList<Tuple>();

        var credentialCheckSuccessIps = new ArrayList<Tuple>();

        for (int i = 0; i < credentialCheckResults.size(); i++)
        {
            var result = credentialCheckResults.getJsonObject(i);

            var success = result.getBoolean(Fields.Discovery.SUCCESS);

            var ip = result.getString(Fields.PluginDiscoveryResponse.IP);

            if (success)
            {
                var credential = result.getJsonObject(Fields.PluginDiscoveryResponse.CREDENTIALS)
                        .getInteger(Fields.Credential.ID);

                credentialCheckSuccessIps.add(
                        Tuple.of(id, credential, ip, result.getString(Fields.DiscoveryResult.MESSAGE), Fields.DiscoveryResult.COMPLETED_STATUS));
            }
            else
            {
                credentialCheckFailedIps.add(
                        Tuple.of(id, null, ip, result.getString(Fields.DiscoveryResult.MESSAGE), Fields.DiscoveryResult.FAILED_STATUS));
            }
        }

        // Batch insert results, handling empty lists to avoid batch query errors
        Future<JsonArray> failure = credentialCheckFailedIps.isEmpty()
                ? Future.succeededFuture(new JsonArray())
                : DbUtility.sendQueryExecutionRequest(Queries.Discovery.INSERT_RESULT, credentialCheckFailedIps);

        Future<JsonArray> success = credentialCheckSuccessIps.isEmpty()
                ? Future.succeededFuture(new JsonArray())
                : DbUtility.sendQueryExecutionRequest(Queries.Discovery.INSERT_RESULT, credentialCheckSuccessIps);

        return Future.join(failure, success)
                .compose(v -> Future.succeededFuture());
    }
}
