package org.example.outils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sprint 9: Classe pour formater les réponses JSON selon la norme
 * 
 * Format attendu:
 * {
 *   "status": "success" | "error",
 *   "code": 200 | 404 | 500 | ...,
 *   "data": {...} ou [...],
 *   "count": 10  (si c'est une liste)
 * }
 */
public class JsonResponse {
    private String status;
    private int code;
    private Object data;
    private Integer count;

    public JsonResponse() {
        this.status = "success";
        this.code = 200;
    }

    public JsonResponse(String status, int code) {
        this.status = status;
        this.code = code;
    }

    public static JsonResponse success(Object data) {
        JsonResponse response = new JsonResponse("success", 200);
        response.setData(data);
        return response;
    }

    public static JsonResponse error(String message, int code) {
        JsonResponse response = new JsonResponse("error", code);
        Map<String, String> errorData = new HashMap<>();
        errorData.put("message", message);
        response.setData(errorData);
        return response;
    }

    public static JsonResponse error(String message) {
        return error(message, 500);
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
        
        // Si c'est une liste, mettre à jour le count automatiquement
        if (data instanceof List) {
            this.count = ((List<?>) data).size();
        }
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    /**
     * Convertit la réponse en JSON manuellement (sans dépendance externe)
     */
    public String toJson() {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"status\":\"").append(escapeJson(status)).append("\",");
        json.append("\"code\":").append(code);
        
        if (count != null) {
            json.append(",\"count\":").append(count);
        }
        
        json.append(",\"data\":");
        json.append(convertToJson(data));
        
        json.append("}");
        return json.toString();
    }

    /**
     * Convertit un objet en JSON (simple, sans dépendance)
     */
    private String convertToJson(Object obj) {
        if (obj == null) {
            return "null";
        }
        
        if (obj instanceof String) {
            return "\"" + escapeJson((String) obj) + "\"";
        }
        
        if (obj instanceof Number || obj instanceof Boolean) {
            return obj.toString();
        }
        
        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(convertToJson(list.get(i)));
            }
            sb.append("]");
            return sb.toString();
        }
        
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) continue;  // Ignorer les clés null
                if (!first) sb.append(",");
                first = false;
                String key = entry.getKey().toString();
                if (key.isEmpty()) continue;  // Ignorer les clés vides
                sb.append("\"").append(escapeJson(key)).append("\":");
                sb.append(convertToJson(entry.getValue()));
            }
            sb.append("}");
            return sb.toString();
        }
        
        // Pour les objets custom, utiliser réflexion simple
        return convertObjectToJson(obj);
    }

    /**
     * Convertit un objet custom en JSON via réflexion
     */
    private String convertObjectToJson(Object obj) {
        if (obj == null) return "null";
        
        try {
            StringBuilder sb = new StringBuilder("{");
            java.lang.reflect.Field[] fields = obj.getClass().getDeclaredFields();
            boolean first = true;
            
            for (java.lang.reflect.Field field : fields) {
                // Ignorer les champs statiques
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                
                field.setAccessible(true);
                Object value = field.get(obj);
                
                if (!first) sb.append(",");
                first = false;
                
                sb.append("\"").append(field.getName()).append("\":");
                sb.append(convertToJson(value));
            }
            
            sb.append("}");
            return sb.toString();
        } catch (Exception e) {
            // En cas d'erreur, retourner toString() entre guillemets
            return "\"" + escapeJson(obj.toString()) + "\"";
        }
    }

    /**
     * Échappe les caractères spéciaux JSON
     */
    private String escapeJson(String str) {
        if (str == null) return "";
        // Important: échapper backslash EN PREMIER avant les autres caractères
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("/", "\\/")
                  .replace("\b", "\\b")
                  .replace("\f", "\\f")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}
