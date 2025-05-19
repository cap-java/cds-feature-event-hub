package com.sap.cds.feature.messaging.eventhub.service;


import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import com.sap.cds.feature.messaging.eventhub.utils.EventHubErrorStatuses;
import com.sap.cds.services.environment.CdsProperties;
import com.sap.cds.services.impl.ContextualizedServiceException;
import com.sap.cds.services.impl.environment.SimplePropertiesProvider;
import com.sap.cds.services.runtime.CdsRuntime;
import com.sap.cds.services.runtime.CdsRuntimeConfigurer;
import com.sap.cloud.environment.servicebinding.api.DefaultServiceBindingBuilder;

class EventHubMessagingServiceTest {

	@Test
	void testEmit_SingleTenantNotSupported() {
		CdsProperties properties = new CdsProperties();
		CdsProperties.Messaging.MessagingServiceConfig config = new CdsProperties.Messaging.MessagingServiceConfig("cfg");
		config.setBinding("eb-mt-tests-eb");
		config.getOutbox().setEnabled(false);
		properties.getMessaging().getServices().put(config.getName(), config);

		CdsRuntimeConfigurer configurer = CdsRuntimeConfigurer.create(new SimplePropertiesProvider(properties));
		configurer.environment(() -> {
			return Stream.of(new DefaultServiceBindingBuilder()
					.withName("eb-mt-tests-eb").withServicePlan("event-connectivity")
					.withServiceName("event-broker").build());
		});

		configurer.environmentConfigurations();
		configurer.serviceConfigurations();
		configurer.eventHandlerConfigurations();
		CdsRuntime runtime = configurer.complete();


		ContextualizedServiceException e = Assertions.assertThrows(ContextualizedServiceException.class, () -> emitMessage(runtime));
		assertEquals(EventHubErrorStatuses.EVENT_HUB_EMIT_FAILED, e.getErrorStatus());

	}

	@Test
	void testEmit_TenantNotSupported() {
		CdsProperties properties = new CdsProperties();
		CdsProperties.Messaging.MessagingServiceConfig config = new CdsProperties.Messaging.MessagingServiceConfig("cfg");
		config.setBinding("eb-mt-tests-eb");
		config.getOutbox().setEnabled(false);
		properties.getMessaging().getServices().put(config.getName(), config);

		CdsRuntimeConfigurer configurer = CdsRuntimeConfigurer.create(new SimplePropertiesProvider(properties));
		configurer.environmentConfigurations();
		configurer.serviceConfigurations();
		configurer.eventHandlerConfigurations();
		CdsRuntime runtime = configurer.complete();

		ContextualizedServiceException e = Assertions.assertThrows(ContextualizedServiceException.class, () -> emitMessage(runtime));
		assertEquals(EventHubErrorStatuses.EVENT_HUB_TENANT_CONTEXT_MISSING, e.getErrorStatus());
	}

	private void emitMessage(CdsRuntime runtime) {
		EventHubMessagingService svc = runtime.getServiceCatalog().getServices(EventHubMessagingService.class).findFirst().get();
		Map<String, Object> data = new HashMap<>();
		data.put("msg", "my msg 1");
		Map<String, Object> headers = new HashMap<>();
		headers.put("msg_header", "my header 1");

		svc.emit("sap.cdscpoc.myobject.myoperation.v1", data, headers);
	}
}
