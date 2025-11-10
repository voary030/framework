package org.example.outils;

import org.example.annotation.Controller;
import org.example.annotation.Url;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ClasspathScanner {

    public static Map<String, MethodInfo> scan(String packageName) throws Exception {
        Map<String, MethodInfo> urlMappings = new HashMap<>();

        List<Class<?>> classes = new ArrayList<>();
        String path = packageName.replace('.', '/');

        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = ClasspathScanner.class.getClassLoader();
        }

        Enumeration<URL> resources = classLoader.getResources(path);
        List<File> dirs = new ArrayList<>();
        List<JarFile> jars = new ArrayList<>();

        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            String protocol = resource.getProtocol();
            if ("file".equals(protocol)) {
                dirs.add(new File(resource.getFile()));
            } else if ("jar".equals(protocol)) {
                String jarPath = resource.getPath();
                if (jarPath.startsWith("file:")) {
                    jarPath = jarPath.substring("file:".length());
                }
                int exclamationIdx = jarPath.indexOf('!');
                if (exclamationIdx != -1) {
                    jarPath = jarPath.substring(0, exclamationIdx);
                }
                jars.add(new JarFile(jarPath));
            }
        }

        // Scan des fichiers
        for (File dir : dirs) {
            findClasses(dir, packageName, classes);
        }

        // Scan des JARs
        for (JarFile jar : jars) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();

                if (name.endsWith(".class")) {
                    String className = name.substring(0, name.length() - 6).replace('/', '.');
                    if (className.startsWith(packageName) || packageName.isEmpty()) {
                        try {
                            Class<?> cls = Class.forName(className);
                            classes.add(cls);
                        } catch (Throwable t) {
                            // Ignorer les erreurs de chargement des classes
                            System.err.println("Erreur au chargement de " + className + ": " + t.getMessage());
                        }
                    }
                }
            }
        }

        // Traiter les classes trouvées
        for (Class<?> cls : classes) {
            if (cls.isAnnotationPresent(Controller.class)) {
                System.out.println("? Controller trouvé : " + cls.getName());
                for (Method method : cls.getDeclaredMethods()) {
                    Url urlAnnotation = method.getAnnotation(Url.class);
                    if (urlAnnotation != null) {
                        String url = urlAnnotation.value();
                        System.out.println("   ? Méthode: " + method.getName() + "  URL: " + url);
                        urlMappings.put(url, new MethodInfo(cls, method));
                    }
                }
                System.out.println("");
            }
        }

        return urlMappings;
    }

    private static void findClasses(File directory, String packageName, List<Class<?>> classes) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    findClasses(file, packageName + "." + file.getName(), classes);
                } else if (file.getName().endsWith(".class")) {
                    String className = packageName + "." + 
                        file.getName().substring(0, file.getName().length() - 6);
                    try {
                        Class<?> cls = Class.forName(className);
                        classes.add(cls);
                    } catch (Throwable t) {
                        // Ignorer les erreurs de chargement
                        System.err.println("Erreur au chargement de " + className + ": " + t.getMessage());
                    }
                }
            }
        }
    }
}