'use strict';

var NewApplicationCtrl = ['$scope', '$modalInstance', '$resource',
  function($scope, $modalInstance, $resource) {
    $scope.app = {};
    $scope.create = function(valid, templateId) {
      if (valid) {
        if (!angular.isUndefined(templateId)) {
          // topologyId linked to the topology template
          $scope.app.topologyId = templateId;
        }
        $modalInstance.close($scope.app);
      }
    };

    $scope.cancel = function() {
      $modalInstance.dismiss('cancel');
    };

    // TopologyTemplate handeling
    var searchTopologyTemplateResource = $resource('rest/templates/topology/search', {}, {
      'search': {
        method: 'POST',
        isArray: false,
        headers: {
          'Content-Type': 'application/json; charset=UTF-8'
        }
      }
    });

    $scope.loadTopologyTemplates = function() {

      var searchRequestObject = {
        'query': $scope.query,
        'from': 0,
        'size': 50
      };

      searchTopologyTemplateResource.search([], angular.toJson(searchRequestObject), function(successResult) {
        $scope.templates = successResult.data.data;
      });

    };

    // First template load
    $scope.loadTopologyTemplates();
  }
];

angular.module('alienUiApp').controller('ApplicationListCtrl', ['$scope', '$modal', '$resource', '$state', 'alienAuthService', 'applicationServices', '$translate', 'toaster',
  function($scope, $modal, $resource, $state, alienAuthService, applicationServices, $translate, toaster) {
    $scope.isManager = alienAuthService.hasRole('APPLICATIONS_MANAGER');
    d3.selectAll('.d3-tip').remove();

    $scope.openNewApp = function() {
      var modalInstance = $modal.open({
        templateUrl: 'newApplication.html',
        controller: NewApplicationCtrl
      });
      modalInstance.result.then(function(application) {
        // create a new application from the given name and description.
        applicationServices.create([], angular.toJson(application), function(successResponse) {
          $scope.openApplication(successResponse.data);
        });
      });
    };

    var countStatus = function(app, statuses) {
      app.sumByStatus = {'DEPLOYED' :0, 'UNDEPLOYED' :0, 'DEPLOYMENT_IN_PROGRESS' :0, 'UNDEPLOYMENT_IN_PROGRESS' :0, 'WARNING' :0, 'FAILURE' :0, 'DEPLOY' :0, 'UNDEPLOY' :0};
      for (var key in statuses) {
        app.sumByStatus[statuses[key]] ++;
      }
    }

    var getApplicationStatuses = function(applications) {
      var requestAppStatuses = [];
      Object.keys(applications).forEach(function(key) {
        requestAppStatuses.push(applications[key].id);
      });
      var appStatuses = applicationServices.applicationStatus.statuses([], angular.toJson(requestAppStatuses));
      return appStatuses;
    };

    var colors = {'DEPLOYED': '#3ADF00', 'UNDEPLOYED': '#D8D8D8'};
    var drawPieChart = function(appName, data) {
      var tip = d3.tip().attr('class', 'd3-tip').html(function(node) {
        return node.data.name;
      });

      var pie = new d3pie("pieChart-" + appName, {
        "size": {
          "canvasWidth": 100,
          "canvasHeight": 100
        },
        "data": {
          "sortOrder": "label-asc",
          "content": data
        },
        "labels": {
          "outer": {
            "format": "none"
          },
          "inner": {
            "format": "none"
          },
        },
        "effects": {
          "load": {
            "effect": "none"
          },
          "pullOutSegmentOnClick": {
            "effect": "none"
          },
          "highlightSegmentOnMouseover": true,
          "highlightLuminosity": 0.10
        },
        "callbacks": {
          "onMouseoverSegment": tip.show,
          "onMouseoutSegment": tip.hide
        }
      });

      pie.svg.call(tip);
    };

    var updateApplicationStatuses = function(applicationSearchResult) {
      if (!angular.isUndefined(applicationSearchResult)) {
        var statuses = getApplicationStatuses(applicationSearchResult.data.data);
        Object.keys(applicationSearchResult.data.data).forEach(function(key) {
          var app = applicationSearchResult.data.data[key];
          statuses.$promise.then(function(statuses) {
            var data = [];
            var tmpArray = statuses.data[app.id];
            for (var key in tmpArray) {
              var segment = {};
              segment['label'] = tmpArray[key][1];
              segment['color'] = colors[tmpArray[key][1]];
              segment['value'] = 1;
              segment['name'] = tmpArray[key][0];
              data.push(segment);
            }
            drawPieChart(app.name, data);
          });
        });
      }
      return applicationSearchResult;
    };



    $scope.search = function() {
      var searchRequestObject = {
        'query': $scope.query,
        'from': 0,
        'size': 50
      };
      var searchResult = applicationServices.search([], angular.toJson(searchRequestObject));

      // when apps search result is ready, update apps statuses
      searchResult.$promise.then(function(applisationListResult) {
        updateApplicationStatuses(applisationListResult);
      });
      $scope.searchResult = searchResult;
    };
    $scope.search();

    $scope.openApplication = function(applicationId) {
      $state.go('applications.detail.info', {
        id: applicationId
      });
    };

    $scope.removeApplication = function(applicationId) {
      applicationServices.remove({
        applicationId: applicationId
      }, function(response) {
        if (!response.error && response.data === true) {
          $scope.search();
        } else {
          // toaster message
          toaster.pop('error', $translate('APPLICATIONS.ERRORS.DELETE_TITLE'), $translate('APPLICATIONS.ERRORS.DELETING_FAILED'), 4000, 'trustedHtml', null);
        }
      });
    };
  }
]);
