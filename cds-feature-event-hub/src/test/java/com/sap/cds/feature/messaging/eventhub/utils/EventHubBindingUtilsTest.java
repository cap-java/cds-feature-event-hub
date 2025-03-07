package com.sap.cds.feature.messaging.eventhub.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.sap.cds.services.environment.CdsProperties;
import com.sap.cds.services.impl.environment.SimplePropertiesProvider;
import com.sap.cds.services.runtime.CdsRuntime;
import com.sap.cds.services.runtime.CdsRuntimeConfigurer;
import com.sap.cloud.environment.servicebinding.api.DefaultServiceBindingBuilder;
import com.sap.cloud.environment.servicebinding.api.ServiceBinding;

class EventHubBindingUtilsTest {

	private ServiceBinding binding;
	private CdsRuntime runtime;

	@BeforeEach
	public void setUp() throws Exception {
		CdsProperties properties = new CdsProperties();
		properties.getEnvironment().getLocal().setDefaultEnvPath("classpath:bindings.json");
		runtime = CdsRuntimeConfigurer.create(new SimplePropertiesProvider(properties)).environmentConfigurations().complete();
		binding = runtime.getEnvironment().getServiceBindings().findFirst().get();
	}

	@Test
	void testGetClientId() {
		String clientId = EventHubBindingUtils.getClientId(binding);
		assertEquals("a5de02ca-a031-47b6-9bec-e15ac24c663a", clientId);
	}

	@Test
	void testIsBindingMultiTenant() {
		boolean isMultitenant = EventHubBindingUtils.isBindingMultitenant(binding);
		assertTrue(isMultitenant);
	}

	@Test
	void testIsBindingSingleTenant() {
		binding = new DefaultServiceBindingBuilder().withCredentials(binding.getCredentials()).withTags(List.of("event-broker")).build();
		boolean isMultitenant = EventHubBindingUtils.isBindingMultitenant(binding);
		assertFalse(isMultitenant);
	}

	@Test
	void testGetServiceBinding() {
		ServiceBinding retrievedBinding = EventHubBindingUtils.getServiceBinding(runtime).get();
		assertEquals(binding, retrievedBinding);
	}
}
