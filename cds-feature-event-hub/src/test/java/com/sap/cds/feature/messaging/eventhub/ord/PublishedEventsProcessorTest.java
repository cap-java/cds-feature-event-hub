package com.sap.cds.feature.messaging.eventhub.ord;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sap.cds.reflect.CdsModel;
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
	private static final String TEST_EVENT_ORD_ID = "sap.demo:eventResource:TestEvents:v1";
	// minimal, hand-crafted CSN with an `@OrdId`-annotated event; matches the output of `cds compile --to json`
	private static final String CSN = """
			{
			  "definitions": {
			    "testEvent": {
			      "kind": "event",
			      "@OrdId": "%s",
			      "elements": {}
			    }
			  }
			}
			""".formatted(TEST_EVENT_ORD_ID);
	private final String INT_DEPS_RESULT = """
				[ {
				"ordId" : "sap.cdsjavacpoc:integrationDependency:consumedEvents:v1",
						"title" : "Consumed Events",
						"partOfPackage" : "a:b:c",
						"version" : "1.0.0",
						"visibility" : "public",
						"releaseStatus" : "active",
						"mandatory" : false,
						"aspects" : [ {
					"title" : "Subscribed Event Types",
							"mandatory" : false,
							"eventResources" : [ {
						"ordId" : "%s",
								"subset" : [ {
							"eventType" : "testEvent"
						} ]
					} ]
				} ]
			} ]
			""".formatted(TEST_EVENT_ORD_ID);

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

	@Test
	void testGetOrdAnnotatedConsumedEvents_groupsByOrdId() {
		var publishedEventsProcessor = new PublishedEventsProcessor();
		publishedEventsProcessor.setCdsRuntime(this.runtime);

		Map<String, List<String>> ordIdToEvents = publishedEventsProcessor.getOrdAnnotatedConsumedEvents();

		assertThat(ordIdToEvents.keySet(), is(Set.of(TEST_EVENT_ORD_ID)));
		assertThat(ordIdToEvents.get(TEST_EVENT_ORD_ID), is(List.of("testEvent")));
	}

	@Test
	void testGetOrdAnnotatedConsumedEvents_ignoresEventsWithoutAnnotation() {
		this.runtime = CdsRuntimeConfigurer.create(new SimplePropertiesProvider(buildProperties()))
				.cdsModel(CdsModel.reader().readCsn("""
						{ "definitions": { "testEvent": { "kind": "event", "elements": {} } } }
						"""))
				.serviceConfigurations()
				.eventHandlerConfigurations()
				.providerConfigurations()
				.complete();
		AbstractMessagingService messagingService = (AbstractMessagingService) OutboxService.unboxed(runtime.getServiceCatalog().getService(MessagingService.class, "eb-mt-tests-eb"));
		messagingService.on("testEvent", null, event -> System.out.println("Event received: " + event));
		messagingService.init();

		var publishedEventsProcessor = new PublishedEventsProcessor();
		publishedEventsProcessor.setCdsRuntime(this.runtime);

		assertThat(publishedEventsProcessor.getOrdAnnotatedConsumedEvents().isEmpty(), is(true));
	}

	private CdsProperties buildProperties() {
		CdsProperties properties = new CdsProperties();
		Map<String, CdsProperties.Messaging.MessagingServiceConfig> services = new HashMap<>();

		CdsProperties.Messaging.MessagingServiceConfig messagingServiceConfig = new CdsProperties.Messaging.MessagingServiceConfig();
		messagingServiceConfig.setKind("event-hub");
		services.put("eb-mt-tests-eb", messagingServiceConfig);
		properties.getMessaging().setServices(services);
		return properties;
	}

	@BeforeEach
	public void prepare() {
		this.runtime = CdsRuntimeConfigurer.create(new SimplePropertiesProvider(buildProperties()))
				.cdsModel(CdsModel.reader().readCsn(CSN))
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
