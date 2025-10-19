package org.example.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

// Retention = visible à l'exécution
@Retention(RetentionPolicy.RUNTIME)
// Target = utilisable sur une méthode
@Target(ElementType.METHOD)
public @interface Route {
    String url(); // paramètre obligatoire
}
