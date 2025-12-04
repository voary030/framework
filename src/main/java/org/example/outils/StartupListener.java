package org.example.outils;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import java.util.Map;

@WebListener
public class StartupListener implements ServletContextListener {
    public static final String URL_MAPPINGS_KEY = "urlMappings";
    public static final String METHOD_MAPPINGS_KEY = "methodMappings";  // Sprint 7

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        System.out.println("\n🚀 [StartupListener] Démarrage de l'application...");
        try {
            // Scanner org.example.test pour trouver les contrôleurs
            System.out.println("📥 [StartupListener] Scan du package org.example.test...");
            
            // Sprint 7: Scanner les méthodes HTTP
            Map<String, MethodMapping> methodMappings = ClasspathScanner.scanMethodMappings("org.example.test");
            
            if (methodMappings == null || methodMappings.isEmpty()) {
                System.out.println("⚠️ [StartupListener] Aucune méthode HTTP trouvée dans org.example.test");
                // Essayer un scan complet
                System.out.println("🔍 [StartupListener] Tentative de scan complet...");
                methodMappings = ClasspathScanner.scanMethodMappings("");
            }
            
            // Stocker la map dans le contexte servlet
            sce.getServletContext().setAttribute(METHOD_MAPPINGS_KEY, methodMappings);
            
            // Pour compatibilité rétroactive, garder aussi MethodInfo
            Map<String, MethodInfo> urlMappings = ClasspathScanner.scan("org.example.test");
            if (urlMappings != null) {
                sce.getServletContext().setAttribute(URL_MAPPINGS_KEY, urlMappings);
            }
            
            // Log détaillé des URLs trouvées
            System.out.println("\n📋 [StartupListener] Routes mappées (" + 
                (methodMappings != null ? methodMappings.size() : 0) + " routes):");
            if (methodMappings != null) {
                methodMappings.forEach((key, mapping) -> 
                    System.out.println("   ├─ " + mapping));
            }
            System.out.println("   └─ Fin des routes\n");
                
        } catch (Exception e) {
            System.err.println("❌ [StartupListener] Erreur lors du scan: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        System.out.println("❌ Application arrêtée");
    }
}