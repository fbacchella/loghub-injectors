package fr.loghub.serializers.msgpack;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestMsgpack {

    @Test
    public void testSerialize() {
        Msgpack mp = new Msgpack();
        Map<String, Object> map = new HashMap<>();
        map.put("int", 42);
        map.put("str", "hello");
        map.put("bool", true);
        map.put("float", 1.5f);
        map.put("double", 3.14);
        map.put("bytes", new byte[]{1, 2, 3});
        map.put("list", List.of(1, 2, 3));
        map.put("map", Map.of("k", "v"));
        map.put("null", null);
        map.put("instant", Instant.now());
        map.put("zoneddatetime", ZonedDateTime.now());
        map.put("date", new Date());
        map.put("int_array", new int[]{1, 2});
        map.put("long_array", new long[]{1L, 2L});
        map.put("double_array", new double[]{1.0, 2.0});
        map.put("float_array", new float[]{1.0f, 2.0f});
        map.put("boolean_array", new boolean[]{true, false});
        map.put("short_array", new short[]{1, 2});
        map.put("char_array", new char[]{'a', 'b'});
        map.put("object_array", new Object[]{"a", 1});
        map.put("byte", (byte) 1);
        map.put("char", 'z');

        Optional<byte[]> result = mp.serialize(map);
        assertTrue(result.isPresent());
        assertTrue(result.get().length > 0);
    }
}
