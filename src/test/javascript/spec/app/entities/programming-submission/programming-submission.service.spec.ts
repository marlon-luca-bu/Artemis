/* tslint:disable max-line-length */
import { getTestBed, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { map, take } from 'rxjs/operators';
import { ProgrammingSubmissionService } from 'app/entities/programming-submission/programming-submission.service';
import { IProgrammingSubmission, ProgrammingSubmission } from 'app/shared/model/programming-submission.model';

describe('Service Tests', () => {
    describe('ProgrammingSubmission Service', () => {
        let injector: TestBed;
        let service: ProgrammingSubmissionService;
        let httpMock: HttpTestingController;
        let elemDefault: IProgrammingSubmission;
        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [HttpClientTestingModule],
            });
            injector = getTestBed();
            service = injector.get(ProgrammingSubmissionService);
            httpMock = injector.get(HttpTestingController);

            elemDefault = new ProgrammingSubmission(0, 'AAAAAAA');
        });

        describe('Service methods', async () => {
            it('should find an element', async () => {
                const returnedFromService = Object.assign({}, elemDefault);
                service
                    .find(123)
                    .pipe(take(1))
                    .subscribe((resp) => expect(resp).toMatchObject({ body: elemDefault }));

                const req = httpMock.expectOne({ method: 'GET' });
                req.flush(JSON.stringify(returnedFromService));
            });

            it('should create a ProgrammingSubmission', async () => {
                const returnedFromService = Object.assign(
                    {
                        id: 0,
                    },
                    elemDefault,
                );
                const expected = Object.assign({}, returnedFromService);
                service
                    .create(new ProgrammingSubmission(null))
                    .pipe(take(1))
                    .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));
                const req = httpMock.expectOne({ method: 'POST' });
                req.flush(JSON.stringify(returnedFromService));
            });

            it('should update a ProgrammingSubmission', async () => {
                const returnedFromService = Object.assign(
                    {
                        commitHash: 'BBBBBB',
                    },
                    elemDefault,
                );

                const expected = Object.assign({}, returnedFromService);
                service
                    .update(expected)
                    .pipe(take(1))
                    .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));
                const req = httpMock.expectOne({ method: 'PUT' });
                req.flush(JSON.stringify(returnedFromService));
            });

            it('should return a list of ProgrammingSubmission', async () => {
                const returnedFromService = Object.assign(
                    {
                        commitHash: 'BBBBBB',
                    },
                    elemDefault,
                );
                const expected = Object.assign({}, returnedFromService);
                service
                    .query(expected)
                    .pipe(
                        take(1),
                        map((resp) => resp.body),
                    )
                    .subscribe((body) => expect(body).toContainEqual(expected));
                const req = httpMock.expectOne({ method: 'GET' });
                req.flush(JSON.stringify([returnedFromService]));
                httpMock.verify();
            });

            it('should delete a ProgrammingSubmission', async () => {
                service.delete(123).subscribe((resp) => expect(resp.ok));

                const req = httpMock.expectOne({ method: 'DELETE' });
                req.flush({ status: 200 });
            });
        });

        afterEach(() => {
            httpMock.verify();
        });
    });
});
