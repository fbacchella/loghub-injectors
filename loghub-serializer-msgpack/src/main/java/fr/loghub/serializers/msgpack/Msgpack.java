package fr.loghub.serializers.msgpack;

import java.io.IOException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessagePacker;

public class Msgpack {

    private static final Logger logger = LogManager.getLogger();

    public Optional<byte[]> serialize(Map<String, Object> map) {
        try (MessageBufferPacker packer = MessagePack.newDefaultBufferPacker()) {
            pack(packer, map);
            return Optional.of(packer.toByteArray());
        } catch (IOException | RuntimeException e) {
            logger.atWarn()
                  .withThrowable(logger.isDebugEnabled() ? e : null)
                  .log("Failed processing message {}: {}", map, e.getMessage());
            return Optional.empty();
        }
    }

    private void pack(MessagePacker packer, Object o) throws IOException {
        switch (o) {
            case null ->
                packer.packNil();
            case String s ->
                packer.packString(s);
            case Integer i ->
                packer.packInt(i);
            case Long l ->
                packer.packLong(l);
            case Byte b ->
                packer.packByte(b);
            case Short s ->
                packer.packShort(s);
            case Character c ->
                packer.packInt(c);
            case Boolean b ->
                packer.packBoolean(b);
            case Float f ->
                packer.packFloat(f);
            case Double d ->
                packer.packDouble(d);
            case byte[] bytes -> {
                packer.packBinaryHeader(bytes.length);
                packer.writePayload(bytes);
            }
            case Map<?, ?> m -> {
                packer.packMapHeader(m.size());
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    pack(packer, e.getKey());
                    pack(packer, e.getValue());
                }
            }
            case Collection<?> l -> {
                packer.packArrayHeader(l.size());
                for (Object v : l) {
                    pack(packer, v);
                }
            }
            case Instant t ->
                packer.packTimestamp(t);
            case ZonedDateTime t ->
                packer.packTimestamp(t.toInstant());
            case Date d ->
                packer.packTimestamp(d.toInstant());
            case int[] a -> {
                packer.packArrayHeader(a.length);
                for (int v : a) {
                    packer.packInt(v);
                }
            }
            case long[] a -> {
                packer.packArrayHeader(a.length);
                for (long v : a) {
                    packer.packLong(v);
                }
            }
            case double[] a -> {
                packer.packArrayHeader(a.length);
                for (double v : a) {
                    packer.packDouble(v);
                }
            }
            case float[] a -> {
                packer.packArrayHeader(a.length);
                for (float v : a) {
                    packer.packFloat(v);
                }
            }
            case boolean[] a -> {
                packer.packArrayHeader(a.length);
                for (boolean v : a) {
                    packer.packBoolean(v);
                }
            }
            case short[] a -> {
                packer.packArrayHeader(a.length);
                for (short v : a) {
                    packer.packShort(v);
                }
            }
            case char[] a -> {
                packer.packArrayHeader(a.length);
                for (char v : a) {
                    packer.packInt(v);
                }
            }
            case Object[] a -> {
                packer.packArrayHeader(a.length);
                for (Object v : a) {
                    pack(packer, v);
                }
            }
            default ->
                throw new IllegalArgumentException("Unhandled type " + o.getClass());
        }
    }
}
