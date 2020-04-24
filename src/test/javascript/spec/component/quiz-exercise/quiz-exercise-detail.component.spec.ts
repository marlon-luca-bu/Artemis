import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { ArtemisTestModule } from '../../test.module';
import { QuizExerciseDetailComponent } from 'app/exercises/quiz/manage/quiz-exercise-detail.component';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';

describe('Component Tests', () => {
    describe('QuizExercise Management Detail Component', () => {
        let comp: QuizExerciseDetailComponent;
        let fixture: ComponentFixture<QuizExerciseDetailComponent>;
        const route = ({ data: of({ quizExercise: new QuizExercise(123) }) } as any) as ActivatedRoute;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArtemisTestModule],
                declarations: [QuizExerciseDetailComponent],
                providers: [{ provide: ActivatedRoute, useValue: route }],
            })
                .overrideTemplate(QuizExerciseDetailComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(QuizExerciseDetailComponent);
            comp = fixture.componentInstance;
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(comp.quizExercise).toEqual(jasmine.objectContaining({ id: 123 }));
            });
        });
    });
});
