Feature: Search deployment logs

  Background:
    Given I am authenticated with "ADMIN" role
    And I upload the archive "tosca-normative-types-1.0.0-SNAPSHOT"
    And I upload a plugin
    And I create an orchestrator named "Mount doom orchestrator" and plugin id "alien4cloud-mock-paas-provider" and bean name "mock-orchestrator-factory"
    And I enable the orchestrator "Mount doom orchestrator"
    And I create a location named "Thark location" and infrastructure type "OpenStack" to the orchestrator "Mount doom orchestrator"
    And I create a resource of type "org.alien4cloud.nodes.mock.openstack.Flavor" named "Small" related to the location "Mount doom orchestrator"/"Thark location"
    And I update the property "id" to "1" for the resource named "Small" related to the location "Mount doom orchestrator"/"Thark location"
    And I create a resource of type "org.alien4cloud.nodes.mock.openstack.Image" named "Ubuntu" related to the location "Mount doom orchestrator"/"Thark location"
    And I update the property "id" to "img1" for the resource named "Ubuntu" related to the location "Mount doom orchestrator"/"Thark location"
    And I autogenerate the on-demand resources for the location "Mount doom orchestrator"/"Thark location"
    And I pre register orchestrator properties
      | managementUrl | http://somewhere.fr:8099 |
      | numberBackup  | 1                       |
      | managerEmail  | admin@alien.fr         |

    And I create a new application with name "The great eye" and description "" and node templates
      | Compute | tosca.nodes.Compute:1.0.0-SNAPSHOT |
    And I Set a unique location policy to "Mount doom orchestrator"/"Thark location" for all nodes
    And I deploy it
    Then I should receive a RestResponse with no error

  @reset
  Scenario: search for all deployment logs
    When I search for deployment logs
    Then I should receive a RestResponse with no error
    And I should get some deployment logs
  
  @reset
  Scenario: search for deployment logs for current deployment
    When I search for deployment logs for current deployment
    Then I should receive a RestResponse with no error
    And I should get current deployment logs

  @reset
  Scenario: search for deployment logs for non existing deployment
    When I search for deployment logs for non existing deployment
    Then I should receive a RestResponse with no error
    And I should get no deployment log
  
  @reset
  Scenario: search for deployment logs with time interval
    When I search for deployment logs with time interval
    Then I should receive a RestResponse with no error
    And I should get some deployment logs

  @reset
  Scenario: search for deployment logs in the future
    When I search for deployment logs in the future
    Then I should receive a RestResponse with no error
    And I should get no deployment log
  
