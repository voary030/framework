package org.example;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ClassScanner {

    public static List<Class<?>> getAllClasses() throws Exception {
        List<Class<?>> classes = new ArrayList<>();
        String[] packageNames = getPackageNames();
        
        for (String packageName : packageNames) {
            try {
                classes.addAll(getClasses(packageName));
            } catch (Exception e) {
                // Ignorer les packages qui ne peuvent pas être chargés
                System.err.println("Impossible de scanner le package: " + packageName + " - " + e.getMessage());
            }
        }
        return classes;
    }

    public static String[] getPackageNames() throws Exception {
        List<String> packageNames = new ArrayList<>();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Enumeration<URL> resources = classLoader.getResources("");
        
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            File file = new File(resource.toURI());
            if (file.isDirectory()) {
                findPackages(file, "", packageNames);
            }
        }
        
        // Ajouter les packages des JARs du classpath
        String classpath = System.getProperty("java.class.path");
        String[] paths = classpath.split(System.getProperty("path.separator"));
        
        for (String path : paths) {
            if (path.endsWith(".jar")) {
                findPackagesInJar(new File(path), packageNames);
            }
        }
        
        return packageNames.toArray(new String[0]);
    }

    private static void findPackages(File directory, String currentPackage, List<String> packageNames) {
        File[] files = directory.listFiles();
        if (files == null) return;
        
        boolean hasClassFile = false;
        for (File file : files) {
            if (file.isDirectory()) {
                String newPackage = currentPackage.isEmpty() ? file.getName() : currentPackage + "." + file.getName();
                findPackages(file, newPackage, packageNames);
            } else if (file.getName().endsWith(".class")) {
                hasClassFile = true;
            }
        }
        
        if (hasClassFile && !currentPackage.isEmpty()) {
            packageNames.add(currentPackage);
        }
    }

    private static void findPackagesInJar(File jarFile, List<String> packageNames) {
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".class")) {
                    String className = entry.getName().replace("/", ".").replace(".class", "");
                    int lastDot = className.lastIndexOf('.');
                    if (lastDot > 0) {
                        String packageName = className.substring(0, lastDot);
                        if (!packageNames.contains(packageName)) {
                            packageNames.add(packageName);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Erreur lors de la lecture du JAR: " + jarFile.getName());
        }
    }

    public static List<Class<?>> getClasses(String packageName) throws Exception {
        List<Class<?>> classes = new ArrayList<>();
        String path = packageName.replace('.', '/');
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Enumeration<URL> resources = classLoader.getResources(path);
        
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            if (resource.getProtocol().equals("file")) {
                File dir = new File(resource.toURI());
                if (dir.exists() && dir.isDirectory()) {
                    findClassesInDirectory(dir, packageName, classes);
                }
            } else if (resource.getProtocol().equals("jar")) {
                findClassesInJar(resource, packageName, classes);
            }
        }
        return classes;
    }

    private static void findClassesInDirectory(File directory, String packageName, List<Class<?>> classes) {
        File[] files = directory.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.isDirectory()) {
                findClassesInDirectory(file, packageName + "." + file.getName(), classes);
            } else if (file.getName().endsWith(".class")) {
                String className = packageName + "." + file.getName().replace(".class", "");
                try {
                    Class<?> clazz = Class.forName(className);
                    classes.add(clazz);
                } catch (ClassNotFoundException | NoClassDefFoundError e) {
                    System.err.println("Impossible de charger la classe: " + className);
                }
            }
        }
    }

    private static void findClassesInJar(URL jarUrl, String packageName, List<Class<?>> classes) {
        try {
            String jarPath = jarUrl.getPath().substring(5, jarUrl.getPath().indexOf("!"));
            JarFile jar = new JarFile(jarPath);
            Enumeration<JarEntry> entries = jar.entries();
            String packagePath = packageName.replace('.', '/');
            
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();
                if (entryName.startsWith(packagePath) && entryName.endsWith(".class")) {
                    String className = entryName.replace("/", ".").replace(".class", "");
                    try {
                        Class<?> clazz = Class.forName(className);
                        classes.add(clazz);
                    } catch (ClassNotFoundException | NoClassDefFoundError e) {
                        System.err.println("Impossible de charger la classe: " + className);
                    }
                }
            }
            jar.close();
        } catch (Exception e) {
            System.err.println("Erreur lors de la lecture du JAR: " + jarUrl);
        }
    }

    public static void scanAll() throws Exception {
        @SuppressWarnings("unchecked")
        Class<? extends Annotation> controllerAnnotation =
                (Class<? extends Annotation>) Class.forName("org.example.annotation.Controller");
        @SuppressWarnings("unchecked")
        Class<? extends Annotation> urlAnnotation =
                (Class<? extends Annotation>) Class.forName("org.example.annotation.Url");

        List<Class<?>> allClasses = getAllClasses();
        System.out.println("🔍 Scan de " + allClasses.size() + " classes trouvées...");
        
        for (Class<?> c : allClasses) {
            try {
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
            } catch (Exception e) {
                System.err.println("Erreur lors de l'analyse de la classe: " + c.getName());
            }
        }
    }

    public static void scan(String packageName) throws Exception {
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