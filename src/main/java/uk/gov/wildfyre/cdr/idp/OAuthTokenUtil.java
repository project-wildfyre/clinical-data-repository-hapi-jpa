package uk.gov.wildfyre.cdr.idp;

import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.jwt.Jwt;
import org.springframework.security.jwt.JwtHelper;
import org.springframework.security.jwt.crypto.sign.RsaVerifier;

import java.io.IOException;

public class OAuthTokenUtil {

    private static final String TOKEN_PREFIX = "bearer ";
    private static final Logger log = LoggerFactory.getLogger(OAuthTokenUtil.class);

    public static OAuthToken parseOAuthToken(String oauthHeader, RsaVerifier verifier){
        log.trace("parseOAuthToken = "+oauthHeader);
        assert oauthHeader != null:"OAuth Token should not be null";
        return parseJwtToken(extractTokenFromHeader(oauthHeader), verifier);
    }

    private static OAuthToken parseJwtToken(String jwtToken, RsaVerifier verifier) {
        log.trace("parseJwtToken = "+jwtToken);
        try {
            Jwt jwt = null;
            if (verifier == null) {
                log.trace("parseJwtToken.decode");
                jwt = JwtHelper.decode(jwtToken);
            } else {
                log.trace("parseJwtToken.decodeAndVerify");
                try {
                    jwt = JwtHelper.decodeAndVerify(jwtToken, verifier);
                } catch (Exception ex) {
                    jwt = null;
                    log.error(ex.getMessage());
                }
            }
            if (jwt == null) throw new AuthenticationException("Invalid OAuth2 Token");
            log.trace("claims "+ jwt.getClaims());
            log.trace("encoded "+ jwt.getEncoded());
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
            return mapper.readValue(jwt.getClaims().getBytes(), OAuthToken.class);
        } catch (IOException e) {
            throw new AuthenticationException("Invalid OAuth2 Token", e);
        }
    }

    public static String extractTokenFromHeader(String authHeader) {
        if (authHeader.toLowerCase().startsWith(TOKEN_PREFIX)) {
            return authHeader.substring(TOKEN_PREFIX.length());
        } else {
            throw new AuthenticationException("Invalid OAuth Header.  Missing Bearer prefix");
        }
    }

}
