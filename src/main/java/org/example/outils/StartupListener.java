package org.example.outils;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import java.util.Map;

@WebListener
public class StartupListener implements ServletContextListener {
    public static final String URL_MAPPINGS_KEY = "urlMappings";

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        System.out.println("\n🚀 [StartupListener] Démarrage de l'application...");
        try {
            // Scanner org.example.test pour trouver les contrôleurs
            System.out.println("📥 [StartupListener] Scan du package org.example.test...");
            Map<String, MethodInfo> urlMappings = ClasspathScanner.scan("org.example.test");
            
            if (urlMappings == null || urlMappings.isEmpty()) {
                System.out.println("⚠️ [StartupListener] Aucune URL trouvée dans org.example.test");
                // Essayer un scan complet
                System.out.println("🔍 [StartupListener] Tentative de scan complet...");
                urlMappings = ClasspathScanner.scan("");
            }
            
            // Stocker la map dans le contexte servlet
            sce.getServletContext().setAttribute(URL_MAPPINGS_KEY, urlMappings);
            
            // Log détaillé des URLs trouvées
            System.out.println("\n📋 [StartupListener] URLs mappées (" + 
                (urlMappings != null ? urlMappings.size() : 0) + " routes):");
            if (urlMappings != null) {
                urlMappings.forEach((url, methodInfo) -> 
                    System.out.println("   ├─ " + url + " ➜ " + methodInfo));
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