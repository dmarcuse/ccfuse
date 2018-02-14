package me.apemanzilla.ccfuse.packets;

import java.io.IOException;
import java.util.Base64;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

/**
 * A {@link TypeAdapter} for de/serializing byte arrays as base64
 * 
 * @author apemanzilla
 *
 */
public class Base64Adapter extends TypeAdapter<byte[]> {
	@Override
	public void write(JsonWriter out, byte[] value) throws IOException {
		out.value(Base64.getEncoder().encodeToString(value));
	}

	@Override
	public byte[] read(JsonReader in) throws IOException {
		return Base64.getDecoder().decode(in.nextString());
	}
}
