import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { User } from 'app/core/user/user.model';
import * as moment from 'moment';
import { HttpResponse } from '@angular/common/http';
import { StudentQuestion } from 'app/entities/student-question.model';
import { StudentQuestionAnswer } from 'app/entities/student-question-answer.model';
import { StudentQuestionService } from 'app/overview/student-questions/student-question.service';
import { StudentQuestionAnswerService } from 'app/overview/student-questions/student-question-answer.service';
import { EditorMode } from 'app/shared/markdown-editor/markdown-editor.component';

export interface StudentQuestionAction {
    name: QuestionActionName;
    studentQuestion: StudentQuestion;
}

export enum QuestionActionName {
    DELETE,
}

@Component({
    selector: 'jhi-student-question-row',
    templateUrl: './student-question-row.component.html',
    styleUrls: ['./student-questions.scss'],
})
export class StudentQuestionRowComponent implements OnInit, OnDestroy {
    @Input() studentQuestion: StudentQuestion;
    @Input() selectedStudentQuestion: StudentQuestion;
    @Input() user: User;
    @Input() isAtLeastTutorInCourse: boolean;
    @Output() interactQuestion = new EventEmitter<StudentQuestionAction>();
    isExpanded = true;
    isAnswerMode: boolean;
    isEditMode: boolean;
    isQuestionAuthor = false;
    showOtherAnswers = false;
    selectedQuestionAnswer: StudentQuestionAnswer | null;
    questionAnswerText: string | null;
    studentQuestionText: string | null;
    sortedQuestionAnswers: StudentQuestionAnswer[];
    approvedQuestionAnswers: StudentQuestionAnswer[];
    EditorMode = EditorMode;

    constructor(private studentQuestionAnswerService: StudentQuestionAnswerService, private studentQuestionService: StudentQuestionService) {}

    ngOnInit(): void {
        if (this.user) {
            this.isQuestionAuthor = this.studentQuestion.author.id === this.user.id;
        }
        this.sortQuestionAnswers();
    }

    /**
     * sorts the answers of a question into approved and not approved and then by date
     */
    sortQuestionAnswers(): void {
        if (!this.studentQuestion.answers) {
            this.sortedQuestionAnswers = [];
            this.approvedQuestionAnswers = [];
            return;
        }
        this.approvedQuestionAnswers = this.studentQuestion.answers
            .filter((ans) => ans.tutorApproved)
            .sort((a, b) => {
                const aValue = moment(a.answerDate!).valueOf();
                const bValue = moment(b.answerDate!).valueOf();

                return aValue - bValue;
            });
        this.sortedQuestionAnswers = this.studentQuestion.answers
            .filter((ans) => !ans.tutorApproved)
            .sort((a, b) => {
                const aValue = moment(a.answerDate!).valueOf();
                const bValue = moment(b.answerDate!).valueOf();

                return aValue - bValue;
            });
    }

    ngOnDestroy(): void {}

    toggleQuestionEditMode(): void {
        this.studentQuestionText = this.studentQuestion.questionText;
        this.isEditMode = !this.isEditMode;
    }

    /**
     * Takes a studentQuestionAnswer and toggles the edit field for it
     * If a studentQuestionAnswer is already selected it closes the edit field for the old one and opens it for the new one
     * @param   {StudentQuestionAnswer} questionAnswer
     */
    toggleAnswerMode(questionAnswer: StudentQuestionAnswer | null): void {
        if (questionAnswer) {
            if (this.selectedQuestionAnswer && questionAnswer.id === this.selectedQuestionAnswer.id) {
                this.isAnswerMode = false;
                this.questionAnswerText = '';
                this.selectedQuestionAnswer = null;
            } else {
                this.isAnswerMode = true;
                this.questionAnswerText = questionAnswer.answerText;
                this.selectedQuestionAnswer = questionAnswer;
            }
        } else {
            this.isAnswerMode = false;
            this.questionAnswerText = '';
            this.selectedQuestionAnswer = questionAnswer;
        }
    }

    /**
     * Toggles the field for a new Answer
     */
    toggleAnswerModeForNewAnswer(): void {
        this.isAnswerMode = true;
        this.questionAnswerText = '';
        this.selectedQuestionAnswer = null;
    }

    /**
     * Changes the question text
     */
    saveQuestion(): void {
        this.studentQuestion.questionText = this.studentQuestionText;
        this.studentQuestionService.update(this.studentQuestion).subscribe(() => {
            this.studentQuestionText = null;
            this.isEditMode = false;
        });
    }

    /**
     * deletes the studentQuestion
     */
    deleteQuestion(): void {
        this.studentQuestionService.delete(this.studentQuestion.id).subscribe(() => {
            this.interactQuestion.emit({
                name: QuestionActionName.DELETE,
                studentQuestion: this.studentQuestion,
            });
        });
    }

    /**
     * Creates a new studentAnswer
     */
    addAnswer(): void {
        const studentQuestionAnswer = new StudentQuestionAnswer();
        studentQuestionAnswer.answerText = this.questionAnswerText;
        studentQuestionAnswer.author = this.user;
        studentQuestionAnswer.verified = true;
        studentQuestionAnswer.question = this.studentQuestion;
        studentQuestionAnswer.tutorApproved = false;
        studentQuestionAnswer.answerDate = moment();
        this.studentQuestionAnswerService.create(studentQuestionAnswer).subscribe((studentQuestionResponse: HttpResponse<StudentQuestionAnswer>) => {
            if (!this.studentQuestion.answers) {
                this.studentQuestion.answers = [];
            }
            this.studentQuestion.answers.push(studentQuestionResponse.body!);
            this.sortQuestionAnswers();
            this.questionAnswerText = null;
            this.isAnswerMode = false;
        });
    }

    /**
     * Updates the text of the selected studentAnswer
     */
    saveAnswer(): void {
        this.selectedQuestionAnswer!.answerText = this.questionAnswerText;
        this.studentQuestionAnswerService.update(this.selectedQuestionAnswer!).subscribe(() => {
            this.questionAnswerText = null;
            this.selectedQuestionAnswer = null;
            this.isAnswerMode = false;
        });
    }

    /**
     * Takes a studentAnswer and deletes it
     * @param   {StudentQuestionAnswer} studentAnswer
     */
    deleteAnswer(studentAnswer: StudentQuestionAnswer): void {
        this.studentQuestionAnswerService.delete(studentAnswer.id).subscribe(() => {
            this.studentQuestion.answers = this.studentQuestion.answers.filter((el) => el.id !== studentAnswer.id);
            this.sortQuestionAnswers();
        });
    }

    /**
     * Takes a studentAnswer and toggles the tutorApproved field
     * @param   {StudentQuestionAnswer} studentAnswer
     */
    toggleAnswerTutorApproved(studentAnswer: StudentQuestionAnswer): void {
        studentAnswer.tutorApproved = !studentAnswer.tutorApproved;
        this.studentQuestionAnswerService.update(studentAnswer).subscribe(() => {
            this.sortQuestionAnswers();
        });
    }

    /**
     * Takes a studentQuestionAnswer and determines if the user is the author of it
     * @param {StudentQuestionAnswer} studentQuestionAnswer
     * @returns {boolean}
     */
    isAuthorOfAnswer(studentQuestionAnswer: StudentQuestionAnswer): boolean {
        if (this.user) {
            return studentQuestionAnswer.author.id === this.user.id;
        } else {
            return false;
        }
    }
}
