package me.apemanzilla.ccfuse;

import java.net.URI;
import java.nio.file.Path;
import java.util.UUID;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.handshake.ServerHandshake;

import com.google.gson.*;

import lombok.val;
import lombok.extern.slf4j.Slf4j;
import me.apemanzilla.ccfuse.packets.BasePacket;
import me.apemanzilla.ccfuse.packets.BasePacket.Source;
import me.apemanzilla.ccfuse.packets.relay.RelayConnectionStatusPacket;

/**
 * The "host" - mounts a CC filesystem from a websocket
 * 
 * @author apemanzilla
 *
 */
@Slf4j
public class CCFuseHost extends WebSocketClient {
	private static final Gson gson = new Gson();

	private final Path mountpoint;
	private final int timeout;
	private CCFilesystem mount;

	public CCFuseHost(URI relay, Path mountpoint, int timeout) {
		super(relay);
		this.mountpoint = mountpoint;
		this.timeout = timeout;
	}

	private void mount() {
		assert mount == null;
		assert getConnection() != null;

		mount = new CCFilesystem(getConnection(), timeout);

		mount.mount(mountpoint);
		log.info("Mounted {}", mountpoint);
	}

	private void unmount() {
		if (mount != null) {
			mount.umount();
			mount = null;
			log.info("Unmounted {}", mountpoint);
		}
	}

	@Override
	public void onOpen(ServerHandshake handshakedata) {
		log.info("Connected to relay {}", getConnection().getRemoteSocketAddress());
	}

	@Override
	public void onMessage(String message) {
		try {
			val obj = gson.fromJson(message, JsonObject.class);

			val packet = gson.fromJson(obj, BasePacket.class);

			if (packet.getSource() == Source.SERVER) {
				// bullshit, we're the server
				throw new InvalidMessageException("Invalid data");
			} else if (packet.getSource() == Source.RELAY) {
				val connStatus = gson.fromJson(obj, RelayConnectionStatusPacket.class);

				switch (connStatus.getStatus()) {
				case CONNECTED:
					mount();
					break;
				case DISCONNECTED:
				default:
					unmount();
					break;
				}
			} else {
				if (mount == null) throw new InvalidMessageException("No connection established");

				val id = gson.fromJson(obj.get("id"), UUID.class);
				if (id == null) throw new InvalidMessageException("ID missing");

				// pass message to the mount
				mount.messageReceived(obj, id);
			}
		} catch (JsonSyntaxException | InvalidMessageException e) {
			log.error("Malformed message, terminating connection", e);
			close(CloseFrame.PROTOCOL_ERROR, "Malformed message");
		} catch (Exception e) {
			log.error("Unexpected internal error, terminating connection", e);
			close(CloseFrame.UNEXPECTED_CONDITION, "Internal error");
		}
	}

	@Override
	public void onError(Exception ex) {
		log.error("Unexpected error: ", ex);
		close(CloseFrame.UNEXPECTED_CONDITION, "Internal error");
	}

	@Override
	public void onClose(int code, String reason, boolean remote) {
		unmount();
		log.info("Disconnected from relay {}",
				getConnection() == null ? "null" : getConnection().getRemoteSocketAddress());
	}
}
