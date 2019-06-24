package uk.gov.wildfyre.cdr;

import java.lang.reflect.InvocationTargetException;
import java.util.Properties;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;

import ca.uhn.fhir.jpa.model.entity.ModelConfig;
import ca.uhn.fhir.jpa.search.ElasticsearchMappingProvider;

import ca.uhn.fhir.jpa.subscription.module.subscriber.email.IEmailSender;
import ca.uhn.fhir.jpa.subscription.module.subscriber.email.JavaMailEmailSender;
import org.apache.commons.dbcp2.BasicDataSource;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.hibernate.search.elasticsearch.cfg.ElasticsearchEnvironment;
import org.hl7.fhir.instance.model.Subscription;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import ca.uhn.fhir.jpa.config.BaseJavaConfigDstu3;
import ca.uhn.fhir.jpa.dao.DaoConfig;

import java.sql.Driver;

/**
 * This is the primary configuration file for the example server
 */
@Configuration
@EnableTransactionManagement()
public class FhirServerConfigCommon extends BaseJavaConfigDstu3 {

	private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(FhirServerConfigCommon.class);

	private Boolean allowContainsSearches = HapiProperties.getAllowContainsSearches();
	private Boolean allowMultipleDelete = HapiProperties.getAllowMultipleDelete();
	private Boolean allowExternalReferences = HapiProperties.getAllowExternalReferences();
	private Boolean expungeEnabled = HapiProperties.getExpungeEnabled();
	private Boolean allowPlaceholderReferences = HapiProperties.getAllowPlaceholderReferences();
	private Boolean subscriptionRestHookEnabled = HapiProperties.getSubscriptionRestHookEnabled();
	private Boolean subscriptionEmailEnabled = HapiProperties.getSubscriptionEmailEnabled();
	private Boolean allowOverrideDefaultSearchParams = HapiProperties.getAllowOverrideDefaultSearchParams();
	private String emailFrom = HapiProperties.getEmailFrom();
	private Boolean emailEnabled = HapiProperties.getEmailEnabled();
	private String emailHost = HapiProperties.getEmailHost();
	private Integer emailPort = HapiProperties.getEmailPort();
	private String emailUsername = HapiProperties.getEmailUsername();
	private String emailPassword = HapiProperties.getEmailPassword();

	/**
	 * Configure FHIR properties around the the JPA server via this bean
	 */
	public FhirServerConfigCommon() {
		ourLog.info("Server configured to " + (this.allowContainsSearches ? "allow" : "deny") + " contains searches");
		ourLog.info("Server configured to " + (this.allowMultipleDelete ? "allow" : "deny") + " multiple deletes");
		ourLog.info("Server configured to " + (this.allowExternalReferences ? "allow" : "deny") + " external references");
		ourLog.info("Server configured to " + (this.expungeEnabled ? "enable" : "disable") + " expunges");
		ourLog.info("Server configured to " + (this.allowPlaceholderReferences ? "allow" : "deny") + " placeholder references");
		ourLog.info("Server configured to " + (this.allowOverrideDefaultSearchParams ? "allow" : "deny") + " overriding default search params");

		if (this.emailEnabled) {
			ourLog.info("Server is configured to enable email with host '" + this.emailHost + "' and port " + this.emailPort.toString());
			ourLog.info("Server will use '" + this.emailFrom + "' as the from email address");

			if (this.emailUsername != null && this.emailUsername.length() > 0) {
				ourLog.info("Server is configured to use username '" + this.emailUsername + "' for email");
			}

			if (this.emailPassword != null && this.emailPassword.length() > 0) {
				ourLog.info("Server is configured to use a password for email");
			}
		}

		if (this.subscriptionRestHookEnabled) {
			ourLog.info("REST-hook subscriptions enabled");
		}

		if (this.subscriptionEmailEnabled) {
			ourLog.info("Email subscriptions enabled");
		}
	}


	@Bean
	public DaoConfig daoConfig() {
		DaoConfig retVal = new DaoConfig();
		retVal.setAllowContainsSearches(this.allowContainsSearches);
		retVal.setAllowMultipleDelete(this.allowMultipleDelete);
		retVal.setAllowExternalReferences(this.allowExternalReferences);
		retVal.setExpungeEnabled(this.expungeEnabled);
		retVal.setAutoCreatePlaceholderReferenceTargets(this.allowPlaceholderReferences);
		retVal.setEmailFromAddress(this.emailFrom);

		Integer maxFetchSize = HapiProperties.getMaximumFetchSize();
		retVal.setFetchSizeDefaultMaximum(maxFetchSize);
		ourLog.info("Server configured to have a maximum fetch size of " + (maxFetchSize == Integer.MAX_VALUE? "'unlimited'": maxFetchSize));

		Long reuseCachedSearchResultsMillis = HapiProperties.getReuseCachedSearchResultsMillis();
		retVal.setReuseCachedSearchResultsForMillis(reuseCachedSearchResultsMillis );
		ourLog.info("Server configured to cache search results for {} milliseconds", reuseCachedSearchResultsMillis);

		// Subscriptions are enabled by channel type
		if (HapiProperties.getSubscriptionRestHookEnabled()) {
			ourLog.info("Enabling REST-hook subscriptions");
			retVal.addSupportedSubscriptionType(Subscription.SubscriptionChannelType.RESTHOOK);
		}
		if (HapiProperties.getSubscriptionEmailEnabled()) {
			ourLog.info("Enabling email subscriptions");
			retVal.addSupportedSubscriptionType(Subscription.SubscriptionChannelType.EMAIL);
		}
		if (HapiProperties.getSubscriptionWebsocketEnabled()) {
			ourLog.info("Enabling websocket subscriptions");
			retVal.addSupportedSubscriptionType(Subscription.SubscriptionChannelType.WEBSOCKET);
		}
		// TODO retVal.getTreatBaseUrlsAsLocal().add(externalUrl);
		retVal.setAllowExternalReferences(true);
		return retVal;
	}

	@Bean
	public ModelConfig modelConfig() {
		ModelConfig modelConfig = new ModelConfig();
		modelConfig.setAllowContainsSearches(this.allowContainsSearches);
		modelConfig.setAllowExternalReferences(this.allowExternalReferences);
		modelConfig.setDefaultSearchParamsCanBeOverridden(this.allowOverrideDefaultSearchParams);
		modelConfig.setEmailFromAddress(this.emailFrom);

		// You can enable these if you want to support Subscriptions from your server
		if (this.subscriptionRestHookEnabled) {
			modelConfig.addSupportedSubscriptionType(Subscription.SubscriptionChannelType.RESTHOOK);
		}

		if (this.subscriptionEmailEnabled) {
			modelConfig.addSupportedSubscriptionType(Subscription.SubscriptionChannelType.EMAIL);
		}

		return modelConfig;
	}
	/**
	 * The following bean configures the database connection. The 'url' property value of "jdbc:derby:directory:jpaserver_derby_files;create=true" indicates that the server should save resources in a
	 * directory called "jpaserver_derby_files".
	 *
	 * A URL to a remote database could also be placed here, along with login credentials and other properties supported by BasicDataSource.
	 */
	@Bean
	public DataSource dataSource() throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
		BasicDataSource retVal = new BasicDataSource();
		Driver driver = (Driver) Class.forName(HapiProperties.getDataSourceDriver()).getConstructor().newInstance();
		retVal.setDriver(driver);
		retVal.setUrl(HapiProperties.getDataSourceUrl());
		retVal.setUsername(HapiProperties.getDataSourceUsername());
		retVal.setPassword(HapiProperties.getDataSourcePassword());
		retVal.setMaxTotal(HapiProperties.getDataSourceMaxPoolSize());
		return retVal;

	}


	@Override
	@Bean
	public LocalContainerEntityManagerFactoryBean entityManagerFactory() {
		LocalContainerEntityManagerFactoryBean retVal = super.entityManagerFactory();
		retVal.setPersistenceUnitName("HAPI_PU");
		try {
			retVal.setDataSource(dataSource());
			retVal.setPersistenceProvider(new HibernatePersistenceProvider());
		} catch (Exception ex) {
			ourLog.error(ex.getMessage());
		}
		retVal.setJpaProperties(jpaProperties());
		return retVal;
	}




	private Properties jpaProperties() {
		Properties extraProperties = new Properties();
		extraProperties.put("hibernate.dialect",  HapiProperties.getHibernateDialect());
		extraProperties.put("hibernate.format_sql", "true");
		extraProperties.put("hibernate.show_sql", HapiProperties.getHibernateShowSql());
		extraProperties.put("hibernate.hbm2ddl.auto", "update");
		extraProperties.put("hibernate.jdbc.batch_size", "20");
		extraProperties.put("hibernate.cache.use_query_cache", "false");
		extraProperties.put("hibernate.cache.use_second_level_cache", "false");
		extraProperties.put("hibernate.cache.use_structured_entries", "false");
		extraProperties.put("hibernate.cache.use_minimal_puts", "false");

		extraProperties.put("current_session_context_class","thread");

		extraProperties.put("hibernate.c3p0.min_size","5");
		extraProperties.put("hibernate.c3p0.max_size","20");
		extraProperties.put("hibernate.c3p0.timeout","300");
		extraProperties.put("hibernate.c3p0.max_statements","50");
		extraProperties.put("hibernate.c3p0.idle_test_period","3000");

		// the belowing properties are used for ElasticSearch integration
		extraProperties.put(ElasticsearchEnvironment.ANALYSIS_DEFINITION_PROVIDER, ElasticsearchMappingProvider.class.getName());
		extraProperties.put("hibernate.search.default.indexmanager", "elasticsearch");
		extraProperties.put("hibernate.search.default.elasticsearch.host", HapiProperties.getHibernateElasticsearchHost());
		extraProperties.put("hibernate.search.default.elasticsearch.index_schema_management_strategy", "CREATE");
		extraProperties.put("hibernate.search.default.elasticsearch.index_management_wait_timeout", "10000");
		extraProperties.put("hibernate.search.default.elasticsearch.required_index_status", "yellow");

		return extraProperties;
	}


	/**
	 * This interceptor adds some pretty syntax highlighting in responses when a browser is detected
	 */

	@Bean()
	public IEmailSender emailSender() {
		if (this.emailEnabled) {
			JavaMailEmailSender retVal = new JavaMailEmailSender();

			retVal.setSmtpServerHostname(this.emailHost);
			retVal.setSmtpServerPort(this.emailPort);
			retVal.setSmtpServerUsername(this.emailUsername);
			retVal.setSmtpServerPassword(this.emailPassword);

			return retVal;
		}

		return null;
	}

	@Bean
	public JpaTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
		JpaTransactionManager retVal = new JpaTransactionManager();
		retVal.setEntityManagerFactory(entityManagerFactory);
		return retVal;
	}

}
