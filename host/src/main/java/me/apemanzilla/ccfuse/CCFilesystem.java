package me.apemanzilla.ccfuse;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import org.java_websocket.WebSocket;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import jnr.ffi.Pointer;
import lombok.val;
import lombok.extern.slf4j.Slf4j;
import me.apemanzilla.ccfuse.packets.client.*;
import me.apemanzilla.ccfuse.packets.server.*;
import ru.serce.jnrfuse.*;
import ru.serce.jnrfuse.struct.FileStat;
import ru.serce.jnrfuse.struct.FuseFileInfo;

/**
 * Represents a CC filesystem accessible over websocket
 * 
 * @author apemanzilla
 *
 */
@Slf4j
public class CCFilesystem extends FuseStubFS {
	private static final Gson gson = new Gson();

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

	private final int timeoutSeconds;
	private final WebSocket conn;

	private final Map<UUID, ClientOperation<?, ?>> waitingOperations = new ConcurrentHashMap<>();

	public CCFilesystem(WebSocket conn, int timeoutSeconds) {
		this.conn = conn;
		this.timeoutSeconds = timeoutSeconds;
	}

	public CCFilesystem(WebSocket conn) {
		this(conn, Integer.parseInt(System.getProperty("me.apemanzilla.ccfuse.CCFilesystem.timeoutSeconds", "10")));
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
		return new ClientOperation<>(serverPacket, clientPacketType).getReply(timeoutSeconds);
	}

	private int safeinvoke(Callable<Integer> func) {
		if (!conn.isOpen()) return -ErrorCodes.ENOTCONN();

		try {
			return func.call();
		} catch (InterruptedException e) {
			return -ErrorCodes.ETIMEDOUT();
		} catch (Exception e) {
			log.error("Unexpected error", e);
			return -ErrorCodes.EIO();
		}
	}

	@Override
	public int getattr(String path, FileStat stat) {
		return safeinvoke(() -> send(new ServerPathPacket("getattr", path), ClientAttrPacket.class).setMode(stat));
	}

	@Override
	public int readdir(String path, Pointer buf, FuseFillDir fill, long offset, FuseFileInfo fi) {
		return safeinvoke(() -> send(new ServerPathPacket("readdir", path), ClientReaddirPacket.class).fillBuffer(buf,
				fill, offset, fi));
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