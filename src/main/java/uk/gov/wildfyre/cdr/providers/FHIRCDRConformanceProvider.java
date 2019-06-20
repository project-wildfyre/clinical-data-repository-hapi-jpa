package uk.gov.wildfyre.cdr.providers;

import ca.uhn.fhir.jpa.dao.DaoConfig;
import ca.uhn.fhir.jpa.dao.IFhirSystemDao;
import ca.uhn.fhir.jpa.provider.dstu3.JpaConformanceProviderDstu3;
import ca.uhn.fhir.rest.annotation.Metadata;
import ca.uhn.fhir.rest.server.RestfulServer;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.hl7.fhir.dstu3.model.*;
import org.json.JSONObject;
import uk.gov.wildfyre.cdr.HapiProperties;

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.UnknownHostException;
import java.time.Instant;

public class FHIRCDRConformanceProvider extends JpaConformanceProviderDstu3 {

    private volatile CapabilityStatement capabilityStatement;

    private Instant lastRefresh;

    private JSONObject openIdObj;

    public FHIRCDRConformanceProvider(RestfulServer theRestfulServer, IFhirSystemDao<Bundle, Meta> theSystemDao, DaoConfig theDaoConfig) {
        super(theRestfulServer, theSystemDao, theDaoConfig);
    }

    @Override
    @Metadata
    public CapabilityStatement getServerConformance(HttpServletRequest theRequest) {
        if (capabilityStatement != null) {
            if (lastRefresh != null) {
                java.time.Duration duration = java.time.Duration.between(Instant.now(), lastRefresh);
                // May need to revisit
                if ((duration.getSeconds() * 60) < 2) return capabilityStatement;
            }
        }
        lastRefresh = Instant.now();

        capabilityStatement = super.getServerConformance(theRequest);

        if (HapiProperties.getSecurityOauth()) {
            for (CapabilityStatement.CapabilityStatementRestComponent nextRest : capabilityStatement.getRest()) {
                nextRest.getSecurity()
                        .addService().addCoding()
                        .setSystem("http://hl7.org/fhir/restful-security-service")
                        .setDisplay("SMART-on-FHIR")
                        .setSystem("SMART-on-FHIR");

                if (HapiProperties.getSecurityOpenidConfig() != null) {
                    Extension securityExtension = nextRest.getSecurity().addExtension()
                            .setUrl("http://fhir-registry.smarthealthit.org/StructureDefinition/oauth-uris");
                    HttpClient client = getHttpClient();
                    HttpGet request = new HttpGet(HapiProperties.getSecurityOpenidConfig());
                    request.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
                    request.setHeader(HttpHeaders.ACCEPT, "application/json");
                    if (openIdObj == null) {
                        try {

                            HttpResponse response = client.execute(request);
                            //System.out.println(response.getStatusLine());
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
                            System.out.println("Host not known");
                        } catch (Exception ex) {
                            System.out.println(ex.getMessage());
                        }
                    }
                    if (openIdObj != null) {
                        if (openIdObj.has("token_endpoint")) {
                            securityExtension.addExtension()
                                    .setUrl("token")
                                    .setValue(new UriType(openIdObj.getString("token_endpoint")));
                        }
                        if (openIdObj.has("authorization_endpoint")) {
                            securityExtension.addExtension()
                                    .setUrl("authorize")
                                    .setValue(new UriType(openIdObj.getString("authorization_endpoint")));
                        }
                        if (openIdObj.has("register_endpoint")) {
                            securityExtension.addExtension()
                                    .setUrl("register")
                                    .setValue(new UriType(openIdObj.getString("register_endpoint")));
                        }
                    }
                }
            }
        }

        if (capabilityStatement.hasImplementation()) {
            capabilityStatement.getImplementation().setDescription(HapiProperties.getSoftwareImplementationDesc());
        }
        if (capabilityStatement.hasSoftware()) {
            capabilityStatement.getSoftware().setName(HapiProperties.getSoftwareName());
        }
        capabilityStatement.setPublisher("Project Wildfyre");
        return  capabilityStatement;
    }

    private HttpClient getHttpClient(){
        final HttpClient httpClient = HttpClientBuilder.create().build();
        return httpClient;
    }
}
