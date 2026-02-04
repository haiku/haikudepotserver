package org.haiku.haikudepotserver.maintenance.model;

/**
 * <p>Implementations of this service will run a task at a given schedule
 * to perform some maintenance tasks.</p>
 */

public interface MaintenanceService {

    void daily();

    void hourly();

    void fiveMinutely();

}
