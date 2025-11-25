package com.sap.cds.feature.messaging.eventhub.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.sap.cds.services.environment.CdsProperties;
import com.sap.cds.services.impl.environment.SimplePropertiesProvider;
import com.sap.cds.services.runtime.CdsRuntime;
import com.sap.cds.services.runtime.CdsRuntimeConfigurer;
import com.sap.cloud.environment.servicebinding.api.ServiceBinding;

class EventHubBindingUtilsTest {

	private Map<String, ServiceBinding> bindings = new HashMap<String, ServiceBinding>();
	private Map<String, CdsRuntime> runtimes = new HashMap<String, CdsRuntime>();

	@BeforeEach
	public void setUp() throws Exception {
		loadBinding("mt-binding", "bindings-mt.json");
		loadBinding("st-binding", "bindings-st.json");
	}

	private void loadBinding(String id, String bindingPath) {
		CdsProperties properties = new CdsProperties();
		properties.getEnvironment().getLocal().setDefaultEnvPath("classpath:" + bindingPath);
		CdsRuntime runtime = CdsRuntimeConfigurer.create(new SimplePropertiesProvider(properties)).environmentConfigurations().complete();
		runtimes.put(id, runtime);
		bindings.put(id, runtime.getEnvironment().getServiceBindings().findFirst().get());
	}

	@Test
	void testGetClientId() {
		for (ServiceBinding binding : bindings.values()) {
			String clientId = EventHubBindingUtils.getClientId(binding);
			assertEquals("a5de02ca-a031-47b6-9bec-e15ac24c663a", clientId);
		}
	}

	@Test
	void testIsBindingMultiTenant() {
		assertTrue(EventHubBindingUtils.isBindingMultitenant(bindings.get("mt-binding")));
		assertFalse(EventHubBindingUtils.isBindingMultitenant(bindings.get("st-binding")));
	}

	@Test
	void testGetServiceBinding() {
		for (String id : runtimes.keySet()) {
			CdsRuntime runtime = runtimes.get(id);
			ServiceBinding retrievedBinding = EventHubBindingUtils.getServiceBinding(runtime).get();
			assertEquals(bindings.get(id), retrievedBinding);
		}
	}

	@Test
	void testBindingHasEndpoints() {
		for (ServiceBinding binding : bindings.values()) {
			assertTrue(EventHubBindingUtils.bindingHasEndpoints(binding));
		}
	}
}
