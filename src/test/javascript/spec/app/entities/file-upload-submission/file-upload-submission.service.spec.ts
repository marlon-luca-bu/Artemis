/* tslint:disable max-line-length */
import { getTestBed, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { map, take } from 'rxjs/operators';
import { FileUploadSubmissionService } from 'app/exercises/file-upload/participate/file-upload-submission.service';
import { FileUploadSubmission, IFileUploadSubmission } from 'app/shared/model/file-upload-submission.model';

describe('Service Tests', () => {
    describe('FileUploadSubmission Service', () => {
        let injector: TestBed;
        let service: FileUploadSubmissionService;
        let httpMock: HttpTestingController;
        let elemDefault: IFileUploadSubmission;
        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [HttpClientTestingModule],
            });
            injector = getTestBed();
            service = injector.get(FileUploadSubmissionService);
            httpMock = injector.get(HttpTestingController);

            elemDefault = new FileUploadSubmission(0, 'AAAAAAA');
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

            it('should create a FileUploadSubmission', async () => {
                const returnedFromService = Object.assign(
                    {
                        id: 0,
                    },
                    elemDefault,
                );
                const expected = Object.assign({}, returnedFromService);
                service
                    .create(new FileUploadSubmission(null))
                    .pipe(take(1))
                    .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));
                const req = httpMock.expectOne({ method: 'POST' });
                req.flush(JSON.stringify(returnedFromService));
            });

            it('should update a FileUploadSubmission', async () => {
                const returnedFromService = Object.assign(
                    {
                        filePath: 'BBBBBB',
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

            it('should return a list of FileUploadSubmission', async () => {
                const returnedFromService = Object.assign(
                    {
                        filePath: 'BBBBBB',
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

            it('should delete a FileUploadSubmission', async () => {
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
