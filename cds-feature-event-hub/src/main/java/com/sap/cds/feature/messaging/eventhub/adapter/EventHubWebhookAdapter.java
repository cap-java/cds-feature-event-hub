/**************************************************************************
 * (C) 2019-2024 SAP SE or an SAP affiliate company. All rights reserved. *
 **************************************************************************/
package com.sap.cds.feature.messaging.eventhub.adapter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sap.cds.feature.messaging.eventhub.service.EventHubMessagingService;
import com.sap.cds.feature.messaging.eventhub.utils.EventHubBindingUtils;
import com.sap.cds.services.ErrorStatuses;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.messaging.MessagingService;
import com.sap.cds.services.messaging.service.MessagingBrokerQueueListener.MessageAccess;
import com.sap.cds.services.messaging.utils.CloudEventUtils;
import com.sap.cds.services.outbox.OutboxService;
import com.sap.cds.services.request.UserInfo;
import com.sap.cds.services.runtime.CdsRuntime;
import com.sap.cds.services.utils.ErrorStatusException;
import com.sap.cloud.environment.servicebinding.api.ServiceBinding;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class EventHubWebhookAdapter extends HttpServlet {

	private static final long serialVersionUID = 1L;
	private static final Logger logger = LoggerFactory.getLogger(EventHubWebhookAdapter.class);
	private static final String UNEXPECTED_ERROR_OCCURRED_MESSAGE = "An unexpected error occurred during servlet processing";

	private final CdsRuntime runtime;
	private final List<EventHubMessagingService> messagingServices;
	private final String clientId;
	private final boolean isMultitenant;

	public EventHubWebhookAdapter(CdsRuntime runtime) {
		this.runtime = runtime;
		this.messagingServices = runtime.getServiceCatalog().getServices(MessagingService.class)
				.map(OutboxService::unboxed)
				.filter(EventHubMessagingService.class::isInstance)
				.map(EventHubMessagingService.class::cast)
				.toList();
		ServiceBinding binding = EventHubBindingUtils.getServiceBinding(runtime).get();
		this.clientId = EventHubBindingUtils.getClientId(binding);
		this.isMultitenant = EventHubBindingUtils.isBindingMultitenant(binding);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		try {
			checkAuthorization(req);

			Message message = new Message(req, isMultitenant);

			for (EventHubMessagingService srv : messagingServices) {
				// only if the receive message topic is subscribed by the service
				if (srv.isRegisteredBrokerTopic(message.getBrokerTopic())) {
					try {
						srv.getQueueListener().receivedMessage(message);
						resp.setStatus(HttpStatus.SC_ACCEPTED);
					} catch (ServiceException exp) {
						if (message.isAcknowledged()) {
							logger.debug("Suppressed exception, as message should be acknowledged", exp);
							resp.setStatus(HttpStatus.SC_ACCEPTED);
						} else {
							throw exp;
						}
					}
				}
			}

		} catch (ServiceException e) {
			int httpStatus = e.getErrorStatus().getHttpStatus();
			if(httpStatus >= 500 && httpStatus < 600) {
				logger.error(UNEXPECTED_ERROR_OCCURRED_MESSAGE, e);
			} else {
				logger.debug(UNEXPECTED_ERROR_OCCURRED_MESSAGE, e);
			}

			writeErrorResponse(req, resp, httpStatus, e.getMessage());
		} catch (Exception e) { // NOSONAR
			logger.error(UNEXPECTED_ERROR_OCCURRED_MESSAGE, e);
			writeErrorResponse(req, resp, 500, new ErrorStatusException(ErrorStatuses.SERVER_ERROR).getMessage());
		}
	}

	private void checkAuthorization(HttpServletRequest req) {
		UserInfo userInfo = runtime.getProvidedUserInfo();
		String azp = (String) userInfo.getAdditionalAttributes().get("azp");
		if(!userInfo.isSystemUser() || azp == null || !azp.equals(clientId)) {
			throw new ErrorStatusException(ErrorStatuses.FORBIDDEN);
		}
	}

	private void writeErrorResponse(HttpServletRequest req, HttpServletResponse resp, int httpStatus, String message) throws IOException {
		String responseContent = "{\"error\":{\"code\":\"" + httpStatus + "\",\"message\":\"" + message + "\"}}";
		resp.setStatus(httpStatus);
		resp.setContentType("application/json");
		resp.getWriter().println(responseContent);
	}

	private static class Message implements MessageAccess {

		private final String id;
		private final String topic;
		private final Map<String, Object> dataMap;
		private final Map<String, Object> headersMap;
		private final String tenant;

		private volatile boolean acknowledged;

		public Message(HttpServletRequest req, boolean isMultiTenant) {
			id = req.getHeader("ce-id");
			topic = req.getHeader("ce-type");

			// only in case of multi-tenant, the tenant header is required
			if(isMultiTenant) {
				tenant = req.getHeader("ce-sapconsumertenant");
			} else {
				tenant = null;
			}

			this.headersMap = new HashMap<>();
			req.getHeaderNames().asIterator().forEachRemaining(h -> {
				if (h.startsWith("ce-")) {
					headersMap.put(h.substring(3), req.getHeader(h));
				}
			});

			logger.debug("Received Event Hub webhook request with type '{}' with ID '{}'", topic, id);
			try {
				String message = new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
				Map<String, Object> map = CloudEventUtils.toMap(message);
				if (map == null) {
					this.dataMap = new HashMap<>(Map.of("message", message));
				} else {
					this.dataMap = map;
				}
			} catch (IOException e) {
				throw new ServiceException("Failed to read body of webhook request with type '{}' with ID '{}'", topic, id, e);
			}
		}

		@Override
		public String getTenant() {
			return tenant;
		}

		@Override
		public String getId() {
			return id;
		}

		@Override
		public String getMessage() {
			throw new IllegalStateException();
		}

		@Override
		public Map<String, Object> getDataMap() {
			return this.dataMap;
		}

		@Override
		public Map<String, Object> getHeadersMap() {
			return this.headersMap;
		}

		@Override
		public String getBrokerTopic() {
			return topic;
		}

		@Override
		public void acknowledge() {
			acknowledged = true;
		}

		public boolean isAcknowledged() {
			return acknowledged;
		}
	}
}
