package org.rocket.network.acara;

import com.github.blackrush.acara.EventModule;
import com.github.blackrush.acara.ListenerMetadata;
import com.github.blackrush.acara.dispatch.Dispatcher;
import com.github.blackrush.acara.dispatch.DispatcherLookup;
import com.google.inject.Key;
import org.rocket.network.Connect;
import org.rocket.network.Disconnect;
import org.rocket.network.PropValidation;
import org.rocket.network.Receive;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public final class RocketAcara {
    private RocketAcara() {}

    public static EventModule newContextfulModule() {
        return builder -> builder
                // @Connect @Disconnect
                .addEventMetadataLookup(RocketBasicEventMetadata::lookup)
                .addDispatcherLookup(wrapInPropValidatorIfNeeded(RocketBasicDispatcher::lookup))
                .addMetadataLookup(RocketAcara::lookupBasic)

                // @Receive
                .addEventMetadataLookup(RocketReceiveEventMetadata::lookup)
                .addDispatcherLookup(wrapInPropValidatorIfNeeded(RocketReceiveDispatcher::lookup))
                .addMetadataLookup(RocketAcara::lookupReceive)
                ;
    }

    static Stream<ListenerMetadata> lookupBasic(Object listener) {
        Class<?> klass = listener.getClass();

        return Stream.concat(Stream.of(klass.getDeclaredMethods()), Stream.of(klass.getMethods()))
                .distinct()
                .flatMap(method -> {
                    boolean disconnecting = false;

                    if (method.isAnnotationPresent(Connect.class) || (disconnecting = method.isAnnotationPresent(Disconnect.class))) {
                        return Stream.of(new ListenerMetadata(
                                klass,
                                method,
                                new RocketBasicEventMetadata(disconnecting)
                        ));
                    }

                    return Stream.empty();
                });
    }

    static Stream<ListenerMetadata> lookupReceive(Object listener) {
        Class<?> klass = listener.getClass();

        return Stream.concat(Stream.of(klass.getDeclaredMethods()), Stream.of(klass.getMethods()))
                .distinct()
                .flatMap(method -> {
                    if (method.isAnnotationPresent(Receive.class)) {
                        return Stream.of(new ListenerMetadata(
                                klass,
                                method,
                                new RocketReceiveEventMetadata(method.getParameterTypes()[0])
                        ));
                    }

                    return Stream.empty();
                });
    }

    public static List<Key<?>> lookupPropValidations(AnnotatedElement element) {
        List<Key<?>> keys = new LinkedList<>();
        for (Annotation annotation : element.getAnnotations()) {
            if (annotation instanceof PropValidation) {
                PropValidation propValidation = (PropValidation) annotation;
                keys.add(Key.get(propValidation.value()));
            } else {
                keys.addAll(lookupPropValidations(annotation.annotationType()));
            }
        }
        return keys;
    }

    public static Dispatcher wrapInPropValidatorIfNeeded(Dispatcher dispatcher, AnnotatedElement element) {
        List<Key<?>> keys = lookupPropValidations(element);

        if (keys.isEmpty()) {
            return dispatcher;
        }

        return new RocketPropValidatorDispatcher(dispatcher, keys);
    }

    public static DispatcherLookup wrapInPropValidatorIfNeeded(DispatcherLookup lookup) {
        return lookup.bind((listener, dispatcher) ->
            Optional.of(wrapInPropValidatorIfNeeded(
                dispatcher,
                listener.getListenerMethod()
            ))
        );
    }
}
