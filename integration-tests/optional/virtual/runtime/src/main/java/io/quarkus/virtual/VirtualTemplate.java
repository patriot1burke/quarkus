package io.quarkus.virtual;

import io.quarkus.runtime.annotations.Template;

@Template
public class VirtualTemplate {
    public static boolean virtualCalled;

    public void setupVirtual() {
        System.out.println("*************** setupVirtual *********************");
    }
}
