import { Injectable } from '@angular/core';
import { ProgrammingExerciseSimulationService } from 'app/exercises/programming/manage/services/programming-exercise-simulation.service';

/**
 * This functionality is only for testing purposes (noVersionControlAndContinuousIntegrationAvailable)
 */

@Injectable({ providedIn: 'root' })
export class ProgrammingExerciseSimulationUtils {
    constructor(private programmingExerciseSimulationService: ProgrammingExerciseSimulationService) {}

    /**
     * Checks if the url includes the string "nolocalsetup', which is an indication
     * that the particular programming exercise is not connected to a version control and continuous integration server
     * @param urlToCheck the url which will be check if it contains the substring
     */
    noVersionControlAndContinuousIntegrationAvailableCheck(urlToCheck: string): boolean {
        return urlToCheck.includes('artemislocalhost');
    }
}
