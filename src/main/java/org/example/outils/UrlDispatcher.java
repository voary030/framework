package org.example.outils;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import org.example.annotation.RequestParam;
import org.example.annotation.JSON;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class UrlDispatcher {

    // Sprint 7: Nouvelle entrée pour supporter les méthodes HTTP
    @SuppressWarnings("unchecked")
    public static Object handleRequestWithMethod(String url, ServletContext ctx, HttpServletRequest request, String httpMethod) {
        System.out.println("\n🔍 [UrlDispatcher] Recherche " + httpMethod + " '" + url + "'");
        
        if (ctx == null) {
            System.out.println("⚠️ [UrlDispatcher] ServletContext est null!");
            return "Aucune correspondance trouvée pour " + httpMethod + " " + url;
        }

        // Sprint 7: Chercher dans les MethodMappings
        Object attr = ctx.getAttribute(StartupListener.METHOD_MAPPINGS_KEY);
        if (attr instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, MethodMapping> map = (Map<String, MethodMapping>) attr;
            System.out.println("📦 [UrlDispatcher] MethodMappings trouvées: " + map.size());
            return handleRequestWithMethodMappings(url, httpMethod, map, request);
        }

        // Fallback sur ancien système si pas de MethodMappings
        System.out.println("⚠️ [UrlDispatcher] Pas de MethodMappings, essai du système antérieur...");
        return handleRequest(url, ctx, request);
    }

    // Résolution avec MethodMapping (Sprint 7)
    private static Object handleRequestWithMethodMappings(String url, String httpMethod, 
                                                           Map<String, MethodMapping> methodMappings, 
                                                           HttpServletRequest request) {
        if (methodMappings == null || methodMappings.isEmpty()) {
            System.out.println("⚠️ [UrlDispatcher] Aucun mapping disponible");
            ModelView mv = new ModelView();
            mv.addObject("error", "Aucune correspondance trouvée pour " + httpMethod + " " + url);
            return mv;
        }

        MethodMapping mapping = null;
        List<String> paramValues = new ArrayList<>();

        // Chercher un mapping correspondant par méthode HTTP et URL
        for (Map.Entry<String, MethodMapping> entry : methodMappings.entrySet()) {
            MethodMapping m = entry.getValue();
            if (m.matches(url, httpMethod)) {
                mapping = m;
                paramValues = m.extractParameters(url);
                System.out.println("✅ [UrlDispatcher] Trouvé: " + httpMethod + " " + m.getUrlPattern());
                break;
            }
        }

        if (mapping == null) {
            System.out.println("⚠️ [UrlDispatcher] Aucun mapping pour " + httpMethod + " " + url);
            ModelView mv = new ModelView();
            mv.addObject("error", "Aucune correspondance trouvée pour " + httpMethod + " " + url);
            return mv;
        }

        // Invoquer la méthode
        try {
            Class<?> controllerClass = mapping.getControllerClass();
            Method method = mapping.getMethod();
            Object instance = controllerClass.getDeclaredConstructor().newInstance();

            Object[] args = buildArguments(method, paramValues, request, mapping);
            Object result = args.length == 0 ? method.invoke(instance) : method.invoke(instance, args);

            System.out.println("✅ [UrlDispatcher] Résultat: " + result);
            // Sprint 9: si annoté @JSON, retourner réponse JSON selon norme
            if (method.isAnnotationPresent(JSON.class)) {
                return buildJsonResponse(result);
            }

            if (result instanceof ModelView) {
                return result;
            }

            ModelView mv = new ModelView();
            mv.addObject("result", result);
            return mv;
        } catch (Exception e) {
            System.err.println("❌ [UrlDispatcher] Erreur lors de l'invocation: " + e.getMessage());
            e.printStackTrace();
            // Sprint 9: en cas d'erreur sur méthode annotée @JSON, retourner erreur JSON
            try {
                if (mapping != null && mapping.getMethod() != null && mapping.getMethod().isAnnotationPresent(JSON.class)) {
                    return JsonResponse.error(e.getMessage());
                }
            } catch (Throwable ignored) {}
            ModelView mv = new ModelView();
            mv.addObject("error", "Erreur: " + e.getMessage());
            return mv;
        }
    }

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
            
            // Sprint 9: si annoté @JSON, retourner réponse JSON selon norme
            if (method.isAnnotationPresent(JSON.class)) {
                return buildJsonResponse(result);
            }

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
            // Sprint 9: si annoté @JSON, retourner erreur JSON
            try {
                if (mi != null && mi.getMethod() != null && mi.getMethod().isAnnotationPresent(JSON.class)) {
                    return JsonResponse.error(e.getMessage());
                }
            } catch (Throwable ignored) {}
            ModelView mv = new ModelView();
            mv.addObject("error", "Erreur: " + e.getMessage());
            return mv;
        }
    }

    // Sprint 9: construire la réponse JSON selon la norme donnée
    private static JsonResponse buildJsonResponse(Object result) {
        // Si le résultat est un ModelView, extraire ses données
        if (result instanceof ModelView) {
            ModelView mv = (ModelView) result;
            Map<String, Object> data = mv.getData();
            if (data == null || data.isEmpty()) {
                return JsonResponse.success(null);
            }
            // Si une seule entrée, retourner directement la valeur
            if (data.size() == 1) {
                Object single = data.values().iterator().next();
                return JsonResponse.success(single);
            }
            // Sinon, retourner l'ensemble de la map
            return JsonResponse.success(data);
        }

        // Si le résultat est une liste, compter et retourner
        if (result instanceof java.util.List) {
            return JsonResponse.success(result);
        }

        // Sinon, retourner l'objet tel quel
        return JsonResponse.success(result);
    }

    // Surcharge pour MethodMapping (Sprint 7)
    private static Object[] buildArguments(Method method, List<String> urlParamValues,
                                           HttpServletRequest request, MethodMapping mapping) {
        return buildArgumentsGeneric(method, urlParamValues, request, mapping != null ? mapping.getParameterNames() : null);
    }

    // Construit les arguments de la méthode avec ordre de priorité Sprint 6-ter:
    // 1) Paramètres d'URL par nom (ex: {id} injected into arg "id")
    // 2) @RequestParam pour cibler un paramètre spécifique (Sprint 6-bis)
    // 3) Paramètres de requête par nom (request.getParameter(name)) pour Sprint 6
    // 4) null (non trouvé)
    private static Object[] buildArguments(Method method, List<String> urlParamValues,
                                           HttpServletRequest request, MethodInfo mi) {
        return buildArgumentsGeneric(method, urlParamValues, request, mi != null ? mi.getParameterNames() : null);
    }

    private static Object[] buildArgumentsGeneric(Method method, List<String> urlParamValues,
                                                   HttpServletRequest request, List<String> urlParamNames) {
        Class<?>[] paramTypes = method.getParameterTypes();
        Parameter[] params = method.getParameters();

        if ((paramTypes == null || paramTypes.length == 0)) {
            return new Object[0];
        }

        Object[] args = new Object[paramTypes.length];
        
        // Construire une map des paramètres URL par nom (Sprint 6-ter)
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

            // SPRINT 8-BIS: Injection de Map<String, Object> depuis request.getParameterMap()
            // Amélioration: garder String[] pour champs multiples, String pour champs simples
            if (request != null && type == Map.class) {
                System.out.println("🗺️ [UrlDispatcher] Sprint 8-BIS: Transformation des paramètres en Map<String, Object>");
                java.util.Map<String, Object> paramMap = new java.util.HashMap<>();
                
                // Récupérer request.getParameterMap() qui retourne Map<String, String[]>
                java.util.Map<String, String[]> rawParams = request.getParameterMap();
                
                // Parcourir dynamiquement et transformer String[] en Object
                // OPTION 1 (Sprint 8-BIS): Si 1 seule valeur → String, sinon → String[]
                for (java.util.Map.Entry<String, String[]> entry : rawParams.entrySet()) {
                    String key = entry.getKey();
                    String[] values = entry.getValue();
                    
                    // SPRINT 8-BIS: Préserver les données multiples (checkboxes, select multiple, etc.)
                    Object value = (values != null && values.length == 1) ? values[0] : values;
                    paramMap.put(key, value);
                    
                    if (values != null && values.length > 1) {
                        System.out.println("   └─ " + key + " (multi) = " + java.util.Arrays.toString(values));
                    } else {
                        System.out.println("   └─ " + key + " = " + value);
                    }
                }
                
                args[i] = paramMap;
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