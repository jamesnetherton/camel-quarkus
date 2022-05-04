package org.apache.camel.quarkus.test.junit5;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.quarkus.test.junit.QuarkusTestExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(QuarkusTestExtension.class)
public @interface CamelQuarkusTest {

    /**
     * NOTE: If you
     * 
     * @return
     */
    String[] properties() default {};
}
