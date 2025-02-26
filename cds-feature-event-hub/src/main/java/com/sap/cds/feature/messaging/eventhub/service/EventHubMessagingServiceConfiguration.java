package com.sap.cds.feature.messaging.eventhub.service;

import static com.sap.cds.services.messaging.utils.MessagingOutboxUtils.outboxed;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sap.cds.feature.messaging.eventhub.utils.EventHubBindingUtils;
import com.sap.cds.services.environment.CdsProperties.Messaging;
import com.sap.cds.services.environment.CdsProperties.Messaging.MessagingServiceConfig;
import com.sap.cds.services.runtime.CdsRuntimeConfiguration;
import com.sap.cds.services.runtime.CdsRuntimeConfigurer;
import com.sap.cds.services.utils.StringUtils;
import com.sap.cloud.environment.servicebinding.api.ServiceBinding;

public class EventHubMessagingServiceConfiguration implements CdsRuntimeConfiguration {
	private static final Logger logger = LoggerFactory.getLogger(EventHubMessagingServiceConfiguration.class);

	public static final String KIND_LABEL = "event-hub";

	@Override
	public void services(CdsRuntimeConfigurer configurer) {
		Messaging config = configurer.getCdsRuntime().getEnvironment().getCdsProperties().getMessaging();
		EventHubBindingUtils.getServiceBinding(configurer.getCdsRuntime()).ifPresent(binding -> {
			logger.debug("Starting the initialization of the Event Hub service binding '{}'", binding.getName().get());

			// determines whether no configuration is available and the default service should be created
			boolean createDefaultService = true;

			// check the services configured by binding
			List<MessagingServiceConfig> serviceConfigs = config.getServicesByBinding(binding.getName().get());

			if (!serviceConfigs.isEmpty()) {
				createDefaultService = false;
				serviceConfigs.forEach(serviceConfig -> {
					if (Boolean.TRUE.equals(serviceConfig.isEnabled())) {
						configureService(configurer, binding, serviceConfig);
					} else {
						logger.info("The messaging service '{}' is explicitly disabled via configuration", serviceConfig.getName());
					}
				});
			}

			// check the services configured by kind if only one service binding is available
			List<MessagingServiceConfig> serviceConfigsByKind = config.getServicesByKind(KIND_LABEL);

			if (!serviceConfigsByKind.isEmpty()) {
				logger.debug("Initialization of the Event Hub based on service binding '{}' and kind '{}'", binding.getName().get(), KIND_LABEL);
				createDefaultService = false;
				serviceConfigsByKind.forEach(serviceConfig -> {
					// check that the service is enabled and whether not already found by name or binding
					if (Boolean.TRUE.equals(serviceConfig.isEnabled())
							&& serviceConfigs.stream().noneMatch(c -> c.getName().equals(serviceConfig.getName()))) {
						configureService(configurer, binding, serviceConfig);
					} else {
						logger.info("The messaging service '{}' is explicitly disabled via configuration", serviceConfig.getName());
					}
				});
			}

			if (createDefaultService) {
				logger.debug("Initialization of the Event Hub service binding '{}' with default messaging configuration", binding.getName().get());

				// otherwise create default service instance for the binding
				MessagingServiceConfig defConfig = config.getService(binding.getName().get());
				if (StringUtils.isEmpty(defConfig.getBinding()) && StringUtils.isEmpty(defConfig.getKind())) {
					configureService(configurer, binding, defConfig);
				} else {
					logger.warn(
							"Could not create service for binding '{}': A configuration with the same name is already defined for another kind or binding.",
							binding.getName().get());
				}
			}

			logger.debug("Finished the initialization of the Event Hub service binding '{}'", binding.getName().get());
		});
	}

	private void configureService(CdsRuntimeConfigurer configurer, ServiceBinding binding, MessagingServiceConfig serviceConfig) {
		EventHubMessagingService messagingService = new EventHubMessagingService(binding, serviceConfig, configurer.getCdsRuntime());
		configurer.service(outboxed(messagingService, serviceConfig, configurer.getCdsRuntime()));
	}

}
