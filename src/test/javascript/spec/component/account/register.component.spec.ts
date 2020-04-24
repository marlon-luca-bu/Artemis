import { async, ComponentFixture, fakeAsync, inject, TestBed, tick } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { JhiLanguageService } from 'ng-jhipster';
import { MockLanguageService } from '../../helpers/mocks/service/mock-language.service';
import { ArtemisTestModule } from '../../test.module';
import { EMAIL_ALREADY_USED_TYPE, LOGIN_ALREADY_USED_TYPE } from 'app/shared/constants/error.constants';
import { RegisterService } from 'app/account/register/register.service';
import { RegisterComponent } from 'app/account/register/register.component';

describe('RegisterComponent', () => {
    let fixture: ComponentFixture<RegisterComponent>;
    let comp: RegisterComponent;

    beforeEach(async(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [RegisterComponent],
        })
            .overrideTemplate(RegisterComponent, '')
            .compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(RegisterComponent);
        comp = fixture.componentInstance;
        comp.ngOnInit();
    });

    it('should ensure the two passwords entered match', () => {
        comp.registerAccount.password = 'password';
        comp.confirmPassword = 'non-matching';

        comp.register();

        expect(comp.doNotMatch).toEqual('ERROR');
    });

    it('should update success to OK after creating an account', inject(
        [RegisterService, JhiLanguageService],
        fakeAsync((service: RegisterService, mockTranslate: MockLanguageService) => {
            spyOn(service, 'save').and.returnValue(of({}));
            comp.registerAccount.password = comp.confirmPassword = 'password';

            comp.register();
            tick();

            expect(service.save).toHaveBeenCalledWith({
                password: 'password',
                langKey: 'en',
            });
            expect(comp.success).toEqual(true);
            expect(comp.registerAccount.langKey).toEqual('en');
            expect(mockTranslate.getCurrentSpy).toHaveBeenCalled();
            expect(comp.errorUserExists).toBeNull();
            expect(comp.errorEmailExists).toBeNull();
            expect(comp.error).toBeNull();
        }),
    ));

    it('should notify of user existence upon 400/login already in use', inject(
        [RegisterService],
        fakeAsync((service: RegisterService) => {
            spyOn(service, 'save').and.returnValue(
                throwError({
                    status: 400,
                    error: { type: LOGIN_ALREADY_USED_TYPE },
                }),
            );
            comp.registerAccount.password = comp.confirmPassword = 'password';

            comp.register();
            tick();

            expect(comp.errorUserExists).toEqual('ERROR');
            expect(comp.errorEmailExists).toBeNull();
            expect(comp.error).toBeNull();
        }),
    ));

    it('should notify of email existence upon 400/email address already in use', inject(
        [RegisterService],
        fakeAsync((service: RegisterService) => {
            spyOn(service, 'save').and.returnValue(
                throwError({
                    status: 400,
                    error: { type: EMAIL_ALREADY_USED_TYPE },
                }),
            );
            comp.registerAccount.password = comp.confirmPassword = 'password';

            comp.register();
            tick();

            expect(comp.errorEmailExists).toEqual('ERROR');
            expect(comp.errorUserExists).toBeNull();
            expect(comp.error).toBeNull();
        }),
    ));

    it('should notify of generic error', inject(
        [RegisterService],
        fakeAsync((service: RegisterService) => {
            spyOn(service, 'save').and.returnValue(
                throwError({
                    status: 503,
                }),
            );
            comp.registerAccount.password = comp.confirmPassword = 'password';

            comp.register();
            tick();

            expect(comp.errorUserExists).toBeNull();
            expect(comp.errorEmailExists).toBeNull();
            expect(comp.error).toEqual('ERROR');
        }),
    ));
});