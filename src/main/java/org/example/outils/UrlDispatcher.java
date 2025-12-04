package org.example.outils;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import org.example.annotation.RequestParam;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class UrlDispatcher {

    // Entrée principale utilisée par FrontServlet pour Sprint 6 (avec HttpServletRequest)
    @SuppressWarnings("unchecked")
    public static Object handleRequest(String url, ServletContext ctx, HttpServletRequest request) {
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
            return handleRequest(url, map, request);
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
                return handleRequest(url, map, request);
            }
        } catch (Throwable t) {
            System.err.println("❌ [UrlDispatcher] Erreur pendant scan: " + t.getMessage());
        }

        ModelView mv = new ModelView();
        mv.addObject("error", "Aucune correspondance trouvée pour " + url);
        return mv;
    }

    // Ancienne entrée (sans HttpServletRequest) maintenue pour compatibilité interne
    public static Object handleRequest(String url, Map<String, MethodInfo> urlMappings) {
        return handleRequest(url, urlMappings, null);
    }

    // Résolution avec support des arguments depuis HttpServletRequest (Sprint 6)
    public static Object handleRequest(String url, Map<String, MethodInfo> urlMappings, HttpServletRequest request) {
        if (urlMappings == null) {
            System.out.println("⚠️ [UrlDispatcher] Map de mappings null!");
            ModelView mv = new ModelView();
            mv.addObject("error", "Aucune correspondance trouvée pour " + url);
            return mv;
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
            ModelView mv = new ModelView();
            mv.addObject("error", "Aucune correspondance trouvée pour " + url);
            return mv;
        }
        
        String controllerMethodFormat = mi.getControllerClass().getSimpleName() + "#" + mi.getMethod().getName();
        System.out.println("✅ [UrlDispatcher] Trouvé: " + controllerMethodFormat);
        
        // Invocation via reflection
        try {
            Class<?> controllerClass = mi.getControllerClass();
            Method method = mi.getMethod();
            
            // Créer une instance du contrôleur
            Object instance = controllerClass.getDeclaredConstructor().newInstance();
            
            // Préparer les arguments de la méthode
            Object result;
            Object[] args = buildArguments(method, paramValues, request, mi);
            result = args.length == 0 ? method.invoke(instance) : method.invoke(instance, args);
            
            System.out.println("✅ [UrlDispatcher] Résultat de l'invocation: " + result);
            
            // Si le résultat est déjà un ModelView, le retourner directement
            if (result instanceof ModelView) {
                return result;
            }
            
            // Sinon, créer un ModelView avec le résultat
            ModelView mv = new ModelView();
            mv.addObject("result", result);
            return mv;
        } catch (Exception e) {
            System.err.println("❌ [UrlDispatcher] Erreur lors de l'invocation: " + e.getMessage());
            e.printStackTrace();
            ModelView mv = new ModelView();
            mv.addObject("error", "Erreur: " + e.getMessage());
            return mv;
        }
    }

    // Construit les arguments de la méthode avec ordre de priorité Sprint 6-ter:
    // 1) Paramètres d'URL par nom (ex: {id} injected into arg "id")
    // 2) @RequestParam pour cibler un paramètre spécifique (Sprint 6-bis)
    // 3) Paramètres de requête par nom (request.getParameter(name)) pour Sprint 6
    // 4) null (non trouvé)
    private static Object[] buildArguments(Method method, List<String> urlParamValues,
                                           HttpServletRequest request, MethodInfo mi) {
        Class<?>[] paramTypes = method.getParameterTypes();
        Parameter[] params = method.getParameters();

        if ((paramTypes == null || paramTypes.length == 0)) {
            return new Object[0];
        }

        Object[] args = new Object[paramTypes.length];
        
        // Construire une map des paramètres URL par nom (Sprint 6-ter)
        List<String> urlParamNames = mi.getParameterNames();
        java.util.Map<String, String> urlParams = new java.util.HashMap<>();
        if (urlParamNames != null && urlParamValues != null) {
            for (int i = 0; i < urlParamNames.size() && i < urlParamValues.size(); i++) {
                urlParams.put(urlParamNames.get(i), urlParamValues.get(i));
            }
        }

        for (int i = 0; i < paramTypes.length; i++) {
            Class<?> type = paramTypes[i];

            // Injection des objets de requête si demandés
            if (request != null && (type == HttpServletRequest.class)) {
                args[i] = request;
                continue;
            }

            String raw = null;
            Parameter param = params[i];
            String argName = param.getName();

            // Sprint 6-ter: Priorité 1 - Chercher le paramètre dans les params d'URL par nom
            if (urlParams.containsKey(argName)) {
                raw = urlParams.get(argName);
                System.out.println("   └─ URL param {" + argName + "} -> " + raw);
            }
            // Sprint 6-bis: Priorité 2 - Vérifier @RequestParam
            else if (param.getAnnotation(RequestParam.class) != null && request != null) {
                RequestParam requestParamAnnotation = param.getAnnotation(RequestParam.class);
                String paramKey = requestParamAnnotation.value();
                raw = request.getParameter(paramKey);
                System.out.println("   └─ @RequestParam(\"" + paramKey + "\") -> " + raw);
            }
            // Sprint 6: Priorité 3 - Paramètres de requête par nom d'argument
            else if (request != null) {
                raw = request.getParameter(argName);
                if (raw != null) {
                    System.out.println("   └─ Query param '" + argName + "' -> " + raw);
                }
            }

            // Conversion de la chaîne en type attendu
            args[i] = convert(raw, type);
        }

        return args;
    }

    private static Object convert(String value, Class<?> targetType) {
        if (value == null) {
            return null;
        }
        try {
            if (targetType == String.class) return value;
            if (targetType == int.class || targetType == Integer.class) return Integer.parseInt(value);
            if (targetType == long.class || targetType == Long.class) return Long.parseLong(value);
            if (targetType == double.class || targetType == Double.class) return Double.parseDouble(value);
            if (targetType == float.class || targetType == Float.class) return Float.parseFloat(value);
            if (targetType == boolean.class || targetType == Boolean.class) return Boolean.parseBoolean(value);
            if (targetType == short.class || targetType == Short.class) return Short.parseShort(value);
            if (targetType == byte.class || targetType == Byte.class) return Byte.parseByte(value);
        } catch (Exception e) {
            System.err.println("⚠️ [UrlDispatcher] Conversion échouée pour valeur '" + value + "' en " + targetType.getSimpleName());
            return null;
        }
        // Types non gérés: retourner brut
        return value;
    }
}