package com.fooddelivery.aimcpserver.tool;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.fooddelivery.aimcpserver.security.Role;

/** Who is offered a tool (and may call it) and how long one call may take. Read by ToolCatalog. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ToolPolicy {

    Role[] roles();

    /** 0 means the default (app.agent.tool-timeout). */
    int timeoutSeconds() default 0;
}
