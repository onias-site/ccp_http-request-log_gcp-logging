package com.ccp.implementations.http.request.log.gcp.logging;

import com.ccp.dependency.injection.CcpInstanceProvider;
import com.ccp.especifications.http.CcpHttpRequestLog;

/**
 * DI provider that exposes {@code GcpLoggingHttpRequestLog} as the {@code CcpHttpRequestLog} implementation.
 */
public class CcpGcpHttpRequestLog implements CcpInstanceProvider<CcpHttpRequestLog> {
	/** The single instance: the class keeps no state. */
	public final static CcpGcpHttpRequestLog INSTANCE = new CcpGcpHttpRequestLog();

	private CcpGcpHttpRequestLog() {}

	/**
	 * Returns the Cloud Logging implementation of {@code CcpHttpRequestLog}.
	 * @return the single {@code GcpLoggingHttpRequestLog}
	 */
	public CcpHttpRequestLog getInstance() {
		GcpLoggingHttpRequestLog gcpLoggingHttpRequestLog = GcpLoggingHttpRequestLog.INSTANCE;
		return gcpLoggingHttpRequestLog;
	}

}
