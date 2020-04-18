package de.tum.in.www1.artemis.config.websocket;

import static de.tum.in.www1.artemis.web.websocket.team.ParticipationTeamWebsocketService.getParticipationIdFromDestination;
import static de.tum.in.www1.artemis.web.websocket.team.ParticipationTeamWebsocketService.isParticipationTeamDestination;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurationSupport;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.web.socket.sockjs.transport.handler.WebSocketTransportHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.security.AuthoritiesConstants;
import de.tum.in.www1.artemis.service.ParticipationService;

@Configuration
public class WebsocketConfiguration extends WebSocketMessageBrokerConfigurationSupport {

    private final Logger log = LoggerFactory.getLogger(WebsocketConfiguration.class);

    public static final String IP_ADDRESS = "IP_ADDRESS";

    private final Environment env;

    private final ObjectMapper objectMapper;

    private final TaskScheduler messageBrokerTaskScheduler;

    private final TaskScheduler taskScheduler;

    private final ParticipationService participationService;

    private static final int LOGGING_DELAY_SECONDS = 10;

    public WebsocketConfiguration(Environment env, MappingJackson2HttpMessageConverter springMvcJacksonConverter, TaskScheduler messageBrokerTaskScheduler,
            TaskScheduler taskScheduler, ParticipationService participationService) {
        this.env = env;
        this.objectMapper = springMvcJacksonConverter.getObjectMapper();
        this.messageBrokerTaskScheduler = messageBrokerTaskScheduler;
        this.taskScheduler = taskScheduler;
        this.participationService = participationService;
    }

    /**
     * initialize the websocket configuration: activate logging when the profile websocketLog is active
     */
    @PostConstruct
    public void init() {
        // using Autowired leads to a weird bug, because the order of the method execution is changed. This somehow prevents messages send to single clients
        // later one, e.g. in the code editor. Therefore we call this method here directly to get a reference and adapt the logging period!
        Collection<String> activeProfiles = Arrays.asList(env.getActiveProfiles());
        // Note: this mechanism prevents that this is logged during testing
        if (activeProfiles.contains("websocketLog")) {
            final var webSocketMessageBrokerStats = webSocketMessageBrokerStats();
            webSocketMessageBrokerStats.setLoggingPeriod(LOGGING_DELAY_SECONDS * 1000);

            taskScheduler.scheduleAtFixedRate(() -> {
                final var subscriptionCount = userRegistry().getUsers().stream().flatMap(simpUser -> simpUser.getSessions().stream())
                        .map(simpSession -> simpSession.getSubscriptions().size()).reduce(0, Integer::sum);
                log.info("Currently active websocket subscriptions: " + subscriptionCount);
            }, LOGGING_DELAY_SECONDS * 1000);
        }
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic").setHeartbeatValue(new long[] { 10000, 20000 }).setTaskScheduler(messageBrokerTaskScheduler);
        // increase the limit of concurrent connections (default is 1024 which is much too low)
        // config.setCacheLimit(10000);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        DefaultHandshakeHandler handshakeHandler = defaultHandshakeHandler();
        // NOTE: by setting a WebSocketTransportHandler we disable http poll, http stream and other exotic workarounds and only support real websocket connections.
        // nowadays all modern browsers support websockets and workarounds are not necessary any more and might only lead to problems
        WebSocketTransportHandler webSocketTransportHandler = new WebSocketTransportHandler(handshakeHandler);
        registry.addEndpoint("/websocket/tracker").setAllowedOrigins("*").withSockJS().setTransportHandlers(webSocketTransportHandler)
                .setInterceptors(httpSessionHandshakeInterceptor());
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new TopicSubscriptionInterceptor());
    }

    @NotNull
    @Override
    protected MappingJackson2MessageConverter createJacksonConverter() {
        // NOTE: We need to adapt the default messageConverter for WebSocket messages
        // with a messageConverter that uses the same ObjectMapper that our REST endpoints use.
        // This gives us consistency in how specific datatypes are serialized (e.g. timestamps)
        MappingJackson2MessageConverter converter = super.createJacksonConverter();
        converter.setObjectMapper(objectMapper);
        return converter;
    }

    /**
     * @return initialize the handshake interceptor stores the remote IP address before handshake
     */
    @Bean
    public HandshakeInterceptor httpSessionHandshakeInterceptor() {
        return new HandshakeInterceptor() {

            @Override
            public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) {
                if (request instanceof ServletServerHttpRequest) {
                    ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
                    attributes.put(IP_ADDRESS, servletRequest.getRemoteAddress());
                }
                return true;
            }

            @Override
            public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
                if (exception != null) {
                    log.warn("Exception occurred in WS.afterHandshake: " + exception.getMessage());
                }
            }
        };
    }

    private DefaultHandshakeHandler defaultHandshakeHandler() {
        return new DefaultHandshakeHandler() {

            @Override
            protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler, Map<String, Object> attributes) {
                Principal principal = request.getPrincipal();
                if (principal == null) {
                    Collection<SimpleGrantedAuthority> authorities = new ArrayList<>();
                    authorities.add(new SimpleGrantedAuthority(AuthoritiesConstants.ANONYMOUS));
                    principal = new AnonymousAuthenticationToken("WebsocketConfiguration", "anonymous", authorities);
                }
                log.debug("determineUser: " + principal);
                return principal;
            }
        };
    }

    public class TopicSubscriptionInterceptor implements ChannelInterceptor {

        /**
         * Method is called before the user's message is sent to the controller
         *
         * @param message Message that the websocket client is sending (e.g. SUBSCRIBE, MESSAGE, UNSUBSCRIBE)
         * @param channel Current message channel
         * @return message that gets passed along further
         */
        @Override
        public Message<?> preSend(Message<?> message, MessageChannel channel) {
            StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(message);
            Principal principal = headerAccessor.getUser();
            String destination = headerAccessor.getDestination();

            if (StompCommand.SUBSCRIBE.equals(headerAccessor.getCommand())) {
                if (!allowSubscription(principal, destination)) {
                    logUnauthorizedDestinationAccess(principal, destination);
                    return null; // erase the forbidden SUBSCRIBE command the user was trying to send
                }
            }

            return message;
        }

        /**
         * Returns whether the subscription of the given principal to the given destination is permitted
         *
         * @param principal User principal of the user who wants to subscribe
         * @param destination Destination topic to which the user wants to subscribe
         * @return flag whether subscription is allowed
         */
        private boolean allowSubscription(Principal principal, String destination) {
            if (isParticipationTeamDestination(destination)) {
                Long participationId = getParticipationIdFromDestination(destination);
                return isParticipationOwnedByUser(principal, participationId);
            }
            return true;
        }

        private void logUnauthorizedDestinationAccess(Principal principal, String destination) {
            if (principal == null) {
                log.warn("Anonymous user tried to access the protected topic: " + destination);
            }
            else {
                log.warn("User with login '" + principal.getName() + "' tried to access the protected topic: " + destination);
            }
        }
    }

    private boolean isParticipationOwnedByUser(Principal principal, Long participationId) {
        StudentParticipation participation = participationService.findOneStudentParticipation(participationId);
        return participation.isOwnedBy(principal.getName());
    }
}
