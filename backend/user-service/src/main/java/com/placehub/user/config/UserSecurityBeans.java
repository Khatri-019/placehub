package com.placehub.user.config;

import com.placehub.common.security.JwtKeys;
import com.placehub.common.security.PlaceHubSecurityProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.time.Clock;

@Configuration
public class UserSecurityBeans {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public JwtEncoder jwtEncoder(PlaceHubSecurityProperties props) {
        return NimbusJwtEncoder.withSecretKey(JwtKeys.hmacKey(props.jwtSecret())).build();
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
