package org.example.outils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MethodInfo {
    private final Class<?> controllerClass;
    private final Method method;
    private final String urlPattern;
    private final List<String> parameterNames;
    private final Pattern regexPattern;

    public MethodInfo(Class<?> controllerClass, Method method, String urlPattern) {
        this.controllerClass = controllerClass;
        this.method = method;
        this.urlPattern = urlPattern;
        this.parameterNames = new ArrayList<>();
        this.regexPattern = buildRegexPattern(urlPattern);
    }

    private Pattern buildRegexPattern(String pattern) {
        // Extraire les noms de paramètres {param}
        Pattern paramPattern = Pattern.compile("\\{([^/]+)\\}");
        Matcher matcher = paramPattern.matcher(pattern);
        
        while (matcher.find()) {
            parameterNames.add(matcher.group(1));
        }
        
        // Convertir le pattern en regex : /zavatra/{valeur} -> /zavatra/([^/]+)
        String regex = pattern.replaceAll("\\{[^/]+\\}", "([^/]+)");
        regex = "^" + regex + "$";
        
        return Pattern.compile(regex);
    }

    public boolean matches(String url) {
        return regexPattern.matcher(url).matches();
    }

    public List<String> extractParameters(String url) {
        List<String> values = new ArrayList<>();
        Matcher matcher = regexPattern.matcher(url);
        
        if (matcher.matches()) {
            for (int i = 1; i <= matcher.groupCount(); i++) {
                values.add(matcher.group(i));
            }
        }
        
        return values;
    }

    public Class<?> getControllerClass() {
        return controllerClass;
    }

    public Method getMethod() {
        return method;
    }

    public String getUrlPattern() {
        return urlPattern;
    }

    public List<String> getParameterNames() {
        return parameterNames;
    }

    @Override
    public String toString() {
        return controllerClass.getSimpleName() + "#" + method.getName();
    }
}