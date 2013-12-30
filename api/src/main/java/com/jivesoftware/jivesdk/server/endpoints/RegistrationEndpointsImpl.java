package com.jivesoftware.jivesdk.server.endpoints;

import com.jivesoftware.jivesdk.api.InstanceRegistrationHandler;
import com.jivesoftware.jivesdk.api.JiveSDKManager;
import com.jivesoftware.jivesdk.api.RegistrationRequest;
import com.jivesoftware.jivesdk.impl.PropertyConfiguration;
import com.jivesoftware.jivesdk.impl.utils.DealRoomUtils;
import com.jivesoftware.jivesdk.server.AuthenticationResponse;
import com.jivesoftware.jivesdk.server.ServerConstants;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpStatus;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * Created with IntelliJ IDEA.
 * User: Zvoykish
 * Date: 17/7/13
 * Time: 6:23 PM
 */
@Path(ServerConstants.Endpoints.TILE)
public class RegistrationEndpointsImpl extends AbstractEndpoint  {
    public static final String PROPERTY_NAME_EXT_PROPS = "extprops";
    public static final String DELIM = ";///;";
    protected static final Logger log = LoggerFactory.getLogger(RegistrationEndpointsImpl.class);
    protected static final String FAILED_REGISTERING_DEAL_ROOM = "Failed registering deal room.";

    protected PropertyConfiguration configuration = PropertyConfiguration.getInstance();

    @Inject
    private InstanceRegistrationHandler instanceRegistrationHandler;

	@Inject
	private JiveSDKManager jiveSDKManager;


    @GET
    @Path("/ping")
    public Response ping() {
        return Response.ok().build();
    }

    @POST
    @Path(ServerConstants.Endpoints.REGISTER)
    @Produces(MediaType.APPLICATION_JSON)
    public Response register(@HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
                             RegistrationRequest registrationRequest) {
        try {
            log.debug("Received registration request: " + registrationRequest);

            String jiveInstanceUrl = registrationRequest.getJiveInstanceUrl();
            String tenantId = registrationRequest.getTenantId();
            if (!DealRoomUtils.isAllExist(jiveInstanceUrl, tenantId)) {
                String msg = String.format("Jive instance URL [%s] / Tenant ID [%s] are missing from V2 registration request", jiveInstanceUrl, tenantId);
                log.warn(msg);
            }

            AuthenticationResponse authResponse = authenticateV2Request(authorization, jiveInstanceUrl, tenantId);
            if (!authResponse.isAuthenticated()) {
                log.error("Registration request unauthorized: " + registrationRequest);
                return Response.status(authResponse.getStatusCode()).build();
            }

            registrationRequest.setClientId(authResponse.getClientId());
            registrationRequest.setClientSecret(authResponse.getClientSecret());
            registrationRequest.setInstance(authResponse.getInstance());

            return doRegister(registrationRequest);
        } catch (Throwable t) {
            return returnResponseOnThrowable(t, FAILED_REGISTERING_DEAL_ROOM);
        }
    }

    public Response doRegister(RegistrationRequest registrationRequest) {
        try {
            if (!DealRoomUtils.isAllExist(registrationRequest.getTempToken(), registrationRequest.getGuid(), registrationRequest.getJivePushUrl(), registrationRequest.getTileDefName())) {
                ObjectNode errorObj = logErrorAndCreateErrorResponse("Failed registering due to bad registration request: " + registrationRequest);
                return Response.status(HttpStatus.SC_BAD_REQUEST).entity(errorObj).build();
            }

            String itemType = registrationRequest.getItemType();
            if (StringUtils.isEmpty(itemType)) {
                ObjectNode errorObj = logErrorAndCreateErrorResponse("Failed registering due to invalid item type: " + itemType);
                return Response.status(HttpStatus.SC_BAD_REQUEST).entity(errorObj).build();
            }


            try {
                instanceRegistrationHandler.register(registrationRequest);
            } catch (Exception e) {
                log.error(e.toString());
                return Response.serverError().build();
            }

            ObjectMapper objectMapper = new ObjectMapper();
            ObjectNode result = objectMapper.createObjectNode();
           /* ObjectNode propsNode = objectMapper.createObjectNode();
            result.put(PROPERTY_NAME_EXT_PROPS, propsNode);*/
            String resultJson = result.toString();

            log.debug("Registration success, responding with: " + resultJson);
            return Response.status(HttpStatus.SC_OK).entity(resultJson).build();

        } catch (Exception e) {
            return returnResponseOnThrowable(e, FAILED_REGISTERING_DEAL_ROOM);
        }
    }
}