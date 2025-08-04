package utils.context;

import java.util.Set;

public class ContextManager {

    private static ScenarioContext scenarioContext;

    public static void setScenarioContext(ScenarioContext context) {
        scenarioContext = context;
    }

    public static void put(String key, Object value) {
        if (StoryContext.isStoryMode()) {
            StoryContext.put(key, value);
        } else if (scenarioContext != null) {
            scenarioContext.set(key, value);
        } else {
            throw new IllegalStateException("No ScenarioContext set for non-story mode");
        }
    }

    public static Object get(String key) {
        if (StoryContext.isStoryMode()) {
            return StoryContext.get(key);
        } else if (scenarioContext != null) {
            return scenarioContext.get(key);
        } else {
            throw new IllegalStateException("No ScenarioContext set for non-story mode");
        }
    }

    private static boolean contains(String key) {
        if (StoryContext.isStoryMode()) {
            return StoryContext.get(key) != null;
        } else if (scenarioContext != null) {
            return scenarioContext.contains(key);
        } else {
            return false;
        }
    }
    
    public static Object getOrDefault(String key, Object defaultValue) {
        return contains(key) ? get(key) : defaultValue;
    }
    
    public static boolean containsKey(String key) {
		return scenarioContext.contains(key);
    }
    
    public static Set<String> getAllKeys(){
    	if (StoryContext.isStoryMode()) {
    		return StoryContext.getAllKeys();
    	} else {
    		return scenarioContext.getAllKeys();
    	}
    }
}