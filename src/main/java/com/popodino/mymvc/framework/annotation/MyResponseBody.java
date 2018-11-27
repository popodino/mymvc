package com.popodino.mymvc.framework.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MyResponseBody {
    String value() default "";
}
