package me.apemanzilla.ccfuse.packets.client;

import ru.serce.jnrfuse.struct.FileStat;

public interface FileAttributes {
	int setMode(FileStat stat);
}
