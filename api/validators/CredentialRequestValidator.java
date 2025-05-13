package org.nms.api.validators;

import io.vertx.ext.web.RoutingContext;
import org.nms.constants.Fields;

public class CredentialRequestValidator
{
    public static void getCredentialByIdRequestValidator(RoutingContext ctx)
    {
        if(Utility.validateID(ctx)) {  return; }

        ctx.next();
    }

    public static void createCredentialRequestValidator(RoutingContext ctx)
    {
        if(Utility.validateBody(ctx)) { return; }

        if(Utility.validateInputFields(ctx, new String[]{Fields.Credential.NAME, Fields.Credential.USERNAME, Fields.Credential.PASSWORD}, true)) { return; }

        ctx.next();
    }

    public static void updateCredentialByIdRequestValidator(RoutingContext ctx)
    {
        if(Utility.validateID(ctx)) {  return; }
        
        if(Utility.validateBody(ctx)) { return; }

        if(Utility.validateInputFields(ctx, new String[]{Fields.Credential.NAME, Fields.Credential.USERNAME, Fields.Credential.PASSWORD}, false)) { return; }

        ctx.next();
    }

    public static void deleteCredentialByIdRequestValidator(RoutingContext ctx)
    {
        if(Utility.validateID(ctx)) {  return; }

        ctx.next();
    }
}