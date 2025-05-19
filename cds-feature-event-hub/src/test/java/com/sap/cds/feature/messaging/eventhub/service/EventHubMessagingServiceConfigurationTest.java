package com.sap.cds.feature.messaging.eventhub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import com.sap.cds.feature.messaging.eventhub.utils.EventHubErrorStatuses;
import com.sap.cds.services.environment.CdsProperties;
import com.sap.cds.services.impl.environment.SimplePropertiesProvider;
import com.sap.cds.services.messaging.MessagingService;
import com.sap.cds.services.runtime.CdsRuntime;
import com.sap.cds.services.runtime.CdsRuntimeConfigurer;
import com.sap.cds.services.utils.ErrorStatusException;
import com.sap.cloud.environment.servicebinding.api.DefaultServiceBindingBuilder;

class EventHubMessagingServiceConfigurationTest {

	@Test
	void testNoBinding() {
		CdsRuntimeConfigurer configurer = CdsRuntimeConfigurer.create();
		configurer.environment(() -> Stream.empty());
		CdsRuntime runtime = configurer.complete();

		assertEquals(0, runtime.getServiceCatalog().getServices(MessagingService.class).count());
	}

	@Test
	void testDefaultServConfiguration() {
		CdsRuntimeConfigurer configurer = CdsRuntimeConfigurer.create();
		configurer.environment(() -> {
			return Stream.of(new DefaultServiceBindingBuilder()
					.withName("eb-mt-tests-eb").withServicePlan("event-connectivity")
					.withServiceName("event-broker").build());
		});

		configurer.serviceConfigurations();
		CdsRuntime runtime = configurer.complete();

		List<MessagingService> services = runtime.getServiceCatalog().getServices(MessagingService.class).toList();
		assertEquals(1, services.size());
		assertEquals("eb-mt-tests-eb", services.get(0).getName());
	}

	@Test
	void testSingleServConfiguration() {
		CdsProperties properties = new CdsProperties();
		CdsProperties.Messaging.MessagingServiceConfig config = new CdsProperties.Messaging.MessagingServiceConfig("cfg");
		config.setBinding("eb-mt-tests-eb");
		config.getOutbox().setEnabled(false);
		properties.getMessaging().getServices().put(config.getName(), config);

		assertEquals(0, properties.getMessaging().getServicesByBinding("").size());
		assertEquals(1, properties.getMessaging().getServicesByBinding("eb-mt-tests-eb").size());
		assertEquals(0, properties.getMessaging().getServicesByKind("").size());
		assertEquals(0, properties.getMessaging().getServicesByKind("event-broker").size());

		CdsRuntimeConfigurer configurer = CdsRuntimeConfigurer.create(new SimplePropertiesProvider(properties));
		configurer.environment(() -> {
			return Stream.of(new DefaultServiceBindingBuilder()
					.withName("eb-mt-tests-eb").withServicePlan("event-connectivity")
					.withServiceName("event-broker").build());
		});

		configurer.environmentConfigurations();
		configurer.serviceConfigurations();
		configurer.eventHandlerConfigurations();

		List<EventHubMessagingService> services = configurer.getCdsRuntime().getServiceCatalog().getServices(EventHubMessagingService.class).collect(Collectors.toList());

		assertEquals(1, services.size());
		assertEquals("cfg", services.get(0).getName());
	}

	@Test
	void testSingleServiceByKindConfiguration() {
		CdsProperties properties = new CdsProperties();
		CdsProperties.Messaging.MessagingServiceConfig config = new CdsProperties.Messaging.MessagingServiceConfig("cfg");
		config.setKind("event-hub");
		config.getOutbox().setEnabled(false);
		properties.getMessaging().getServices().put(config.getName(), config);

		assertEquals(0, properties.getMessaging().getServicesByBinding("").size());
		assertEquals(0, properties.getMessaging().getServicesByBinding("eb-mt-tests-eb").size());
		assertEquals(0, properties.getMessaging().getServicesByKind("").size());
		assertEquals(1, properties.getMessaging().getServicesByKind("event-hub").size());

		CdsRuntimeConfigurer configurer = CdsRuntimeConfigurer.create(new SimplePropertiesProvider(properties));
		configurer.environment(() -> {
			return Stream.of(new DefaultServiceBindingBuilder()
					.withName("eb-mt-tests-eb").withServicePlan("event-connectivity")
					.withServiceName("event-broker").build());
		});

		configurer.serviceConfigurations();
		configurer.eventHandlerConfigurations();

		List<EventHubMessagingService> services = configurer.getCdsRuntime().getServiceCatalog().getServices(EventHubMessagingService.class).collect(Collectors.toList());

		assertEquals(1, services.size());
		assertEquals("cfg", services.get(0).getName());
	}

	@Test
	void testSingleServiceByBindingAndKindConfiguration() {
		CdsProperties properties = new CdsProperties();
		CdsProperties.Messaging.MessagingServiceConfig config = new CdsProperties.Messaging.MessagingServiceConfig("cfg");
		config.setBinding("eb-mt-tests-eb");
		config.setKind("event-broker");
		config.getOutbox().setEnabled(false);
		properties.getMessaging().getServices().put(config.getName(), config);

		assertEquals(0, properties.getMessaging().getServicesByBinding("").size());
		assertEquals(1, properties.getMessaging().getServicesByBinding("eb-mt-tests-eb").size());
		assertEquals(0, properties.getMessaging().getServicesByKind("").size());
		assertEquals(1, properties.getMessaging().getServicesByKind("event-broker").size());

		CdsRuntimeConfigurer configurer = CdsRuntimeConfigurer.create(new SimplePropertiesProvider(properties));
		configurer.environment(() -> {
			return Stream.of(new DefaultServiceBindingBuilder()
					.withName("eb-mt-tests-eb").withServicePlan("event-connectivity")
					.withServiceName("event-broker").build());
		});

		configurer.serviceConfigurations();
		configurer.eventHandlerConfigurations();

		List<EventHubMessagingService> services = configurer.getCdsRuntime().getServiceCatalog().getServices(EventHubMessagingService.class).collect(Collectors.toList());

		assertEquals(1, services.size());
		assertEquals("cfg", services.get(0).getName());
	}

	@Test
	void testMultipleBindings() {
		CdsRuntimeConfigurer configurer = CdsRuntimeConfigurer.create();
		configurer.environment(() -> {
			return Stream.of(
					new DefaultServiceBindingBuilder()
					.withName("eb-mt-tests-eb").withServicePlan("event-connectivity")
					.withServiceName("event-broker").build(),
					new DefaultServiceBindingBuilder()
					.withName("eb-mt-tests-eb2").withServicePlan("event-mesh-multi-tenant")
					.withServiceName("eventmesh-sap2sap-internal").build());
		});

		ErrorStatusException e = Assertions.assertThrows(ErrorStatusException.class, () -> configurer.serviceConfigurations());
		assertEquals(EventHubErrorStatuses.MULTIPLE_EVENT_HUB_BINDINGS, e.getErrorStatus());
	}
}
