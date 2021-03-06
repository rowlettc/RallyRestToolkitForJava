package com.rallydev.rest.client;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.http.auth.Credentials;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.auth.BasicScheme;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.rallydev.rest.response.GetResponse;

/**
 * A HttpClient which authenticates using basic authentication (username/password).
 */
public class BasicAuthClient extends HttpClient {

    protected static final String SECURITY_ENDPOINT_DOES_NOT_EXIST = "SECURITY_ENDPOINT_DOES_NOT_EXIST";
    protected static final String SECURITY_TOKEN_PARAM_KEY = "key";
    private static final String SECURITY_TOKEN_URL = "/security/authorize";
    protected static final String SECURITY_TOKEN_KEY = "SecurityToken";
    protected String securityToken;
    protected Credentials credentials;

    private Object privateLock = new Object();

    /**
     * Construct a new client.
     * @param server the server to connect to
     * @param userName the username to be used for authentication
     * @param password the password to be used for authentication
     */
    public BasicAuthClient(URI server, String userName, String password) {
        super(server);
        credentials = setClientCredentials(server, userName, password);
    }
    
    /**
     * Construct a new client with a pre-configured HttpClient.
     * 
     * @param server the server to connect to
     * @param userName the username to be used for authentication
     * @param password the password to be used for authentication
     */
    public BasicAuthClient(URI server, String userName, String password, org.apache.http.client.HttpClient client) {
        super(server, client);
        credentials = setClientCredentials(server, userName, password);
    }

    /**
     * Execute a request against the WSAPI.
     * 
     * Always attaches the Basic Authentication header to avoid HTTP Spec handling.
     * 
     * Traditionally, all requests are attempted without authorization, 
     * however we know that all resources are protected, so we can force pre-authentication.
     *
     * @param request the request to be executed
     * @return the JSON encoded string response
     * @throws java.io.IOException if a non-200 response code is returned or if some other
     *             problem occurs while executing the request
     */
    @Override
    protected String doRequest(HttpRequestBase request) throws IOException {
        request.addHeader(BasicScheme.authenticate(credentials, "utf-8", false));
        
        if(!request.getMethod().equals(HttpGet.METHOD_NAME) &&
                !this.getWsapiVersion().matches("^1[.]\\d+")) {
            try {
                attachSecurityInfo(request);
            } catch (URISyntaxException e) {
                throw new IOException("Unable to build URI with security token", e);
            }
        }
        return super.doRequest(request);
    }

    /**
     * Attach the security token parameter to the request.
     *
     * Response Structure:
     * {"OperationResult": {"SecurityToken": "UUID"}}
     *
     * @param request the request to be modified
     * @throws IOException if a non-200 response code is returned or if some other
     *                     problem occurs while executing the request
     * @throws URISyntaxException if there is a problem with the url in the request
     */
    protected void attachSecurityInfo(HttpRequestBase request) throws IOException, URISyntaxException {
        if (!SECURITY_ENDPOINT_DOES_NOT_EXIST.equals(securityToken)) {
            try {
                synchronized (privateLock) {
                    if (securityToken == null) {
                        HttpGet httpGet = new HttpGet(getWsapiUrl() + SECURITY_TOKEN_URL);
                        GetResponse getResponse = new GetResponse(doRequest(httpGet));
                        JsonObject operationResult = getResponse.getObject();
                        JsonPrimitive securityTokenPrimitive = operationResult.getAsJsonPrimitive(SECURITY_TOKEN_KEY);
                        securityToken = securityTokenPrimitive.getAsString();
                    }
                }
                request.setURI(new URIBuilder(request.getURI()).addParameter(SECURITY_TOKEN_PARAM_KEY, securityToken).build());
            } catch (IOException e) {
                //swallow the exception in this case as url does not exist indicates running and old version of
                //ALM without the security endpoint
                securityToken = SECURITY_ENDPOINT_DOES_NOT_EXIST;
            }
        }
    }
}