package org.example.outils;

import java.lang.reflect.Method;

public class MethodInfo {
    private final Class<?> controllerClass;
    private final Method method;

    public MethodInfo(Class<?> controllerClass, Method method) {
        this.controllerClass = controllerClass;
        this.method = method;
    }

    public Class<?> getControllerClass() {
        return controllerClass;
    }

    public Method getMethod() {
        return method;
    }

    @Override
    public String toString() {
        return controllerClass.getSimpleName() + "#" + method.getName();
    }
}