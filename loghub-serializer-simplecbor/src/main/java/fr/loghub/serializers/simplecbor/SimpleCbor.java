package fr.loghub.serializers.simplecbor;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.DoublePrecisionFloat;
import co.nstant.in.cbor.model.SimpleValue;
import co.nstant.in.cbor.model.SinglePrecisionFloat;
import co.nstant.in.cbor.model.UnicodeString;
import co.nstant.in.cbor.model.UnsignedInteger;

public class SimpleCbor {

    private static final Logger logger = LogManager.getLogger();

    public Optional<byte[]> serialize(Map<String, Object> map) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            CborBuilder builder = new CborBuilder();
            builder.add(convert(map));
            new CborEncoder(baos).encode(builder.build());
            byte[] result = baos.toByteArray();
            return Optional.of(result);
        } catch (CborException  | RuntimeException e) {
            logger.atWarn()
                  .withThrowable(logger.isDebugEnabled() ? e : null)
                  .log("Failed processing message {}: {}", map, e.getMessage());
            return Optional.empty();
        }
    }

    private DataItem convert(Object o) {
        return switch (o) {
            case null ->
                SimpleValue.NULL;
            case String s ->
                new UnicodeString(s);
            case Integer i ->
                new UnsignedInteger(i);
            case Long l ->
                new UnsignedInteger(l);
            case Byte b ->
                new UnsignedInteger(b.intValue());
            case Character c ->
                new UnsignedInteger((int) c);
            case Boolean b ->
                b ? SimpleValue.TRUE : SimpleValue.FALSE;
            case Float f ->
                new SinglePrecisionFloat(f);
            case Double d ->
                new DoublePrecisionFloat(d);
            case byte[] bytes ->
                new ByteString(bytes);
            case Map<?, ?> m -> {
                co.nstant.in.cbor.model.Map map = new co.nstant.in.cbor.model.Map();
                m.forEach((k, v) -> map.put(convert(k), convert(v)));
                yield map;
            }
            case List<?> l -> {
                Array array = new Array();
                l.forEach(v -> array.add(convert(v)));
                yield array;
            }
            case Instant t -> {
                double seconds = t.getEpochSecond() + t.getNano() / 1_000_000_000.0;
                DataItem item = new DoublePrecisionFloat(seconds);
                item.setTag(1);
                yield item;
            }
            case Date d -> {
                Instant t = d.toInstant();
                double seconds = t.getEpochSecond() + t.getNano() / 1_000_000_000.0;
                DataItem item = new DoublePrecisionFloat(seconds);
                item.setTag(1);
                yield item;
            }
            case int[] a -> {
                Array array = new Array();
                for (int v : a) {
                    array.add(new UnsignedInteger(v));
                }
                yield array;
            }
            case long[] a -> {
                Array array = new Array();
                for (long v : a) {
                    array.add(new UnsignedInteger(v));
                }
                yield array;
            }
            case double[] a -> {
                Array array = new Array();
                for (double v : a) {
                    array.add(new DoublePrecisionFloat(v));
                }
                yield array;
            }
            case float[] a -> {
                Array array = new Array();
                for (float v : a) {
                    array.add(new SinglePrecisionFloat(v));
                }
                yield array;
            }
            case boolean[] a -> {
                Array array = new Array();
                for (boolean v : a) {
                    array.add(v ? SimpleValue.TRUE : SimpleValue.FALSE);
                }
                yield array;
            }
            case short[] a -> {
                Array array = new Array();
                for (short v : a) {
                    array.add(new UnsignedInteger(v));
                }
                yield array;
            }
            case char[] a -> {
                Array array = new Array();
                for (char v : a) {
                    array.add(new UnsignedInteger(v));
                }
                yield array;
            }
            case Object[] a -> {
                Array array = new Array();
                for (Object v : a) {
                    array.add(convert(v));
                }
                yield array;
            }
            default ->
                throw new IllegalArgumentException("Unhandled type " + o.getClass());
        };
    }
}
