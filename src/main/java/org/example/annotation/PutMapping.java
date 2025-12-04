package org.example.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation pour mapper une méthode aux requêtes PUT
 * 
 * Exemple:
 *   @PutMapping("/users/{id}")
 *   public String updateUser(int id, ...) { ... }
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PutMapping {
    String value();
}
