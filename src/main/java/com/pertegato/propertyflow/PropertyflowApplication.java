package com.pertegato.propertyflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Die MCP-OAuth-Kette wird nur bei Bedarf für ihre eigenen Endpunkte eingerichtet.
@SpringBootApplication(excludeName = {
        "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration",
        "org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration",
        "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration",
        "org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerAutoConfiguration"
})
public class PropertyflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(PropertyflowApplication.class, args);
    }

}
