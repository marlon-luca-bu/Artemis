import { ComponentFixture, TestBed } from '@angular/core/testing';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ArtemisTestModule } from '../../test.module';
import { MockComponent } from 'ng-mocks';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { RouterModule } from '@angular/router';
import { SidePanelComponent } from 'app/shared/side-panel/side-panel.component';
import { TutorLeaderboardComponent } from 'app/shared/dashboards/tutor-leaderboard/tutor-leaderboard.component';
import { StatsForDashboard } from 'app/course/dashboards/instructor-course-dashboard/stats-for-dashboard.model';
import { ChartsModule } from 'ng2-charts';
import { TranslateModule } from '@ngx-translate/core';
import { HeaderExercisePageWithDetailsComponent } from 'app/exercises/shared/exercise-headers/header-exercise-page-with-details.component';
import { InstructorExerciseDashboardComponent } from 'app/exercises/shared/dashboards/instructor/instructor-exercise-dashboard.component';
import { HeaderParticipationPageComponent } from 'app/exercises/shared/exercise-headers/header-participation-page.component';

chai.use(sinonChai);
const expect = chai.expect;

describe('InstructorExerciseDashboardComponent', () => {
    let comp: InstructorExerciseDashboardComponent;
    let fixture: ComponentFixture<InstructorExerciseDashboardComponent>;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ArtemisSharedModule, RouterModule, ChartsModule, TranslateModule.forRoot()],
            declarations: [
                InstructorExerciseDashboardComponent,
                MockComponent(HeaderExercisePageWithDetailsComponent),
                MockComponent(HeaderParticipationPageComponent),
                MockComponent(SidePanelComponent),
                MockComponent(TutorLeaderboardComponent),
            ],
            providers: [],
        });
        fixture = TestBed.createComponent(InstructorExerciseDashboardComponent);
        comp = fixture.componentInstance;
    });

    it('Statistics are calculated correctly', () => {
        const stats = new StatsForDashboard();
        stats.numberOfSubmissions = 420;
        stats.numberOfAssessments = 333;
        stats.numberOfAutomaticAssistedAssessments = 42;
        comp.stats = stats;
        comp.setStatistics();
        expect(comp.totalManualAssessmentPercentage).equal(69);
        expect(comp.totalAutomaticAssessmentPercentage).equal(10);
        expect(comp.dataForAssessmentPieChart[0]).equal(87);
        expect(comp.dataForAssessmentPieChart[1]).equal(291);
        expect(comp.dataForAssessmentPieChart[2]).equal(42);
    });
});
