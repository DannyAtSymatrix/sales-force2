package utils.context;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ScenarioContext {
    private Map<String, Object> data;

    public ScenarioContext() {
        data = new HashMap<>();
    }

    public void set(String key, Object value) {
        data.put(key, value);
    }

    public Object get(String key) {
        return data.get(key);
    }

    public boolean contains(String key) {
        return data.containsKey(key);
    }
    
    public Set<String> getAllKeys(){
    	return data.keySet();
    }
}
