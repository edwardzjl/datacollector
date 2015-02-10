/**
 * (c) 2014 StreamSets, Inc. All rights reserved. May not
 * be copied, modified, or distributed in whole or part without
 * written consent of StreamSets, Inc.
 */
package com.streamsets.pipeline.runner.production;

import com.google.common.annotations.VisibleForTesting;
import com.streamsets.pipeline.config.DataAlertDefinition;
import com.streamsets.pipeline.config.MetricsAlertDefinition;
import com.streamsets.pipeline.config.RuleDefinition;
import com.streamsets.pipeline.runner.LaneResolver;
import com.streamsets.pipeline.runner.Observer;
import com.streamsets.pipeline.store.PipelineStoreException;
import com.streamsets.pipeline.store.PipelineStoreTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RulesConfigLoader {

  private final PipelineStoreTask pipelineStoreTask;
  private final String pipelineName;
  private final String revision;
  private RuleDefinition previousRuleDefinition;


  public RulesConfigLoader(String pipelineName, String revision, PipelineStoreTask pipelineStoreTask) {
    this.pipelineStoreTask = pipelineStoreTask;
    this.pipelineName = pipelineName;
    this.revision = revision;
  }

  public RuleDefinition load(Observer observer) throws InterruptedException, PipelineStoreException {
    RuleDefinition newRuleDefinition = pipelineStoreTask.retrieveRules(pipelineName, revision);
    if (newRuleDefinition != previousRuleDefinition) {
      RulesConfigurationChangeRequest rulesConfigurationChangeRequest = detectChanges(previousRuleDefinition,
        newRuleDefinition);
      observer.setConfiguration(rulesConfigurationChangeRequest);
    }
    previousRuleDefinition = newRuleDefinition;
    return newRuleDefinition;
  }

  @VisibleForTesting
  RulesConfigurationChangeRequest detectChanges(RuleDefinition previousRuleDefinition,
                                                        RuleDefinition newRuleDefinition)
    throws InterruptedException {
    //TODO: compute, detect changes etc and upload
    Set<String> rulesToRemove = new HashSet<>();
    Set<String> pipelineAlertsToRemove = new HashSet<>();
    if (previousRuleDefinition != null) {
      rulesToRemove.addAll(detectRulesToRemove(previousRuleDefinition.getDataAlertDefinitions(),
        newRuleDefinition.getDataAlertDefinitions()));
      pipelineAlertsToRemove.addAll(detectAlertsToRemove(previousRuleDefinition.getMetricsAlertDefinitions(),
        newRuleDefinition.getMetricsAlertDefinitions()));
    }
    Map<String, List<DataAlertDefinition>> laneToDataRule = new HashMap<>();
    for (DataAlertDefinition dataAlertDefinition : newRuleDefinition.getDataAlertDefinitions()) {
      String lane = LaneResolver.getPostFixedLaneForObserver(dataAlertDefinition.getLane());
      List<DataAlertDefinition> dataAlertDefinitions = laneToDataRule.get(lane);
      if (dataAlertDefinitions == null) {
        dataAlertDefinitions = new ArrayList<>();
        laneToDataRule.put(lane, dataAlertDefinitions);
      }
      dataAlertDefinitions.add(dataAlertDefinition);
    }

    RulesConfigurationChangeRequest rulesConfigurationChangeRequest =
      new RulesConfigurationChangeRequest(newRuleDefinition, rulesToRemove, pipelineAlertsToRemove,
        laneToDataRule);

    return rulesConfigurationChangeRequest;
  }

  private Set<String> detectAlertsToRemove(List<MetricsAlertDefinition> oldMetricsAlertDefinitions,
                                   List<MetricsAlertDefinition> newMetricsAlertDefinitions) {
    Set<String> alertsToRemove = new HashSet<>();
    if(newMetricsAlertDefinitions != null && oldMetricsAlertDefinitions != null) {
      for(MetricsAlertDefinition oldMetricAlertDefinition : oldMetricsAlertDefinitions) {
        boolean found = false;
        for(MetricsAlertDefinition newMetricAlertDefinition : newMetricsAlertDefinitions) {
          if(oldMetricAlertDefinition.getId().equals(newMetricAlertDefinition.getId())) {
            found = true;
            if(oldMetricAlertDefinition.isEnabled() && !newMetricAlertDefinition.isEnabled()) {
              alertsToRemove.add(oldMetricAlertDefinition.getMetricId());
            }
            if(hasAlertChanged(oldMetricAlertDefinition, newMetricAlertDefinition)) {
              alertsToRemove.add(oldMetricAlertDefinition.getMetricId());
            }
          }
        }
        if(!found) {
          alertsToRemove.add(oldMetricAlertDefinition.getMetricId());
        }
      }
    }
    return alertsToRemove;
  }

  private Set<String> detectRulesToRemove(List<DataAlertDefinition> oldMetricDefinitions,
                                  List<DataAlertDefinition> newMetricDefinitions) {
    Set<String> rulesToRemove = new HashSet<>();
    if(newMetricDefinitions != null && oldMetricDefinitions != null) {
      for(DataAlertDefinition oldMetricDefinition : oldMetricDefinitions) {
        boolean found = false;
        for(DataAlertDefinition newMetricDefinition : newMetricDefinitions) {
          if(oldMetricDefinition.getId().equals(newMetricDefinition.getId())) {
            found = true;
            if(oldMetricDefinition.isEnabled() && !newMetricDefinition.isEnabled()) {
              rulesToRemove.add(oldMetricDefinition.getId());
            }
            if(hasRuleChanged(oldMetricDefinition, newMetricDefinition)) {
              if(oldMetricDefinition.isAlertEnabled() || oldMetricDefinition.isMeterEnabled()) {
                rulesToRemove.add(oldMetricDefinition.getId());
              }
            }
          }
        }
        if(!found) {
          rulesToRemove.add(oldMetricDefinition.getId());
        }
      }
    }
    return rulesToRemove;
  }

  private boolean hasRuleChanged(DataAlertDefinition oldDataAlertDefinition,
                         DataAlertDefinition newDataAlertDefinition) {
    boolean noChange = true;
    if(newDataAlertDefinition.isEnabled()) {
      noChange &= areStringsSame(oldDataAlertDefinition.getLane(), newDataAlertDefinition.getLane());
      noChange &= areStringsSame(oldDataAlertDefinition.getCondition(), newDataAlertDefinition.getCondition());
      noChange &= areStringsSame(oldDataAlertDefinition.getThresholdValue(), newDataAlertDefinition.getThresholdValue());
      noChange &= areStringsSame(String.valueOf(oldDataAlertDefinition.getMinVolume()),
        String.valueOf(newDataAlertDefinition.getMinVolume()));
      noChange &= areStringsSame(String.valueOf(oldDataAlertDefinition.getSamplingPercentage()),
        String.valueOf(newDataAlertDefinition.getSamplingPercentage()));
      noChange &= areStringsSame(oldDataAlertDefinition.getThresholdType().name(),
        newDataAlertDefinition.getThresholdType().name());
      noChange &= (oldDataAlertDefinition.isEnabled() && newDataAlertDefinition.isEnabled());
    }
    return !noChange;
  }

  private boolean hasAlertChanged(MetricsAlertDefinition oldMetricsAlertDefinition,
                          MetricsAlertDefinition newMetricsAlertDefinition) {
    boolean noChange = true;
    if(newMetricsAlertDefinition.isEnabled()) {
      noChange &= areStringsSame(oldMetricsAlertDefinition.getMetricId(), newMetricsAlertDefinition.getMetricId());
      noChange &= areStringsSame(oldMetricsAlertDefinition.getCondition(), newMetricsAlertDefinition.getCondition());
      noChange &= areStringsSame(oldMetricsAlertDefinition.getMetricType().name(),
        newMetricsAlertDefinition.getMetricType().name());
      noChange &= areStringsSame(oldMetricsAlertDefinition.getMetricElement().name(),
        newMetricsAlertDefinition.getMetricElement().name());
      noChange &= (oldMetricsAlertDefinition.isEnabled() && newMetricsAlertDefinition.isEnabled());
    }
    return !noChange;
  }

  private boolean areStringsSame(String lhs, String rhs) {
    if(lhs == null && rhs != null) {
      return false;
    }
    if(lhs != null && rhs == null) {
      return false;
    }
    return lhs.equals(rhs);
  }

  void setPreviousRuleDefinition(RuleDefinition previousRuleDefinition) {
    this.previousRuleDefinition = previousRuleDefinition;
  }
}
