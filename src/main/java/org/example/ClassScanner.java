package org.example;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class ClassScanner {

    public static List<Class<?>> getClasses(String packageName) throws Exception {
        List<Class<?>> classes = new ArrayList<>();
        String path = packageName.replace('.', '/');
        URL url = Thread.currentThread().getContextClassLoader().getResource(path);
        if (url == null) {
            throw new RuntimeException("Package introuvable : " + packageName);
        }

        File dir = new File(url.toURI());
        for (File file : dir.listFiles()) {
            if (file.getName().endsWith(".class")) {
                String className = packageName + "." + file.getName().replace(".class", "");
                Class<?> clazz = Class.forName(className);
                classes.add(clazz);
            }
        }
        return classes;
    }

    public static void scan(String packageName) throws Exception {
        // Obtenez les types d'annotations avec la sécurité de typage
        @SuppressWarnings("unchecked")
        Class<? extends Annotation> controllerAnnotation =
                (Class<? extends Annotation>) Class.forName("org.example.annotation.Controller");
        @SuppressWarnings("unchecked")
        Class<? extends Annotation> urlAnnotation =
                (Class<? extends Annotation>) Class.forName("org.example.annotation.Url");

        List<Class<?>> classes = getClasses(packageName);
        for (Class<?> c : classes) {
            if (c.isAnnotationPresent(controllerAnnotation)) {
                System.out.println("🔹 Controller trouvé : " + c.getName());
                for (Method m : c.getDeclaredMethods()) {
                    if (m.isAnnotationPresent(urlAnnotation)) {
                        Annotation annotation = m.getAnnotation(urlAnnotation);
                        String urlValue = (String) annotation.getClass().getMethod("value").invoke(annotation);
                        System.out.println("   ↳ Méthode: " + m.getName() + "  URL: " + urlValue);
                    }
                }
            }
        }
    }
}
