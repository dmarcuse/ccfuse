package me.apemanzilla.ccfuse;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft;
import org.java_websocket.exceptions.InvalidDataException;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ServerHandshakeBuilder;
import org.java_websocket.server.WebSocketServer;

import com.google.gson.Gson;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import me.apemanzilla.ccfuse.packets.relay.RelayConnectionStatusPacket;
import me.apemanzilla.ccfuse.packets.relay.RelayConnectionStatusPacket.Status;

@Slf4j
public class CCFuseRelay extends WebSocketServer {
	private static final Gson gson = new Gson();

	private static enum Role {
		SERVER, CLIENT;
	}

	@Value
	@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
	private static class TunnelConfig {
		private static final Pattern pattern = Pattern.compile("^/?(\\w+)/(client|server)/?$");

		@NonNull
		private final String channel;

		@NonNull
		private final Role role;

		public static TunnelConfig of(@NonNull URI uri) {
			val matcher = pattern.matcher(uri.getPath());

			if (!matcher.matches()) throw new IllegalArgumentException("Invalid format");

			return new TunnelConfig(matcher.group(1), matcher.group(2).equals("server") ? Role.SERVER : Role.CLIENT);
		}

		public static TunnelConfig of(@NonNull WebSocket conn) {
			return of(URI.create(conn.getResourceDescriptor()));
		}
	}

	@Getter
	private static class Tunnel {
		private WebSocket server;
		private WebSocket client;

		public boolean roleFilled(Role role) {
			switch (role) {
			case CLIENT:
				return client != null;
			case SERVER:
				return server != null;
			}

			throw new IllegalArgumentException();
		}

		public void opened(TunnelConfig cfg, WebSocket conn) {
			assert !roleFilled(cfg.getRole());

			switch (cfg.getRole()) {
			case CLIENT:
				client = conn;
				break;
			case SERVER:
				server = conn;
				break;
			}

			if (server != null && client != null)
				server.send(gson.toJson(new RelayConnectionStatusPacket(Status.CONNECTED)));
		}

		public void closed(TunnelConfig cfg) {
			switch (cfg.getRole()) {
			case CLIENT:
				client = null;
				break;
			case SERVER:
				server = null;
				break;
			}

			if (server != null && client == null)
				server.send(gson.toJson(new RelayConnectionStatusPacket(Status.DISCONNECTED)));

			if (server == null && client != null) client.close(CloseFrame.NORMAL, "Server disconnected");
		}

		public void relayPacket(TunnelConfig cfg, WebSocket from, String data) {
			val to = cfg.getRole() == Role.SERVER ? client : server;

			if (to != null) {
				to.send(data);
			} else {
				from.close(CloseFrame.PROTOCOL_ERROR, "Tunnel not connected");
			}
		}
	}

	private final Map<String, Tunnel> tunnels = new ConcurrentHashMap<>();

	public CCFuseRelay(InetSocketAddress addr) {
		super(addr);
	}

	@Override
	public ServerHandshakeBuilder onWebsocketHandshakeReceivedAsServer(WebSocket conn, Draft draft,
			ClientHandshake request) throws InvalidDataException {

		try {
			val cfg = TunnelConfig.of(conn);

			if (tunnels.containsKey(cfg.getChannel())) {
				if (tunnels.get(cfg.getChannel()).roleFilled(cfg.getRole())) {
					log.warn("Rejecting connection from {}: Role {} already filled", conn.getRemoteSocketAddress(),
							cfg.getRole());
					throw new InvalidDataException(CloseFrame.PROTOCOL_ERROR, "Role already filled");
				}
			}
		} catch (IllegalArgumentException e) {
			log.warn("Rejecting connection from {}: Invalid tunnel config", conn.getRemoteSocketAddress());
			throw new InvalidDataException(CloseFrame.PROTOCOL_ERROR, e);
		}

		return super.onWebsocketHandshakeReceivedAsServer(conn, draft, request);
	}

	@Override
	public void onOpen(WebSocket conn, ClientHandshake handshake) {
		val cfg = TunnelConfig.of(conn);
		if (!tunnels.containsKey(cfg.getChannel())) tunnels.put(cfg.getChannel(), new Tunnel());

		val t = tunnels.get(cfg.getChannel());
		t.opened(cfg, conn);

		log.info("Connection opened from {}: channel {}, role {}", conn.getRemoteSocketAddress(), cfg.getChannel(),
				cfg.getRole());

		log.debug("Currently {} tunnel(s) open", tunnels.size());
	}

	@Override
	public void onClose(WebSocket conn, int code, String reason, boolean remote) {
		val cfg = TunnelConfig.of(conn);

		assert tunnels.containsKey(cfg.getChannel());

		val t = tunnels.get(cfg.getChannel());
		t.closed(cfg);

		log.info("{} disconnected: channel {}, role {}", conn.getRemoteSocketAddress(), cfg.getChannel(),
				cfg.getRole());

		log.debug("Current {} tunnel(s) open", tunnels.size());

	}

	@Override
	public void onMessage(WebSocket conn, String message) {
		val cfg = TunnelConfig.of(conn);

		assert tunnels.containsKey(cfg.getChannel());

		val t = tunnels.get(cfg.getChannel());
		t.relayPacket(cfg, conn, message);
	}

	@Override
	public void onError(WebSocket conn, Exception ex) {
		log.error("Unexpected error - conn {}", conn == null ? "null" : conn.getRemoteSocketAddress(), ex);
		conn.close(CloseFrame.PROTOCOL_ERROR, "Unexpected error");
	}

	@Override
	public void onStart() {
		log.info("Hosting relay server on {}", getAddress());
	}
}
