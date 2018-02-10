package me.apemanzilla.ccfuse;

import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft;
import org.java_websocket.exceptions.InvalidDataException;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ServerHandshakeBuilder;
import org.java_websocket.server.WebSocketServer;

import com.google.gson.*;

import jnr.ffi.Pointer;
import lombok.val;
import lombok.extern.slf4j.Slf4j;
import me.apemanzilla.ccfuse.packets.client.*;
import me.apemanzilla.ccfuse.packets.server.*;
import ru.serce.jnrfuse.*;
import ru.serce.jnrfuse.struct.FileStat;
import ru.serce.jnrfuse.struct.FuseFileInfo;

@Slf4j
public class CCFuseHost extends WebSocketServer {
	private static final Gson gson = new Gson();

	private final Path mountpoint;
	private final int timeout;
	private CCFilesystem mount;

	public CCFuseHost(int port, Path mountpoint, int timeout) {
		super(new InetSocketAddress(port));
		this.mountpoint = mountpoint;
		this.timeout = timeout;
	}

	public CCFuseHost(int port, Path mountpoint) {
		this(port, mountpoint, 10);
	}

	@Override
	public void onStart() {
		log.info("Started CCFuse host on {}", getAddress());
	}

	@Override
	public synchronized ServerHandshakeBuilder onWebsocketHandshakeReceivedAsServer(WebSocket conn, Draft draft,
			ClientHandshake req) throws InvalidDataException {
		if (mount != null) {
			log.warn("Rejecting client {} - another client is already connected", conn.getRemoteSocketAddress());
			throw new InvalidDataException(CloseFrame.POLICY_VALIDATION, "Client already connected");
		}

		mount = new CCFilesystem(conn);

		return super.onWebsocketHandshakeReceivedAsServer(conn, draft, req);
	}

	@Override
	public synchronized void onOpen(WebSocket conn, ClientHandshake handshake) {
		assert mount != null;

		mount.mount(mountpoint);

		log.info("Client {} connected, mounted on {}", conn.getRemoteSocketAddress(), mountpoint);
	}

	@Override
	public synchronized void onClose(WebSocket conn, int code, String reason, boolean remote) {
		assert mount != null;

		mount.umount();
		mount = null;

		log.info("Client {} disconnected (Code {}: {})", conn.getRemoteSocketAddress(), code, reason);
	}

	@Override
	public void onMessage(WebSocket conn, String message) {
		assert mount != null;

		try {
			val obj = gson.fromJson(message, JsonObject.class);
			val id = gson.fromJson(obj.get("id"), UUID.class);

			if (id == null) throw new InvalidMessageException("ID missing");

			// pass message to the mount
			mount.messageReceived(obj, id);
		} catch (JsonSyntaxException | InvalidMessageException e) {
			log.error("Malformed message from client {}, terminating connection: {}", conn.getRemoteSocketAddress(), e);
			conn.close(CloseFrame.PROTOCOL_ERROR, "Malformed message");
		} catch (Exception e) {
			log.error("Unexpected internal error, terminating connection: {}", e);
			conn.close(CloseFrame.UNEXPECTED_CONDITION, "Internal error");
		}
	}

	@Override
	public void onError(WebSocket conn, Exception ex) {
		log.error("Unexpected error (client {}): {}", conn == null ? "null" : conn.getRemoteSocketAddress(), ex);
		if (conn != null) conn.close(CloseFrame.UNEXPECTED_CONDITION, "Internal error");
	}

	private class CCFilesystem extends FuseStubFS {
		private class ClientOperation<S extends ServerPacket, C extends ClientPacket> {
			private boolean sentServerPacket;
			private final S serverPacket;

			private final CountDownLatch replyLatch = new CountDownLatch(1);
			private C clientPacket;
			private final Type clientPacketType;

			public ClientOperation(S serverPacket, Class<C> clientPacketType) {
				this.serverPacket = serverPacket;
				this.clientPacketType = clientPacketType;
			}

			public synchronized void sendServerPacket() {
				if (!sentServerPacket) {
					waitingOperations.put(serverPacket.getId(), this);
					sentServerPacket = true;
					conn.send(gson.toJson(serverPacket));
				}
			}

			public synchronized void setReply(JsonObject reply) {
				if (replyLatch.getCount() == 0) throw new IllegalStateException("Reply has already been set");

				clientPacket = gson.fromJson(reply, clientPacketType);
				replyLatch.countDown();
			}

			public C getReply(int timeout) throws InterruptedException {
				sendServerPacket();
				replyLatch.await(timeout, TimeUnit.SECONDS);
				return clientPacket;
			}
		}

		private final WebSocket conn;

		private final Map<UUID, ClientOperation<?, ?>> waitingOperations = new ConcurrentHashMap<>();

		public CCFilesystem(WebSocket conn) {
			this.conn = conn;
		}

		public void messageReceived(JsonObject message, UUID id) {
			if (waitingOperations.containsKey(id)) {
				waitingOperations.get(id).setReply(message);
			} else {
				throw new IllegalArgumentException("No operation with given ID");
			}
		}

		private <S extends ServerPacket, C extends ClientPacket> C send(S serverPacket, Class<C> clientPacketType)
				throws InterruptedException {
			return new ClientOperation<>(serverPacket, clientPacketType).getReply(timeout);
		}

		private int safeinvoke(Callable<Integer> func) {
			try {
				return func.call();
			} catch (InterruptedException e) {
				return -ErrorCodes.ETIMEDOUT();
			} catch (Exception e) {
				log.error("Unexpected error: {}", e);
				return -ErrorCodes.EIO();
			}
		}

		@Override
		public int getattr(String path, FileStat stat) {
			return safeinvoke(() -> send(new ServerPathPacket("getattr", path), ClientAttrPacket.class).setMode(stat));
		}

		@Override
		public int readdir(String path, Pointer buf, FuseFillDir fill, long offset, FuseFileInfo fi) {
			return safeinvoke(() -> send(new ServerPathPacket("readdir", path), ClientReaddirPacket.class)
					.fillBuffer(buf, fill, offset, fi));
		}

		@Override
		public int unlink(String path) {
			return safeinvoke(() -> send(new ServerPathPacket("unlink", path), ClientPacket.class).checkError());
		}

		@Override
		public int rmdir(String path) {
			return safeinvoke(() -> send(new ServerPathPacket("rmdir", path), ClientPacket.class).checkError());
		}

		@Override
		public int rename(String oldpath, String newpath) {
			return safeinvoke(() -> send(new ServerRenamePacket(oldpath, newpath), ClientPacket.class).checkError());
		}

		@Override
		public int mkdir(String path, long mode) {
			return safeinvoke(() -> send(new ServerPathPacket("mkdir", path), ClientPacket.class).checkError());
		}

		@Override
		public int access(String path, int mask) {
			return safeinvoke(() -> send(new ServerPathPacket("access", path), ClientPacket.class).checkError());
		}

		@Override
		public int open(String path, FuseFileInfo fi) {
			return safeinvoke(() -> send(new ServerPathPacket("open", path), ClientPacket.class).checkError());
		}

		@Override
		public int truncate(String path, long size) {
			return safeinvoke(() -> send(new ServerTruncatePacket(path, size), ClientPacket.class).checkError());
		}

		@Override
		public int read(String path, Pointer buf, long size, long offset, FuseFileInfo fi) {
			return safeinvoke(() -> send(new ServerReadPacket(path, size, offset), ClientReadPacket.class).copy(buf));
		}
		
		@Override
		public int create(String path, long mode, FuseFileInfo fi) {
			return safeinvoke(() -> send(new ServerPathPacket("create", path), ClientPacket.class).checkError());
		}

		@Override
		public int write(String path, Pointer buf, long size, long offset, FuseFileInfo fi) {
			return safeinvoke(() -> {
				val code = send(new ServerWritePacket(path, buf, size, offset), ClientReadPacket.class).checkError();
				return (int) (code == 0 ? size : code);
			});
		}
	}
}
