package io.asterconfig.client.spring;

import org.springframework.core.env.MapPropertySource;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AsterDynamicPropertySource extends MapPropertySource {

    public AsterDynamicPropertySource(String name) {
        super(name, new ConcurrentHashMap<>());
    }

    public synchronized void replaceProperties(Map<String, Object> properties) {
        getSource().clear();
        getSource().putAll(properties);
    }
}
