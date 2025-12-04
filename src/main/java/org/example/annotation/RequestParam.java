package org.example.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface RequestParam {
    /**
     * Le nom du paramètre de requête à rechercher
     */
    String value();
    
    /**
     * Indique si le paramètre est obligatoire (non utilisé pour l'instant)
     */
    boolean required() default true;
}
