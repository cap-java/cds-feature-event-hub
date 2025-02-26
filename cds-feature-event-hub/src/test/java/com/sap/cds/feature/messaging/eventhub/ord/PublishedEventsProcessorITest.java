package com.sap.cds.feature.messaging.eventhub.ord;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;
import com.sap.cds.feature.ord.processor.OrdJsonInputStream;
import com.sap.cds.services.environment.CdsProperties;
import com.sap.cds.services.environment.CdsProperties.Messaging.MessagingServiceConfig;
import com.sap.cds.services.impl.environment.SimplePropertiesProvider;
import com.sap.cds.services.messaging.MessagingService;
import com.sap.cds.services.messaging.service.AbstractMessagingService;
import com.sap.cds.services.outbox.OutboxService;
import com.sap.cds.services.runtime.CdsRuntime;
import com.sap.cds.services.runtime.CdsRuntimeConfigurer;

class PublishedEventsProcessorITest {

	@Test
	void testWithExistingIntegrationDependencies() throws IOException {
		testIntegrationDependencies("ord/simple-open-resource-discovery-with-integrationDependencies.json");
	}

	@Test
	void testWithoutExistingIntegrationDependencies() throws IOException {
		testIntegrationDependencies("ord/simple-open-resource-discovery-without-integrationDependencies.json");
	}

	private void testIntegrationDependencies(String ordPath) throws IOException {
		CdsProperties properties = new CdsProperties();

		// create local messaging service
		MessagingServiceConfig messagingServiceConfig = new MessagingServiceConfig();
		messagingServiceConfig.setKind("event-hub");
		messagingServiceConfig.getOutbox().setEnabled(false);
		properties.getMessaging().getServices().put("eb-mt-tests-eb", messagingServiceConfig);
		CdsRuntime runtime = CdsRuntimeConfigurer
				.create(new SimplePropertiesProvider(properties))
				.serviceConfigurations()
				.eventHandlerConfigurations()
				.providerConfigurations()
				.complete();
		AbstractMessagingService messagingService = (AbstractMessagingService) OutboxService.unboxed(runtime.getServiceCatalog().getService(MessagingService.class, "eb-mt-tests-eb"));

		messagingService.on("testEvent", null, event ->
				System.out.println("Event received: " + event)
		);
		messagingService.on("com.sap.cds.ord.test-event", null, event ->
				System.out.println("Event received: " + event)
		);
		messagingService.init();

		var eventsProcessor = new PublishedEventsProcessor();
		eventsProcessor.setCdsRuntime(runtime);
		var ordInputStream = Thread.currentThread()
				.getContextClassLoader()
				.getResourceAsStream(ordPath);
		var ordJsonInputStream = new OrdJsonInputStream(ordInputStream, List.of(eventsProcessor));
		String json = IOUtils.toString(ordJsonInputStream, StandardCharsets.UTF_8);
		List<String> eventTypes= JsonPath.read(json, "$.integrationDependencies[*].aspects[*].eventResources[*].subset[*].eventType");

		assertThat(eventTypes, allOf(hasItem("testEvent"), hasItem("com.sap.cds.ord.test-event")));
	}

}
