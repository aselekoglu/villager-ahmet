package dev.ahmet;

import dev.ahmet.agent.ActionBudget;
import dev.ahmet.agent.ActionPreconditions;
import dev.ahmet.agent.AgentController;
import dev.ahmet.agent.AgentGoal;
import dev.ahmet.protection.CompositeProtectionHook;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.block.Block;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates the complete mutable state of a single villager in the roster.
 * Outside of context switches, AhmetPlugin holds PRIMARY_VILLAGER_ID ("ahmet").
 */
final class VillagerSlot {
  String id;
  String displayName;
  VillagerRole role = VillagerRole.ALL_ROUNDER;
  boolean roleStateValid = true;
  NPC npc;
  SavedNpcReference savedNpcReference;
  WoodTask task;
  OperationJournal journal;
  AgentController controller;
  ProductionGoalAdapter productionGoalAdapter;
  ScheduleAdapter scheduleAdapter;
  Location chestLocation;
  Location treeTarget;
  Location navigationGoal;
  AgentGoal navigationPurpose = AgentGoal.REST_IDLE;
  int routeClears = 0;
  NavigationWatchdog navigationWatchdog;
  NavigationRetry navigationRetry;
  ForeignNavigationWait foreignNavigationWait;
  OwnedNavigationRoute taskNavigationRoute;
  FailedTargets failedTargets;
  Location home;
  FarmPlot farmPlot;
  boolean farmPlotStateValid = true;
  boolean taskStateValid = true;
  boolean deciding = false;
  int waitDecisions = 0;
  DecisionRetry decisionRetry;
  PartialDeliveryPolicy partialDeliveryPolicy;
  IncrementalTargetScanner targetScanner;
  List<Block> scanCandidates = new ArrayList<>();
  List<Block> scanEligible = new ArrayList<>();
  List<Block> scanResult = List.of();
  boolean productionCandidateAvailable = false;
  int scanEligibilityCursor = 0;
  boolean scanEvaluated = false;
  long scanCompletedAtMs = 0;
  long lastWarningMs = 0;
  String lastPlayerHelpReason;
  long treeRetryAfterMs = 0;
  boolean recoveryBlocked = false;
  Location explorationTarget;
  int explorationAttempts = 0;
  Location homeStepTarget;
  int homeStepAttempt = 0;
  boolean returningHome = false;
  Location scheduleBed;

  static VillagerSlot createDefault(String id, String displayName, VillagerRole role,
      Path journalPath, CompositeProtectionHook protectionHook,
      Runnable beginNav, Runnable endNav) {
    VillagerSlot slot = new VillagerSlot();
    slot.id = id;
    slot.displayName = displayName != null ? displayName : id;
    slot.role = role != null ? role : VillagerRole.ALL_ROUNDER;
    slot.roleStateValid = true;
    slot.savedNpcReference = new SavedNpcReference(-1);
    slot.task = new WoodTask();
    slot.journal = new OperationJournal(journalPath);
    slot.controller = new AgentController(new ActionPreconditions(protectionHook, ActionBudget.DEFAULT));
    slot.productionGoalAdapter = new ProductionGoalAdapter();
    slot.scheduleAdapter = new ScheduleAdapter(System::currentTimeMillis, beginNav, endNav);
    slot.navigationWatchdog = new NavigationWatchdog();
    slot.navigationRetry = new NavigationRetry();
    slot.foreignNavigationWait = new ForeignNavigationWait();
    slot.taskNavigationRoute = new OwnedNavigationRoute();
    slot.failedTargets = new FailedTargets();
    slot.decisionRetry = new DecisionRetry(2_000);
    slot.partialDeliveryPolicy = new PartialDeliveryPolicy();
    slot.targetScanner = new IncrementalTargetScanner();
    slot.scanCandidates = new ArrayList<>();
    slot.scanEligible = new ArrayList<>();
    slot.scanResult = List.of();
    slot.farmPlotStateValid = true;
    slot.taskStateValid = true;
    return slot;
  }
}
