package org.rocket;

import java.util.Optional;

public interface Service extends Startable, Stoppable {
	Optional<Class<? extends Service>> dependsOn();
}
