(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .component('instructorDashboard', {
            bindings: {
                courseId: '<',
                exerciseId: '<'
            },
            controller: InstructorDashboardController,
            templateUrl: 'app/instructor-dashboard/instructor-dashboard.html'
        });

    InstructorDashboardController.$inject = ['$filter', '$uibModal', 'ExerciseResults'];

    function InstructorDashboardController ($filter, $uibModal, ExerciseResults) {
        var vm = this;

        vm.showAllResults = false;
        vm.sortColumn = 'buildCompletionDate';
        vm.sortReverse = false;


        vm.$onInit = init;
        vm.buildDuration = buildDuration;
        vm.export = exportData;
        vm.refresh = getResults;
        vm.showDetails = showDetails;
        vm.toggleShowAllResults = toggleShowAllResults;

        function init() {
            getResults();
        }

        function buildDuration(completionDate, initializationDate) {
            return $filter('amDifference')(completionDate, initializationDate, 'minutes');
        }

        function exportData() {
            if (vm.sortedResults.length > 0) {
                var rows = [];
                vm.sortedResults.forEach(function (result, index) {
                    var studentName = result.participation.student.firstName;
                    rows.push(index === 0 ? 'data:text/csv;charset=utf-8,' + studentName : studentName);
                });
                var csvContent = rows.join('\n');
                var encodedUri = encodeURI(csvContent);
                var link = document.createElement('a');
                link.setAttribute('href', encodedUri);
                link.setAttribute('download', 'results.csv');
                document.body.appendChild(link); // Required for FF
                link.click();
            }
        }

        function getResults() {
            vm.results = ExerciseResults.query({
                courseId: vm.courseId,
                exerciseId: vm.exerciseId,
                showAllResults: vm.showAllResults
            });
        }

        function showDetails(result) {
            $uibModal.open({
                size: 'lg',
                templateUrl: 'app/courses/results/result-detail.html',
                controller: ['$http', 'result', function ($http, result) {
                    var vm = this;

                    vm.$onInit = init;

                    function init() {
                        vm.loading = true;
                        $http.get('api/results/' + result.id + '/details', {
                            params: {
                                username: result.participation.student.login
                            }
                        }).then(function (response) {
                            vm.details = response.data;
                        }).finally(function () {
                            vm.loading = false;
                        });
                    }
                }],
                resolve: {
                    result: result
                },
                controllerAs: '$ctrl'
            });
        }

        function toggleShowAllResults(newValue) {
            vm.showAllResults = newValue;
            getResults();
        }
    }
})();
