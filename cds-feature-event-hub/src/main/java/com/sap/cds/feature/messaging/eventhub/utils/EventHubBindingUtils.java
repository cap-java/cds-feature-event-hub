package com.sap.cds.feature.messaging.eventhub.utils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sap.cds.services.runtime.CdsRuntime;
import com.sap.cds.services.utils.CdsErrorStatuses;
import com.sap.cds.services.utils.ErrorStatusException;
import com.sap.cds.services.utils.environment.ServiceBindingUtils;
import com.sap.cloud.environment.servicebinding.api.ServiceBinding;

public class EventHubBindingUtils {
	private static final Logger logger = LoggerFactory.getLogger(EventHubBindingUtils.class);

	public static final String MT_BINDING_LABEL = "eventmesh-sap2sap-internal";
	public static final String ST_BINDING_LABEL = "event-broker";

	public static Optional<ServiceBinding> getServiceBinding(CdsRuntime runtime) {
		List<ServiceBinding> bindings = runtime.getEnvironment().getServiceBindings()
				.filter(binding -> ServiceBindingUtils.matches(binding, MT_BINDING_LABEL) || ServiceBindingUtils.matches(binding, ST_BINDING_LABEL))
				.toList();

		if (bindings.size() == 1) {
			ServiceBinding binding = bindings.get(0);
			logger.debug("Found EventBroker binding '{}' with service '{}' and plan '{}'", binding.getName().get(), binding.getServiceName().get(), binding.getServicePlan().get());
			return Optional.of(binding);
		} else if (bindings.size() > 1) {
			throw new ErrorStatusException(CdsErrorStatuses.MULTIPLE_EVENT_HUB_BINDINGS);
		} else {
			return Optional.empty();
		}

	}

	@SuppressWarnings("unchecked")
	public static String getClientId(ServiceBinding binding) {
		Map<String, Object> credentials = binding.getCredentials();
		Map<String, Object> ias = (Map<String, Object>) credentials.getOrDefault("ias", Map.of());
		return (String) ias.get("clientId");
	}

	public static boolean isBindingMultitenant(ServiceBinding binding) {
		return ServiceBindingUtils.matches(binding, MT_BINDING_LABEL);
	}

}
