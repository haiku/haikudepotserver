package org.haiku.haikudepotserver.maintenance;

import org.haiku.haikudepotserver.maintenance.model.MaintenanceService;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.stereotype.Component;

/**
 * <p>This is a means of triggering the maintenance tasks via the actuator.</p>
 */

@Component
@Endpoint(id = "hdsmaintenance")
public class MaintenanceEndpoint {

    public enum Type {
        DAILY,
        HOURLY
    }

    private final MaintenanceService maintenanceService;

    public MaintenanceEndpoint(MaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    @WriteOperation
    public String trigger(Type type) {
        return switch (type) {
            case DAILY -> {
                maintenanceService.daily();
                yield "ok";
            }
            case HOURLY -> {
                maintenanceService.hourly();
                yield "ok";
            }
        };
    }

}
