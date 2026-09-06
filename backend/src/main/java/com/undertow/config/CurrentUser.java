package com.undertow.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects the current authenticated user's externalId (email) into a
 * controller method parameter.
 *
 * Resolved from a validated "Authorization: Bearer &lt;token&gt;" header
 * (see CurrentUserArgumentResolver / com.undertow.auth) - never trusted
 * from client-supplied data. A missing, unknown, or expired token results
 * in a 401 before the controller method ever runs.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
}
