import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';

import { ArtemisTestModule } from '../../test.module';
import { CourseUpdateComponent } from 'app/course/manage/course-update.component';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';

describe('Component Tests', () => {
    describe('Course Management Update Component', () => {
        let comp: CourseUpdateComponent;
        let fixture: ComponentFixture<CourseUpdateComponent>;
        let service: CourseManagementService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArtemisTestModule],
                declarations: [CourseUpdateComponent],
            })
                .overrideTemplate(CourseUpdateComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(CourseUpdateComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(CourseManagementService);
        });

        describe('save', () => {
            it('Should call update service on save for existing entity', fakeAsync(() => {
                // GIVEN
                const entity = new Course();
                entity.id = 123;
                spyOn(service, 'update').and.returnValue(of(new HttpResponse({ body: entity })));
                comp.course = entity;
                comp.courseForm = { value: entity }; // mocking reactive form
                // WHEN
                comp.save();
                tick(); // simulate async

                // THEN
                expect(service.update).toHaveBeenCalledWith(entity);
                expect(comp.isSaving).toEqual(false);
            }));

            it('Should call create service on save for new entity', fakeAsync(() => {
                // GIVEN
                const entity = new Course();
                spyOn(service, 'create').and.returnValue(of(new HttpResponse({ body: entity })));
                comp.course = entity;
                comp.courseForm = { value: entity }; // mocking reactive form
                // WHEN
                comp.save();
                tick(); // simulate async

                // THEN
                expect(service.create).toHaveBeenCalledWith(entity);
                expect(comp.isSaving).toEqual(false);
            }));
        });
    });
});
