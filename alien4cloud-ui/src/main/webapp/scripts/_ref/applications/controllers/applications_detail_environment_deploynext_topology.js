define(function (require) {
  'use strict';

  var modules = require('modules');
  var states = require('states');

  require('scripts/_ref/applications/controllers/applications_detail_environment_deploynext_topology_editor');

  require('scripts/topology/directives/topology_validation_display');
  require('scripts/topology/directives/topology_rendering');

  states.state('applications.detail.environment.deploynext.topology', {
    url: '/topology',
    templateUrl: 'views/_ref/applications/applications_detail_environment_deploynext_topology.html',
    controller: 'AppEnvDeployNextTopologyCtrl',
    menu: {
      id: 'applications.detail.environment.deploynext.topology',
      state: 'applications.detail.environment.deploynext.topology',
      key: 'NAVAPPLICATIONS.MENU_DEPLOY_NEXT.TOPOLOGY',
      icon: '',
      priority: 200,
      step: {
        taskCodes: ['EMPTY', 'IMPLEMENT_RELATIONSHIP', 'SATISFY_LOWER_BOUND', 'PROPERTIES', 'SCALABLE_CAPABILITY_INVALID', 'NODE_FILTER_INVALID', 'WORKFLOW_INVALID'],
        source: 'topology'
      }
    }
  });

  modules.get('a4c-applications').controller('AppEnvDeployNextTopologyCtrl',
    ['$scope', '$state', 'authService', 'resizeServices',
    function ($scope, $state, authService, resizeServices) {
      // Filter tasks to match only the screen task codes
      $scope.canEditTopology = authService.hasResourceRoleIn($scope.application, ['APPLICATION_MANAGER', 'APPLICATION_DEVOPS']);

      $scope.topologyBox = {
        width: 600,
        height: 300
      };

      resizeServices.registerContainer(function(width, height) {
        console.log('Updating container size', width, height);
        $scope.topologyBox = {
          width: width - 10,
          height: height - 10
        };
        $scope.$digest();
      }, '#topology-preview');

      $scope.editTopology = function() {
        $state.go('editor_app_env.editor', {
          applicationId: $scope.application.id,
          environmentId: $scope.environment.id,
          archiveId: $scope.application.id + ':' + $scope.environment.currentVersionName
        });
      };
    }
  ]);
});
