/**************************************************************************
 * (C) 2019-2024 SAP SE or an SAP affiliate company. All rights reserved. *
 **************************************************************************/
package com.sap.cds.feature.messaging.eventhub.adapter;

import com.sap.cds.adapter.ServletAdapterFactory;
import com.sap.cds.adapter.UrlResourcePath;
import com.sap.cds.feature.messaging.eventhub.service.EventHubMessagingService;
import com.sap.cds.services.messaging.MessagingService;
import com.sap.cds.services.outbox.OutboxService;
import com.sap.cds.services.runtime.CdsRuntime;
import com.sap.cds.services.runtime.CdsRuntimeAware;
import com.sap.cds.services.utils.path.UrlPathUtil;
import com.sap.cds.services.utils.path.UrlResourcePathBuilder;

public class EventHubWebhookAdapterFactory implements ServletAdapterFactory, CdsRuntimeAware {

	private static final String URL_PATH = "messaging/v1.0/eb";

	private CdsRuntime runtime;

	@Override
	public void setCdsRuntime(CdsRuntime runtime) {
		this.runtime = runtime;
	}

	@Override
	public boolean isEnabled() {
		return runtime.getServiceCatalog().getServices(MessagingService.class)
				.map(OutboxService::unboxed)
				.anyMatch(EventHubMessagingService.class::isInstance);
	}

	@Override
	public String getBasePath() {
		return UrlPathUtil.normalizeBasePath(URL_PATH);
	}

	@Override
	public Object create() {
		return new EventHubWebhookAdapter(runtime);
	}

	@Override
	public String[] getMappings() {
		return new String[] { getBasePath(), getBasePath() + "/*" };
	}

	@Override
	public UrlResourcePath getServletPath() {
		return UrlResourcePathBuilder.path(getBasePath()).isPublic(false).build();
	}
}
