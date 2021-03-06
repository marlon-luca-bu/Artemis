package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.config.Constants.SHORT_NAME_PATTERN;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.notFound;
import static java.time.ZonedDateTime.now;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.ComplaintType;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.domain.enumeration.TutorParticipationStatus;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.participation.TutorParticipation;
import de.tum.in.www1.artemis.exception.ArtemisAuthenticationException;
import de.tum.in.www1.artemis.exception.GroupAlreadyExistsException;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.ArtemisAuthenticationProvider;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.connectors.VcsUserManagementService;
import de.tum.in.www1.artemis.web.rest.dto.StatsForInstructorDashboardDTO;
import de.tum.in.www1.artemis.web.rest.dto.TutorLeaderboardDTO;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import io.github.jhipster.config.JHipsterConstants;
import io.github.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing Course.
 */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('ADMIN')")
public class CourseResource {

    private final Logger log = LoggerFactory.getLogger(CourseResource.class);

    private static final String ENTITY_NAME = "course";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final UserService userService;

    private final CourseService courseService;

    private final ParticipationService participationService;

    private final AuthorizationCheckService authCheckService;

    private final CourseRepository courseRepository;

    private final ExerciseService exerciseService;

    private final ArtemisAuthenticationProvider artemisAuthenticationProvider;

    private final TutorParticipationService tutorParticipationService;

    private final LectureService lectureService;

    private final ComplaintRepository complaintRepository;

    private final ComplaintResponseRepository complaintResponseRepository;

    private final NotificationService notificationService;

    private final SubmissionService submissionService;

    private final ResultService resultService;

    private final ComplaintService complaintService;

    private final TutorLeaderboardService tutorLeaderboardService;

    private final ProgrammingExerciseService programmingExerciseService;

    private final ExampleSubmissionRepository exampleSubmissionRepository;

    private final AuditEventRepository auditEventRepository;

    private final Optional<VcsUserManagementService> vcsUserManagementService;

    private final Environment env;

    public CourseResource(UserService userService, CourseService courseService, ParticipationService participationService, CourseRepository courseRepository,
            ExerciseService exerciseService, AuthorizationCheckService authCheckService, TutorParticipationService tutorParticipationService, Environment env,
            ArtemisAuthenticationProvider artemisAuthenticationProvider, ComplaintRepository complaintRepository, ComplaintResponseRepository complaintResponseRepository,
            LectureService lectureService, NotificationService notificationService, SubmissionService submissionService, ResultService resultService,
            ComplaintService complaintService, TutorLeaderboardService tutorLeaderboardService, ExampleSubmissionRepository exampleSubmissionRepository,
            ProgrammingExerciseService programmingExerciseService, AuditEventRepository auditEventRepository, Optional<VcsUserManagementService> vcsUserManagementService) {
        this.userService = userService;
        this.courseService = courseService;
        this.participationService = participationService;
        this.courseRepository = courseRepository;
        this.exerciseService = exerciseService;
        this.authCheckService = authCheckService;
        this.tutorParticipationService = tutorParticipationService;
        this.artemisAuthenticationProvider = artemisAuthenticationProvider;
        this.complaintRepository = complaintRepository;
        this.complaintResponseRepository = complaintResponseRepository;
        this.lectureService = lectureService;
        this.notificationService = notificationService;
        this.submissionService = submissionService;
        this.resultService = resultService;
        this.complaintService = complaintService;
        this.tutorLeaderboardService = tutorLeaderboardService;
        this.programmingExerciseService = programmingExerciseService;
        this.exampleSubmissionRepository = exampleSubmissionRepository;
        this.vcsUserManagementService = vcsUserManagementService;
        this.auditEventRepository = auditEventRepository;
        this.env = env;
    }

    /**
     * POST /courses : Create a new course.
     *
     * @param course the course to create
     * @return the ResponseEntity with status 201 (Created) and with body the new course, or with status 400 (Bad Request) if the course has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/courses")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Course> createCourse(@RequestBody Course course) throws URISyntaxException {
        log.debug("REST request to save Course : {}", course);
        if (course.getId() != null) {
            throw new BadRequestAlertException("A new course cannot already have an ID", ENTITY_NAME, "idexists");
        }

        // Check if course shortname matches regex
        Matcher shortNameMatcher = SHORT_NAME_PATTERN.matcher(course.getShortName());
        if (!shortNameMatcher.matches()) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, "The shortname is invalid", "shortnameInvalid")).body(null);
        }

        List<Course> coursesWithSameShortName = courseRepository.findAllByShortName(course.getShortName());
        if (coursesWithSameShortName.size() > 0) {
            return ResponseEntity.badRequest().headers(
                    HeaderUtil.createAlert(applicationName, "A course with the same short name already exists. Please choose a different short name.", "shortnameAlreadyExists"))
                    .body(null);
        }

        validateComplaintsConfig(course);

        try {

            // We use default names if a group was not specified by the ADMIN.
            // NOTE: instructors cannot change the group of a course, because this would be a security issue!

            // only create default group names, if the ADMIN has used a custom group names, we assume that it already exists.

            if (course.getStudentGroupName() == null) {
                course.setStudentGroupName(course.getDefaultStudentGroupName());
                artemisAuthenticationProvider.createGroup(course.getStudentGroupName());
            }
            else {
                checkIfGroupsExists(course.getStudentGroupName());
            }

            if (course.getTeachingAssistantGroupName() == null) {
                course.setTeachingAssistantGroupName(course.getDefaultTeachingAssistantGroupName());
                artemisAuthenticationProvider.createGroup(course.getTeachingAssistantGroupName());
            }
            else {
                checkIfGroupsExists(course.getTeachingAssistantGroupName());
            }

            if (course.getInstructorGroupName() == null) {
                course.setInstructorGroupName(course.getDefaultInstructorGroupName());
                artemisAuthenticationProvider.createGroup(course.getInstructorGroupName());
            }
            else {
                checkIfGroupsExists(course.getInstructorGroupName());
            }
        }
        catch (GroupAlreadyExistsException ex) {
            throw new BadRequestAlertException(
                    ex.getMessage() + ": One of the groups already exists (in the external user management), because the short name was already used in Artemis before. "
                            + "Please choose a different short name!",
                    ENTITY_NAME, "shortNameWasAlreadyUsed", true);
        }
        catch (ArtemisAuthenticationException ex) {
            // a specified group does not exist, notify the client
            throw new BadRequestAlertException(ex.getMessage(), ENTITY_NAME, "groupNotFound", true);
        }
        Course result = courseService.save(course);
        return ResponseEntity.created(new URI("/api/courses/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.getTitle())).body(result);
    }

    /**
     * PUT /courses : Updates an existing updatedCourse.
     *
     * @param updatedCourse the updatedCourse to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated updatedCourse, or with status 400 (Bad Request) if the updatedCourse is not valid, or with status
     * 500 (Internal Server Error) if the updatedCourse couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/courses")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<Course> updateCourse(@RequestBody Course updatedCourse) throws URISyntaxException {
        log.debug("REST request to update Course : {}", updatedCourse);
        if (updatedCourse.getId() == null) {
            return createCourse(updatedCourse);
        }
        Optional<Course> existingCourse = courseRepository.findById(updatedCourse.getId());
        if (existingCourse.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        if (!Objects.equals(existingCourse.get().getShortName(), updatedCourse.getShortName())) {
            throw new BadRequestAlertException("The course short name cannot be changed", ENTITY_NAME, "shortNameCannotChange", true);
        }

        User user = userService.getUserWithGroupsAndAuthorities();
        // only allow admins or instructors of the existing updatedCourse to change it
        // this is important, otherwise someone could put himself into the instructor group of the updated Course
        if (!authCheckService.isAtLeastInstructorInCourse(existingCourse.get(), user)) {
            return forbidden();
        }

        if (authCheckService.isAdmin()) {
            // if an admin changes a group, we need to check that the changed group exists
            try {
                if (!Objects.equals(existingCourse.get().getStudentGroupName(), updatedCourse.getStudentGroupName())) {
                    checkIfGroupsExists(updatedCourse.getStudentGroupName());
                }
                if (!Objects.equals(existingCourse.get().getTeachingAssistantGroupName(), updatedCourse.getTeachingAssistantGroupName())) {
                    checkIfGroupsExists(updatedCourse.getTeachingAssistantGroupName());
                }
                if (!Objects.equals(existingCourse.get().getInstructorGroupName(), updatedCourse.getInstructorGroupName())) {
                    checkIfGroupsExists(updatedCourse.getInstructorGroupName());
                }
            }
            catch (ArtemisAuthenticationException ex) {
                // a specified group does not exist, notify the client
                throw new BadRequestAlertException(ex.getMessage(), ENTITY_NAME, "groupNotFound", true);
            }
        }
        else {
            // this means the user must be an instructor, who has NOT Admin rights.
            // instructors are not allowed to change group names, because this would lead to security problems

            if (!Objects.equals(existingCourse.get().getStudentGroupName(), updatedCourse.getStudentGroupName())) {
                throw new BadRequestAlertException("The student group name cannot be changed", ENTITY_NAME, "studentGroupNameCannotChange", true);
            }
            if (!Objects.equals(existingCourse.get().getTeachingAssistantGroupName(), updatedCourse.getTeachingAssistantGroupName())) {
                throw new BadRequestAlertException("The teaching assistant group name cannot be changed", ENTITY_NAME, "teachingAssistantGroupNameCannotChange", true);
            }
            if (!Objects.equals(existingCourse.get().getInstructorGroupName(), updatedCourse.getInstructorGroupName())) {
                throw new BadRequestAlertException("The instructor group name cannot be changed", ENTITY_NAME, "instructorGroupNameCannotChange", true);
            }
        }

        // Check if course shortname matches regex
        Matcher shortNameMatcher = SHORT_NAME_PATTERN.matcher(updatedCourse.getShortName());
        if (!shortNameMatcher.matches()) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, "The shortname is invalid", "shortnameInvalid")).body(null);
        }

        // Based on the old instructors and TAs, we can update all exercises in the course in the VCS (if necessary)
        // We need the old instructors and TAs, so that the VCS user management service can determine which
        // users no longer have TA or instructor rights in the related exercise repositories.
        final var oldInstructorGroup = existingCourse.get().getInstructorGroupName();
        final var oldTeachingAssistantGroup = existingCourse.get().getTeachingAssistantGroupName();
        Course result = courseService.save(updatedCourse);
        vcsUserManagementService.ifPresent(userManagementService -> userManagementService.updateCoursePermissions(result, oldInstructorGroup, oldTeachingAssistantGroup));
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, updatedCourse.getTitle())).body(result);
    }

    private void checkIfGroupsExists(String group) {
        if (!Arrays.asList(env.getActiveProfiles()).contains(JHipsterConstants.SPRING_PROFILE_PRODUCTION)) {
            return;
        }
        // only execute this check in the production environment because normal developers (while testing) might not have the right to call this method on the authentication server
        if (!artemisAuthenticationProvider.isGroupAvailable(group)) {
            throw new ArtemisAuthenticationException("Cannot save! The group " + group + " does not exist. Please double check the group name!");
        }
    }

    private void validateComplaintsConfig(Course course) {
        if (course.getMaxComplaints() == null) {
            // set the default value to prevent null pointer exceptions
            course.setMaxComplaints(3);
        }
        if (course.getMaxComplaintTimeDays() == null) {
            // set the default value to prevent null pointer exceptions
            course.setMaxComplaintTimeDays(7);
        }
        if (course.getMaxComplaints() < 0) {
            throw new BadRequestAlertException("Max Complaints cannot be negative", ENTITY_NAME, "maxComplaintsInvalid", true);
        }
        if (course.getMaxComplaintTimeDays() < 0) {
            throw new BadRequestAlertException("Max Complaint Days cannot be negative", ENTITY_NAME, "maxComplaintDaysInvalid", true);
        }
        if (course.getMaxComplaintTimeDays() == 0 && course.getMaxComplaints() != 0) {
            throw new BadRequestAlertException("Both complaints configs must be 0 (disabled) or must have positive integer values", ENTITY_NAME, "complaintsConfigInvalid", true);
        }
        if (course.getMaxComplaintTimeDays() != 0 && course.getMaxComplaints() == 0) {
            throw new BadRequestAlertException("Both complaints configs must be 0 (disabled) or must have positive integer values", ENTITY_NAME, "complaintsConfigInvalid", true);
        }
    }

    /**
     * POST /courses/{courseId}/register : Register for an existing course. This method registers the current user for the given course id in case the course has already started
     * and not finished yet. The user is added to the course student group in the Authentication System and the course student group is added to the user's groups in the Artemis
     * database.
     *
     * @param courseId to find the course
     * @return response entity for user who has been registered to the course
     */
    @PostMapping("/courses/{courseId}/register")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<User> registerForCourse(@PathVariable Long courseId) {
        Course course = courseService.findOne(courseId);
        User user = userService.getUserWithGroupsAndAuthorities();
        log.debug("REST request to register {} for Course {}", user.getName(), course.getTitle());
        if (course.getStartDate() != null && course.getStartDate().isAfter(now())) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createFailureAlert(applicationName, false, ENTITY_NAME, "courseNotStarted", "The course has not yet started. Cannot register user"))
                    .body(null);
        }
        if (course.getEndDate() != null && course.getEndDate().isBefore(now())) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createFailureAlert(applicationName, false, ENTITY_NAME, "courseAlreadyFinished", "The course has already finished. Cannot register user"))
                    .body(null);
        }
        if (course.isRegistrationEnabled() != Boolean.TRUE) {
            return ResponseEntity.badRequest().headers(
                    HeaderUtil.createFailureAlert(applicationName, false, ENTITY_NAME, "registrationDisabled", "The course does not allow registration. Cannot register user"))
                    .body(null);
        }
        artemisAuthenticationProvider.registerUserForCourse(user, course);
        return ResponseEntity.ok(user);
    }

    /**
     * GET /courses : get all courses for administration purposes.
     *
     * @param onlyActive if true, only active courses will be considered in the result
     * @return the list of courses (the user has access to)
     */
    @GetMapping("/courses")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public List<Course> getAllCourses(@RequestParam(defaultValue = "false") boolean onlyActive) {
        log.debug("REST request to get all Courses the user has access to");
        User user = userService.getUserWithGroupsAndAuthorities();
        List<Course> courses = courseService.findAll();
        Stream<Course> userCourses = courses.stream().filter(course -> user.getGroups().contains(course.getTeachingAssistantGroupName())
                || user.getGroups().contains(course.getInstructorGroupName()) || authCheckService.isAdmin());
        if (onlyActive) {
            // only include courses that have NOT been finished
            userCourses = userCourses.filter(course -> course.getEndDate() == null || course.getEndDate().isAfter(ZonedDateTime.now()));
        }
        return userCourses.collect(Collectors.toList());
    }

    /**
     * GET /courses : get all courses for administration purposes with user stats.
     *
     * @param onlyActive if true, only active courses will be considered in the result
     * @return the list of courses (the user has access to)
     */
    @GetMapping("/courses/with-user-stats")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public List<Course> getAllCoursesWithUserStats(@RequestParam(defaultValue = "false") boolean onlyActive) {
        log.debug("get courses with user stats, only active: " + onlyActive);
        long start = System.currentTimeMillis();
        List<Course> courses = getAllCourses(onlyActive);
        for (Course course : courses) {
            course.setNumberOfInstructors(userService.countUserInGroup(course.getInstructorGroupName()));
            course.setNumberOfTeachingAssistants(userService.countUserInGroup(course.getTeachingAssistantGroupName()));
            course.setNumberOfStudents(userService.countUserInGroup(course.getStudentGroupName()));
        }
        long end = System.currentTimeMillis();
        log.debug("getAllCoursesWithUserStats took " + (end - start) + "ms for " + courses.size() + " courses");
        return courses;
    }

    /**
     * GET /courses : get all courses that the current user can register to. Decided by the start and end date and if the registrationEnabled flag is set correctly
     *
     * @return the list of courses which are active)
     */
    @GetMapping("/courses/to-register")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public List<Course> getAllCoursesToRegister() {
        log.debug("REST request to get all currently active Courses that are not online courses");
        return courseService.findAllCurrentlyActiveAndNotOnlineAndEnabled();
    }

    /**
     * GET /courses/for-dashboard
     *
     * @return the list of courses (the user has access to) including all exercises with participation and result for the user
     */
    @GetMapping("/courses/for-dashboard")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public List<Course> getAllCoursesForDashboard() {
        long start = System.currentTimeMillis();
        log.debug("REST request to get all Courses the user has access to with exercises, participations and results");
        log.debug("/courses/for-dashboard.start");
        User user = userService.getUserWithGroupsAndAuthorities();

        // get all courses with exercises for this user
        List<Course> courses = courseService.findAllActiveWithExercisesAndLecturesForUser(user);
        log.debug("          /courses/for-dashboard.findAllActiveWithExercisesAndLecturesForUser in " + (System.currentTimeMillis() - start) + "ms");

        Map<ExerciseMode, List<Exercise>> activeExercises = courses.stream().flatMap(course -> course.getExercises().stream()).collect(Collectors.groupingBy(Exercise::getMode));
        List<Exercise> activeIndividualExercises = Optional.ofNullable(activeExercises.get(ExerciseMode.INDIVIDUAL)).orElse(List.of());
        List<Exercise> activeTeamExercises = Optional.ofNullable(activeExercises.get(ExerciseMode.TEAM)).orElse(List.of());

        if (activeIndividualExercises.isEmpty() && activeTeamExercises.isEmpty()) {
            return courses;
        }

        // Note: we need two database calls here, because of performance reasons: the entity structure for team is significantly different and a combined database call
        // would lead to a SQL statement that cannot be optimized
        // 1st: fetch participations, submissions and results for individual exercises
        List<StudentParticipation> individualParticipations = participationService.findByStudentIdAndIndividualExercisesWithEagerSubmissionsResult(user.getId(),
                activeIndividualExercises);
        log.debug("          /courses/for-dashboard.findByStudentIdAndIndividualExercisesWithEagerSubmissionsResult in " + (System.currentTimeMillis() - start) + "ms");

        // 2nd: fetch participations, submissions and results for team exercises
        List<StudentParticipation> teamParticipations = participationService.findByStudentIdAndTeamExercisesWithEagerSubmissionsResult(user.getId(), activeTeamExercises);
        log.debug("          /courses/for-dashboard.findByStudentIdAndTeamExercisesWithEagerSubmissionsResult in " + (System.currentTimeMillis() - start) + "ms");

        // 3rd: merge both into one list for further processing
        List<StudentParticipation> participations = Stream.concat(individualParticipations.stream(), teamParticipations.stream()).collect(Collectors.toList());

        for (Course course : courses) {
            boolean isStudent = !authCheckService.isAtLeastTeachingAssistantInCourse(course, user);
            for (Exercise exercise : course.getExercises()) {
                // add participation with submission and result to each exercise
                exercise.filterForCourseDashboard(participations, user.getLogin(), isStudent);
                // remove sensitive information from the exercise for students
                if (isStudent) {
                    exercise.filterSensitiveInformation();
                }
            }
        }
        log.info("/courses/for-dashboard.done in " + (System.currentTimeMillis() - start) + "ms for " + courses.size() + " courses with " + activeIndividualExercises.size()
                + " individual exercises and " + activeTeamExercises.size() + " team exercises for user " + user.getLogin());

        return courses;
    }

    /**
     * GET /courses/:courseId/for-tutor-dashboard
     *
     * @param courseId the id of the course to retrieve
     * @return data about a course including all exercises, plus some data for the tutor as tutor status for assessment
     */
    @GetMapping("/courses/{courseId}/for-tutor-dashboard")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Course> getCourseForTutorDashboard(@PathVariable long courseId) {
        log.debug("REST request /courses/{courseId}/for-tutor-dashboard");
        Course course = courseService.findOneWithExercises(courseId);
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!userHasPermission(course, user)) {
            return forbidden();
        }

        Set<Exercise> interestingExercises = course.getInterestingExercisesForAssessmentDashboards();
        course.setExercises(interestingExercises);

        List<TutorParticipation> tutorParticipations = tutorParticipationService.findAllByCourseAndTutor(course, user);

        for (Exercise exercise : interestingExercises) {
            long numberOfSubmissions;
            if (exercise instanceof ProgrammingExercise) {
                numberOfSubmissions = programmingExerciseService.countSubmissionsByExerciseIdSubmitted(exercise.getId());
            }
            else {
                numberOfSubmissions = submissionService.countSubmissionsForExercise(exercise.getId());
            }
            final long numberOfAssessments = resultService.countNumberOfFinishedAssessmentsForExercise(exercise.getId());

            exerciseService.calculateNrOfOpenComplaints(exercise);

            exercise.setNumberOfParticipations(numberOfSubmissions);
            exercise.setNumberOfAssessments(numberOfAssessments);

            List<ExampleSubmission> exampleSubmissions = this.exampleSubmissionRepository.findAllByExerciseId(exercise.getId());
            // Do not provide example submissions without any assessment
            exampleSubmissions.removeIf(exampleSubmission -> exampleSubmission.getSubmission() == null || exampleSubmission.getSubmission().getResult() == null);
            exercise.setExampleSubmissions(new HashSet<>(exampleSubmissions));

            TutorParticipation tutorParticipation = tutorParticipations.stream().filter(participation -> participation.getAssessedExercise().getId().equals(exercise.getId()))
                    .findFirst().orElseGet(() -> {
                        TutorParticipation emptyTutorParticipation = new TutorParticipation();
                        emptyTutorParticipation.setStatus(TutorParticipationStatus.NOT_PARTICIPATED);
                        return emptyTutorParticipation;
                    });
            exercise.setTutorParticipations(Collections.singleton(tutorParticipation));
        }

        return ResponseUtil.wrapOrNotFound(Optional.of(course));
    }

    /**
     * GET /courses/:courseId/stats-for-tutor-dashboard A collection of useful statistics for the tutor course dashboard, including: - number of submissions to the course - number of
     * assessments - number of assessments assessed by the tutor - number of complaints
     *
     * @param courseId the id of the course to retrieve
     * @return data about a course including all exercises, plus some data for the tutor as tutor status for assessment
     */
    @GetMapping("/courses/{courseId}/stats-for-tutor-dashboard")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<StatsForInstructorDashboardDTO> getStatsForTutorDashboard(@PathVariable long courseId) {
        log.debug("REST request /courses/{courseId}/stats-for-tutor-dashboard");

        Course course = courseService.findOne(courseId);
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!userHasPermission(course, user)) {
            return forbidden();
        }
        StatsForInstructorDashboardDTO stats = new StatsForInstructorDashboardDTO();

        final long numberOfSubmissions = submissionService.countSubmissionsForCourse(courseId) + programmingExerciseService.countSubmissionsByCourseIdSubmitted(courseId);
        stats.setNumberOfSubmissions(numberOfSubmissions);

        final long numberOfAssessments = resultService.countNumberOfAssessments(courseId);
        stats.setNumberOfAssessments(numberOfAssessments);

        final long numberOfMoreFeedbackRequests = complaintService.countMoreFeedbackRequestsByCourseId(courseId);
        stats.setNumberOfMoreFeedbackRequests(numberOfMoreFeedbackRequests);

        final long numberOfComplaints = complaintService.countComplaintsByCourseId(courseId);
        stats.setNumberOfComplaints(numberOfComplaints);

        List<TutorLeaderboardDTO> leaderboardEntries = tutorLeaderboardService.getCourseLeaderboard(course);
        stats.setTutorLeaderboardEntries(leaderboardEntries);

        return ResponseEntity.ok(stats);
    }

    /**
     * GET /courses/:courseId : get the "id" course.
     *
     * @param courseId the id of the course to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the course, or with status 404 (Not Found)
     */
    @GetMapping("/courses/{courseId}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Course> getCourse(@PathVariable Long courseId) {
        log.debug("REST request to get Course : {}", courseId);
        Course course = courseService.findOne(courseId);
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!userHasPermission(course, user)) {
            return forbidden();
        }

        return ResponseUtil.wrapOrNotFound(Optional.ofNullable(course));
    }

    /**
     * GET /courses/:courseId : get the "id" course.
     *
     * @param courseId the id of the course to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the course, or with status 404 (Not Found)
     */
    @GetMapping("/courses/{courseId}/with-exercises")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Course> getCourseWithExercises(@PathVariable Long courseId) {
        log.debug("REST request to get Course : {}", courseId);
        Course course = courseService.findOneWithExercises(courseId);
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!userHasPermission(course, user)) {
            return forbidden();
        }
        return ResponseUtil.wrapOrNotFound(Optional.ofNullable(course));
    }

    /**
     * GET /courses/:courseId/with-exercises-and-relevant-participations Get the "id" course, with text and modelling exercises and their participations It can be used only by
     * instructors for the instructor dashboard
     *
     * @param courseId the id of the course to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the course, or with status 404 (Not Found)
     * @throws AccessForbiddenException if the current user doesn't have the permission to access the course
     */
    @GetMapping("/courses/{courseId}/with-exercises-and-relevant-participations")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Course> getCourseWithExercisesAndRelevantParticipations(@PathVariable Long courseId) throws AccessForbiddenException {
        log.debug("REST request to get Course with exercises and relevant participations : {}", courseId);
        long start = System.currentTimeMillis();
        Course course = courseService.findOneWithExercises(courseId);
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastInstructorInCourse(course, user)) {
            throw new AccessForbiddenException("You are not allowed to access this resource");
        }

        Set<Exercise> interestingExercises = course.getInterestingExercisesForAssessmentDashboards();
        course.setExercises(interestingExercises);

        for (Exercise exercise : interestingExercises) {
            long numberOfSubmissions;
            if (exercise instanceof ProgrammingExercise) {
                numberOfSubmissions = programmingExerciseService.countSubmissionsByExerciseIdSubmitted(exercise.getId());
            }
            else {
                numberOfSubmissions = submissionService.countSubmissionsForExercise(exercise.getId());
            }
            final long numberOfAssessments = resultService.countNumberOfFinishedAssessmentsForExercise(exercise.getId());
            final long numberOfMoreFeedbackRequests = complaintService.countMoreFeedbackRequestsByExerciseId(exercise.getId());
            final long numberOfComplaints = complaintService.countComplaintsByExerciseId(exercise.getId());

            exercise.setNumberOfParticipations(numberOfSubmissions);
            exercise.setNumberOfAssessments(numberOfAssessments);
            exercise.setNumberOfComplaints(numberOfComplaints);
            exercise.setNumberOfMoreFeedbackRequests(numberOfMoreFeedbackRequests);
        }
        long end = System.currentTimeMillis();
        log.info("Finished /courses/" + courseId + "/with-exercises-and-relevant-participations call in " + (end - start) + "ms");
        return ResponseUtil.wrapOrNotFound(Optional.of(course));
    }

    /**
     * GET /courses/:courseId/stats-for-instructor-dashboard
     * <p>
     * A collection of useful statistics for the instructor course dashboard, including: - number of students - number of instructors - number of submissions - number of
     * assessments - number of complaints - number of open complaints - tutor leaderboard data
     *
     * @param courseId the id of the course to retrieve
     * @return data about a course including all exercises, plus some data for the tutor as tutor status for assessment
     * @throws AccessForbiddenException if the current user doesn't have the permission to access the course
     */
    @GetMapping("/courses/{courseId}/stats-for-instructor-dashboard")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<StatsForInstructorDashboardDTO> getStatsForInstructorDashboard(@PathVariable Long courseId) throws AccessForbiddenException {
        log.debug("REST request /courses/{courseId}/stats-for-instructor-dashboard");
        final long start = System.currentTimeMillis();
        final Course course = courseService.findOne(courseId);
        final User user = userService.getUserWithGroupsAndAuthorities();
        if (!userHasPermission(course, user)) {
            throw new AccessForbiddenException("You are not allowed to access this resource");
        }

        StatsForInstructorDashboardDTO stats = new StatsForInstructorDashboardDTO();

        final long numberOfComplaints = complaintRepository.countByResult_Participation_Exercise_Course_IdAndComplaintType(courseId, ComplaintType.COMPLAINT);
        stats.setNumberOfComplaints(numberOfComplaints);
        final long numberOfComplaintResponses = complaintResponseRepository.countByComplaint_Result_Participation_Exercise_Course_Id_AndComplaint_ComplaintType(courseId,
                ComplaintType.COMPLAINT);
        stats.setNumberOfOpenComplaints(numberOfComplaints - numberOfComplaintResponses);

        final long numberOfMoreFeedbackRequests = complaintRepository.countByResult_Participation_Exercise_Course_IdAndComplaintType(courseId, ComplaintType.MORE_FEEDBACK);
        stats.setNumberOfMoreFeedbackRequests(numberOfMoreFeedbackRequests);
        final long numberOfMoreFeedbackComplaintResponses = complaintResponseRepository
                .countByComplaint_Result_Participation_Exercise_Course_Id_AndComplaint_ComplaintType(courseId, ComplaintType.MORE_FEEDBACK);
        stats.setNumberOfOpenMoreFeedbackRequests(numberOfMoreFeedbackRequests - numberOfMoreFeedbackComplaintResponses);

        stats.setNumberOfStudents(courseService.countNumberOfStudentsForCourse(course));

        final long numberOfSubmissions = submissionService.countSubmissionsForCourse(courseId) + programmingExerciseService.countSubmissionsByCourseIdSubmitted(courseId);

        stats.setNumberOfSubmissions(numberOfSubmissions);
        stats.setNumberOfAssessments(resultService.countNumberOfAssessments(courseId));

        final long startT = System.currentTimeMillis();
        List<TutorLeaderboardDTO> leaderboardEntries = tutorLeaderboardService.getCourseLeaderboard(course);
        stats.setTutorLeaderboardEntries(leaderboardEntries);

        log.info("Finished TutorLeaderboard in " + (System.currentTimeMillis() - startT) + "ms");

        log.info("Finished /courses/" + courseId + "/stats-for-instructor-dashboard call in " + (System.currentTimeMillis() - start) + "ms");
        return ResponseEntity.ok(stats);
    }

    private boolean userHasPermission(Course course, User user) {
        return authCheckService.isTeachingAssistantInCourse(course, user) || authCheckService.isInstructorInCourse(course, user) || authCheckService.isAdmin();
    }

    /**
     * DELETE /courses/:courseId : delete the "id" course.
     *
     * @param courseId the id of the course to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/courses/{courseId}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Void> deleteCourse(@PathVariable long courseId) {
        log.info("REST request to delete Course : {}", courseId);
        Course course = courseService.findOneWithExercisesAndLectures(courseId);
        User user = userService.getUserWithGroupsAndAuthorities();
        if (course == null) {
            return notFound();
        }
        for (Exercise exercise : course.getExercises()) {
            exerciseService.delete(exercise.getId(), false, false);
        }

        var auditEvent = new AuditEvent(user.getLogin(), Constants.DELETE_COURSE, "course=" + course.getTitle());
        auditEventRepository.add(auditEvent);
        log.info("User " + user.getLogin() + " has requested to delete the course {}", course.getTitle());

        for (Lecture lecture : course.getLectures()) {
            lectureService.delete(lecture);
        }

        List<GroupNotification> notifications = notificationService.findAllNotificationsForCourse(course);
        for (GroupNotification notification : notifications) {
            notificationService.deleteNotification(notification);
        }
        String title = course.getTitle();

        // only delete (default) groups which have been created by Artemis before
        if (course.getStudentGroupName().equals(course.getDefaultStudentGroupName())) {
            artemisAuthenticationProvider.deleteGroup(course.getStudentGroupName());
        }
        if (course.getTeachingAssistantGroupName().equals(course.getDefaultTeachingAssistantGroupName())) {
            artemisAuthenticationProvider.deleteGroup(course.getTeachingAssistantGroupName());
        }
        if (course.getInstructorGroupName().equals(course.getDefaultInstructorGroupName())) {
            artemisAuthenticationProvider.deleteGroup(course.getInstructorGroupName());
        }

        courseService.delete(courseId);

        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, title)).build();
    }

    /**
     * GET /courses/:courseId/categories : Returns all categories used in a course
     *
     * @param courseId the id of the course to get the categories from
     * @return the ResponseEntity with status 200 (OK) and the list of categories or with status 404 (Not Found)
     */
    @GetMapping(value = "/courses/{courseId}/categories")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Set<String>> getCategoriesInCourse(@PathVariable Long courseId) {
        log.debug("REST request to get categories of Course : {}", courseId);

        User user = userService.getUserWithGroupsAndAuthorities();
        Course course = courseService.findOne(courseId);
        if (authCheckService.isAdmin() || authCheckService.isInstructorInCourse(course, user)) {
            Set<String> categories = exerciseService.findAllExerciseCategoriesForCourse(course);
            return ResponseEntity.ok().body(categories);
        }
        else {
            return forbidden();
        }
    }

    /**
     * GET /courses/:courseId/students : Returns all users that belong to the student group of the course
     *
     * @param courseId the id of the course
     * @return list of users with status 200 (OK)
     */
    @GetMapping(value = "/courses/{courseId}/students")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<User>> getAllStudentsInCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all students in course : {}", courseId);
        Course course = courseService.findOne(courseId);
        return getAllUsersInGroup(course, course.getStudentGroupName());
    }

    /**
     * GET /courses/:courseId/tutors : Returns all users that belong to the tutor group of the course
     *
     * @param courseId the id of the course
     * @return list of users with status 200 (OK)
     */
    @GetMapping(value = "/courses/{courseId}/tutors")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<User>> getAllTutorsInCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all tutors in course : {}", courseId);
        Course course = courseService.findOne(courseId);
        return getAllUsersInGroup(course, course.getTeachingAssistantGroupName());
    }

    /**
     * GET /courses/:courseId/instructors : Returns all users that belong to the instructor group of the course
     *
     * @param courseId the id of the course
     * @return list of users with status 200 (OK)
     */
    @GetMapping(value = "/courses/{courseId}/instructors")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<User>> getAllInstructorsInCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all instructors in course : {}", courseId);
        Course course = courseService.findOne(courseId);
        return getAllUsersInGroup(course, course.getInstructorGroupName());
    }

    /**
     * Returns all users in a course that belong to the given group
     *
     * @param course    the course
     * @param groupName the name of the group
     * @return list of users
     */
    @NotNull
    public ResponseEntity<List<User>> getAllUsersInGroup(Course course, @PathVariable String groupName) {
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastInstructorInCourse(course, user)) {
            return forbidden();
        }
        return ResponseEntity.ok().body(userService.findAllUsersInGroup(groupName));
    }

    /**
     * Post /courses/:courseId/students/:studentLogin : Add the given user to the students of the course so that the student can access the course
     *
     * @param courseId     the id of the course
     * @param studentLogin the login of the user who should get student access
     * @return empty ResponseEntity with status 200 (OK) or with status 404 (Not Found)
     */
    @PostMapping(value = "/courses/{courseId}/students/{studentLogin:" + Constants.LOGIN_REGEX + "}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> addStudentToCourse(@PathVariable Long courseId, @PathVariable String studentLogin) {
        log.debug("REST request to add {} as student to course : {}", studentLogin, courseId);
        var course = courseService.findOne(courseId);
        return addUserToCourseGroup(studentLogin, userService.getUserWithGroupsAndAuthorities(), course, course.getStudentGroupName());
    }

    /**
     * Post /courses/:courseId/tutors/:tutorLogin : Add the given user to the tutors of the course so that the student can access the course administration
     *
     * @param courseId   the id of the course
     * @param tutorLogin the login of the user who should get tutor access
     * @return empty ResponseEntity with status 200 (OK) or with status 404 (Not Found)
     */
    @PostMapping(value = "/courses/{courseId}/tutors/{tutorLogin:" + Constants.LOGIN_REGEX + "}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> addTutorToCourse(@PathVariable Long courseId, @PathVariable String tutorLogin) {
        log.debug("REST request to add {} as tutors to course : {}", tutorLogin, courseId);
        var course = courseService.findOne(courseId);
        return addUserToCourseGroup(tutorLogin, userService.getUserWithGroupsAndAuthorities(), course, course.getTeachingAssistantGroupName());
    }

    /**
     * Post /courses/:courseId/instructors/:instructorLogin : Add the given user to the instructors of the course so that the student can access the course administration
     *
     * @param courseId        the id of the course
     * @param instructorLogin the login of the user who should get instructors access
     * @return empty ResponseEntity with status 200 (OK) or with status 404 (Not Found)
     */
    @PostMapping(value = "/courses/{courseId}/instructors/{instructorLogin:" + Constants.LOGIN_REGEX + "}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> addInstructorToCourse(@PathVariable Long courseId, @PathVariable String instructorLogin) {
        log.debug("REST request to add {} as instructors to course : {}", instructorLogin, courseId);
        var course = courseService.findOne(courseId);
        return addUserToCourseGroup(instructorLogin, userService.getUserWithGroupsAndAuthorities(), course, course.getInstructorGroupName());
    }

    /**
     * adds the userLogin to the group (student, tutors or instructors) of the given course
     *
     * @param userLogin         the user login of the student, tutor or instructor who should be added to the group
     * @param instructorOrAdmin the user who initiates this request who must be an instructor of the given course or an admin
     * @param course            the course which is only passes to check if the instructorOrAdmin is an instructor of the course
     * @param group             the group to which the userLogin should be added
     * @return empty ResponseEntity with status 200 (OK) or with status 404 (Not Found) or with status 403 (Forbidden)
     */
    @NotNull
    public ResponseEntity<Void> addUserToCourseGroup(String userLogin, User instructorOrAdmin, Course course, String group) {
        if (authCheckService.isAdmin() || authCheckService.isInstructorInCourse(course, instructorOrAdmin)) {
            Optional<User> userToAddToGroup = userService.getUserWithGroupsAndAuthoritiesByLogin(userLogin);
            if (userToAddToGroup.isEmpty()) {
                return notFound();
            }
            userService.addUserToGroup(userToAddToGroup.get(), group);
            return ResponseEntity.ok().body(null);
        }
        else {
            return forbidden();
        }
    }

    /**
     * DELETE /courses/:courseId/students/:studentLogin : Remove the given user from the students of the course so that the student cannot access the course any more
     *
     * @param courseId     the id of the course
     * @param studentLogin the login of the user who should lose student access
     * @return empty ResponseEntity with status 200 (OK) or with status 404 (Not Found)
     */
    @DeleteMapping(value = "/courses/{courseId}/students/{studentLogin:" + Constants.LOGIN_REGEX + "}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> removeStudentFromCourse(@PathVariable Long courseId, @PathVariable String studentLogin) {
        log.debug("REST request to remove {} as student from course : {}", studentLogin, courseId);
        var course = courseService.findOne(courseId);
        return removeUserFromCourseGroup(studentLogin, userService.getUserWithGroupsAndAuthorities(), course, course.getStudentGroupName());
    }

    /**
     * DELETE /courses/:courseId/tutors/:tutorsLogin : Remove the given user from the tutors of the course so that the tutors cannot access the course administration any more
     *
     * @param courseId   the id of the course
     * @param tutorLogin the login of the user who should lose student access
     * @return empty ResponseEntity with status 200 (OK) or with status 404 (Not Found)
     */
    @DeleteMapping(value = "/courses/{courseId}/tutors/{tutorLogin:" + Constants.LOGIN_REGEX + "}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> removeTutorFromCourse(@PathVariable Long courseId, @PathVariable String tutorLogin) {
        log.debug("REST request to remove {} as tutor from course : {}", tutorLogin, courseId);
        var course = courseService.findOne(courseId);
        return removeUserFromCourseGroup(tutorLogin, userService.getUserWithGroupsAndAuthorities(), course, course.getTeachingAssistantGroupName());
    }

    /**
     * DELETE /courses/:courseId/instructors/:instructorLogin : Remove the given user from the instructors of the course so that the instructor cannot access the course administration any more
     *
     * @param courseId        the id of the course
     * @param instructorLogin the login of the user who should lose student access
     * @return empty ResponseEntity with status 200 (OK) or with status 404 (Not Found)
     */
    @DeleteMapping(value = "/courses/{courseId}/instructors/{instructorLogin:" + Constants.LOGIN_REGEX + "}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> removeInstructorFromCourse(@PathVariable Long courseId, @PathVariable String instructorLogin) {
        log.debug("REST request to remove {} as instructor from course : {}", instructorLogin, courseId);
        var course = courseService.findOne(courseId);
        return removeUserFromCourseGroup(instructorLogin, userService.getUserWithGroupsAndAuthorities(), course, course.getInstructorGroupName());
    }

    /**
     * removes the userLogin from the group (student, tutors or instructors) of the given course
     *
     * @param userLogin         the user login of the student, tutor or instructor who should be removed from the group
     * @param instructorOrAdmin the user who initiates this request who must be an instructor of the given course or an admin
     * @param course            the course which is only passes to check if the instructorOrAdmin is an instructor of the course
     * @param group             the group from which the userLogin should be removed
     * @return empty ResponseEntity with status 200 (OK) or with status 404 (Not Found) or with status 403 (Forbidden)
     */
    @NotNull
    public ResponseEntity<Void> removeUserFromCourseGroup(String userLogin, User instructorOrAdmin, Course course, String group) {
        if (authCheckService.isAdmin() || authCheckService.isInstructorInCourse(course, instructorOrAdmin)) {
            Optional<User> userToRemoveFromGroup = userService.getUserWithGroupsAndAuthoritiesByLogin(userLogin);
            if (userToRemoveFromGroup.isEmpty()) {
                return notFound();
            }
            userService.removeUserFromGroup(userToRemoveFromGroup.get(), group);
            return ResponseEntity.ok().body(null);
        }
        else {
            return forbidden();
        }
    }
}
