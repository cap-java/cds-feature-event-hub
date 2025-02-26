package com.sap.cds.feature.messaging.eventhub.ord;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sap.cds.services.environment.CdsProperties;
import com.sap.cds.services.impl.environment.SimplePropertiesProvider;
import com.sap.cds.services.messaging.MessagingService;
import com.sap.cds.services.messaging.service.AbstractMessagingService;
import com.sap.cds.services.outbox.OutboxService;
import com.sap.cds.services.runtime.CdsRuntime;
import com.sap.cds.services.runtime.CdsRuntimeConfigurer;


class PublishedEventsProcessorTest {

	private CdsRuntime runtime;

	private final String PACKAGE_ORD_ID = "a:b:c";
	private final String PACKAGE_VERSION = "1.0.0";
	private final String INT_DEPS_RESULT = """
				[ {
				"ordId" : "sap.cdsjavacpoc:integrationDependency:RawEvent:v1",
						"title" : "Customer Integration Needs",
						"partOfPackage" : "a:b:c",
						"version" : "1.0.0",
						"visibility" : "public",
						"releaseStatus" : "active",
						"mandatory" : false,
						"aspects" : [ {
					"title" : "RawEvent",
							"mandatory" : false,
							"eventResources" : [ {
						"ordId" : "sap.cdsjavacpoc:eventResource:RawEvent:v1",
								"subset" : [ {
							"eventType" : "testEvent"
						} ]
					} ]
				} ]
			} ]
			""";

	@Test
	void testPredicate() {
		var publishedEventsProcessor = new PublishedEventsProcessor();
		publishedEventsProcessor.setCdsRuntime(this.runtime);
		Predicate<String> predicate = publishedEventsProcessor.predicate();

		assertThat(predicate.test("integrationDependencies"), is(true));
		assertThat(predicate.test("packages"), is(true));
	}

	@Test
	void testPredicate_Negative() {
		var publishedEventsProcessor = new PublishedEventsProcessor();
		publishedEventsProcessor.setCdsRuntime(this.runtime);
		Predicate<String> predicate = publishedEventsProcessor.predicate();

		assertThat(predicate.test("apiResources"), is(false));
	}

	@Test
	void testPredicate_NegativeWithoutBinding() {
		this.runtime = CdsRuntimeConfigurer.create()
				.environment(() -> Stream.empty())
				.serviceConfigurations()
				.eventHandlerConfigurations()
				.providerConfigurations()
				.complete();
		var publishedEventsProcessor = new PublishedEventsProcessor();
		publishedEventsProcessor.setCdsRuntime(this.runtime);
		Predicate<String> predicate = publishedEventsProcessor.predicate();

		assertThat(predicate.test("integrationDependencies"), is(false));
		assertThat(predicate.test("packages"), is(false));
	}

	@Test
	void testGetGeneratedNodeName() {
		var publishedEventsProcessor = new PublishedEventsProcessor();
		publishedEventsProcessor.setCdsRuntime(this.runtime);
		String generatedNodeName = publishedEventsProcessor.getGeneratedNodeName();

		assertThat(generatedNodeName, is("integrationDependencies"));
	}

	@Test
	void testProcess_Packages() {
		ArrayNode packages = createPackageNode();

		var publishedEventsProcessor = new PublishedEventsProcessor();
		publishedEventsProcessor.setCdsRuntime(this.runtime);
		Optional<ArrayNode> newPackages = publishedEventsProcessor.process("packages", packages);

		assertThat(publishedEventsProcessor.getPackageOrdId(), is(PACKAGE_ORD_ID));
		assertThat(publishedEventsProcessor.getPackageVersion(), is(PACKAGE_VERSION));
		assertThat(newPackages.isPresent(), is(true));
		assertThat(newPackages.get().toPrettyString(), is(packages.toPrettyString()));
	}

	@Test
	void testProcess_IntegrationDependencies() throws IOException {
		ArrayNode packages = createPackageNode();

		var publishedEventsProcessor = new PublishedEventsProcessor();
		publishedEventsProcessor.setCdsRuntime(this.runtime);
		publishedEventsProcessor.process("packages", packages);

		Optional<ArrayNode> newIntegrationDependencies = publishedEventsProcessor.process("integrationDependencies", JsonNodeFactory.instance.arrayNode());

		assertThat(newIntegrationDependencies.isPresent(), is(true));
		assertThat(newIntegrationDependencies.get().toPrettyString(), is(new ObjectMapper().readTree(INT_DEPS_RESULT).toPrettyString()));
	}

	@Test
	void testProcess_IntegrationDependencies_NoEvents() {
		// reset messaging services that have been initialized in the prepare method
		this.runtime.getEnvironment().getCdsProperties().getMessaging().setServices(Collections.emptyMap());
		AbstractMessagingService messagingService = (AbstractMessagingService) OutboxService.unboxed(runtime.getServiceCatalog().getService(MessagingService.class, "eb-mt-tests-eb"));
		messagingService.init();

		ArrayNode packages = createPackageNode();

		var publishedEventsProcessor = new PublishedEventsProcessor();
		publishedEventsProcessor.setCdsRuntime(this.runtime);
		publishedEventsProcessor.process("packages", packages);

		Optional<ArrayNode> newIntegrationDependencies = publishedEventsProcessor.process("integrationDependencies", JsonNodeFactory.instance.arrayNode());

		assertThat(newIntegrationDependencies.isPresent(), is(false));
	}

	@Test
	void testProcess_getConsumedEvents() {
		var publishedEventsProcessor = new PublishedEventsProcessor();
		publishedEventsProcessor.setCdsRuntime(this.runtime);

		var events = publishedEventsProcessor.getConsumedEvents();

		assertThat(events.size(), is(1));
	}

	@Test
	void testProcess_getConsumedEvents_NoEvents() {
		// reset messaging services that have been initialized in the prepare method
		this.runtime.getEnvironment().getCdsProperties().getMessaging().setServices(Collections.emptyMap());
		AbstractMessagingService messagingService = (AbstractMessagingService) OutboxService.unboxed(runtime.getServiceCatalog().getService(MessagingService.class, "eb-mt-tests-eb"));
		messagingService.init();

		var publishedEventsProcessor = new PublishedEventsProcessor();
		publishedEventsProcessor.setCdsRuntime(this.runtime);

		var events = publishedEventsProcessor.getConsumedEvents();

		assertThat(events.size(), is(0));
	}

	@BeforeEach
	public void prepare() {
		CdsProperties properties = new CdsProperties();
		Map<String, CdsProperties.Messaging.MessagingServiceConfig> services = new HashMap<>();

		CdsProperties.Messaging.MessagingServiceConfig messagingServiceConfig = new CdsProperties.Messaging.MessagingServiceConfig();
		messagingServiceConfig.setKind("event-hub");
		services.put("eb-mt-tests-eb", messagingServiceConfig);
		properties.getMessaging().setServices(services);

		this.runtime = CdsRuntimeConfigurer.create(new SimplePropertiesProvider(properties))
				.serviceConfigurations()
				.eventHandlerConfigurations()
				.providerConfigurations()
				.complete();

		AbstractMessagingService messagingService = (AbstractMessagingService) OutboxService.unboxed(runtime.getServiceCatalog().getService(MessagingService.class, "eb-mt-tests-eb"));

		messagingService.on("testEvent", null, event ->
				System.out.println("Event received: " + event)
		);
		messagingService.init();
	}

	private ArrayNode createPackageNode() {
		ArrayNode packages = JsonNodeFactory.instance.arrayNode();
		ObjectNode packageNode = JsonNodeFactory.instance.objectNode();
		packageNode.put("ordId", PACKAGE_ORD_ID);
		packageNode.put("version", PACKAGE_VERSION);
		packages.add(packageNode);
		return packages;
	}
}
