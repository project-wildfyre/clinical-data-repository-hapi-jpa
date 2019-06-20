
package uk.gov.wildfyre.cdr;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.FhirVersionEnum;
import ca.uhn.fhir.jpa.dao.DaoConfig;
import ca.uhn.fhir.jpa.dao.IFhirSystemDao;
import ca.uhn.fhir.jpa.model.interceptor.executor.InterceptorService;
import ca.uhn.fhir.jpa.provider.dstu3.JpaConformanceProviderDstu3;
import ca.uhn.fhir.jpa.provider.dstu3.JpaSystemProviderDstu3;
import ca.uhn.fhir.jpa.provider.dstu3.TerminologyUploaderProviderDstu3;
import ca.uhn.fhir.jpa.provider.r4.JpaConformanceProviderR4;
import ca.uhn.fhir.jpa.provider.r4.JpaSystemProviderR4;
import ca.uhn.fhir.jpa.search.DatabaseBackedPagingProvider;
import ca.uhn.fhir.jpa.subscription.SubscriptionInterceptorLoader;
import ca.uhn.fhir.jpa.subscription.module.interceptor.SubscriptionDebugLogInterceptor;
import ca.uhn.fhir.rest.server.HardcodedServerAddressStrategy;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.RestfulServer;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Meta;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.ContextLoaderListener;
import uk.gov.wildfyre.cdr.idp.OAuth2Interceptor;
import uk.gov.wildfyre.cdr.interceptors.RequestValidatingInterceptor;
import uk.gov.wildfyre.cdr.providers.FHIRCDRConformanceProvider;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.util.List;

@WebServlet(urlPatterns = { "/STU3/*" }, displayName = "FHIR CDR Server")
public class JpaRestfulServer extends RestfulServer {

	private static final long serialVersionUID = 1L;

	private ApplicationContext appCtx;

	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JpaRestfulServer.class);

	public JpaRestfulServer() {

		// This is called from the war deployment??

		appCtx = (ApplicationContext) ContextLoaderListener.getCurrentWebApplicationContext();
	}

	JpaRestfulServer(ApplicationContext context) {

		// This is called from spring boot
		this.appCtx = context;

	}


	@SuppressWarnings("unchecked")

	@Override
	protected void initialize() throws ServletException {
		super.initialize();

		FhirVersionEnum fhirVersion = FhirVersionEnum.DSTU3;

		/*
		 * ResourceProviders are fetched from the Spring context
		 */

		List<IResourceProvider> resourceProviders;
		Object systemProvider;
		/*
		if (fhirVersion == FhirVersionEnum.DSTU2) {
			resourceProviders = appCtx.getBean("myResourceProvidersDstu2", List.class);
			systemProvider = appCtx.getBean("mySystemProviderDstu2", JpaSystemProviderDstu2.class);
		} else */
		if (fhirVersion == FhirVersionEnum.DSTU3) {
			resourceProviders = appCtx.getBean("myResourceProvidersDstu3", List.class);
			systemProvider = appCtx.getBean("mySystemProviderDstu3", JpaSystemProviderDstu3.class);
		} else if (fhirVersion == FhirVersionEnum.R4) {
			resourceProviders = appCtx.getBean("myResourceProvidersR4", List.class);
			systemProvider = appCtx.getBean("mySystemProviderR4", JpaSystemProviderR4.class);
		} else {
			throw new IllegalStateException();
		}

		setFhirContext(appCtx.getBean(FhirContext.class));

		registerProviders(resourceProviders);
		registerProvider(systemProvider);

		/*
		 * The conformance provider exports the supported resources, search parameters, etc for
		 * this server. The JPA version adds resourceProviders counts to the exported statement, so it
		 * is a nice addition.
		 *
		 * You can also create your own subclass of the conformance provider if you need to
		 * provide further customization of your server's CapabilityStatement
		 */

		if (fhirVersion == FhirVersionEnum.DSTU3) {
			IFhirSystemDao<Bundle, Meta> systemDao = appCtx.getBean("mySystemDaoDstu3", IFhirSystemDao.class);
			JpaConformanceProviderDstu3 confProvider = new FHIRCDRConformanceProvider(this, systemDao, appCtx.getBean(DaoConfig.class));
			confProvider.setImplementationDescription("HAPI FHIR DSTU3 Server");
			setServerConformanceProvider(confProvider);
		} else if (fhirVersion == FhirVersionEnum.R4) {
			IFhirSystemDao<org.hl7.fhir.r4.model.Bundle, org.hl7.fhir.r4.model.Meta> systemDao = appCtx.getBean("mySystemDaoR4", IFhirSystemDao.class);
			JpaConformanceProviderR4 confProvider = new JpaConformanceProviderR4(this, systemDao, appCtx.getBean(DaoConfig.class));
			confProvider.setImplementationDescription("HAPI FHIR R4 Server");
			setServerConformanceProvider(confProvider);
		} else {
			throw new IllegalStateException();
		}

		/*
		 * Enable ETag Support (this is already the default)
		 */
		setETagSupport(HapiProperties.getEtagSupport());

		/*
		 * This server tries to dynamically generate narratives
		 */
		FhirContext ctx = getFhirContext();
	//	ctx.setNarrativeGenerator(new DefaultThymeleafNarrativeGenerator());

		/*
		 * Default to JSON and pretty printing
		 */
		setDefaultPrettyPrint(HapiProperties.getDefaultPrettyPrint());

		/*
		 * Default encoding
		 */
		setDefaultResponseEncoding(HapiProperties.getDefaultEncoding());


		setPagingProvider(appCtx.getBean(DatabaseBackedPagingProvider.class));


		String serverAddress = HapiProperties.getServerAddress();
		if (serverAddress != null && serverAddress.length() > 0) {
			setServerAddressStrategy(new HardcodedServerAddressStrategy(serverAddress));
		}

		setServerName(HapiProperties.getServerName());
		setServerVersion(HapiProperties.getSoftwareVersion());
		setImplementationDescription(HapiProperties.getSoftwareImplementationDesc());


		if (HapiProperties.getSubscriptionWebsocketEnabled() ||
				HapiProperties.getSubscriptionEmailEnabled() ||
				HapiProperties.getSubscriptionRestHookEnabled()) {
			// Loads subscription interceptors (SubscriptionActivatingInterceptor, SubscriptionMatcherInterceptor)
			// with activation of scheduled subscription
			SubscriptionInterceptorLoader subscriptionInterceptorLoader = appCtx.getBean(SubscriptionInterceptorLoader.class);
			subscriptionInterceptorLoader.registerInterceptors();

			// Subscription debug logging
			InterceptorService interceptorService = (InterceptorService) appCtx.getBean("interceptorService");
			interceptorService.registerInterceptor(new SubscriptionDebugLogInterceptor());
		}
		if (fhirVersion == FhirVersionEnum.DSTU3) {
			 registerProvider(appCtx.getBean(TerminologyUploaderProviderDstu3.class));
		}

		if (HapiProperties.getSecurityOauth()) {
			registerInterceptor(new OAuth2Interceptor(appCtx));  // Add OAuth2 Security Filter
		}

		if (HapiProperties.getValidationFlag()) {
			RequestValidatingInterceptor requestInterceptor = new RequestValidatingInterceptor(log, ctx, HapiProperties.getValidationServer());
			registerInterceptor(requestInterceptor);
		}


	}

}
