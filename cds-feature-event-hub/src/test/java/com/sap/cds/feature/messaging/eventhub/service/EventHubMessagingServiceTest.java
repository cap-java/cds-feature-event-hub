package com.sap.cds.feature.messaging.eventhub.service;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.sap.cds.feature.messaging.eventhub.utils.EventHubErrorStatuses;
import com.sap.cds.services.environment.CdsProperties;
import com.sap.cds.services.impl.ContextualizedServiceException;
import com.sap.cds.services.impl.environment.SimplePropertiesProvider;
import com.sap.cds.services.runtime.CdsRuntime;
import com.sap.cds.services.runtime.CdsRuntimeConfigurer;

class EventHubMessagingServiceTest {
	private EventHubMessagingService svc;
	private CdsRuntime runtime;

	@BeforeEach
	public void setUp() throws Exception {
		CdsProperties properties = new CdsProperties();
		CdsProperties.Messaging.MessagingServiceConfig config = new CdsProperties.Messaging.MessagingServiceConfig("cfg");
		config.setBinding("eb-mt-tests-eb");
		config.getOutbox().setEnabled(false);
		properties.getMessaging().getServices().put(config.getName(), config);

		CdsRuntimeConfigurer configurer = CdsRuntimeConfigurer.create(new SimplePropertiesProvider(properties));
		configurer.environmentConfigurations();
		configurer.serviceConfigurations();
		configurer.eventHandlerConfigurations();
		runtime = configurer.complete();
		svc = runtime.getServiceCatalog().getServices(EventHubMessagingService.class).findFirst().get();
	}

	@Test
	void testEmit_TenantNotSupported() {
		ContextualizedServiceException e = Assertions.assertThrows(ContextualizedServiceException.class, () -> emitMessage(runtime));
		assertEquals(EventHubErrorStatuses.EVENT_HUB_TENANT_CONTEXT_MISSING, e.getErrorStatus());
	}

	private void emitMessage(CdsRuntime runtime) {
		Map<String, Object> data = new HashMap<>();
		data.put("msg", "my msg 1");
		Map<String, Object> headers = new HashMap<>();
		headers.put("msg_header", "my header 1");
		svc.emit("sap.cdscpoc.myobject.myoperation.v1", data, headers);
	}

	@Test
	void testResolveSourceSuffixUsesSubaccountId() {
		Map<String, Object> headers = new HashMap<>();
		headers.put(EventHubMessagingService.KEY_SUBACCOUNT_ID, "my-subaccount");
		String suffix = svc.resolveSourceSuffix(headers, "tenant-id");
		assertEquals("my-subaccount", suffix);
		assertFalse(headers.containsKey(EventHubMessagingService.KEY_SUBACCOUNT_ID));
	}

	@Test
	void testResolveSourceSuffixFallsBackToTenant() {
		Map<String, Object> headers = new HashMap<>();
		String suffix = svc.resolveSourceSuffix(headers, "tenant-id");
		assertEquals("tenant-id", suffix);
		assertNull(headers.get(EventHubMessagingService.KEY_SUBACCOUNT_ID));
	}
}
