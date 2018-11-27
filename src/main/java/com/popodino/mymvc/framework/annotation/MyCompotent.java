package com.popodino.mymvc.framework.annotation;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MyCompotent {
    String value() default "";
}
