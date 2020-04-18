package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.TeamImportStrategyType;
import de.tum.in.www1.artemis.repository.TeamRepository;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.dto.TeamSearchUserDTO;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/**
 * REST controller for managing Teams.
 */
@RestController
@RequestMapping("/api")
public class TeamResource {

    private final Logger log = LoggerFactory.getLogger(TeamResource.class);

    public static final String ENTITY_NAME = "team";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final TeamRepository teamRepository;

    private final TeamService teamService;

    private final CourseService courseService;

    private final ExerciseService exerciseService;

    private final UserService userService;

    private final AuthorizationCheckService authCheckService;

    private final ParticipationService participationService;

    private final AuditEventRepository auditEventRepository;

    public TeamResource(TeamRepository teamRepository, TeamService teamService, CourseService courseService, ExerciseService exerciseService, UserService userService,
            AuthorizationCheckService authCheckService, ParticipationService participationService, AuditEventRepository auditEventRepository) {
        this.teamRepository = teamRepository;
        this.teamService = teamService;
        this.courseService = courseService;
        this.exerciseService = exerciseService;
        this.userService = userService;
        this.authCheckService = authCheckService;
        this.participationService = participationService;
        this.auditEventRepository = auditEventRepository;
    }

    /**
     * POST /exercises/{exerciseId}/teams : Create a new team for an exercise.
     *
     * @param team       the team to create
     * @param exerciseId the exercise id for which to create a team
     * @return the ResponseEntity with status 201 (Created) and with body the new team, or with status 400 (Bad Request) if the team already has an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/exercises/{exerciseId}/teams")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Team> createTeam(@RequestBody Team team, @PathVariable long exerciseId) throws URISyntaxException {
        log.debug("REST request to save Team : {}", team);
        if (team.getId() != null) {
            throw new BadRequestAlertException("A new team cannot already have an ID", ENTITY_NAME, "idexists");
        }
        User user = userService.getUserWithGroupsAndAuthorities();
        Exercise exercise = exerciseService.findOne(exerciseId);
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise, user)) {
            return forbidden();
        }
        team.setOwner(user); // the TA (or above) who creates the team, is the owner of the team
        Team result = teamService.save(exercise, team);
        result.filterSensitiveInformation();
        return ResponseEntity.created(new URI("/api/teams/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.getId().toString())).body(result);
    }

    /**
     * PUT /exercises/:exerciseId/teams/:id : Updates an existing team.
     *
     * @param team       the team to update
     * @param exerciseId the id of the exercise that the team belongs to
     * @param id the id of the team which to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated team, or with status 400 (Bad Request) if the team is not valid, or with status 500 (Internal
     * Server Error) if the team couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/exercises/{exerciseId}/teams/{id}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Team> updateTeam(@RequestBody Team team, @PathVariable long exerciseId, @PathVariable long id) throws URISyntaxException {
        log.debug("REST request to update Team : {}", team);
        if (team.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!team.getId().equals(id)) {
            throw new BadRequestAlertException("The team has an incorrect id.", ENTITY_NAME, "wrongId");
        }
        Optional<Team> existingTeam = teamRepository.findById(id);
        if (existingTeam.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (!existingTeam.get().getExercise().getId().equals(exerciseId)) {
            throw new BadRequestAlertException("The team does not belong to the specified exercise id.", ENTITY_NAME, "wrongExerciseId");
        }
        if (!team.getShortName().equals(existingTeam.get().getShortName())) {
            return forbidden(ENTITY_NAME, "shortNameChangeForbidden", "The team's short name cannot be changed after the team has been created.");
        }

        // Prepare auth checks
        User user = userService.getUserWithGroupsAndAuthorities();
        Exercise exercise = exerciseService.findOne(exerciseId);
        final boolean isAtLeastInstructor = authCheckService.isAtLeastInstructorForExercise(exercise, user);
        final boolean isAtLeastTeachingAssistantAndOwner = authCheckService.isAtLeastTeachingAssistantForExercise(exercise, user)
                && authCheckService.isOwnerOfTeam(existingTeam.get(), user);

        // User must be (1) at least instructor or (2) TA but the owner of the team
        if (!isAtLeastInstructor && !isAtLeastTeachingAssistantAndOwner) {
            return forbidden();
        }

        // The team owner can only be changed by instructors
        if (!isAtLeastInstructor && !existingTeam.get().getOwner().equals(team.getOwner())) {
            return forbidden();
        }

        // Save team (includes check for conflicts that no student is assigned to multiple teams for an exercise)
        Team result = teamService.save(exercise, team);

        // For programming exercise teams with existing participation, the repository access needs to be updated according to the new team member set
        if (exercise instanceof ProgrammingExercise) {
            teamService.updateRepositoryMembersIfNeeded(exerciseId, existingTeam.get(), team);
        }

        result.filterSensitiveInformation();
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, team.getId().toString())).body(result);
    }

    /**
     * GET /exercises/:exerciseId/teams/:id : get the "id" team.
     *
     * @param exerciseId the id of the exercise that the team belongs to
     * @param id         the id of the team to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the team, or with status 404 (Not Found)
     */
    @GetMapping("/exercises/{exerciseId}/teams/{id}")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Team> getTeam(@PathVariable long exerciseId, @PathVariable long id) {
        log.debug("REST request to get Team : {}", id);
        Optional<Team> optionalTeam = teamRepository.findOneWithEagerStudents(id);
        if (optionalTeam.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Team team = optionalTeam.get();
        if (team.getExercise() != null && !team.getExercise().getId().equals(exerciseId)) {
            throw new BadRequestAlertException("The team does not belong to the specified exercise id.", ENTITY_NAME, "wrongExerciseId");
        }
        User user = userService.getUserWithGroupsAndAuthorities();
        Exercise exercise = exerciseService.findOne(exerciseId);
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise, user) && !team.hasStudent(user)) {
            return forbidden();
        }
        team.filterSensitiveInformation();
        return ResponseEntity.ok().body(team);
    }

    /**
     * GET /exercises/:exerciseId/teams : get all the teams of an exercise for the exercise administration page
     *
     * @param exerciseId the exerciseId of the exercise for which all teams should be returned
     * @return the ResponseEntity with status 200 (OK) and the list of teams in body
     */
    @GetMapping("/exercises/{exerciseId}/teams")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<Team>> getTeamsForExercise(@PathVariable long exerciseId) {
        log.debug("REST request to get all Teams for the exercise with id : {}", exerciseId);
        User user = userService.getUserWithGroupsAndAuthorities();
        Exercise exercise = exerciseService.findOne(exerciseId);
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise, user)) {
            return forbidden();
        }
        List<Team> teams = teamRepository.findAllByExerciseIdWithEagerStudents(exerciseId);
        teams.forEach(Team::filterSensitiveInformation);
        return ResponseEntity.ok().body(teams);
    }

    /**
     * DELETE /exercises/:exerciseId/teams/:id : delete the "id" team.
     *
     * @param exerciseId the id of the exercise that the team belongs to
     * @param id         the id of the team to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/exercises/{exerciseId}/teams/{id}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> deleteTeam(@PathVariable long exerciseId, @PathVariable long id) {
        log.info("REST request to delete Team with id {} in exercise with id {}", id, exerciseId);
        User user = userService.getUserWithGroupsAndAuthorities();
        Optional<Team> optionalTeam = teamRepository.findById(id);
        if (optionalTeam.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Team team = optionalTeam.get();
        if (team.getExercise() != null && !team.getExercise().getId().equals(exerciseId)) {
            throw new BadRequestAlertException("The team does not belong to the specified exercise id.", ENTITY_NAME, "wrongExerciseId");
        }
        Exercise exercise = exerciseService.findOne(exerciseId);
        if (!authCheckService.isAtLeastInstructorForExercise(exercise, user)) {
            return forbidden();
        }
        // Create audit event for team delete action
        var logMessage = "Delete Team with id " + id + " in exercise with id " + exerciseId;
        var auditEvent = new AuditEvent(user.getLogin(), Constants.DELETE_TEAM, logMessage);
        auditEventRepository.add(auditEvent);
        // Delete all participations of the team first and then the team itself
        participationService.deleteAllByTeamId(id, false, false);
        teamRepository.delete(team);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, Long.toString(id))).build();
    }

    /**
     * GET /exercises/{exerciseId}/teams/exists?shortName={shortName} : get boolean flag whether team with shortName exists for exercise
     *
     * @param exerciseId the id of the exercise for which to check teams
     * @param shortName the shortName of the team to check for existence
     * @return Response with status 200 (OK) and boolean flag in the body
     */
    @GetMapping("/exercises/{exerciseId}/teams/exists")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Boolean> existsTeamByShortName(@PathVariable long exerciseId, @RequestParam("shortName") String shortName) {
        log.debug("REST request to check Team existence for exercise with id {} for shortName : {}", exerciseId, shortName);
        Exercise exercise = exerciseService.findOne(exerciseId);
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise, user)) {
            return forbidden();
        }
        return ResponseEntity.ok().body(teamRepository.findOneByExerciseIdAndShortName(exerciseId, shortName).isPresent());
    }

    /**
     * GET /courses/:courseId/exercises/:exerciseId/team-search-users : get all users for a given course.
     *
     * @param courseId    the id of the course for which to search users
     * @param exerciseId  the id of the exercise for which to search users to join a team
     * @param loginOrName the login or name by which to search users
     * @return the ResponseEntity with status 200 (OK) and with body all users
     */
    @GetMapping("/courses/{courseId}/exercises/{exerciseId}/team-search-users")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<TeamSearchUserDTO>> searchUsersInCourse(@PathVariable long courseId, @PathVariable long exerciseId,
            @RequestParam("loginOrName") String loginOrName) {
        log.debug("REST request to search Users for {} in course with id : {}", loginOrName, courseId);
        // restrict result size by only allowing reasonable searches
        if (loginOrName.length() < 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query param 'loginOrName' must be three characters or longer.");
        }
        User user = userService.getUserWithGroupsAndAuthorities();
        Course course = courseService.findOne(courseId);
        if (!authCheckService.isAtLeastTeachingAssistantInCourse(course, user)) {
            return forbidden();
        }
        Exercise exercise = exerciseService.findOne(exerciseId);
        return ResponseEntity.ok().body(teamService.searchByLoginOrNameInCourseForExerciseTeam(course, exercise, loginOrName));
    }

    /**
     * PUT /exercises/:destinationExerciseId/teams/import-from-exercise/:sourceExerciseId : copy teams from source exercise into destination exercise
     *
     * @param destinationExerciseId the exercise id of the exercise for which to import teams (= destination exercise)
     * @param sourceExerciseId the exercise id of the exercise from which to copy the teams (= source exercise)
     * @param importStrategyType the import strategy to use when importing the teams
     * @return the ResponseEntity with status 200 (OK) and the list of created teams in body
     */
    @PutMapping("/exercises/{destinationExerciseId}/teams/import-from-exercise/{sourceExerciseId}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<Team>> importTeamsFromSourceExercise(@PathVariable long destinationExerciseId, @PathVariable long sourceExerciseId,
            @RequestParam TeamImportStrategyType importStrategyType) {
        log.debug("REST request import all teams from source exercise with id {} into destination exercise with id {}", sourceExerciseId, destinationExerciseId);

        User user = userService.getUserWithGroupsAndAuthorities();
        Exercise destinationExercise = exerciseService.findOne(destinationExerciseId);

        if (!authCheckService.isAtLeastInstructorForExercise(destinationExercise, user)) {
            return forbidden();
        }
        if (destinationExerciseId == sourceExerciseId) {
            throw new BadRequestAlertException("The source and destination exercise must be different.", ENTITY_NAME, "sourceDestinationExerciseNotDifferent");
        }
        if (!destinationExercise.isTeamMode()) {
            throw new BadRequestAlertException("The destination exercise must be a team-based exercise.", ENTITY_NAME, "destinationExerciseNotTeamBased");
        }
        Exercise sourceExercise = exerciseService.findOne(sourceExerciseId);
        if (!sourceExercise.isTeamMode()) {
            throw new BadRequestAlertException("The source exercise must be a team-based exercise.", ENTITY_NAME, "sourceExerciseNotTeamBased");
        }

        // Create audit event for team import action
        var logMessage = "Import teams from source exercise '" + sourceExercise.getTitle() + "' (id: " + sourceExercise.getId() + ") into destination exercise '"
                + destinationExercise.getTitle() + "' (id: " + destinationExercise.getId() + ") using strategy " + importStrategyType;
        var auditEvent = new AuditEvent(user.getLogin(), Constants.IMPORT_TEAMS, logMessage);
        auditEventRepository.add(auditEvent);

        // Import teams and return the teams that now belong to the destination exercise
        List<Team> destinationTeams = teamService.importTeamsFromSourceExerciseIntoDestinationExerciseUsingStrategy(sourceExercise, destinationExercise, importStrategyType);
        destinationTeams.forEach(Team::filterSensitiveInformation);
        return ResponseEntity.ok().body(destinationTeams);
    }

}
