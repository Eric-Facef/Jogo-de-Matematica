package dc.unifacef.Jogo.Victor.configuracao;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-jogo")
                .setAllowedOriginPatterns("*") // 🔍 Alterado aqui: de setAllowedOrigins para setAllowedOriginPatterns
                .withSockJS(); // 🔍 Garante que o fallback do SockJS que usamos no front seja aceito
    }
}