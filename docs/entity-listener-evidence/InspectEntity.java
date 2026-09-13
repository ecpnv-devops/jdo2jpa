import java.lang.annotation.Annotation;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

// Read compiled annotation metadata without initializing application classes.
// XML/default listeners are deliberately outside this annotation-only inventory.
public class InspectEntity {
    static String json(Object value) {
        if (value == null) return "null";
        if (value instanceof Boolean) return value.toString();
        if (value instanceof Collection<?> xs) return "[" + String.join(",", xs.stream().map(InspectEntity::json).toList()) + "]";
        if (value instanceof Map<?, ?> map) {
            List<String> parts = new ArrayList<>();
            map.forEach((k, v) -> parts.add(json(k.toString()) + ":" + json(v)));
            return "{" + String.join(",", parts) + "}";
        }
        return "\"" + value.toString().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }
    static Annotation annotation(Class<?> type, String name) {
        for (Annotation a : type.getDeclaredAnnotations()) if (a.annotationType().getName().equals(name)) return a;
        return null;
    }
    static List<String> listeners(Class<?> type) throws Exception {
        Annotation a = annotation(type, "javax.persistence.EntityListeners");
        if (a == null) return List.of();
        Class<?>[] values = (Class<?>[]) a.annotationType().getMethod("value").invoke(a);
        return Arrays.stream(values).map(Class::getName).toList();
    }
    static void inspect(Class<?> type) throws Exception {
        List<Class<?>> chain = new ArrayList<>();
        for (Class<?> c = type; c != null; c = c.getSuperclass()) chain.add(c);
        Collections.reverse(chain);
        List<String> effective = new ArrayList<>();
        for (Class<?> c : chain) {
            if (annotation(c, "javax.persistence.Entity") == null && annotation(c, "javax.persistence.MappedSuperclass") == null) continue;
            if (annotation(c, "javax.persistence.ExcludeSuperclassListeners") != null) effective.clear();
            effective.addAll(listeners(c));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("class", type.getName());
        data.put("superclass", type.getSuperclass() == null ? null : type.getSuperclass().getName());
        data.put("entity", annotation(type, "javax.persistence.Entity") != null);
        data.put("mappedSuperclass", annotation(type, "javax.persistence.MappedSuperclass") != null);
        data.put("declaredListeners", listeners(type));
        data.put("annotationDefinedEffectiveListeners", effective);
        data.put("declaresEntityScan", annotation(type, "org.springframework.boot.autoconfigure.domain.EntityScan") != null);
        data.put("declaresComponentScan", annotation(type, "org.springframework.context.annotation.ComponentScan") != null);
        System.out.println(json(data));
        for (Class<?> nested : type.getDeclaredClasses()) inspect(nested);
    }
    public static void main(String[] args) throws Exception {
        String[] paths = Files.readString(Path.of(args[0])).trim().split(java.io.File.pathSeparator);
        URL[] urls = new URL[paths.length];
        for (int i = 0; i < paths.length; i++) urls[i] = Path.of(paths[i]).toUri().toURL();
        try (URLClassLoader loader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader())) {
            for (int i = 1; i < args.length; i++) {
                try { inspect(Class.forName(args[i], false, loader)); }
                catch (Throwable ex) { System.out.println(json(Map.of("class", args[i], "error", ex.toString()))); }
            }
        }
    }
}
