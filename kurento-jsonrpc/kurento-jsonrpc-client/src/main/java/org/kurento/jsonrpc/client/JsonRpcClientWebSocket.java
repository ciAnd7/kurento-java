/*
 * (C) Copyright 2013 Kurento (http://kurento.org/)
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 */
package org.kurento.jsonrpc.client;

import static org.kurento.jsonrpc.JsonUtils.fromJson;
import static org.kurento.jsonrpc.JsonUtils.fromJsonRequest;
import static org.kurento.jsonrpc.JsonUtils.fromJsonResponse;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.kurento.commons.exception.KurentoException;
import org.kurento.jsonrpc.JsonRpcErrorException;
import org.kurento.jsonrpc.TransportException;
import org.kurento.jsonrpc.internal.JsonRpcConstants;
import org.kurento.jsonrpc.internal.JsonRpcRequestSenderHelper;
import org.kurento.jsonrpc.internal.client.ClientSession;
import org.kurento.jsonrpc.internal.client.ClientWebSocketResponseSender;
import org.kurento.jsonrpc.internal.client.TransactionImpl.ResponseSender;
import org.kurento.jsonrpc.internal.ws.PendingRequests;
import org.kurento.jsonrpc.message.MessageUtils;
import org.kurento.jsonrpc.message.Request;
import org.kurento.jsonrpc.message.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class JsonRpcClientWebSocket extends JsonRpcClient {

	@WebSocket(maxTextMessageSize = 64 * 1024)
	public class SimpleEchoSocket {

		@OnWebSocketClose
		public void onClose(int statusCode, String closeReason) {
			handleReconnectDisconnection(statusCode, closeReason);
		}

		@OnWebSocketConnect
		public void onConnect(Session session) {
			wsSession = session;
			rs = new ClientWebSocketResponseSender(wsSession);
			latch.countDown();
			if (connectionListener != null && !reconnecting) {
				connectionListener.connected();
			}
		}

		@OnWebSocketMessage
		public void onMessage(String message) {
			try {
				handleWebSocketTextMessage(message);
			} catch (IOException e) {
				throw new KurentoException(e);
			}
		}
	}

	private static final Logger log = LoggerFactory
			.getLogger(JsonRpcClientWebSocket.class);

	private CountDownLatch latch = new CountDownLatch(1);

	private ExecutorService execService = Executors.newFixedThreadPool(10);

	private String url;
	private volatile Session wsSession;
	private final PendingRequests pendingRequests = new PendingRequests();
	private ResponseSender rs;

	private JsonRpcWSConnectionListener connectionListener;

	private boolean clientClose = false;

	private static final long TIMEOUT = 60000;

	private WebSocketClient client;

	private boolean reconnecting = false;

	public JsonRpcClientWebSocket(String url) {
		this(url, null);
	}

	public JsonRpcClientWebSocket(String url,
			JsonRpcWSConnectionListener connectionListener) {

		this.url = url;
		this.connectionListener = connectionListener;

		rsHelper = new JsonRpcRequestSenderHelper() {
			@Override
			protected void internalSendRequest(
					Request<? extends Object> request,
					Class<JsonElement> resultClass,
					Continuation<Response<JsonElement>> continuation) {

				internalSendRequestWebSocket(request, resultClass, continuation);
			}

			@Override
			public <P, R> Response<R> internalSendRequest(Request<P> request,
					Class<R> resultClass) throws IOException {

				return internalSendRequestWebSocket(request, resultClass);
			}
		};
	}

	@Override
	public void close() throws IOException {
		if (wsSession != null) {
			clientClose = true;
			wsSession.close();
			client.destroy();
		}
	}

	public void closeNativeSession() {
		wsSession.close();
	}

	@Override
	public void connect() throws IOException {
		connectIfNecessary();
	}

	public synchronized void connectIfNecessary() throws IOException {

		if ((wsSession == null) || !wsSession.isOpen()) {

			try {

				client = new WebSocketClient();
				SimpleEchoSocket socket = new SimpleEchoSocket();

				client.start();

				ClientUpgradeRequest request = new ClientUpgradeRequest();
				// FIXME Give the client some time, otherwise the exception is
				// not thrown if the server is down.
				Thread.sleep(100);
				wsSession = client.connect(socket, new URI(url), request).get();

			} catch (Exception e) {
				throw new KurentoException(
						"Exception connecting to WebSocket server", e);
			}

			try {
				// FIXME: Make this configurable
				if (!latch.await(15, TimeUnit.SECONDS)) {
					if (connectionListener != null) {
						connectionListener.connectionTimeout();
					}
					throw new KurentoException(
							"Timeout of 15s when waiting to connect to Websocket server");
				}

				if (session == null) {

					session =
							new ClientSession(null, null,
									JsonRpcClientWebSocket.this);
					handlerManager.afterConnectionEstablished(session);

				} else {

					try {
						String result =
								rsHelper.sendRequest(
										JsonRpcConstants.METHOD_RECONNECT,
										String.class);

						log.info("Reconnection result: {}", result);

						log.info("Reconnected to the same Kurento server");

					} catch (JsonRpcErrorException e) {
						if (e.getCode() == 40007) { // Invalid session exception

							rsHelper.setSessionId(null);
							String result =
									rsHelper.sendRequest(
											JsonRpcConstants.METHOD_RECONNECT,
											String.class);

							log.info("Reconnection result: {}", result);

							log.info("Reconnected to a new Kurento server");
						}
					}
				}

			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

	public Session getWebSocketSession() {
		return wsSession;
	}

	protected void handleReconnectDisconnection(final int statusCode,
			final String closeReason) {

		if (!clientClose) {

			reconnecting  = true;

			execService.execute(new Runnable() {
				@Override
				public void run() {
					try {
						connectIfNecessary();

						reconnecting = false;
					} catch (KurentoException e) {

						handlerManager.afterConnectionClosed(session,
								closeReason);

						log.debug("WebSocket closed due to: {}", closeReason);
						wsSession = null;

						if (connectionListener != null) {
							connectionListener.disconnected();
						}

					} catch (IOException e) {
						log.warn("Exception trying to reconnect", e);
					}
				}
			});

		} else {

			handlerManager.afterConnectionClosed(session, closeReason);

			if (connectionListener != null) {
				connectionListener.disconnected();
			}
		}
	}

	private void handleRequestFromServer(final JsonObject message)
			throws IOException {

		// TODO: Think better ways to do this:
		// handleWebSocketTextMessage seems to be sequential. That is, the
		// message waits to be processed until previous message is being
		// processed. This behavior doesn't allow making a new request in the
		// handler of an event. To avoid this problem, we have decided to
		// process requests from server in a new thread (reused from
		// ExecutorService).
		execService.submit(new Runnable() {
			@Override
			public void run() {
				try {
					handlerManager.handleRequest(session,
							fromJsonRequest(message, JsonElement.class), rs);
				} catch (IOException e) {
					log.warn("Exception processing request " + message, e);
				}
			}
		});
	}

	private void handleResponseFromServer(JsonObject message) {

		Response<JsonElement> response =
				fromJsonResponse(message, JsonElement.class);

		setSessionId(response.getSessionId());

		pendingRequests.handleResponse(response);
	}

	private void handleWebSocketTextMessage(String message) throws IOException {

		JsonObject jsonMessage = fromJson(message, JsonObject.class);

		if (jsonMessage.has(JsonRpcConstants.METHOD_PROPERTY)) {
			handleRequestFromServer(jsonMessage);
		} else {
			handleResponseFromServer(jsonMessage);
		}
	}

	protected void internalSendRequestWebSocket(
			final Request<? extends Object> request,
			final Class<JsonElement> resultClass,
			final Continuation<Response<JsonElement>> continuation) {

		// FIXME: Poor man async implementation.
		execService.submit(new Runnable() {
			@Override
			public void run() {
				try {
					Response<JsonElement> result =
							internalSendRequestWebSocket(request, resultClass);
					try {
						continuation.onSuccess(result);
					} catch (Exception e) {
						log.error("Exception while processing response", e);
					}
				} catch (Exception e) {
					continuation.onError(e);
				}
			}
		});
	}

	private <P, R> Response<R> internalSendRequestWebSocket(Request<P> request,
			Class<R> resultClass) throws IOException {

		connectIfNecessary();

		Future<Response<JsonElement>> responseFuture = null;

		if (request.getId() != null) {
			responseFuture = pendingRequests.prepareResponse(request.getId());
		}

		String jsonMessage = request.toString();
		log.debug("Req-> {}", jsonMessage.trim());
		synchronized (wsSession) {
			wsSession.getRemote().sendString(jsonMessage);
		}

		if (responseFuture == null) {
			return null;
		}

		Response<JsonElement> responseJson;
		try {

			responseJson = responseFuture.get(TIMEOUT, TimeUnit.MILLISECONDS);

			log.debug("<-Res {}", responseJson.toString());

			Response<R> response =
					MessageUtils.convertResponse(responseJson, resultClass);

			if (response.getSessionId() != null) {
				session.setSessionId(response.getSessionId());
			}

			return response;

		} catch (InterruptedException e) {
			// TODO What to do in this case?
			throw new KurentoException(
					"Interrupted while waiting for a response", e);
		} catch (ExecutionException e) {
			// TODO Is there a better way to handle this?
			throw new KurentoException("This exception shouldn't be thrown", e);
		} catch (TimeoutException e) {
			throw new TransportException("Timeout of " + TIMEOUT
					+ " milliseconds waiting from response to request with id:"
					+ request.getId(), e);
		}
	}
}
