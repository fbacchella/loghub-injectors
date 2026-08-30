package fr.loghub.core.serializers;

import java.util.Map;
import java.util.Optional;

public interface Serializer {
    Optional<byte[]> serialize(Map<String, Object> map);
}
