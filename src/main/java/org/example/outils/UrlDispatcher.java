package org.example.outils;

import jakarta.servlet.ServletContext;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class UrlDispatcher {

    @SuppressWarnings("unchecked")
    public static String handleRequest(String url, ServletContext ctx) {
        System.out.println("\n🔍 [UrlDispatcher] Recherche correspondance pour URL: '" + url + "'");
        
        if (ctx == null) {
            System.out.println("⚠️ [UrlDispatcher] ServletContext est null!");
            return "Aucune correspondance trouvée pour " + url;
        }

        Object attr = ctx.getAttribute(StartupListener.URL_MAPPINGS_KEY);
        System.out.println("📦 [UrlDispatcher] Mappings dans ServletContext: " + 
            (attr != null ? "présents" : "absents"));
        
        if (attr instanceof Map) {
            Map<String, MethodInfo> map = (Map<String, MethodInfo>) attr;
            System.out.println("   ├─ Nombre de routes: " + map.size());
            System.out.println("   └─ URLs disponibles: " + String.join(", ", map.keySet()));
            return handleRequest(url, map);
        }

        // Si la map n'est pas présente, tenter un scan dynamique
        System.out.println("⚠️ [UrlDispatcher] Pas de mappings - tentative de scan org.example.test");
        try {
            Map<String, MethodInfo> map = ClasspathScanner.scan("org.example.test");
            if (map == null || map.isEmpty()) {
                System.out.println("⚠️ [UrlDispatcher] Scan org.example.test vide - tentative scan org.example");
                map = ClasspathScanner.scan("org.example");
            }
            if (map != null) {
                ctx.setAttribute(StartupListener.URL_MAPPINGS_KEY, map);
                System.out.println("✅ [UrlDispatcher] Scan réussi, routes trouvées: " + map.size());
                return handleRequest(url, map);
            }
        } catch (Throwable t) {
            System.err.println("❌ [UrlDispatcher] Erreur pendant scan: " + t.getMessage());
        }

        return "Aucune correspondance trouvée pour " + url;
    }

    public static String handleRequest(String url, Map<String, MethodInfo> urlMappings) {
        if (urlMappings == null) {
            System.out.println("⚠️ [UrlDispatcher] Map de mappings null!");
            return "Aucune correspondance trouvée pour " + url;
        }
        
        // D'abord, chercher une correspondance exacte
        MethodInfo mi = urlMappings.get(url);
        List<String> paramValues = new ArrayList<>();
        
        // Si pas de correspondance exacte, chercher un pattern dynamique
        if (mi == null) {
            System.out.println("🔎 [UrlDispatcher] Pas de correspondance exacte, recherche de pattern dynamique...");
            for (Map.Entry<String, MethodInfo> entry : urlMappings.entrySet()) {
                MethodInfo methodInfo = entry.getValue();
                if (methodInfo.matches(url)) {
                    mi = methodInfo;
                    paramValues = methodInfo.extractParameters(url);
                    System.out.println("✅ [UrlDispatcher] Pattern trouvé: " + entry.getKey() + 
                        " avec paramètres: " + paramValues);
                    break;
                }
            }
        }
        
        if (mi == null) {
            System.out.println("⚠️ [UrlDispatcher] Aucune correspondance pour '" + url + 
                "' parmi " + urlMappings.size() + " routes");
            return "Aucune correspondance trouvée pour " + url;
        }
        
        String controllerMethodFormat = mi.getControllerClass().getSimpleName() + "#" + mi.getMethod().getName();
        System.out.println("✅ [UrlDispatcher] Trouvé: " + controllerMethodFormat);
        
        // Invocation via reflection
        try {
            Class<?> controllerClass = mi.getControllerClass();
            Method method = mi.getMethod();
            
            // Créer une instance du contrôleur
            Object instance = controllerClass.getDeclaredConstructor().newInstance();
            
            // Invoquer la méthode via reflection avec les paramètres extraits
            Object result;
            if (paramValues.isEmpty()) {
                // Pas de paramètres, invocation simple
                result = method.invoke(instance);
            } else {
                // Convertir les paramètres en tableau d'objets
                Object[] args = paramValues.toArray();
                result = method.invoke(instance, args);
            }
            
            System.out.println("✅ [UrlDispatcher] Résultat de l'invocation: " + result);
            
            return String.valueOf(result);
        } catch (Exception e) {
            System.err.println("❌ [UrlDispatcher] Erreur lors de l'invocation: " + e.getMessage());
            e.printStackTrace();
            return controllerMethodFormat;
        }
    }
}