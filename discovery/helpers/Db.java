package org.nms.discovery.helpers;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Tuple;
import org.nms.constants.Fields;
import org.nms.constants.Queries;
import org.nms.database.helpers.DbEventBus;

import java.util.ArrayList;

public class Db
{
    public static Future<JsonObject> fetchDiscoveryDetails(int id)
    {
        Promise<JsonObject> promise = Promise.promise();

        DbEventBus.sendQueryExecutionRequest(Queries.Discovery.GET_BY_ID, new JsonArray().add(id))
                .onComplete(ar -> {
                    if (ar.succeeded()) {
                        var discoveryArray = ar.result();
                        if (discoveryArray.isEmpty()) {
                            promise.fail("Discovery not found");
                        } else {
                            promise.complete(discoveryArray.getJsonObject(0));
                        }
                    } else {
                        promise.fail(ar.cause());
                    }
                });

        return promise.future();
    }

    public static Future<Void> updateDiscoveryStatus(int id, String status)
    {
        Promise<Void> promise = Promise.promise();

        DbEventBus.sendQueryExecutionRequest(
                        Queries.Discovery.UPDATE_STATUS,
                        new JsonArray().add(id).add(status)
                )
                .onComplete(ar -> {
                    if (ar.succeeded()) {
                        promise.complete();
                    } else {
                        promise.fail(ar.cause());
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
            var success = result.getBoolean("success");
            var ip = result.getString("ip");

            if (success)
            {
                pingCheckPassedIps.add(ip);
            }
            else
            {
                pingCheckFailedIps.add(Tuple.of(id, null, ip, result.getString("message"), "FAILED"));
            }
        }

        // Insert failed IPs if any
        if (!pingCheckFailedIps.isEmpty())
        {
            DbEventBus.sendQueryExecutionRequest(Queries.Discovery.INSERT_RESULT, pingCheckFailedIps);
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
            var success = result.getBoolean("success");
            var ip = result.getString("ip");

            if (success)
            {
                portCheckPassedIps.add(ip);
            }
            else
            {
                portCheckFailedIps.add(Tuple.of(id, null, ip, result.getString("message"), "FAILED"));
            }
        }

        // Insert failed IPs if any
        if (!portCheckFailedIps.isEmpty())
        {
            DbEventBus.sendQueryExecutionRequest(Queries.Discovery.INSERT_RESULT, portCheckFailedIps);
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
            var success = result.getBoolean("success");
            var ip = result.getString(Fields.PluginDiscoveryResponse.IP);

            if (success)
            {
                var credential = result.getJsonObject(Fields.PluginDiscoveryResponse.CREDENTIALS)
                        .getInteger(Fields.Credential.ID);
                credentialCheckSuccessIps.add(
                        Tuple.of(id, credential, ip, result.getString("message"), "COMPLETED"));
            }
            else
            {
                credentialCheckFailedIps.add(
                        Tuple.of(id, null, ip, result.getString("message"), "FAILED"));
            }
        }

        // Batch insert results, handling empty lists to avoid batch query errors
        Future<JsonArray> failureFuture = credentialCheckFailedIps.isEmpty()
                ? Future.succeededFuture(new JsonArray())
                : DbEventBus.sendQueryExecutionRequest(Queries.Discovery.INSERT_RESULT, credentialCheckFailedIps);

        Future<JsonArray> successFuture = credentialCheckSuccessIps.isEmpty()
                ? Future.succeededFuture(new JsonArray())
                : DbEventBus.sendQueryExecutionRequest(Queries.Discovery.INSERT_RESULT, credentialCheckSuccessIps);

        return Future.join(failureFuture, successFuture)
                .compose(v -> Future.succeededFuture());
    }
}
