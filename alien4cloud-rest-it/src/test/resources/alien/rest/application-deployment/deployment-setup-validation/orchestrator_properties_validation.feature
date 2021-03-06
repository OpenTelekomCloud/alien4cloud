Feature: orchestrator properties validation in deployment setup

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
    And I create a new application with name "ALIEN" and description "ALIEN_1" and node templates
      | Compute | tosca.nodes.Compute:1.0.0-SNAPSHOT |
    And I Set a unique location policy to "Mount doom orchestrator"/"Thark location" for all nodes


#  @reset
#  Scenario: Unfilled NOT REQUIRED orchestrators property should be considered valid

  @reset
  Scenario: Missing REQUIRED orchestrators property value should be considered invalid
    When I check for the valid status of the deployment topology
    Then the deployment topology should not be valid
    And the missing orchestrator properties should be
      | managerEmail  |
      | managementUrl |
      | numberBackup  |
    When I set the following orchestrator properties
      | managerEmail | toto@titi.fr |
    And I check for the valid status of the deployment topology
    Then the deployment topology should not be valid
    And the missing orchestrator properties should be
      | managementUrl |
      | numberBackup  |

  @reset
  Scenario: Provided required property should be considered valid
    Given I set the following orchestrator properties
      | managerEmail  | toto@titi.fr            |
      | managementUrl | http://cloudifyurl:8099 |
      | numberBackup  | 1                       |
    When I check for the valid status of the deployment topology
    Then the deployment topology should be valid