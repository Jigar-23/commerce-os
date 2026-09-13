package com.commerceos.payment.config;

import com.commerceos.payment.provider.PaymentProvider;
import com.commerceos.payment.provider.RazorpayPaymentProvider;
import com.commerceos.payment.provider.SandboxPaymentProvider;
import com.commerceos.payment.provider.StripePaymentProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class PaymentProviderConfig {

    @Bean
    @Primary
    public PaymentProvider paymentProvider(
            SandboxPaymentProvider sandboxProvider,
            RazorpayPaymentProvider razorpayProvider,
            StripePaymentProvider stripeProvider
    ) {
        String configured = System.getenv("PAYMENT_PROVIDER");
        if (configured == null || configured.isBlank()) {
            configured = System.getProperty("payment.provider", "RAZORPAY");
        }

        String env = System.getenv("NODE_ENV");
        if (env == null || env.isBlank()) {
            env = System.getenv("SPRING_PROFILES_ACTIVE");
        }
        boolean isProduction = "production".equalsIgnoreCase(env) || "prod".equalsIgnoreCase(env);

        String normalized = configured.trim().toUpperCase();

        if ("SANDBOX".equals(normalized)) {
            if (isProduction) {
                throw new IllegalStateException("CRITICAL_SECURITY_CONFIGURATION_ERROR: PAYMENT_PROVIDER=SANDBOX is strictly forbidden in production mode.");
            }
            return sandboxProvider;
        }

        if ("STRIPE".equals(normalized)) {
            return stripeProvider;
        }

        // Default authoritative provider
        return razorpayProvider;
    }
}
