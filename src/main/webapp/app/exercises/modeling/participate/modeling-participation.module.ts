import { ModelingSubmissionComponent } from './modeling-submission.component';
import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { MomentModule } from 'ngx-moment';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ArtemisComplaintsModule } from 'app/complaints/complaints.module';
import { ArtemisModelingEditorModule } from 'app/exercises/modeling/shared/modeling-editor.module';
import { ModelingAssessmentModule } from 'app/exercises/modeling/assess/modeling-assessment.module';
import { ArtemisModelingParticipationRoutingModule } from 'app/exercises/modeling/participate/modeling-participation.route';
import { ArtemisTeamModule } from 'app/exercises/shared/team/team.module';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercises/shared/exercise-headers/exercise-headers.module';

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisResultModule,
        ArtemisModelingEditorModule,
        ArtemisComplaintsModule,
        ArtemisSharedComponentModule,
        ArtemisModelingParticipationRoutingModule,
        ModelingAssessmentModule,
        MomentModule,
        ArtemisTeamModule,
        ArtemisHeaderExercisePageWithDetailsModule,
    ],
    declarations: [ModelingSubmissionComponent],
})
export class ArtemisModelingParticipationModule {}
