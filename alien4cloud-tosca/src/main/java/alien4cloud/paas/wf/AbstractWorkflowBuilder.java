package alien4cloud.paas.wf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.RelationshipTemplate;
import org.alien4cloud.tosca.model.workflow.NodeWorkflowStep;
import org.alien4cloud.tosca.model.workflow.RelationshipWorkflowStep;
import org.alien4cloud.tosca.model.workflow.Workflow;
import org.alien4cloud.tosca.model.workflow.WorkflowStep;
import org.alien4cloud.tosca.model.workflow.activities.AbstractWorkflowActivity;
import org.alien4cloud.tosca.model.workflow.activities.DelegateWorkflowActivity;
import org.apache.commons.lang3.StringUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import alien4cloud.exception.AlreadyExistException;
import alien4cloud.paas.wf.exception.BadWorkflowOperationException;
import alien4cloud.paas.wf.exception.InconsistentWorkflowException;
import alien4cloud.paas.wf.util.WorkflowUtils;
import alien4cloud.utils.AlienUtils;

import static alien4cloud.utils.AlienUtils.safe;

public abstract class AbstractWorkflowBuilder {

    public abstract void addNode(Workflow wf, String nodeId, TopologyContext toscaTypeFinder, boolean isCompute);

    public abstract void addRelationship(Workflow wf, String nodeId, NodeTemplate nodeTemplate, String relationshipId,
            RelationshipTemplate relationshipTemplate, TopologyContext toscaTypeFinder);

    public void removeEdge(Workflow wf, String from, String to) {
        WorkflowStep fromStep = wf.getSteps().get(from);
        if (fromStep == null) {
            throw new InconsistentWorkflowException(
                    String.format("Inconsistent workflow: a step named '%s' can not be found while it's referenced else where ...", from));
        }
        WorkflowStep toStep = wf.getSteps().get(to);
        if (toStep == null) {
            throw new InconsistentWorkflowException(
                    String.format("Inconsistent workflow: a step named '%s' can not be found while it's referenced else where ...", to));
        }
        fromStep.removeFollowing(to);
        toStep.removePreceding(from);
    }

    public void removeFailureEdge(Workflow wf, String from, String to) {
        WorkflowStep fromStep = wf.getSteps().get(from);
        if (fromStep == null) {
            throw new InconsistentWorkflowException(
                    String.format("Inconsistent workflow: a step named '%s' can not be found while it's referenced else where ...", from));
        }
        WorkflowStep toStep = wf.getSteps().get(to);
        if (toStep == null) {
            throw new InconsistentWorkflowException(
                    String.format("Inconsistent workflow: a step named '%s' can not be found while it's referenced else where ...", to));
        }
        fromStep.removeFollowingFailure(to);
        toStep.removePrecedingFailure(from);
    }

    void connectStepFrom(Workflow wf, String stepId, String[] stepNames) {
        WorkflowStep to = wf.getSteps().get(stepId);
        if (to == null) {
            throw new InconsistentWorkflowException(
                    String.format("Inconsistent workflow: a step named '%s' can not be found while it's referenced else where ...", stepId));
        }
        for (String preceding : stepNames) {
            WorkflowStep precedingStep = wf.getSteps().get(preceding);
            if (precedingStep == null) {
                throw new InconsistentWorkflowException(
                        String.format("Inconsistent workflow: a step named '%s' can not be found while it's referenced else where ...", preceding));
            }
            WorkflowUtils.linkSteps(precedingStep, to);
        }
    }

    void connectStepTo(Workflow wf, String stepId, String[] stepNames) {
        WorkflowStep from = wf.getSteps().get(stepId);
        if (from == null) {
            throw new InconsistentWorkflowException(
                    String.format("Inconsistent workflow: a step named '%s' can not be found while it's referenced else where ...", stepId));
        }
        for (String following : stepNames) {
            WorkflowStep followingStep = wf.getSteps().get(following);
            WorkflowUtils.linkSteps(from, followingStep);
        }
    }

    void failStepTo(Workflow wf, String stepId, String[] stepNames) {
        WorkflowStep from = wf.getSteps().get(stepId);
        if (from == null) {
            throw new InconsistentWorkflowException(
                    String.format("Inconsistent workflow: a step named '%s' can not be found while it's referenced else where ...", stepId));
        }
        for (String following : stepNames) {
            WorkflowStep followingStep = wf.getSteps().get(following);
            WorkflowUtils.linkStepsWithOnFailure(from, followingStep);
        }
    }

    void failStepFrom(Workflow wf, String stepId, String[] stepNames) {
        WorkflowStep to = wf.getSteps().get(stepId);
        if (to == null) {
            throw new InconsistentWorkflowException(
                    String.format("Inconsistent workflow: a step named '%s' can not be found while it's referenced else where ...", stepId));
        }
        for (String preceding : stepNames) {
            WorkflowStep precedingStep = wf.getSteps().get(preceding);
            if (precedingStep == null) {
                throw new InconsistentWorkflowException(
                        String.format("Inconsistent workflow: a step named '%s' can not be found while it's referenced else where ...", preceding));
            }
            WorkflowUtils.linkStepsWithOnFailure(precedingStep, to);
        }
    }

    private WorkflowStep addActivityStep(Workflow wf, String target, String targetRelationship, AbstractWorkflowActivity activity) {
        WorkflowStep step;
        if (StringUtils.isEmpty(targetRelationship)) {
            step = new NodeWorkflowStep();
        } else {
            RelationshipWorkflowStep relationshipWorkflowStep = new RelationshipWorkflowStep();
            relationshipWorkflowStep.setTargetRelationship(targetRelationship);
            step = relationshipWorkflowStep;
        }
        step.setTarget(target);
        step.setActivity(activity);
        step.setName(WorkflowUtils.buildStepName(wf, step, 0));
        wf.addStep(step);
        return step;
    }

    /**
     * @param wf the workflow to add activity
     * @param relatedStepId if specified, the step will be added near this one (maybe before)
     * @param before if true, the step will be added before the relatedStepId
     * @param target the target
     * @param activity the activity to be added
     */
    void addActivity(Workflow wf, String relatedStepId, boolean before, String target, String targetRelationship, AbstractWorkflowActivity activity,
            TopologyContext topologyContext) {
        if (WorkflowUtils.isNativeOrSubstitutionNode(target, topologyContext)) {
            throw new BadWorkflowOperationException("Activity can not be added for abstract nodes");
        }
        if (relatedStepId != null) {
            if (before) {
                // insert
                insertActivityStep(wf, relatedStepId, target, targetRelationship, activity);
            } else {
                // append
                appendActivityStep(wf, relatedStepId, target, targetRelationship, activity);
            }
        } else {
            addActivityStep(wf, target, targetRelationship, activity);
        }
    }

    private void insertActivityStep(Workflow wf, String stepId, String target, String targetRelationship, AbstractWorkflowActivity activity) {
        WorkflowStep lastStep = wf.getSteps().get(stepId);
        String stepBeforeId = null;
        if (lastStep.getPrecedingSteps() != null && lastStep.getPrecedingSteps().size() == 1
                && (lastStep.getPrecedingFailSteps() == null || lastStep.getPrecedingFailSteps().size() == 0)) {
            stepBeforeId = lastStep.getPrecedingSteps().iterator().next();
        }

        WorkflowStep insertedStep = addActivityStep(wf, target, targetRelationship, activity);
        WorkflowUtils.linkSteps(insertedStep, lastStep);
        if (stepBeforeId != null) {
            WorkflowStep stepBefore = wf.getSteps().get(stepBeforeId);
            WorkflowUtils.unlinkSteps(stepBefore, lastStep);
            WorkflowUtils.linkSteps(stepBefore, insertedStep);
        }
    }

    private void appendActivityStep(Workflow wf, String stepId, String target, String targetRelationship, AbstractWorkflowActivity activity) {
        WorkflowStep lastStep = wf.getSteps().get(stepId);
        String stepAfterId = null;
        if (lastStep.getOnSuccess() != null && lastStep.getOnSuccess().size() == 1
                && (lastStep.getOnFailure() == null || lastStep.getOnFailure().size() == 0) ) {
            stepAfterId = lastStep.getOnSuccess().iterator().next();
        }
        WorkflowStep insertedStep = addActivityStep(wf, target, targetRelationship, activity);
        WorkflowUtils.linkSteps(lastStep, insertedStep);
        if (stepAfterId != null) {
            WorkflowStep stepAfter = wf.getSteps().get(stepAfterId);
            WorkflowUtils.unlinkSteps(lastStep, stepAfter);
            WorkflowUtils.linkSteps(insertedStep, stepAfter);
        }
    }

    void removeStep(Workflow wf, String stepId, boolean force) {
        WorkflowUtils.removeStep(wf, stepId, force);
    }

    public void renameStep(Workflow wf, String stepId, String newStepName) {
        if (wf.getSteps().containsKey(newStepName)) {
            throw new AlreadyExistException(String.format("A step named ''%s'' already exists in workflow '%s'", newStepName, wf.getName()));
        }
        WorkflowStep step = wf.getSteps().remove(stepId);
        step.setName(newStepName);
        wf.addStep(step);
        // now explore the links
        if (step.getPrecedingSteps() != null) {
            for (String precedingId : step.getPrecedingSteps()) {
                WorkflowStep precedingStep = wf.getSteps().get(precedingId);
                precedingStep.removeFollowing(stepId);
                precedingStep.addFollowing(newStepName);
            }
        }
        if (step.getOnSuccess() != null) {
            for (String followingId : step.getOnSuccess()) {
                WorkflowStep followingStep = wf.getSteps().get(followingId);
                followingStep.removePreceding(stepId);
                followingStep.addPreceding(newStepName);
            }
        }
    }

    public void removeNode(Workflow wf, String nodeName) {
        WorkflowStep[] steps = new WorkflowStep[wf.getSteps().size()];
        steps = wf.getSteps().values().toArray(steps);
        for (WorkflowStep step : steps) {
            if (nodeName.equals(step.getTarget())) {
                removeStep(wf, step.getName(), true);
            }
        }
    }

    /**
     * Modify the workflow to take into account the remove of all relationships from the given source node to the given target node
     * 
     * @param wf the workflow
     * @param sourceNodeId the source node's id
     * @param targetNodeId the target node's id
     */
    void removeRelationships(Workflow wf, String sourceNodeId, Map<String, RelationshipTemplate> sourceRelationships, String targetNodeId,
            Map<String, RelationshipTemplate> targetRelationships) {
        doRemoveRelationships(wf, sourceNodeId, sourceRelationships);
        doRemoveRelationships(wf, targetNodeId, targetRelationships);
    }

    private void doRemoveRelationships(Workflow wf, String sourceNodeId, Map<String, RelationshipTemplate> sourceRelationships) {
        // Remove relationships steps
        Collection<WorkflowStep> relationshipWorkflowSteps = wf.getSteps().values().stream()
                .filter(step -> sourceRelationships.entrySet().stream()
                        .anyMatch(relationshipTemplateEntry -> WorkflowUtils.isRelationshipStep(step, sourceNodeId, relationshipTemplateEntry.getKey())))
                .collect(Collectors.toList());
        relationshipWorkflowSteps.forEach(step -> removeStep(wf, step.getName(), true));
        // Find all links from and to the node's steps and unlink them all if they come from relationship template
        wf.getSteps().values().stream().filter(step -> sourceNodeId.equals(step.getTarget()))
                .forEach(nodeStep -> new ArrayList<>(safe(nodeStep.getOnSuccess())).stream().map(followingId -> wf.getSteps().get(followingId))
                        .filter(followingStep -> sourceRelationships.entrySet().stream().anyMatch(
                                relationshipTemplateEntry -> WorkflowUtils.isNodeStep(followingStep, relationshipTemplateEntry.getValue().getTarget())))
                        .forEach(followingStep ->  WorkflowUtils.unlinkSteps(nodeStep, followingStep)));
        wf.getSteps().values().stream().filter(step -> sourceNodeId.equals(step.getTarget()))
                .forEach(nodeStep -> new ArrayList<>(safe(nodeStep.getPrecedingSteps())).stream().map(precedingId -> wf.getSteps().get(precedingId))
                        .filter(precedingStep -> sourceRelationships.entrySet().stream().anyMatch(
                                relationshipTemplateEntry -> WorkflowUtils.isNodeStep(precedingStep, relationshipTemplateEntry.getValue().getTarget())))
                        .forEach(precedingStep ->  WorkflowUtils.unlinkSteps(precedingStep, nodeStep)));
    }

    /**
     * Swap steps means:
     * <ul>
     * <li>The connection between step and target is inverted.
     * <li>All step's predecessors become predecessors of target & vice versa
     * <li>All step's followers become followers of target & vice versa
     * </ul>
     * That's all folks !
     */
    public void swapSteps(Workflow wf, String stepId, String targetId) {
        WorkflowStep step = wf.getSteps().get(stepId);
        WorkflowStep target = wf.getSteps().get(targetId);

        boolean hasSuccessLink = step.getOnSuccess().contains(targetId);
        boolean hasFailureLink = step.getOnFailure().contains(targetId);

        if (hasSuccessLink) {
            WorkflowUtils.unlinkSteps(step, target);
        }

        if (hasFailureLink) {
            WorkflowUtils.unlinkOnFailureSteps(step,target);
        }

        List<WorkflowStep> stepPredecessors = removePredecessors(wf, step);
        List<WorkflowStep> stepFollowers = removeFollowers(wf, step);
        List<WorkflowStep> targetPredecessors = removePredecessors(wf, target);
        List<WorkflowStep> targetFollowers = removeFollowers(wf, target);

        List<WorkflowStep> stepFailPredecessors = removeFailPredecessors(wf, step);
        List<WorkflowStep> stepFailFollowers = removeFailFollowers(wf, step);
        List<WorkflowStep> targetFailPredecessors = removeFailPredecessors(wf, target);
        List<WorkflowStep> targetFailFollowers = removeFailFollowers(wf, target);

        targetFollowers.forEach(follower -> WorkflowUtils.linkSteps(step,follower));
        stepFollowers.forEach(follower -> WorkflowUtils.linkSteps(target,follower));
        targetPredecessors.forEach(predecessor -> WorkflowUtils.linkSteps(predecessor,step));
        stepPredecessors.forEach(predecessor -> WorkflowUtils.linkSteps(predecessor,target));

        targetFailFollowers.forEach(follower -> WorkflowUtils.linkStepsWithOnFailure(step,follower));
        stepFailFollowers.forEach(follower -> WorkflowUtils.linkStepsWithOnFailure(target,follower));
        targetFailPredecessors.forEach(predecessor -> WorkflowUtils.linkStepsWithOnFailure(predecessor,step));
        stepFailPredecessors.forEach(predecessor -> WorkflowUtils.linkStepsWithOnFailure(predecessor,target));

        if (hasSuccessLink) {
            WorkflowUtils.linkSteps(target, step);
        }

        if (hasFailureLink) {
            WorkflowUtils.linkStepsWithOnFailure(target, step);
        }
    }

    private List<WorkflowStep> removePredecessors(Workflow wf, WorkflowStep step) {
        List<WorkflowStep> result = safe(step.getPrecedingSteps()).stream().map(name -> wf.getSteps().get(name)).collect(Collectors.toList());
        result.forEach(precedingStep -> WorkflowUtils.unlinkSteps(precedingStep,step));
        return result;
    }

    private List<WorkflowStep> removeFollowers(Workflow wf, WorkflowStep step) {
        List<WorkflowStep> result = safe(step.getOnSuccess()).stream().map(name -> wf.getSteps().get(name)).collect(Collectors.toList());
        result.forEach(followingStep -> WorkflowUtils.unlinkSteps(step, followingStep));
        return result;
    }

    private List<WorkflowStep> removeFailPredecessors(Workflow wf, WorkflowStep step) {
        List<WorkflowStep> result = safe(step.getPrecedingFailSteps()).stream().map(name -> wf.getSteps().get(name)).collect(Collectors.toList());
        result.forEach(precedingStep -> WorkflowUtils.unlinkOnFailureSteps(precedingStep,step));
        return result;
    }

    private List<WorkflowStep> removeFailFollowers(Workflow wf, WorkflowStep step) {
        List<WorkflowStep> result = safe(step.getOnFailure()).stream().map(name -> wf.getSteps().get(name)).collect(Collectors.toList());
        result.forEach(followingStep -> WorkflowUtils.unlinkOnFailureSteps(step, followingStep));
        return result;
    }

    void renameNode(Workflow wf, String oldName, String newName) {
        if (wf.getSteps() != null) {
            for (WorkflowStep step : wf.getSteps().values()) {
                if (oldName.equals(step.getTarget())) {
                    step.setTarget(newName);
                }
                if (step instanceof NodeWorkflowStep && oldName.equals((((NodeWorkflowStep)step).getHostId()))) {
                    ((NodeWorkflowStep)step).setHostId(newName);
                }
            }
        }
    }

    Workflow reinit(Workflow wf, TopologyContext toscaTypeFinder) {
        Map<String, WorkflowStep> steps = Maps.newHashMap();
        wf.setSteps(steps);
        wf.setHasCustomModifications(false);
        if (toscaTypeFinder.getTopology().getNodeTemplates() != null) {
            // first stage : add the nodes
            for (Entry<String, NodeTemplate> entry : toscaTypeFinder.getTopology().getNodeTemplates().entrySet()) {
                String nodeId = entry.getKey();
                boolean forceOperation = WorkflowUtils.isComputeOrVolume(nodeId, toscaTypeFinder);
                addNode(wf, nodeId, toscaTypeFinder, forceOperation);
            }
            // second stage : add the relationships
            for (Entry<String, NodeTemplate> entry : toscaTypeFinder.getTopology().getNodeTemplates().entrySet()) {
                String nodeId = entry.getKey();
                if (entry.getValue().getRelationships() != null) {
                    for (Map.Entry<String, RelationshipTemplate> relationshipTemplateEntry : entry.getValue().getRelationships().entrySet()) {
                        addRelationship(wf, nodeId, entry.getValue(), relationshipTemplateEntry.getKey(), relationshipTemplateEntry.getValue(),
                                toscaTypeFinder);
                    }
                }
            }
        }
        return wf;
    }
}
