package com.golfing8.elevatorsigns.config.annotation;

import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Retention;

@Retention(RetentionPolicy.RUNTIME)
public @interface Configurable {
    String path() default "";
    
    String name() default "";
}
 