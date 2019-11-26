import { JhiAlertService } from 'ng-jhipster';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs/Subscription';
import { ActivatedRoute } from '@angular/router';
import { DifferencePipe } from 'ngx-moment';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { HttpResponse } from '@angular/common/http';
import { Moment } from 'moment';
import { areManualResultsAllowed, Exercise, ExerciseService, ExerciseType } from 'app/entities/exercise';
import { Course, CourseService } from 'app/entities/course';
import { Result, ResultService } from 'app/entities/result';
import { SourceTreeService } from 'app/components/util/sourceTree.service';
import { ModelingAssessmentService } from 'app/entities/modeling-assessment';
import { ParticipationService, ProgrammingExerciseStudentParticipation, StudentParticipation } from 'app/entities/participation';
import { ProgrammingSubmissionService } from 'app/programming-submission';
import { take, tap } from 'rxjs/operators';
import { of, zip } from 'rxjs';
import { AssessmentType } from 'app/entities/assessment-type';

enum FilterProp {
    ALL = 'all',
    SUCCESSFUL = 'successful',
    UNSUCCESSFUL = 'unsuccessful',
    MANUAL = 'manual',
    AUTOMATIC = 'automatic',
}

@Component({
    selector: 'jhi-exercise-scores',
    templateUrl: './exercise-scores.component.html',
    providers: [JhiAlertService, ModelingAssessmentService, SourceTreeService],
})
export class ExerciseScoresComponent implements OnInit, OnDestroy {
    // make constants available to html for comparison
    FilterProp = FilterProp;

    readonly QUIZ = ExerciseType.QUIZ;
    readonly PROGRAMMING = ExerciseType.PROGRAMMING;
    readonly MODELING = ExerciseType.MODELING;

    course: Course;
    exercise: Exercise;
    paramSub: Subscription;
    reverse: boolean;
    results: Result[];
    eventSubscriber: Subscription;
    newManualResultAllowed: boolean;

    resultCriteria: {
        filterProp: FilterProp;
    };

    isLoading: boolean;

    constructor(
        private route: ActivatedRoute,
        private momentDiff: DifferencePipe,
        private courseService: CourseService,
        private exerciseService: ExerciseService,
        private resultService: ResultService,
        private modelingAssessmentService: ModelingAssessmentService,
        private participationService: ParticipationService,
        private programmingSubmissionService: ProgrammingSubmissionService,
        private sourceTreeService: SourceTreeService,
        private modalService: NgbModal,
    ) {
        this.resultCriteria = {
            filterProp: FilterProp.ALL,
        };
        this.results = [];
    }

    ngOnInit() {
        this.paramSub = this.route.params.subscribe(params => {
            this.isLoading = true;
            this.courseService.find(params['courseId']).subscribe((res: HttpResponse<Course>) => {
                this.course = res.body!;
            });
            this.exerciseService.find(params['exerciseId']).subscribe((res: HttpResponse<Exercise>) => {
                this.exercise = res.body!;
                // After both calls are done, the loading flag is removed. If the exercise is not a programming exercise, only the result call is needed.
                zip(this.getResults(), this.loadAndCacheProgrammingExerciseSubmissionState())
                    .pipe(take(1))
                    .subscribe(() => (this.isLoading = false));
                this.newManualResultAllowed = areManualResultsAllowed(this.exercise);
            });
        });
    }

    /**
     * We need to preload the pending submissions here, otherwise every updating-result would trigger a single REST call.
     * Will return immediately if the exercise is not of type PROGRAMMING.
     */
    private loadAndCacheProgrammingExerciseSubmissionState() {
        return this.exercise.type === ExerciseType.PROGRAMMING ? this.programmingSubmissionService.getSubmissionStateOfExercise(this.exercise.id) : of(null);
    }

    getResults() {
        return this.resultService
            .getResultsForExercise(this.exercise.course!.id, this.exercise.id, {
                ratedOnly: true,
                withSubmissions: this.exercise.type === ExerciseType.MODELING,
                withAssessors: this.exercise.type === ExerciseType.MODELING,
            })
            .pipe(
                tap((res: HttpResponse<Result[]>) => {
                    const tempResults: Result[] = res.body!;
                    tempResults.forEach(result => {
                        result.participation!.results = [result];
                        (result.participation! as StudentParticipation).exercise = this.exercise;
                        result.durationInMinutes = this.durationInMinutes(
                            result.completionDate!,
                            result.participation!.initializationDate ? result.participation!.initializationDate : this.exercise.releaseDate!,
                        );
                    });
                    this.results = tempResults;
                    // Nest submission into participation so that it is available for the result component
                    this.results = this.results.map(result => {
                        if (result.participation && result.submission) {
                            result.participation.submissions = [result.submission];
                        }
                        return result;
                    });
                }),
            );
    }

    updateResultFilter(newValue: FilterProp) {
        this.resultCriteria.filterProp = newValue;
    }

    filterResultByProp = (result: Result) => {
        switch (this.resultCriteria.filterProp) {
            case FilterProp.SUCCESSFUL:
                return result.successful;
            case FilterProp.UNSUCCESSFUL:
                return !result.successful;
            case FilterProp.MANUAL:
                return result.assessmentType === AssessmentType.MANUAL;
            case FilterProp.AUTOMATIC:
                return result.assessmentType === AssessmentType.AUTOMATIC;
            default:
                return true;
        }
    };

    durationInMinutes(completionDate: Moment, initializationDate: Moment) {
        return this.momentDiff.transform(completionDate, initializationDate, 'minutes');
    }

    goToBuildPlan(result: Result) {
        // TODO: get the continuous integration URL as a client constant during the management info call
        window.open('https://bamboobruegge.in.tum.de/browse/' + (result.participation! as ProgrammingExerciseStudentParticipation).buildPlanId);
    }

    goToRepository(result: Result) {
        window.open((result.participation! as ProgrammingExerciseStudentParticipation).repositoryUrl);
    }

    exportNames() {
        if (this.results.length > 0) {
            const rows: string[] = [];
            this.results.forEach((result, index) => {
                const studentParticipation = result.participation! as StudentParticipation;
                let studentName = studentParticipation.student.firstName!;
                if (studentParticipation.student.lastName != null && studentParticipation.student.lastName !== '') {
                    studentName = studentName + ' ' + studentParticipation.student.lastName;
                }
                rows.push(index === 0 ? 'data:text/csv;charset=utf-8,' + studentName : studentName);
            });
            const csvContent = rows.join('\n');
            const encodedUri = encodeURI(csvContent);
            const link = document.createElement('a');
            link.setAttribute('href', encodedUri);
            link.setAttribute('download', 'results-names.csv');
            document.body.appendChild(link); // Required for FF
            link.click();
        }
    }

    exportResults() {
        if (this.results.length > 0) {
            const rows: string[] = [];
            this.results.forEach((result, index) => {
                const studentParticipation = result.participation! as StudentParticipation;
                let studentName = studentParticipation.student.firstName!;
                if (studentParticipation.student.lastName != null && studentParticipation.student.lastName !== '') {
                    studentName = studentName + ' ' + studentParticipation.student.lastName;
                }
                const studentId = studentParticipation.student.login;
                const score = result.score;

                if (index === 0) {
                    if (this.exercise.type !== ExerciseType.PROGRAMMING) {
                        rows.push('data:text/csv;charset=utf-8,Name, Username, Score');
                    } else {
                        rows.push('data:text/csv;charset=utf-8,Name, Username, Score, Repo Link');
                    }
                }
                if (this.exercise.type !== ExerciseType.PROGRAMMING) {
                    rows.push(studentName + ', ' + studentId + ', ' + score);
                } else {
                    const repoLink = (studentParticipation as ProgrammingExerciseStudentParticipation).repositoryUrl;
                    rows.push(studentName + ', ' + studentId + ', ' + score + ', ' + repoLink);
                }
            });
            const csvContent = rows.join('\n');
            const encodedUri = encodeURI(csvContent);
            const link = document.createElement('a');
            link.setAttribute('href', encodedUri);
            link.setAttribute('download', 'results-scores.csv');
            document.body.appendChild(link); // Required for FF
            link.click();
        }
    }

    /**
     * Formats the results in the autocomplete overlay.
     *
     * @param result
     */
    searchResultFormatter = (result: Result) => {
        const login = (result.participation as StudentParticipation).student.login;
        const name = (result.participation as StudentParticipation).student.name;
        return `${login} (${name})`;
    };

    /**
     * Converts a result object to a string that can be searched for. This is
     * used by the autocomplete select inside the data table.
     *
     * @param result
     */
    searchTextFromResult = (result: Result): string => {
        return (result.participation as StudentParticipation).student.login || '';
    };

    refresh() {
        this.isLoading = true;
        this.results = [];
        this.getResults().subscribe(() => (this.isLoading = false));
    }

    ngOnDestroy() {
        this.paramSub.unsubscribe();
    }

    callback() {}
}
