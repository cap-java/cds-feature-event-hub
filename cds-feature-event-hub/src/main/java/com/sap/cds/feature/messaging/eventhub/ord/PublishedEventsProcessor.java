package com.sap.cds.feature.messaging.eventhub.ord;

import static com.sap.cds.feature.messaging.eventhub.service.EventHubMessagingService.CE_SOURCE;
import static com.sap.cds.feature.messaging.eventhub.service.EventHubMessagingServiceConfiguration.KIND_LABEL;
import static com.sap.cds.services.messaging.MessagingService.EVENT_MESSAGING_ERROR;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import com.sap.cds.feature.messaging.eventhub.service.EventHubMessagingService;
import com.sap.cds.feature.messaging.eventhub.utils.EventHubBindingUtils;
import com.sap.cds.feature.ord.processor.CdsOrdNodeProcessor;
import com.sap.cds.services.environment.CdsProperties.Messaging.MessagingServiceConfig;
import com.sap.cds.services.messaging.MessagingService;
import com.sap.cds.services.outbox.OutboxService;
import com.sap.cds.services.runtime.CdsRuntime;
import com.sap.cds.services.utils.CdsErrorStatuses;
import com.sap.cds.services.utils.ErrorStatusException;
import com.sap.cds.services.utils.StringUtils;
import com.sap.cloud.environment.servicebinding.api.ServiceBinding;

public class PublishedEventsProcessor implements CdsOrdNodeProcessor {
	private static final String INTEGRATION_DEPENDENCIES_KEY = "integrationDependencies";

	private static final Logger logger = LoggerFactory.getLogger(PublishedEventsProcessor.class);

	private CdsRuntime runtime;

	private boolean processedNode = false;
	private String packageOrdId;
	private String packageVersion;
	private boolean hasEventHubBinding = false;
	private String namespace;

	@Override
	public Predicate<String> predicate() {
		return nodeName -> ("integrationDependencies".equalsIgnoreCase(nodeName)
				|| "packages".equalsIgnoreCase(nodeName)) && hasEventHubBinding;
	}

	@Override
	public boolean canGenerate() {
		return !this.processedNode && hasEventHubBinding;
	}

	@Override
	public String getGeneratedNodeName() {
		return INTEGRATION_DEPENDENCIES_KEY;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T extends TreeNode> Optional<T> process(String nodeName, T node) {
		// - if node is a "packages" node:
		//   - retrieve and store the ordId of the first package
		//   - return the passed node again
		if ("packages".equalsIgnoreCase(nodeName)) {
			this.packageOrdId = retrievePackageOrdId(node);
			this.packageVersion = retrievePackageVersion(node);
			return Optional.ofNullable(node);
		}

		JsonNode resultNode;
		ArrayNode integrationDependenciesArray;

		List<String> consumedEvents = this.getConsumedEvents();

		// - if node is null, create a new node named "integrationDependencies" which is an array node,
		//   otherwise use the passed node for further processing
		if (node == null) {
			// TODO This does not work!!! -> needs to be fixed
			integrationDependenciesArray = JsonNodeFactory.instance.arrayNode();
			resultNode = integrationDependenciesArray;
		} else if (consumedEvents.isEmpty()) {
			return Optional.empty();
		} else {
			resultNode = (JsonNode) node;
			integrationDependenciesArray = (ArrayNode) node;
		}

		validateValues(this.packageOrdId, this.packageVersion);

		// - Create a new IntegrationDependency object with the mandatory fields:
		ObjectNode integrationDependency = JsonNodeFactory.instance.objectNode();

		//   - ordId: "<namespace>:integrationDependency:RawEvent:v1"
		integrationDependency.put("ordId", namespace + ":integrationDependency:RawEvent:v1");

		//   - title: "Customer Integration Needs"
		integrationDependency.put("title", "Customer Integration Needs");

		//   - partOfPackage:
		integrationDependency.put("partOfPackage", this.packageOrdId);

		//   - version:
		integrationDependency.put("version", this.packageVersion);

		//   - visibility: "public"
		integrationDependency.put("visibility", "public");

		//   - releaseStatus: "active"
		integrationDependency.put("releaseStatus", "active");

		//   - mandatory: false
		integrationDependency.put("mandatory", false);

		//   - aspects: (array)
		ArrayNode aspectsArray = JsonNodeFactory.instance.arrayNode();
		ObjectNode aspectObject = JsonNodeFactory.instance.objectNode();
		aspectsArray.add(aspectObject);
		integrationDependency.putIfAbsent("aspects", aspectsArray);
		//     - title: "RawEvent"
		aspectObject.put("title", "RawEvent");
		//     - mandatory: false
		aspectObject.put("mandatory", false);

		//     - eventResources: (array)
		ArrayNode eventResourcesArray = JsonNodeFactory.instance.arrayNode();
		ObjectNode eventResourceObject = JsonNodeFactory.instance.objectNode();
		eventResourcesArray.add(eventResourceObject);
		aspectObject.putIfAbsent("eventResources", eventResourcesArray);
		//       - ordId: "<namespace>:eventResource:RawEvent:v1"
		eventResourceObject.put("ordId", namespace + ":eventResource:RawEvent:v1");
		//       - subset: (array)
		ArrayNode subsetArray = JsonNodeFactory.instance.arrayNode();
		eventResourceObject.putIfAbsent("subset", subsetArray);
		consumedEvents.forEach(eventType -> {
			ObjectNode subsetObject = JsonNodeFactory.instance.objectNode();
			subsetObject.put("eventType", eventType);
			subsetArray.add(subsetObject);
		});

		integrationDependenciesArray.add(integrationDependency);

		this.processedNode = true;

		return Optional.of((T) resultNode);
	}

	private void validateValues(String packageOrdId, String packageVersion) {
		if (StringUtils.isEmpty(packageOrdId)) {
			throw new ErrorStatusException(CdsErrorStatuses.PACKAGE_ORDID_NOT_FOUND);
		}

		if (StringUtils.isEmpty(packageVersion)) {
			throw new ErrorStatusException(CdsErrorStatuses.PACKAGE_VERSION_NOT_FOUND);
		}
	}

	@SuppressWarnings("unchecked")
	private String retrieveNamespaceFromBindings(ServiceBinding binding) {
		String ceSource = ((List<String>) binding.getCredentials().get(CE_SOURCE)).get(0);
		String[] components = ceSource.split("/");

		logger.info("ceSource found in service binding; ceSource = {} | components = [{}]", ceSource, String.join(", ", components));

		if (components.length > 1) {
			return components[components.length - 1];
		} else {
			logger.error("Namespace not found in service binding; ceSource = {}", ceSource);
			throw new ErrorStatusException(CdsErrorStatuses.NAMESPACE_NOT_FOUND);
		}
	}

	private <T extends TreeNode> String retrievePackageVersion(T node) {
		ArrayNode packagesNode = (ArrayNode) node;

		if (!packagesNode.isEmpty()) {
			return packagesNode.get(0).get("version").asText();
		}

		return null;
	}

	private <T extends TreeNode> String retrievePackageOrdId(T node) {
		ArrayNode packagesNode = (ArrayNode) node;

		if (!packagesNode.isEmpty()) {
			return packagesNode.get(0).get("ordId").asText();
		}

		return null;
	}

	@Override
	public void setCdsRuntime(CdsRuntime runtime) {
		this.runtime = runtime;
		Optional<ServiceBinding> binding = EventHubBindingUtils.getServiceBinding(this.runtime);
		if (binding.isPresent()) {
			this.hasEventHubBinding = true;
			this.namespace = retrieveNamespaceFromBindings(binding.get());
		}
	}

	@VisibleForTesting
	List<String> getConsumedEvents() {
		// for now, we only support event subscription from EventHub
		List<String> kinds = List.of(KIND_LABEL);
		return this.getConsumedEvents(kinds).toList();
	}

	@VisibleForTesting
	Stream<String> getConsumedEvents(List<String> messagingServiceKinds) {
		Set<String> kinds = new HashSet<>(messagingServiceKinds);
		return this.runtime
				.getEnvironment()
				.getCdsProperties()
				.getMessaging()
				.getServices()
				.values()
				.stream()
				.filter(messagingServiceConfig -> kinds.contains(messagingServiceConfig.getKind()))
				.map(MessagingServiceConfig::getName)
				.map(serviceName -> this.runtime.getServiceCatalog().getService(MessagingService.class, serviceName))
				.map(OutboxService::unboxed)
				.map(service -> (EventHubMessagingService) service)
				.flatMap(messagingService ->
						messagingService.getQueueTopicSubscriptions().entrySet().stream()
								.flatMap(entry -> entry.getValue().stream())
				)
				.filter(event -> !"*".equals(event) && !EVENT_MESSAGING_ERROR.equals(event));
	}

	@VisibleForTesting
	String getPackageOrdId() {
		return this.packageOrdId;
	}

	@VisibleForTesting
	String getPackageVersion() {
		return this.packageVersion;
	}
}
