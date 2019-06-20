package uk.gov.wildfyre.cdr.idp;


import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import ca.uhn.fhir.rest.server.exceptions.ForbiddenOperationException;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import ca.uhn.fhir.rest.server.interceptor.InterceptorAdapter;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.json.JSONArray;
import org.json.JSONObject;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.security.jwt.crypto.sign.RsaVerifier;
import uk.gov.wildfyre.cdr.HapiProperties;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.UnknownHostException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Interceptor which checks that a valid OAuth2 Token has been supplied.
 * Checks the following rules:
 *   1.  A token is supplied in the Authorization Header
 *   2.  The token is a valid OAuth2 Token
 *   3.  The token is for the correct server
 *   4.  The token has not expired
 *
 * Ignored if this request is in the list of excluded URIs (e.g. metadata)
 *
 */
public class OAuth2Interceptor extends InterceptorAdapter {

    private final List<String> excludedPaths =  new ArrayList<>();

    Logger log = LoggerFactory.getLogger(OAuth2Interceptor.class);

    Map<String, String> accessRights = null;

    private static final Pattern RESOURCE_PATTERN = Pattern.compile("^/(\\w+)[//|\\?]?.*$");

    private JSONObject openIdObj;

    private JSONObject jwksObj;

    private RsaVerifier verifier;

    private ApplicationContext appCtx;

    public OAuth2Interceptor(ApplicationContext context) {

        log.trace("OAuth2 init");
        excludedPaths.add("/metadata");
        appCtx = context;

        accessRights = getAccessRights();

        if (HapiProperties.getSecurityOauth()) {
            log.trace("OAuth2 active");
            HttpClient client = getHttpClient();
            log.info("OAuth2 openid = "+HapiProperties.getSecurityOpenidConfig());
            HttpGet request = new HttpGet(HapiProperties.getSecurityOpenidConfig());
            request.setHeader(org.apache.http.HttpHeaders.CONTENT_TYPE, "application/json");
            request.setHeader(org.apache.http.HttpHeaders.ACCEPT, "application/json");

            try {

                HttpResponse response = client.execute(request);

                if (response.getStatusLine().toString().contains("200")) {
                    InputStreamReader reader = new InputStreamReader(response.getEntity().getContent());
                    BufferedReader bR = new BufferedReader(reader);
                    String line = "";

                    StringBuilder responseStrBuilder = new StringBuilder();
                    while ((line = bR.readLine()) != null) {
                        responseStrBuilder.append(line);
                    }
                    openIdObj = new JSONObject(responseStrBuilder.toString());
                }
            } catch (UnknownHostException e) {
                log.error("Host not known");
            } catch (Exception ex) {
                log.error(ex.getMessage());
            }
            if (openIdObj != null && openIdObj.has("jwks_uri"))  {
                log.info("Calling jwks endpoint " + openIdObj.getString("jwks_uri"));
                request = new HttpGet(openIdObj.getString("jwks_uri"));
                request.setHeader(org.apache.http.HttpHeaders.CONTENT_TYPE, "application/json");
                request.setHeader(org.apache.http.HttpHeaders.ACCEPT, "application/json");
                try {

                    HttpResponse response = client.execute(request);

                    if (response.getStatusLine().toString().contains("200")) {
                        InputStreamReader reader = new InputStreamReader(response.getEntity().getContent());
                        BufferedReader bR = new BufferedReader(reader);
                        String line = "";

                        StringBuilder responseStrBuilder = new StringBuilder();
                        while ((line = bR.readLine()) != null) {
                            responseStrBuilder.append(line);
                        }
                        log.trace(responseStrBuilder.toString());
                        jwksObj = new JSONObject(responseStrBuilder.toString());
                        // https://auth0.com/blog/navigating-rs256-and-jwks/
                        if (jwksObj.has("keys")) {
                            JSONArray keys = jwksObj.getJSONArray("keys");
                            for (Object object : keys) {
                                if (object instanceof JSONObject) {
                                   JSONObject keyObj = (JSONObject) object;
                                   if (keyObj.has("kty") && keyObj.getString("kty").equals("RSA")) {

                                       BigInteger modulus = new BigInteger(1, Base64.decodeBase64(keyObj.getString("n")));
                                       BigInteger exponent = new BigInteger(1, Base64.decodeBase64(keyObj.getString("e")));


                                       RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
                                       KeyFactory factory = KeyFactory.getInstance("RSA");
                                       PublicKey key = factory.generatePublic(spec);

                                       verifier = new RsaVerifier((RSAPublicKey) key, "SHA256withRSA");
                                       if (verifier == null) throw new InternalErrorException("Unable to process public key");
                                       /*
                                       JSONArray x5cObj = keyObj.getJSONArray("x5c");
                                       for (Object obj : x5cObj) {
                                           if (obj instanceof String) {

                                           }
                                       }

                                        */
                                   }
                                }
                            }
                        }
                    }
                } catch (UnknownHostException e) {
                    log.error("Host not known");
                } catch (Exception ex) {
                    log.error(ex.getMessage());
                }
            }

        }

    }

    protected Map<String, String> getAccessRights() {
        Map<String, String> accessRights = new HashMap();
        accessRights.put("Patient","Patient");
        accessRights.put("Observation","Observation");
        accessRights.put("Encounter","Encounter");
        accessRights.put("Condition","Condition");
        accessRights.put("Procedure","Observation");
        accessRights.put("AllergyIntolerance","AllergyIntolerance");
        accessRights.put("MedicationRequest","MedicationRequest");
        accessRights.put("MedicationStatement","MedicationStatement");
        accessRights.put("Immunization","Immunization");
        accessRights.put("Medication","Medication");
        accessRights.put("ReferralRequest","ReferralRequest");

        accessRights.put("DocumentReference","DocumentReference");
        accessRights.put("Binary","Binary");
        accessRights.put("Bundle","Bundle");

        List<IResourceProvider> resourceProviders = appCtx.getBean("myResourceProvidersDstu3", List.class);
        for (IResourceProvider provider : resourceProviders) {
            if (!accessRights.containsKey(provider.getResourceType().getSimpleName())) {
                log.debug("Adding Scope access right " + provider.getResourceType().getSimpleName());
                accessRights.put(provider.getResourceType().getSimpleName(), provider.getResourceType().getSimpleName());
            }
        }

        return accessRights;
    }

    @Override
    public boolean incomingRequestPreProcessed(HttpServletRequest theRequest, HttpServletResponse theResponse) {

        String resourcePath = theRequest.getPathInfo();
        log.trace("Accessing Resource" + resourcePath);
        if (excludedPaths.contains(resourcePath)){
            log.debug("Accessing unprotected resource" + resourcePath);
            return true;
        }

        String authorizationHeader = theRequest.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorizationHeader == null){
            log.warn("OAuth2 Authentication failure.  No OAuth Token supplied in Authorization Header on Request.");
            throw new AuthenticationException("Unauthorised access to protected resource");
        }
        OAuthToken oAuthToken = OAuthTokenUtil.parseOAuthToken(authorizationHeader, verifier);


        // Check that the OAuth Token has not expired
        if (oAuthToken.isExpired()){
            log.warn("OAuth2 Authentication failure due to expired token");
            throw new AuthenticationException("OAuth2 Authentication Token has expired.");
        }



        // Check that the Scopes on the Token allow access to the specified resource
        String resourceName = extractResourceName(resourcePath);
        if (!allowedAccess(resourceName, theRequest.getMethod(), oAuthToken)){
            log.warn("OAuth2 Authentication failed due to insufficient access rights: ");
            throw new ForbiddenOperationException(String.format("Insufficient Access Rights to access %s.", resourceName));
        }

        log.debug("Authenticated Access to " + resourcePath);
        return true;
    }

    /**
     * Check if the Scopes on the OAuth Token allow access to the specified Resource
     *
     * @param resourceName
     * @param method
     * @param oAuthToken
     * @return
     */

    private boolean inList(List<String> scopes,String scope) {
        for(String str: scopes) {
           // log.info(str);
            if(str.trim().contains(scope))
                return true;
        }
        return false;
    }
    public boolean allowedAccess(String resourceName, String method, OAuthToken oAuthToken) {
        log.info(HapiProperties.getSecurityOauthScope());
        log.info(HapiProperties.getSecuritySmartScope().toString());
        log.info(oAuthToken.getScopes().toString());
        if (HapiProperties.getSecuritySmartScope()) {
            if (accessRights.containsKey(resourceName)) {
                String requiredAccess = accessRights.get(resourceName);
                return oAuthToken.allowsAccess(requiredAccess, method);
            }
            log.info(String.format("Access to %s is unrestricted.", resourceName));
            return true;
        } else {

            if (inList(oAuthToken.getScopes(),HapiProperties.getSecurityOauthScope())) {
                log.info(String.format("Access to %s is unrestricted.", resourceName));
                return true;
            } else {
                log.warn("Unable to find "+ HapiProperties.getSecurityOauthScope() + " in "+oAuthToken.getScopes().toString() );
                String requiredAccess = accessRights.get(resourceName);
                return oAuthToken.allowsAccess(requiredAccess, method);
            }
        }

    }

    public String extractResourceName(String resourcePath) {
        Matcher match = RESOURCE_PATTERN.matcher(resourcePath);
        if (!match.matches()){
            log.warn(String.format("%s does not match secured pattern", resourcePath));
            return "";
        }
        return match.group(1);
    }

    private HttpClient getHttpClient(){
        final HttpClient httpClient = HttpClientBuilder.create().build();
        return httpClient;
    }
}
