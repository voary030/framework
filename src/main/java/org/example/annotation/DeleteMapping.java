package org.example.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation pour mapper une méthode aux requêtes DELETE
 * 
 * Exemple:
 *   @DeleteMapping("/users/{id}")
 *   public String deleteUser(int id) { ... }
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface DeleteMapping {
    String value();
}
