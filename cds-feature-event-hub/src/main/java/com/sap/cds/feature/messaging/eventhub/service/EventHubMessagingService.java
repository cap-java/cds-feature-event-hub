package com.sap.cds.feature.messaging.eventhub.service;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.sap.cds.feature.messaging.eventhub.client.EventHubClient;
import com.sap.cds.feature.messaging.eventhub.utils.EventHubBindingUtils;
import com.sap.cds.services.EventContext;
import com.sap.cds.services.environment.CdsProperties.Messaging.MessagingServiceConfig;
import com.sap.cds.services.messaging.TopicMessageEventContext;
import com.sap.cds.services.messaging.service.AbstractMessagingService;
import com.sap.cds.services.messaging.service.MessageTopic;
import com.sap.cds.services.messaging.service.MessagingBrokerQueueListener;
import com.sap.cds.services.messaging.utils.CloudEventUtils;
import com.sap.cds.services.mt.TenantProviderService;
import com.sap.cds.services.runtime.CdsRuntime;
import com.sap.cds.services.utils.CdsErrorStatuses;
import com.sap.cds.services.utils.ErrorStatusException;
import com.sap.cloud.environment.servicebinding.api.ServiceBinding;

public class EventHubMessagingService extends AbstractMessagingService {

	private static final Logger logger = LoggerFactory.getLogger(EventHubMessagingService.class);
	public  static final String CE_SOURCE = "ceSource";

	private final String ceSource;
	private final boolean isMultitenant;
	private final MessagingBrokerQueueListener queueListener;
	private final EventHubClient eventHubClient;
	private final Map<String, Set<String>> queueTopicSubscriptions = new HashMap<>();

	private volatile String providerTenant;

	@SuppressWarnings({ "unchecked", "deprecation" })
	public EventHubMessagingService(ServiceBinding binding, MessagingServiceConfig serviceConfig, CdsRuntime cdsRuntime) {
		super(ensureMandatoryConfig(serviceConfig), cdsRuntime);

		if (binding.getCredentials().containsKey(CE_SOURCE)) {
			this.ceSource = ((List<String>) binding.getCredentials().get(CE_SOURCE)).get(0) + '/';
		} else {
			this.ceSource = null;
		}

		this.isMultitenant = EventHubBindingUtils.isBindingMultitenant(binding);
		this.queueListener = new MessagingBrokerQueueListener(this, toFullyQualifiedQueueName(queue), queue, runtime, serviceConfig.isStructured());
		// emitting messages is only supported in multitenant mode
		this.eventHubClient = this.isMultitenant ? new EventHubClient(binding) : null;
	}

	@SuppressWarnings("deprecation")
	private static MessagingServiceConfig ensureMandatoryConfig(MessagingServiceConfig serviceConfig) {
		// for event-hub we enforce the cloudevents format
		serviceConfig.setFormat(FORMAT_CLOUDEVENTS);
		serviceConfig.setStructured(true);
		return serviceConfig;
	}

	@Override
	public void init() {
		super.init();
		if (logger.isDebugEnabled()) {
			queue.getTopics().forEach(t -> {
				logger.debug("Registered messaging handler for Event Hub event '{}'", t.getBrokerName());
			});
		}
	}

	@Override
	protected boolean createOrUpdateQueuesAndSubscriptions() {
		boolean result = super.createOrUpdateQueuesAndSubscriptions();
		String queueName = toFullyQualifiedQueueName(queue);

		for(MessageTopic topic : queue.getTopics()) {
			String topicName = topic.getBrokerName();
			cacheQueueTopicSubscription(queueName, topicName);
		}

		return result;
	}

	protected void cacheQueueTopicSubscription(String queueName, String topicName) {
		queueTopicSubscriptions.computeIfAbsent(queueName, k -> new HashSet<>()).add(topicName);
	}

	/**
	 * Returns the queue topic subscriptions of the messaging service.
	 *
	 * @return the queue topic subscriptions
	 */
	public Map<String, Set<String>> getQueueTopicSubscriptions() {
		return this.queueTopicSubscriptions.entrySet().stream()
				.collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, e -> Set.copyOf(e.getValue())));
	}

	/**
	 * @return the queue listener of the service.
	 */
	public MessagingBrokerQueueListener getQueueListener() {
		return queueListener;
	}

	public boolean isRegisteredBrokerTopic(String event) {
		return queue.getTopics().stream().anyMatch(t -> t.getBrokerName().equals(event));
	}


	@Override
	protected void removeQueue(String name) throws IOException {
		// not used
	}

	@Override
	protected void createQueue(String name, Map<String, Object> properties) throws IOException {
		// not used
	}

	@Override
	protected void createQueueSubscription(String queue, String topic) throws IOException {
		// not used
	}

	@Override
	protected void registerQueueListener(String queue, MessagingBrokerQueueListener listener) throws IOException {
		// not used (webhook approach is used)
	}

	@Override
	@SuppressWarnings("removal")
	protected void emitTopicMessage(String topic, TopicMessageEventContext context) {
		// emitting messages is only supported in multitenant mode
		if (!this.isMultitenant) {
			throw new ErrorStatusException(CdsErrorStatuses.EVENT_HUB_EMIT_FAILED);
		}

		String tenant = getTenant(context);

		if (context.getData() != null) {
			throw new ErrorStatusException(CdsErrorStatuses.INVALID_CLOUDEVENTS_MESSAGE);
		}

		try {
			Map<String, Object> headers = context.getHeadersMap();
			if (ceSource != null) {
				headers.put(CloudEventUtils.KEY_SOURCE, ceSource + tenant);
			}

			logger.debug("Sending message for Event Hub '{}' to type '{}'", getName(), headers.get(CloudEventUtils.KEY_TYPE));
			eventHubClient.sendMessage(context.getDataMap(), headers);
		} catch (IOException e) {
			throw new ErrorStatusException(CdsErrorStatuses.EVENT_EMITTING_FAILED, topic, e);
		}
	}

	private String getTenant(EventContext context) {

		String tenant = context.getUserInfo().getTenant();

		if (tenant != null) {
			return tenant;
		}

		if (providerTenant != null) {
			return providerTenant;
		}

		TenantProviderService tenantService = context.getServiceCatalog().getService(TenantProviderService.class,
				TenantProviderService.DEFAULT_NAME);

		if (tenantService != null) {
			providerTenant = tenantService.readProviderTenant();
			if (providerTenant != null) {
				return providerTenant;
			}
		}

		throw new ErrorStatusException(CdsErrorStatuses.EVENT_HUB_TENANT_CONTEXT_MISSING);

	}
}
