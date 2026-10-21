package dev.ahmet;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Entity;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import dev.ahmet.agent.ActionBudget;
import dev.ahmet.agent.ActionPreconditions;
import dev.ahmet.agent.ActionType;
import dev.ahmet.agent.AgentController;
import dev.ahmet.agent.AgentGoal;
import dev.ahmet.agent.AgentObservation;
import dev.ahmet.agent.AgentState;
import dev.ahmet.agent.ExecutionResult;
import dev.ahmet.agent.OfferedAction;
import dev.ahmet.agent.ProductionGoalPolicy;
import dev.ahmet.protection.CompositeProtectionHook;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongConsumer;

public final class AhmetPlugin extends JavaPlugin implements Listener, TabCompleter {
  private NPC npc;
  private BrainClient brain;
  private OperationJournal journal;
  private SavedNpcReference savedNpcReference;
  private final WoodTask task=new WoodTask();
  private final PlayerPlacedWoodRegistry playerPlacedWood=new PlayerPlacedWoodRegistry();
  private final CompositeProtectionHook protectionHook=new CompositeProtectionHook();
  private final AgentController controller=new AgentController(new ActionPreconditions(protectionHook,ActionBudget.DEFAULT));
  private final ProductionGoalAdapter productionGoalAdapter=new ProductionGoalAdapter();
  private final BukkitPresentationAdapter presentation=new BukkitPresentationAdapter();
  private Location chestLocation;
  private Location treeTarget;
  private Location navigationGoal;
  private AgentGoal navigationPurpose=AgentGoal.REST_IDLE;
  private int routeClears=0;
  private final NavigationWatchdog navigationWatchdog=new NavigationWatchdog();
  private final NavigationRetry navigationRetry=new NavigationRetry();
  private final ForeignNavigationWait foreignNavigationWait=new ForeignNavigationWait();
  private final OwnedNavigationRoute taskNavigationRoute=new OwnedNavigationRoute();
  private final FailedTargets failedTargets=new FailedTargets();
  private Location home;
  private Location protectedHome;
  private int protectHomeRadius=10;
  private FarmPlot farmPlot;
  private boolean farmPlotStateValid=true;
  private boolean woodProtectionStateValid=true;
  private boolean taskStateValid=true;
  private boolean deciding=false;
  private int waitDecisions=0;
  private final DecisionRetry decisionRetry=new DecisionRetry(2_000);
  private final PartialDeliveryPolicy partialDeliveryPolicy=new PartialDeliveryPolicy();
  private final IncrementalTargetScanner targetScanner=new IncrementalTargetScanner();
  private final List<Block> scanCandidates=new ArrayList<>();
  private final List<Block> scanEligible=new ArrayList<>();
  private List<Block> scanResult=List.of();
  private boolean productionCandidateAvailable=false;
  private int scanEligibilityCursor=0;
  private boolean scanEvaluated=false;
  private long scanCompletedAtMs=0;
  private long lastWarningMs=0;
  private String lastPlayerHelpReason;
  private long treeRetryAfterMs=0;
  private boolean recoveryBlocked=false;
  private Location explorationTarget;
  private int explorationAttempts=0;
  private static final int MAX_EXPLORATION_ATTEMPTS=4;
  private static final String FOREIGN_NAVIGATION_HELP_REASON="Başka bir Citizens hareketi devam ettiği için Ahmet görevine devam edemiyor; bu hareketi durdurun veya bitmesini bekleyin.";
  private Location homeStepTarget;
  private int homeStepAttempt=0;
  private boolean returningHome=false;
  private Location scheduleBed;
  private final ActivationRangeGuard activationRangeGuard=new ActivationRangeGuard();
  private CitizensActivation citizensActivation;
  private boolean activationFailureLogged;
  private final ScheduleAdapter scheduleAdapter=new ScheduleAdapter(System::currentTimeMillis,
      this::beginOwnedNavigation,this::endOwnedNavigation);

  @Override public void onEnable() {
    saveDefaultConfig();brain=new BrainClient(this);
    citizensActivation=CitizensActivation.bind(Bukkit.getPluginManager().getPlugin("Citizens"),Bukkit.getVersion(),Entity.class);
    try {
      MaterialCatalog.write(getDataFolder().toPath().resolve("item-catalog-1.21.6.json"));
    } catch (IOException e) {
      getLogger().warning("Could not write the vanilla item catalog: " + e.getMessage());
    }
    int id=getConfig().getInt("npc-id",-1);
    savedNpcReference=new SavedNpcReference(id);
    chestLocation=loadLoc("chest"); home=loadLoc("home");
    scheduleBed=loadLoc("schedule.bed");
    protectedHome=loadLoc("protection.home");if(protectedHome==null&&home!=null)protectedHome=home.clone();
    protectHomeRadius=getConfig().getInt("options.protect-home-radius",10);
    if(protectHomeRadius<0||protectHomeRadius>32){getLogger().warning("Invalid options.protect-home-radius; using 10 blocks.");protectHomeRadius=10;}
    try{playerPlacedWood.load(getConfig().getStringList("protection.playerPlacedWood"));}
    catch(Exception e){woodProtectionStateValid=false;blockRecovery("Player-placed wood protection data is invalid",e);}
    farmPlotStateValid = true;
    try {
      farmPlot = FarmPlot.load(getConfig());
    } catch (Exception e) {
      farmPlotStateValid = false;
      blockRecovery("Saved farm plot configuration is invalid", e);
    }
    protectionHook.addHook((block, loc) -> getProtectedHomeArea() != null && getProtectedHomeArea().contains(block.getWorld().getName(), block.getX(), block.getY(), block.getZ()));
    protectionHook.addHook((block, loc) -> playerPlacedWood != null && playerPlacedWood.contains(block.getWorld().getName(), block.getX(), block.getY(), block.getZ()));
    protectionHook.addHook((block, loc) -> FarmCatalog.isCropBlock(block.getType().name())
        && (farmPlot == null || !farmPlot.contains(block.getWorld().getName(), block.getX(), block.getY(), block.getZ())));
    taskStateValid=true;
    try{task.load(getConfig());}catch(Exception e){taskStateValid=false;blockRecovery("Saved task state is invalid and must be inspected",e);}
    journal=new OperationJournal(getDataFolder().toPath().resolve("operations.json"));
    try{journal.load();if(taskStateValid)recoverJournal();}
    catch(Exception e){blockRecovery("Operation journal could not be recovered",e);}
    if(task.active && npc==null&&!savedNpcReference.pending()){getLogger().warning("Saved task exists but NPC is missing; task paused until spawned.");}
    if(taskStateValid)persist();
    Bukkit.getPluginManager().registerEvents(this,this);
    if(getCommand("ahmet")!=null)getCommand("ahmet").setTabCompleter(this);
    Bukkit.getScheduler().runTaskTimer(this,this::step,1L,1L);
    if(savedNpcReference.pending())Bukkit.getScheduler().runTaskLater(this,this::restoreSavedNpc,1L);
    getLogger().info("Villager Ahmet ready. Run /ahmet spawn as an operator to summon Ahmet.");
  }
  @Override public void onDisable(){
    try{scheduleAdapter.suspend(npc,"shutdown");if(taskStateValid)persist();flushJournalOnShutdown();}
    finally{activationRangeGuard.clear();}
  }
  private void persist(){
    if(!taskStateValid)return;
    Integer liveNpcId=npc==null?null:npc.getId();
    getConfig().set("npc-id",savedNpcReference==null?(liveNpcId==null?-1:liveNpcId):savedNpcReference.idForPersistence(liveNpcId));
    saveLoc("chest",chestLocation);saveLoc("home",home);saveLoc("protection.home",protectedHome);
    getConfig().set("options.protect-home-radius",protectHomeRadius);
    if(woodProtectionStateValid)getConfig().set("protection.playerPlacedWood",playerPlacedWood.serialize());
    if(farmPlotStateValid)FarmPlot.save(getConfig(),farmPlot);
    task.save(getConfig());
    try {
      java.nio.file.Path configFile=getDataFolder().toPath().resolve("config.yml");
      java.nio.file.Files.createDirectories(configFile.getParent());
      java.nio.file.Path temporary=configFile.resolveSibling(configFile.getFileName()+".tmp");
      try (var channel=java.nio.channels.FileChannel.open(temporary,java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,java.nio.file.StandardOpenOption.WRITE)) {
        var bytes=java.nio.ByteBuffer.wrap(getConfig().saveToString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        while(bytes.hasRemaining())channel.write(bytes);
        channel.force(true);
      }
      try { java.nio.file.Files.move(temporary,configFile,java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
      catch(java.nio.file.AtomicMoveNotSupportedException unsupported) { java.nio.file.Files.move(temporary,configFile,java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
    } catch(java.io.IOException e) { throw TaskStateSaveFailure.failClosed(e,this::lockRecovery); }
  }
  private void restoreSavedNpc(){
    if(savedNpcReference==null||!savedNpcReference.pending())return;
    NPC restored=CitizensAPI.getNPCRegistry().getById(savedNpcReference.savedId());
    savedNpcReference.recordLookup(restored==null?null:restored.getId());
    if(restored!=null){
      npc=restored;
      getLogger().info("Restored saved Citizens NPC id "+npc.getId()+" after registry load.");
      persist();
    }else if(savedNpcReference.pending()){
      Bukkit.getScheduler().runTaskLater(this,this::restoreSavedNpc,1L);
    }else{
      getLogger().warning("Saved Citizens NPC id "+savedNpcReference.savedId()+" was not found after registry load; use /ahmet spawn to recover it.");
    }
  }
  private Location loadLoc(String key) {
    String w=getConfig().getString(key+".world"); if(w==null)return null;
    World world=Bukkit.getWorld(w);if(world==null)return null;
    return new Location(world,getConfig().getDouble(key+".x"),getConfig().getDouble(key+".y"),getConfig().getDouble(key+".z"));
  }
  private void saveLoc(String key,Location value){
    getConfig().set(key,null);if(value==null)return;
    getConfig().set(key+".world",value.getWorld().getName());getConfig().set(key+".x",value.getBlockX());getConfig().set(key+".y",value.getBlockY());getConfig().set(key+".z",value.getBlockZ());
  }
  private void say(String message){
    presentation.sayNearby(npc,message);
  }
  @Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args) {
    if(!cmd.getName().equalsIgnoreCase("ahmet"))return false;
    if(args.length==0||args[0].equalsIgnoreCase("help")){sendHelp(sender);return true;}
    String op=args[0].toLowerCase();
    if(op.equals("status")){sender.sendMessage("§6Ahmet: §f"+task.describe()+" | Durum: "+controller.state()+" | Program: "+scheduleAdapter.status()+" | Yatak: "+(scheduleBed==null?"bağlı değil":coords(scheduleBed))+" | Sandık: "+(chestLocation!=null?"bağlı":"bağlı değil")+" | NPC: "+(npc!=null&&npc.isSpawned()?"aktif":"yok")+" | Korunan ev: "+(protectedHome==null||protectHomeRadius==0?"yok":protectHomeRadius+" blok")+" | Yerleştirilmiş odun işareti: "+playerPlacedWood.size()+" | Tarla: "+(farmPlot==null?"bağlı değil":farmPlot.centerX()+","+farmPlot.centerY()+","+farmPlot.centerZ()+" (r="+farmPlot.radius()+")")+(recoveryBlocked?" | Kurtarma: manuel inceleme gerekli":""));return true;}
    if(op.equals("cargo")){
      if(task.cargoItems().isEmpty())sender.sendMessage("§eAhmet'in taşıdığı kaynak yok.");
      else {
        sender.sendMessage("§6Ahmet'in taşıdığı kaynaklar (§f"+task.cargo+"§6):");
        task.cargoItems().forEach((material,amount)->sender.sendMessage("§7• "+amount+" × "+material.toLowerCase(java.util.Locale.ROOT)));
      }
      return true;
    }
    if(op.equals("items")){
      String query=String.join(" ",java.util.Arrays.copyOfRange(args,1,args.length));
      List<MaterialCatalog.Entry> matches=MaterialCatalog.search(query);
      sender.sendMessage("§6Minecraft 1.21.6 item kataloğu: §f"+MaterialCatalog.items().size()+" kayıt; eşleşen: "+matches.size());
      for(MaterialCatalog.Entry item:matches.stream().limit(10).toList()){
        sender.sendMessage("§7"+item.id()+" §8| "+(item.gatherable()?"§a"+item.capability():"§cşimdilik toplanmıyor"));
      }
      if(matches.size()>10)sender.sendMessage("§7Daha fazla sonuç var; daha dar arama kullan: /ahmet items <ad>");
      return true;
    }
    if(!(sender instanceof Player player)){sender.sendMessage("Bu komut oyun içi oyuncu gerektiriyor.");return true;}
    if(op.equals("protecthome")){
      if(!player.hasPermission("ahmet.admin")){player.sendMessage("§cYetkin yok.");return true;}
      if(args.length!=2){player.sendMessage("§eKullanım: /ahmet protecthome <0-32>; evinin ortasında çalıştır. 0 korumayı kapatır.");return true;}
      int radius;
      try{radius=Integer.parseInt(args[1]);}catch(NumberFormatException error){player.sendMessage("§cYarıçap 0-32 arasında sayı olmalı.");return true;}
      if(radius<0||radius>32){player.sendMessage("§cYarıçap 0-32 arasında olmalı.");return true;}
      protectedHome=player.getLocation().clone();protectHomeRadius=radius;persist();
      player.sendMessage(radius==0?"§aEv çevresi koruması kapatıldı.":"§aBu konumun "+radius+" blok çevresi korunacak; içindeki odunlar toplanmayacak.");return true;
    }
    if(op.equals("bed")){
      if(!player.hasPermission("ahmet.admin")){player.sendMessage("§cYetkin yok.");return true;}
      Block target=player.getTargetBlockExact(6);
      ScheduleAdapter.BedBinding binding=scheduleAdapter.bindBed(target,npc,home!=null?home:protectedHome,getConfig().getInt("options.max-distance-from-home",32),scheduleChunkAccess());
      if(!binding.valid()){player.sendMessage("§cYatak bağlanamadı: "+binding.error());return true;}
      scheduleAdapter.bedRebound();scheduleAdapter.suspend(npc,"bed rebound");scheduleBed=binding.foot();saveLoc("schedule.bed",scheduleBed);persist();
      player.sendMessage("§aAhmet'in yatağı kaydedildi: "+coords(scheduleBed));return true;
    }
    if(op.equals("collect")){
      if(args.length!=3){player.sendMessage("§eKullanım: /ahmet collect <item|odun> <1-64>");return true;}
      TaskRequest request=TaskRequest.parse(args[1],args[2]).orElse(null);
      if(request==null){player.sendMessage("§cBu kaynak desteklenmiyor veya miktar 1-64 arasında değil. Örnek aramalar: /ahmet items oak_log, /ahmet items raw_iron. Genel odun için 'odun' yaz.");return true;}
      if(npc==null||!npc.isSpawned()){player.sendMessage("§cÖnce /ahmet spawn ile Ahmet'i oluştur.");return true;}
      if(!player.getWorld().equals(npc.getEntity().getWorld())||player.getLocation().distanceSquared(npc.getEntity().getLocation())>18*18){player.sendMessage("§cGörev vermek için Ahmet'e yaklaş.");return true;}
      boolean chestBound=chestLocation!=null&&chestLocation.getWorld().equals(npc.getEntity().getWorld());
      TaskAdmission.Result admission=TaskAdmission.evaluate(true,recoveryBlocked,journal.hasOperations(),task.active,task.cargo,chestBound);
      switch(admission){
        case RECOVERY_REQUIRED -> {player.sendMessage("§cKurtarma bekliyor; yeni görev güvenli değil.");return true;}
        case BUSY -> {player.sendMessage("§cÖnce mevcut görevi bitir veya /ahmet deliver ile taşıdığı yükü boşalt.");return true;}
        case CHEST_REQUIRED -> {player.sendMessage("§cÖnce aynı dünyadaki teslimat sandığını /ahmet chest ile bağla.");return true;}
        case ACCEPT -> {}
        default -> {player.sendMessage("§cBu kaynak desteklenmiyor.");return true;}
      }
      scheduleAdapter.suspend(npc,"task admitted");
      productionGoalAdapter.prepareTaskAdmission(true,getConfig().getBoolean("options.production-goals-enabled",false),this::clearPriorNavigationForTaskAdmission);
      task.start(player.getUniqueId(),request.amount(),request.preference());navigationPurpose=AgentGoal.HARVEST_RESOURCE;treeTarget=null;failedTargets.clear();treeRetryAfterMs=0;waitDecisions=0;decisionRetry.clear();partialDeliveryPolicy.reset();controller.nextGeneration();controller.transitionTo(AgentState.PLANNING,"Task started via command");controller.clearHelp();persist();event("task_started",request.amount());
      say("Tamam, "+request.amount()+" "+request.itemName()+" toplamaya başlıyorum.");
      return true;
    }
    if(op.equals("craft")){
      if(args.length!=3){player.sendMessage("§eKullanım: /ahmet craft <oak_planks|stick> <4-64 (4'ün katı)>");return true;}
      TaskRequest.CraftRequest request=TaskRequest.parseCraft(args[1],args[2]).orElse(null);
      if(request==null){player.sendMessage("§cSadece oak_planks veya stick üretilebilir ve miktar 4 ile 64 arasında 4'ün katı olmalı (ör. 4, 8, 12, 16).");return true;}
      if(npc==null||!npc.isSpawned()){player.sendMessage("§cÖnce /ahmet spawn ile Ahmet'i oluştur.");return true;}
      if(!player.getWorld().equals(npc.getEntity().getWorld())||player.getLocation().distanceSquared(npc.getEntity().getLocation())>18*18){player.sendMessage("§cGörev vermek için Ahmet'e yaklaş.");return true;}
      boolean chestBound=chestLocation!=null&&chestLocation.getWorld()!=null&&chestLocation.getWorld().equals(npc.getEntity().getWorld());
      TaskAdmission.Result admission=TaskAdmission.evaluate(true,recoveryBlocked,journal.hasOperations(),task.active,task.cargo,chestBound);
      switch(admission){
        case RECOVERY_REQUIRED -> {player.sendMessage("§cKurtarma bekliyor; yeni görev güvenli değil.");return true;}
        case BUSY -> {player.sendMessage("§cÖnce mevcut görevi bitir veya /ahmet deliver ile taşıdığı yükü boşalt.");return true;}
        case CHEST_REQUIRED -> {player.sendMessage("§cÖnce aynı dünyadaki teslimat sandığını /ahmet chest ile bağla.");return true;}
        case ACCEPT -> {}
        default -> {player.sendMessage("§cBu üretim desteklenmiyor.");return true;}
      }
      if(!chestLocation.getWorld().isChunkLoaded(chestLocation.getBlockX()>>4,chestLocation.getBlockZ()>>4)||!(chestLocation.getBlock().getState() instanceof Chest boundChest)){
        player.sendMessage("§cİşaretli sandık yüklü değil veya artık orada değil.");return true;
      }
      CraftingPlanner.Result prePlan=CraftingPlanner.plan(craftInventorySlots(deliveryInventory(boundChest)),request.recipe(),request.amount());
      if(prePlan.failure()==CraftingPlanner.Failure.INSUFFICIENT_INPUTS){
        player.sendMessage("§cSandıkta yeterli malzeme yok ("+request.itemName()+" x"+request.amount()+" için girdi eksik).");return true;
      }
      if(prePlan.failure()==CraftingPlanner.Failure.OUTPUT_CAPACITY){
        player.sendMessage("§cSandıkta üretilecek ürünler için yeterli boş yer yok.");return true;
      }
      if(!prePlan.succeeded()){
        player.sendMessage("§cBu üretim isteği şu an gerçekleştirilemiyor.");return true;
      }
      scheduleAdapter.suspend(npc,"craft task admitted");
      productionGoalAdapter.prepareTaskAdmission(true,getConfig().getBoolean("options.production-goals-enabled",false),this::clearPriorNavigationForTaskAdmission);
      task.startCraft(player.getUniqueId(),request.recipe(),request.amount());
      navigationPurpose=AgentGoal.DELIVER_CARGO;treeTarget=null;failedTargets.clear();treeRetryAfterMs=0;waitDecisions=0;decisionRetry.clear();partialDeliveryPolicy.reset();routeClears=0;
      controller.nextGeneration();controller.transitionTo(AgentState.PLANNING,"Craft task started via command");controller.clearHelp();persist();event("task_started",request.amount());
      say("Tamam, sandığa gidip "+request.amount()+" "+request.itemName()+" üreteceğim.");
      return true;
    }
    if (op.equals("farmplot") || op.equals("plot")) {
      if (!player.hasPermission("ahmet.admin")) { player.sendMessage("§cYetkin yok."); return true; }
      if (args.length == 2) {
        if (args[1].equalsIgnoreCase("off") || args[1].equals("0")) {
          farmPlot = null; invalidateTargetScan(); persist(); player.sendMessage("§aOnaylı tarla alanı kaldırıldı."); return true;
        }
        int radius;
        try {
          radius = Integer.parseInt(args[1]);
        } catch (NumberFormatException error) {
          player.sendMessage("§cTarla yarıçapı 1-16 arasında olmalı (0 kapatır)."); return true;
        }
        if (radius < 1 || radius > 16) {
          player.sendMessage("§cTarla yarıçapı 1-16 arasında olmalı (0 kapatır)."); return true;
        }
        int cx, cy, cz;
        Block target = player.getTargetBlockExact(6);
        if (target != null && target.getType() == Material.FARMLAND) {
          cx = target.getX(); cy = target.getY() + 1; cz = target.getZ();
        } else if (target != null && FarmCatalog.isCropBlock(target.getType().name())) {
          cx = target.getX(); cy = target.getY(); cz = target.getZ();
        } else {
          Location loc = player.getLocation(); cx = loc.getBlockX(); cy = loc.getBlockY(); cz = loc.getBlockZ();
        }
        farmPlot = new FarmPlot(player.getWorld().getName(), cx, cy, cz, radius);
        invalidateTargetScan(); persist();
        player.sendMessage("§aOnaylı tarla alanı kaydedildi: " + cx + "," + cy + "," + cz + " (r=" + radius + ")"); return true;
      } else if (args.length == 5) {
        int cx, cy, cz, radius;
        try {
          cx = Integer.parseInt(args[1]);
          cy = Integer.parseInt(args[2]);
          cz = Integer.parseInt(args[3]);
          radius = Integer.parseInt(args[4]);
        } catch (NumberFormatException error) {
          player.sendMessage("§cKullanım: /ahmet farmplot <x> <y> <z> <1-16>"); return true;
        }
        if (radius < 1 || radius > 16) {
          player.sendMessage("§cKullanım: /ahmet farmplot <x> <y> <z> <1-16>"); return true;
        }
        farmPlot = new FarmPlot(player.getWorld().getName(), cx, cy, cz, radius);
        invalidateTargetScan(); persist();
        player.sendMessage("§aOnaylı tarla alanı kaydedildi: " + cx + "," + cy + "," + cz + " (r=" + radius + ")"); return true;
      } else {
        player.sendMessage("§eKullanım: /ahmet farmplot <1-16|0> veya /ahmet farmplot <x> <y> <z> <1-16>"); return true;
      }
    }
    if (op.equals("farm") || op.equals("harvest")) {
      if (args.length != 3) {
        player.sendMessage("§eKullanım: /ahmet farm <wheat|carrot|potato|ekin> <1-64>"); return true;
      }
      TaskRequest.FarmRequest request = TaskRequest.parseFarm(args[1], args[2]).orElse(null);
      if (request == null) {
        player.sendMessage("§cSadece olgun buğday (wheat), havuç (carrot), patates (potato) veya genel ekin (1-64 adet) hasat edilebilir."); return true;
      }
      if (npc == null || !npc.isSpawned()) {
        player.sendMessage("§cÖnce /ahmet spawn ile Ahmet'i oluştur."); return true;
      }
      if (!player.getWorld().equals(npc.getEntity().getWorld()) || player.getLocation().distanceSquared(npc.getEntity().getLocation()) > 18 * 18) {
        player.sendMessage("§cGörev vermek için Ahmet'e yaklaş."); return true;
      }
      boolean chestBound = chestLocation != null && chestLocation.getWorld() != null && chestLocation.getWorld().equals(npc.getEntity().getWorld());
      boolean plotBound = farmPlot != null && farmPlot.world().equals(npc.getEntity().getWorld().getName());
      TaskAdmission.Result admission = TaskAdmission.evaluateFarm(true, recoveryBlocked, journal.hasOperations(), task.active, task.cargo, chestBound, plotBound);
      switch (admission) {
        case RECOVERY_REQUIRED -> { player.sendMessage("§cKurtarma bekliyor; yeni görev güvenli değil."); return true; }
        case BUSY -> { player.sendMessage("§cÖnce mevcut görevi bitir veya /ahmet deliver ile taşıdığı yükü boşalt."); return true; }
        case CHEST_REQUIRED -> { player.sendMessage("§cÖnce aynı dünyadaki teslimat sandığını /ahmet chest ile bağla."); return true; }
        case PLOT_REQUIRED -> { player.sendMessage("§cÖnce aynı dünyada onaylı bir tarla alanını /ahmet farmplot <1-16> ile bağla."); return true; }
        case ACCEPT -> {}
        default -> { player.sendMessage("§cBu hasat görevi desteklenmiyor."); return true; }
      }
      scheduleAdapter.suspend(npc, "farm task admitted");
      productionGoalAdapter.prepareTaskAdmission(true, getConfig().getBoolean("options.production-goals-enabled", false), this::clearPriorNavigationForTaskAdmission);
      task.startFarm(player.getUniqueId(), request.amount(), request.preference());
      navigationPurpose = AgentGoal.HARVEST_RESOURCE; treeTarget = null; failedTargets.clear(); treeRetryAfterMs = 0; waitDecisions = 0; decisionRetry.clear(); partialDeliveryPolicy.reset(); routeClears = 0;
      controller.nextGeneration(); controller.transitionTo(AgentState.PLANNING, "Farm task started via command"); controller.clearHelp(); persist(); event("task_started", request.amount());
      say("Tamam, onaylı tarladan " + request.amount() + " " + request.itemName() + " hasat edip yeniden ekeceğim.");
      return true;
    }
    if(op.equals("spawn")){
      if(!player.hasPermission("ahmet.admin")){player.sendMessage("§cYetkin yok.");return true;}
      if(npc==null&&savedNpcReference.pending()){
        player.sendMessage("§eCitizens eski Ahmet kaydını yüklüyor; birkaç saniye sonra tekrar dene.");return true;
      }
      if(npc==null){npc=CitizensAPI.getNPCRegistry().createNPC(EntityType.VILLAGER,"Ahmet");savedNpcReference.created(npc.getId());}
      if(npc.isSpawned()){player.sendMessage("§eAhmet zaten dünyada.");return true;}
      Location spawnAt=task.active&&home!=null?home:player.getLocation();
      if(!npc.spawn(spawnAt)){player.sendMessage("§cAhmet dünyada oluşturulamadı.");return true;}
      if(!task.active)home=player.getLocation().clone();if(protectedHome==null&&home!=null)protectedHome=home.clone();treeRetryAfterMs=0;persist();say("Selam! İşe hazırım.");return true;
    }
    if(op.equals("summon")){
      if(!player.hasPermission("ahmet.admin")){player.sendMessage("§cYetkin yok.");return true;}
      if(task.active||recoveryBlocked){player.sendMessage("§cAhmet görevde veya kurtarma bekliyor; şu an yanına çağrılamaz.");return true;}
      if(task.cargo>0&&getConfig().getBoolean("options.production-goals-enabled",false)){
        player.sendMessage("§cAhmet'in iptalden kalan yükü var; önce /ahmet deliver ile teslim et.");return true;
      }
      if(npc==null&&savedNpcReference.pending()){
        player.sendMessage("§eCitizens eski Ahmet kaydını yüklüyor; birkaç saniye sonra tekrar dene.");return true;
      }
      Location destination=findSafeSummonLocation(player);
      if(destination==null){player.sendMessage("§cYakınında yüklü ve güvenli bir boş nokta bulamadım.");return true;}
      if(npc==null){npc=CitizensAPI.getNPCRegistry().createNPC(EntityType.VILLAGER,"Ahmet");savedNpcReference.created(npc.getId());}
      scheduleAdapter.suspend(npc,"operator summon");
      if(npc.isSpawned()){
        clearNavigation();
        npc.teleport(destination,PlayerTeleportEvent.TeleportCause.PLUGIN);
      }else if(!npc.spawn(destination)){
        player.sendMessage("§cAhmet güvenli noktada oluşturulamadı.");return true;
      }
      home=destination.clone();treeRetryAfterMs=0;persist();
      player.sendMessage("§aAhmet yanındaki güvenli noktaya geldi.");say("Yanına geldim.");return true;
    }
    if(op.equals("chest")){
      if(!player.hasPermission("ahmet.admin")){player.sendMessage("§cYetkin yok.");return true;}
      Block target=player.getTargetBlockExact(6);
      if(target==null || !(target.getState() instanceof Chest)){player.sendMessage("§cAltı blok içinde bir sandığa bakarak tekrar dene.");return true;}
      chestLocation=target.getLocation();persist();player.sendMessage("§aTeslimat sandığı kaydedildi: "+coords(chestLocation));return true;
    }
    if(op.equals("deliver")){
      if(recoveryBlocked){player.sendMessage("§cKurtarma bekliyor; teslimatı başlatmak güvenli değil.");return true;}
      if(task.cargo<=0){player.sendMessage("§eTaşınan kaynak yok.");return true;}
      if(task.active){player.sendMessage("§eÖnce görevi bitir veya iptal et.");return true;}
      if(!player.hasPermission("ahmet.admin") && !task.owner.equals(player.getUniqueId().toString())){player.sendMessage("§cYetkin yok.");return true;}
      scheduleAdapter.suspend(npc,"delivery admitted");task.resumeDelivery();treeTarget=null;controller.nextGeneration();controller.clearHelp();controller.transitionTo(AgentState.PLANNING,"Cargo delivery resumed");persist();say("Kalan kaynakları sandığa götürüyorum.");return true;
    }
    if(op.equals("cancel")){
      if(!TaskAuthorization.mayControl(player.getUniqueId().toString(),task.owner,player.hasPermission("ahmet.admin"),task.active)||!task.active){
        player.sendMessage("§cİptal edebileceğin bir görevin yok.");
        return true;
      }
      if(!canCancelOrReturnHome()){
        player.sendMessage("§cKurtarma veya bekleyen işlem var; görev şu an iptal edilemez.");
        return true;
      }
      if(cancel())say("Görevi iptal ettim. Topladığım kaynaklar şimdilik bende.");
      return true;
    }
    if(op.equals("home")||op.equals("return")){
      if(!canCancelOrReturnHome()){player.sendMessage("§cKurtarma bekliyor; işlem güvenli değil.");return true;}
      if(npc==null||!npc.isSpawned()){player.sendMessage("§cÖnce /ahmet spawn ile Ahmet'i oluştur.");return true;}
      Location effectiveHome=getEffectiveHome();
      if(effectiveHome==null){player.sendMessage("§cKayıtlı ev konumu yok.");return true;}
      if(!TaskAuthorization.mayControl(player.getUniqueId().toString(),task.owner,player.hasPermission("ahmet.admin"),task.active||task.cargo>0)){player.sendMessage("§cYetkin yok.");return true;}
      if(!productionGoalAdapter.admitReturnHome(getConfig().getBoolean("options.production-goals-enabled",false),
          task.active,task.cargo,this::cancel)){
        player.sendMessage("§cAhmet'in iptalden kalan yükü var; eve yönlendirmeden önce /ahmet deliver kullan.");return true;
      }
      scheduleAdapter.suspend(npc,"operator return-home");returningHome=true;homeStepAttempt=0;clearNavigation();persist();
      player.sendMessage("§aAhmet eve doğru yönlendirildi.");
      say("Eve doğru yürüyorum.");return true;
    }
    if(op.equals("ask")){
      if(args.length<2){player.sendMessage("§e/ahmet ask <mesaj>");return true;}
      interact(player,String.join(" ",java.util.Arrays.copyOfRange(args,1,args.length)));return true;
    }
    player.sendMessage("§eBilinmeyen komut. /ahmet");return true;
  }
  private void sendHelp(CommandSender sender){
    sender.sendMessage("§6Ahmet komutları");
    sender.sendMessage("§e/ahmet status §7— görev, program ve NPC durumu");
    sender.sendMessage("§e/ahmet cargo §7— taşıdığı kaynakların tür ve adetleri");
    sender.sendMessage("§e/ahmet collect <item|odun> <1-64> §7— doğrudan toplama görevi");
    sender.sendMessage("§e/ahmet craft <oak_planks|stick> <4-64> §7— bağlı sandıktaki malzemeyle üretim");
    sender.sendMessage("§e/ahmet farm <wheat|carrot|potato|ekin> <1-64> §7— onaylı tarladan ekin hasadı");
    sender.sendMessage("§e/ahmet items [ara] §7— 1.21.6 item kataloğu ve toplama desteği");
    sender.sendMessage("§e/ahmet ask <mesaj> §7— Ahmet'le komut/sohbet");
    sender.sendMessage("§e/ahmet deliver §7— iptalden kalan yükü sandığa teslim et");
    sender.sendMessage("§e/ahmet cancel §7— kendi görevini iptal et");
    sender.sendMessage("§e/ahmet home §7— görevi bırakıp eve dön");
    if(sender.hasPermission("ahmet.admin"))sender.sendMessage("§e/ahmet spawn | summon | chest | bed | farmplot <1-16|0> | protecthome <0-32> §7— yönetici işlemleri");
    sender.sendMessage("§7Ahmet doğal ağaç adaylarını seçer; oyuncu tarafından konan odunları ve korunan ev alanını atlar.");
  }
  @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
    if(!command.getName().equalsIgnoreCase("ahmet")||args.length==0)return List.of();
    List<String> commands=new ArrayList<>(List.of("help","status","cargo","collect","craft","farm","items","ask","deliver","cancel","home"));
    if(sender.hasPermission("ahmet.admin"))commands.addAll(List.of("spawn","summon","chest","bed","farmplot","protecthome"));
    if(args.length==1)return commands.stream().filter(value->value.startsWith(args[0].toLowerCase(java.util.Locale.ROOT))).toList();
    if(args.length==2&&args[0].equalsIgnoreCase("protecthome")&&sender.hasPermission("ahmet.admin"))return List.of("0","8","10","12","16").stream().filter(value->value.startsWith(args[1])).toList();
    if(args.length==2&&args[0].equalsIgnoreCase("farmplot")&&sender.hasPermission("ahmet.admin"))return List.of("0","4","8","12","16").stream().filter(value->value.startsWith(args[1])).toList();
    if(args.length==2&&args[0].equalsIgnoreCase("farm"))return List.of("wheat","carrot","potato","ekin","buğday","havuç","patates").stream().filter(value->value.startsWith(args[1].toLowerCase(java.util.Locale.ROOT))).toList();
    if(args.length==3&&args[0].equalsIgnoreCase("farm"))return List.of("8","16","32","64").stream().filter(value->value.startsWith(args[2])).toList();
    if(args.length==2&&args[0].equalsIgnoreCase("collect")){
      List<String> resources=new ArrayList<>(List.of("odun","oak_log","spruce_log","birch_log","jungle_log","acacia_log","dark_oak_log","mangrove_log","cherry_log","pale_oak_log","crimson_stem","warped_stem","bamboo","dirt","sand","red_sand","cobblestone","coal","raw_iron","raw_gold"));
      return resources.stream().filter(value->value.startsWith(args[1].toLowerCase(java.util.Locale.ROOT))).toList();
    }
    if(args.length==3&&args[0].equalsIgnoreCase("collect"))return List.of("8","16","32","64").stream().filter(value->value.startsWith(args[2])).toList();
    if(args.length==2&&args[0].equalsIgnoreCase("craft"))return List.of("oak_planks","stick").stream().filter(value->value.startsWith(args[1].toLowerCase(java.util.Locale.ROOT))).toList();
    if(args.length==3&&args[0].equalsIgnoreCase("craft"))return List.of("4","8","12","16","32","64").stream().filter(value->value.startsWith(args[2])).toList();
    return List.of();
  }
  private Location findSafeSummonLocation(Player player){
    World world=player.getWorld();
    return SummonTarget.find(player.getLocation(),candidate->{
      int blockX=candidate.getBlockX(),blockY=candidate.getBlockY(),blockZ=candidate.getBlockZ();
      if(!world.isChunkLoaded(blockX>>4,blockZ>>4))return false;
      Block feet=world.getBlockAt(blockX,blockY,blockZ);
      Block head=world.getBlockAt(blockX,blockY+1,blockZ);
      Block floor=world.getBlockAt(blockX,blockY-1,blockZ);
      return safeBlocks(floor,feet,head);
    });
  }
  @EventHandler public void onChat(AsyncChatEvent event){
    var player=event.getPlayer();
    String plainMessage=PlainTextComponentSerializer.plainText().serialize(event.message());
    AddressedChat.parse(plainMessage).ifPresent(message ->
      // Chat events may run asynchronously; all entity/world access stays on the server thread.
      Bukkit.getScheduler().runTask(this,()->interact(player,message)));
  }
  @EventHandler public void onInteract(PlayerInteractEntityEvent event){
    if(presentation.isAhmet(npc,event.getRightClicked())){
      event.setCancelled(true);presentation.replyToActor(npc,event.getPlayer(),"Selam! Bana sohbetten 'Ahmet, ...' yazabilirsin.");
    }
  }
  @EventHandler public void lockPendingDepositClick(InventoryClickEvent event){
    if(pendingDepositInventory(event.getView().getTopInventory()))event.setCancelled(true);
  }
  @EventHandler public void lockPendingDepositDrag(InventoryDragEvent event){
    if(pendingDepositInventory(event.getView().getTopInventory()))event.setCancelled(true);
  }
  @EventHandler public void lockPendingDepositHopper(InventoryMoveItemEvent event){
    if(pendingDepositInventory(event.getSource())||pendingDepositInventory(event.getDestination()))event.setCancelled(true);
  }
  private boolean pendingDepositInventory(Inventory inventory){
    if(inventory==null||journal==null)return false;
    if(journal.pendingDeposit().isPresent()){
      OperationJournal.Deposit pending=journal.pendingDeposit().get();
      World world=Bukkit.getWorld(pending.world());
      if(world!=null){
        Block block=world.getBlockAt(pending.x(),pending.y(),pending.z());
        if(block.getState() instanceof Chest chest){
          if(inventory==deliveryInventory(chest)||inventory.equals(deliveryInventory(chest)))return true;
        }
      }
    }
    if(journal.pendingCraft().isPresent()){
      OperationJournal.Craft pending=journal.pendingCraft().get();
      World world=Bukkit.getWorld(pending.world());
      if(world!=null){
        Block block=world.getBlockAt(pending.x(),pending.y(),pending.z());
        if(block.getState() instanceof Chest chest){
          if(inventory==deliveryInventory(chest)||inventory.equals(deliveryInventory(chest)))return true;
        }
      }
    }
    return false;
  }
  @EventHandler(ignoreCancelled=true) public void onPlayerPlaceWood(BlockPlaceEvent event){
    if(!woodProtectionStateValid)return;
    Block block=event.getBlockPlaced();
    if(!WoodMaterials.isCollectableWood(block.getType()) && !ObstructionPolicy.isLeaf(block.getType()))return;
    if(playerPlacedWood.record(block.getWorld().getName(),block.getX(),block.getY(),block.getZ()))persist();
  }


  private void interact(Player player,String message){
    if(recoveryBlocked){player.sendMessage("§cAhmet'in görev verisi kurtarma bekliyor; önce yönetici incelemeli.");return;}
    if(npc==null || !npc.isSpawned()){player.sendMessage("§cÖnce /ahmet spawn ile Ahmet'i oluştur.");return;}
    if(!player.getWorld().equals(npc.getEntity().getWorld()) || player.getLocation().distanceSquared(npc.getEntity().getLocation())>18*18){player.sendMessage("§cAhmet'le konuşmak için ona yaklaş.");return;}
    JsonObject req=new JsonObject();req.addProperty("actorUuid",player.getUniqueId().toString());req.addProperty("actorName",player.getName());req.addProperty("text",message);
    final long interactionGeneration=controller.jobGeneration();
    GroundedPerception.Snapshot perception=BukkitPerception.capture(npc,player,chestLocation,
        getConfig().getBoolean("perception.enabled",true),
        getConfig().getBoolean("perception.include-bound-chest",false),
        getConfig().getBoolean("perception.include-players",false),interactionGeneration);
    JsonObject context=new JsonObject();context.addProperty("generation",interactionGeneration);context.add("perception",com.google.gson.JsonParser.parseString(perception.toJson()));context.addProperty("busy",task.active || task.cargo>0);context.addProperty("taskActive",task.active);context.addProperty("cargo",task.cargo);context.addProperty("chestBound",chestLocation!=null);context.addProperty("plotBound",farmPlot!=null&&npc.getEntity().getWorld()!=null&&farmPlot.world().equals(npc.getEntity().getWorld().getName()));req.add("context",context);
    brain.post("/api/chat",req).whenComplete((reply,error)->Bukkit.getScheduler().runTask(this,()->{
      if(controller.jobGeneration()!=interactionGeneration)return;
      if(error!=null){player.sendMessage("§cAhmet'in beyni şu an yanıt vermiyor: "+error.getMessage());return;}
      if(!player.isOnline())return;
      String answer=reply.has("reply")?reply.get("reply").getAsString():"Hmm?";
      if(reply.has("taskSource")){
        String source=reply.get("taskSource").getAsString();
        if(source.equals("llm")||source.equals("heuristic"))getLogger().info("Gather task intent source: "+source);
      }
      boolean requestedHome=reply.has("goHome")&&reply.get("goHome").getAsBoolean();
      boolean requestedCancel=reply.has("cancel")&&reply.get("cancel").getAsBoolean();
      if((requestedHome||requestedCancel) && !TaskAuthorization.mayControl(player.getUniqueId().toString(),task.owner,player.hasPermission("ahmet.admin"),task.active||task.cargo>0)){
        player.sendMessage("§cBaşkasının görevini iptal edemez veya yönlendiremezsin.");return;
      }
      if(requestedHome||requestedCancel){
        if(!canCancelOrReturnHome()){
          getLogger().warning("Chat cancel/home rejected because operation recovery or pending operation is present.");
          answer="Görev günlüğü kurtarma veya bekleyen işlemde; şu an iptal edilemez veya eve yönlendirilemez.";
          presentation.replyToActor(npc,player,answer);
          return;
        }else{
          if(requestedCancel&&task.active)cancel();
          if(requestedHome){
            if(task.active)cancel();
            scheduleAdapter.suspend(npc,"chat return-home");returningHome=true;homeStepAttempt=0;clearNavigation();persist();
          }
        }
      }
      if(reply.has("task")&&reply.get("task").isJsonObject()){
        JsonObject spec=reply.getAsJsonObject("task");
        if(spec.has("type")&&"craft_item".equals(spec.get("type").getAsString())){
          String recipeKey=spec.has("recipe")&&spec.get("recipe").isJsonPrimitive()?spec.get("recipe").getAsString():null;
          String amountText=spec.has("amount")&&spec.get("amount").isJsonPrimitive()?spec.get("amount").getAsString():null;
          TaskRequest.CraftRequest craftReq=TaskRequest.parseCraft(recipeKey,amountText).orElse(null);
          boolean validIntent=craftReq!=null;
          boolean chestBound=chestLocation!=null&&chestLocation.getWorld()!=null&&chestLocation.getWorld().equals(npc.getEntity().getWorld());
          TaskAdmission.Result admission=TaskAdmission.evaluate(validIntent,recoveryBlocked,journal.hasOperations(),task.active,task.cargo,chestBound);
          switch(admission){
            case ACCEPT -> {
              if(!chestLocation.getWorld().isChunkLoaded(chestLocation.getBlockX()>>4,chestLocation.getBlockZ()>>4)||!(chestLocation.getBlock().getState() instanceof Chest boundChest)){
                answer="İşaretli sandık yüklü değil veya artık orada değil; önce sandığı kontrol et.";
              }else{
                CraftingPlanner.Result prePlan=CraftingPlanner.plan(craftInventorySlots(deliveryInventory(boundChest)),craftReq.recipe(),craftReq.amount());
                if(prePlan.failure()==CraftingPlanner.Failure.INSUFFICIENT_INPUTS){
                  answer="Sandıkta "+craftReq.amount()+" "+craftReq.itemName()+" üretmek için yeterli malzeme yok.";
                }else if(prePlan.failure()==CraftingPlanner.Failure.OUTPUT_CAPACITY){
                  answer="Sandıkta üretilecek ürünler için yeterli boş yer yok.";
                }else if(!prePlan.succeeded()){
                  answer="Bu üretim isteği şu an gerçekleştirilemiyor.";
                }else{
                  scheduleAdapter.suspend(npc,"craft task admitted");
                  productionGoalAdapter.prepareTaskAdmission(true,getConfig().getBoolean("options.production-goals-enabled",false),this::clearPriorNavigationForTaskAdmission);
                  task.startCraft(player.getUniqueId(),craftReq.recipe(),craftReq.amount());
                  navigationPurpose=AgentGoal.DELIVER_CARGO;treeTarget=null;failedTargets.clear();treeRetryAfterMs=0;waitDecisions=0;decisionRetry.clear();partialDeliveryPolicy.reset();routeClears=0;
                  controller.nextGeneration();controller.transitionTo(AgentState.PLANNING,"Craft task started via chat");controller.clearHelp();persist();event("task_started",craftReq.amount());
                }
              }
            }
            case UNSUPPORTED -> answer="Şimdilik sadece bağlı sandıktaki malzemelerle 4–64 arası (4'ün katı) oak_planks veya stick üretebilirim.";
            case RECOVERY_REQUIRED -> {getLogger().warning("Chat task request rejected because operation recovery/checkpoint is pending.");answer="Görev günlüğü kurtarma veya dünya kaydı bekliyor; güvenli duruma gelince tekrar dene.";}
            case BUSY -> answer="Önce mevcut görevi bitir veya taşıdığım kaynakları /ahmet deliver ile teslim et.";
            case CHEST_REQUIRED -> answer="Önce Ahmet'le aynı dünyada bir teslimat sandığı bağla: sandığa bakıp /ahmet chest kullan.";
          }
        }else if(spec.has("type")&&"harvest_crop".equals(spec.get("type").getAsString())){
          String cropKey=spec.has("crop")&&spec.get("crop").isJsonPrimitive()?spec.get("crop").getAsString():null;
          String amountText=spec.has("amount")&&spec.get("amount").isJsonPrimitive()?spec.get("amount").getAsString():null;
          TaskRequest.FarmRequest farmReq=TaskRequest.parseFarm(cropKey,amountText).orElse(null);
          boolean validIntent=farmReq!=null;
          boolean chestBound=chestLocation!=null&&chestLocation.getWorld()!=null&&chestLocation.getWorld().equals(npc.getEntity().getWorld());
          boolean plotBound=farmPlot!=null&&npc.getEntity().getWorld()!=null&&farmPlot.world().equals(npc.getEntity().getWorld().getName());
          TaskAdmission.Result admission=TaskAdmission.evaluateFarm(validIntent,recoveryBlocked,journal.hasOperations(),task.active,task.cargo,chestBound,plotBound);
          switch(admission){
            case ACCEPT -> {
              scheduleAdapter.suspend(npc,"farm task admitted");
              productionGoalAdapter.prepareTaskAdmission(true,getConfig().getBoolean("options.production-goals-enabled",false),this::clearPriorNavigationForTaskAdmission);
              task.startFarm(player.getUniqueId(),farmReq.amount(),farmReq.preference());
              navigationPurpose=AgentGoal.HARVEST_RESOURCE;treeTarget=null;failedTargets.clear();treeRetryAfterMs=0;waitDecisions=0;decisionRetry.clear();partialDeliveryPolicy.reset();routeClears=0;
              controller.nextGeneration();controller.transitionTo(AgentState.PLANNING,"Farm task started via chat");controller.clearHelp();persist();event("task_started",farmReq.amount());
            }
            case UNSUPPORTED -> answer="Şimdilik sadece onaylı tarladaki olgun buğday (wheat), havuç (carrot) ve patates (potato) ekinlerini (1–64 adet) hasat edebilirim.";
            case RECOVERY_REQUIRED -> {getLogger().warning("Chat task request rejected because operation recovery/checkpoint is pending.");answer="Görev günlüğü kurtarma veya dünya kaydı bekliyor; güvenli duruma gelince tekrar dene.";}
            case BUSY -> answer="Önce mevcut görevi bitir veya taşıdığım kaynakları /ahmet deliver ile teslim et.";
            case CHEST_REQUIRED -> answer="Önce Ahmet'le aynı dünyada bir teslimat sandığı bağla: sandığa bakıp /ahmet chest kullan.";
            case PLOT_REQUIRED -> answer="Hangi tarlayı hasat edeceğimi bilmiyorum. Önce yönetici /ahmet farmplot <1-16> komutuyla tarlayı onaylamalı.";
          }
        }else{
          String preference=null;int amount=0;
          if(spec.has("type")&&spec.has("amount")){
            amount=spec.get("amount").getAsInt();
            if(spec.get("type").getAsString().equals("collect_wood")&&spec.has("material"))
              preference=spec.get("material").getAsString();
            else if(spec.get("type").getAsString().equals("collect_item")&&spec.has("itemId"))
              preference=ResourceCatalog.preferenceFor(spec.get("itemId").getAsString()).orElse(null);
          }
          boolean validIntent=preference!=null&&amount>=1&&amount<=64&&WoodMaterials.isPreference(preference);
          boolean chestBound=chestLocation!=null&&chestLocation.getWorld().equals(npc.getEntity().getWorld());
          TaskAdmission.Result admission=TaskAdmission.evaluate(validIntent,recoveryBlocked,journal.hasOperations(),task.active,task.cargo,chestBound);
          switch(admission){
            case ACCEPT -> {
              scheduleAdapter.suspend(npc,"task admitted");
              productionGoalAdapter.prepareTaskAdmission(true,getConfig().getBoolean("options.production-goals-enabled",false),this::clearPriorNavigationForTaskAdmission);
              task.start(player.getUniqueId(),amount,preference);navigationPurpose=AgentGoal.HARVEST_RESOURCE;treeTarget=null;failedTargets.clear();treeRetryAfterMs=0;waitDecisions=0;decisionRetry.clear();partialDeliveryPolicy.reset();routeClears=0;controller.nextGeneration();controller.transitionTo(AgentState.PLANNING,"Task started via chat");controller.clearHelp();persist();event("task_started",amount);
            }
            case UNSUPPORTED -> answer="Bu item kataloğumda tanımlı, ancak güvenli toplama yeteneğim henüz yok. Şimdilik yaygın odun, toprak, kum, taş, kömür, demir ve altın kaynaklarını isteyebilirsin.";
            case RECOVERY_REQUIRED -> {getLogger().warning("Chat task request rejected because operation recovery/checkpoint is pending.");answer="Görev günlüğü kurtarma veya dünya kaydı bekliyor; güvenli duruma gelince tekrar dene.";}
            case BUSY -> answer="Önce mevcut görevi bitir veya taşıdığım kaynakları /ahmet deliver ile teslim et.";
            case CHEST_REQUIRED -> answer="Önce Ahmet'le aynı dünyada bir teslimat sandığı bağla: sandığa bakıp /ahmet chest kullan.";
          }
        }
      }
      presentation.replyToActor(npc,player,answer);
    }));
  }
  private void clearPriorNavigationForTaskAdmission(){clearNavigation();returningHome=false;homeStepAttempt=0;}
  private boolean canCancelOrReturnHome(){
    return TaskAdmission.canCancelOrReturnHome(taskStateValid,recoveryBlocked,journal==null||journal.hasPendingOperation());
  }
  private boolean cancel(){
    if(!canCancelOrReturnHome())return false;
    event("task_cancelled",task.requested);
    task.cancel();
    controller.nextGeneration();
    controller.transitionTo(AgentState.IDLE,"Task cancelled");
    controller.clearHelp();
    treeTarget=null;
    failedTargets.clear();
    treeRetryAfterMs=0;
    waitDecisions=0;
    decisionRetry.clear();
    partialDeliveryPolicy.reset();
    routeClears=0;
    clearNavigation();
    explorationAttempts=0;
    returningHome=task.cargo==0;
    persist();
    return true;
  }

  private boolean authorizeProductionGoal(AgentGoal executorBranch,Location current,long now,
      boolean routeBlocked,boolean clearOfferedByLiveJev){
    return authorizeProductionGoal(executorBranch,current,now,routeBlocked,clearOfferedByLiveJev,
        navigationRetry.pending(),false,null,null);
  }

  private boolean authorizeProductionGoal(AgentGoal executorBranch,Location current,long now,
      boolean routeBlocked,boolean clearOfferedByLiveJev,boolean routeRetryAvailable){
    return authorizeProductionGoal(executorBranch,current,now,routeBlocked,clearOfferedByLiveJev,
        routeRetryAvailable,false,null,null);
  }

  private boolean authorizeProductionGoal(AgentGoal executorBranch,Location current,long now,
      boolean routeBlocked,boolean clearOfferedByLiveJev,boolean routeRetryAvailable,
      boolean foreignNavigationWaitExpired,String helpMessage,LongConsumer helpAccepted){
    ProductionGoalAdapter.Result result=evaluateProductionGoal(executorBranch,current,routeBlocked,
        clearOfferedByLiveJev,routeRetryAvailable,foreignNavigationWaitExpired);
    if(result.status()==ProductionGoalAdapter.Status.DISABLED)return true;
    if(result.applied()){
      if(result.goal()==AgentGoal.REQUEST_PLAYER_HELP)recordProductionHelp(now,helpMessage,helpAccepted);
      return true;
    }
    if(result.goal()==AgentGoal.REQUEST_PLAYER_HELP){
      recordProductionHelp(now,helpMessage,helpAccepted);
    }
    return false;
  }

  private ProductionGoalAdapter.Result evaluateProductionGoal(AgentGoal executorBranch,Location current,
      boolean routeBlocked,boolean clearOfferedByLiveJev,boolean routeRetryAvailable,
      boolean foreignNavigationWaitExpired){
    boolean enabled=getConfig().getBoolean("options.production-goals-enabled",false);
    return productionGoalAdapter.authorize(enabled,controller,Bukkit::isPrimaryThread,
        ()->captureProductionGoalFacts(current,routeBlocked,clearOfferedByLiveJev,routeRetryAvailable,
            foreignNavigationWaitExpired),executorBranch);
  }

  private void recordProductionHelp(long now,String helpMessage,LongConsumer helpAccepted){
    if(controller.recordHelpRequest(now)){
      if(helpAccepted!=null)helpAccepted.accept(controller.helpRequestSequence());
      lastPlayerHelpReason=helpMessage==null?"Güvenli şekilde devam edebilmem için yardıma ihtiyacım var.":helpMessage;
      say(lastPlayerHelpReason);
    }
  }

  private ProductionGoalAdapter.Facts captureProductionGoalFacts(Location current,boolean routeBlocked,
      boolean clearOfferedByLiveJev,boolean routeRetryAvailable,boolean foreignNavigationWaitExpired){
    boolean pending=journal==null||journal.hasPendingOperation();
    boolean gatherActive = TaskAdmission.canExecuteHarvestOrDeliverStep(task);
    boolean scanPending=gatherActive&&!task.readyToDeliver()&&(!targetScanner.complete()||!scanEvaluated);
    boolean scanExhausted=gatherActive&&!task.readyToDeliver()&&targetScanner.complete()&&scanEvaluated&&!productionCandidateAvailable;
    boolean navigationInProgress=navigationGoal!=null&&taskNavigationRoute.matches(npc);
    Location effectiveHome=getEffectiveHome();
    boolean outsideSafeHome=outsideHomeBoundary(current,effectiveHome);
    UUID owner=taskOwner();
    long serverStep=productionGoalAdapter.globalStepId(Bukkit.getCurrentTick());
    ProductionGoalPolicy.SafetyContext safety=ProductionGoalPolicy.SafetyContext
        .builder(controller.jobGeneration(),task.taskId,owner,serverStep)
        .taskActive(gatherActive).cargo(task.cargo).readyToDeliver(task.readyToDeliver())
        .recoveryBlocked(recoveryBlocked).transactionPending(pending)
        .committedJournalHistory(journal!=null&&journal.hasOperations())
        .navigationInProgress(navigationInProgress).routeBlocked(routeBlocked)
        .routeRetryAvailable(routeRetryAvailable).foreignNavigationWaitExpired(foreignNavigationWaitExpired)
        .clearOfferedByLiveJev(clearOfferedByLiveJev)
        .targetAvailable(treeTarget!=null||productionCandidateAvailable).scanPending(scanPending)
        .scanExhausted(scanExhausted).explorationAllowed(explorationAttempts<MAX_EXPLORATION_ATTEMPTS)
        .navigationPurpose(navigationPurpose).returningHome(!task.active&&returningHome)
        .requiredSafeReturn(productionGoalAdapter.requiresSafeReturn(task.active,returningHome,
            homeStepTarget!=null,outsideSafeHome))
        .routeClears(routeClears).jobClears(task.cleared).explorationAttempts(explorationAttempts).build();
    AgentObservation observation=new AgentObservation(current,chestLocation!=null,chestLocation,getEffectiveHome(),
        navigationGoal,task.taskId,task.active,task.materialPreference,task.requested,task.collected,task.cargo,
        task.deposited,task.cleared,routeClears,explorationAttempts,List.of(),System.currentTimeMillis());
    return new ProductionGoalAdapter.Facts(observation,safety);
  }

  private UUID taskOwner(){
    try{return task.owner==null||task.owner.isBlank()?null:UUID.fromString(task.owner);}
    catch(IllegalArgumentException ignored){return null;}
  }

  private boolean outsideHomeBoundary(Location current,Location effectiveHome){
    if(current==null||effectiveHome==null||!effectiveHome.getWorld().equals(current.getWorld()))return false;
    return productionGoalAdapter.outsideHomeBoundary(task.active,current.distanceSquared(effectiveHome),
        getConfig().getInt("options.max-distance-from-home",32));
  }

  private AgentGoal currentNavigationGoal(Location current){
    if(task.active && TaskAdmission.canExecuteHarvestOrDeliverStep(task)){
      if(task.readyToDeliver())return AgentGoal.DELIVER_CARGO;
      if(outsideHomeBoundary(current,getEffectiveHome()))return AgentGoal.RETURN_HOME;
      if(navigationPurpose==AgentGoal.EXPLORE_RESOURCES||navigationPurpose==AgentGoal.RETURN_HOME)return navigationPurpose;
      return AgentGoal.HARVEST_RESOURCE;
    }
    if(task.isCraft())return AgentGoal.REST_IDLE;
    if(task.cargo>0)return AgentGoal.REST_IDLE;
    return returningHome||homeStepTarget!=null||outsideHomeBoundary(current,getEffectiveHome())
        ?AgentGoal.RETURN_HOME:AgentGoal.REST_IDLE;
  }

  private void step(){
    if(!Bukkit.isPrimaryThread())return;
    taskNavigationRoute.runStep(npc,this::stepOwnedNavigation,this::reconcileActivationRange,
        ()->scheduleAdapter.suspend(npc,"step failure"),activationRangeGuard::clear);
  }

  private void stepOwnedNavigation(){
    advanceSchedule();
    boolean presentationBlocked=task.active||task.cargo>0||recoveryBlocked||journal.hasOperations()
        ||deciding||returningHome||scheduleAdapter.ownsNavigation();
    presentation.tickLook(npc,presentationBlocked,Integer.toUnsignedLong(Bukkit.getCurrentTick()));
    if(scheduleAdapter.ownsTurn()){
      foreignNavigationWait.resetWaitForSafetyBlock();
      return;
    }
    if(recoveryBlocked || journal.hasPendingOperation()){
      foreignNavigationWait.resetWaitForSafetyBlock();
      return;
    }
    if(npc==null || !npc.isSpawned()){
      foreignNavigationWait.resetWaitForSafetyBlock();
      return;
    }
    final Location current=npc.getEntity().getLocation();
    final long now=System.currentTimeMillis();
    if(task.active && TaskAdmission.canExecuteCraftStep(task)){
      stepCraftTask(current, now);
      return;
    }
    if(task.active && !TaskAdmission.canExecuteHarvestOrDeliverStep(task)){
      foreignNavigationWait.resetWaitForSafetyBlock();
      return;
    }
    final boolean productionGoalsEnabled=getConfig().getBoolean("options.production-goals-enabled",false);
    boolean foreignRoute=task.active&&npc.getNavigator().isNavigating()&&!taskNavigationRoute.matches(npc);
    long serverTick=productionGoalAdapter.globalStepId(Bukkit.getCurrentTick());
    ForeignNavigationWait.Observation foreignWait=foreignNavigationWait.observe(task.active,task.taskId,
        taskOwner(),controller.jobGeneration(),serverTick,foreignRoute);
    if(foreignWait.helpSequenceToClear()!=null
        &&controller.clearHelp(foreignWait.helpSequenceToClear())
        &&FOREIGN_NAVIGATION_HELP_REASON.equals(lastPlayerHelpReason))lastPlayerHelpReason=null;
    if(deciding)return;
    boolean safeReturnRequired=productionGoalsEnabled&&task.active&&!task.readyToDeliver()
        &&outsideHomeBoundary(current,getEffectiveHome());
    boolean safeHomeRouteExists=productionGoalsEnabled
        &&!foreignRoute&&(navigationGoal!=null||navigationRetry.pending()||npc.getNavigator().isNavigating());
    ProductionGoalAdapter.SafeHomeAction safeHomeAction=productionGoalAdapter.ensureSafeHomeRoute(
        productionGoalsEnabled,safeReturnRequired,task.readyToDeliver(),navigationPurpose,
        navigationPurpose==AgentGoal.RETURN_HOME&&safeHomeRouteExists,this::clearNavigation,()->{
          treeTarget=null;
          invalidateTargetScan();
          homeStepTarget=null;
          navigationPurpose=AgentGoal.RETURN_HOME;
          if(!authorizeProductionGoal(AgentGoal.RETURN_HOME,current,now,false,false))return;
          Location safeStep=ExplorationPlanner.findStepTowardsHome(current,getEffectiveHome(),16.0,
              homeStepAttempt,this::isSafeGround);
          if(safeStep!=null){
            homeStepTarget=safeStep;
            navigate(NavigationTarget.fromPlanner(safeStep),AgentGoal.RETURN_HOME);
            warn("Güvenlik sınırının dışındayım, eve doğru ilerliyorum.");
          }
        });
    if(safeHomeAction==ProductionGoalAdapter.SafeHomeAction.PREEMPTED_AND_PLANNED
        ||safeHomeAction==ProductionGoalAdapter.SafeHomeAction.PLANNED)return;
    if(foreignRoute&&task.active&&task.readyToDeliver()){
      if(!authorizeProductionGoal(AgentGoal.DELIVER_CARGO,current,now,false,false))return;
      if(chestLocation==null||!chestLocation.getWorld().equals(current.getWorld())){
        warn("Teslimat sandığı kayıp veya başka dünyada.");return;
      }
      if(current.distanceSquared(chestLocation.clone().add(.5,0,.5))>3.2*3.2)
        navigate(NavigationTarget.fromBlock(chestLocation),AgentGoal.DELIVER_CARGO);
      else execute("deposit");
      return;
    }
    if(deciding)return;
    if(foreignRoute){
      if(foreignWait.status()==ForeignNavigationWait.Status.HELP_DUE
          &&foreignNavigationWait.beginHelpAttempt(serverTick)){
        if(productionGoalsEnabled){
          authorizeProductionGoal(AgentGoal.REQUEST_PLAYER_HELP,current,now,true,false,false,true,
              FOREIGN_NAVIGATION_HELP_REASON,foreignNavigationWait::markHelpAccepted);
        }else if(controller.recordHelpRequest(now)){
          foreignNavigationWait.markHelpAccepted(controller.helpRequestSequence());
          lastPlayerHelpReason=FOREIGN_NAVIGATION_HELP_REASON;
          say(lastPlayerHelpReason);
        }
      }
      return;
    }
    if(navigationRetry.pending()){
      if(!navigationRetry.consumeIfDue(now))return;
      AgentGoal retryGoal=currentNavigationGoal(current);
      if(!authorizeProductionGoal(retryGoal,current,now,false,false))return;
      if(navigationGoal!=null)issueNavigationTarget(navigationGoal);
      return;
    }
    if(navigationGoal!=null){
      boolean harvestRoute=productionGoalsEnabled
          ?task.active&&!task.readyToDeliver()&&navigationPurpose==AgentGoal.HARVEST_RESOURCE
          :task.active&&!task.readyToDeliver()&&treeTarget!=null;
      boolean reachedHarvest=harvestRoute&&treeTarget!=null
          &&treeTarget.getWorld().equals(current.getWorld())
          &&current.distanceSquared(treeTarget.clone().add(.5,0,.5))<=2.9*2.9;
      boolean reachedChest=task.active&&task.readyToDeliver()&&chestLocation!=null
          &&chestLocation.getWorld().equals(current.getWorld())
          &&current.distanceSquared(chestLocation.clone().add(.5,0,.5))<=3.2*3.2;
      double watchdogDistSq=harvestRoute&&treeTarget!=null&&treeTarget.getWorld().equals(current.getWorld())
          ? current.distanceSquared(treeTarget.clone().add(.5,0,.5))
          : current.distanceSquared(navigationGoal);
      NavigationWatchdog.Outcome outcome=(reachedHarvest||reachedChest)
          ? NavigationWatchdog.Outcome.REACHED
          : navigationWatchdog.observe(watchdogDistSq,now);
      if(outcome==NavigationWatchdog.Outcome.REACHED){
        boolean explorationRouteReached=explorationTarget!=null;
        if(explorationRouteReached&&getConfig().getBoolean("options.production-goals-enabled",false)
            &&!authorizeProductionGoal(AgentGoal.EXPLORE_RESOURCES,current,now,false,false))return;
        if(reachedHarvest||reachedChest)taskNavigationRoute.cancelIfOwned(npc);
        else taskNavigationRoute.clear();
        navigationWatchdog.reset();
        navigationGoal=null;
        navigationPurpose=AgentGoal.REST_IDLE;
        routeClears=0;
        controller.clearHelp();
        if(explorationTarget!=null){explorationTarget=null;navigationPurpose=AgentGoal.REST_IDLE;invalidateTargetScan();}
        if(homeStepTarget!=null){homeStepTarget=null;homeStepAttempt=0;}
        if(explorationRouteReached&&getConfig().getBoolean("options.production-goals-enabled",false))return;
      }else if(outcome==NavigationWatchdog.Outcome.RETRY){
        taskNavigationRoute.cancelIfOwned(npc);
        boolean waitingForHelp=getConfig().getBoolean("options.production-goals-enabled",false)
            &&controller.state()==AgentState.BLOCKED_AWAITING_HELP;
        if(task.active && !task.readyToDeliver() && (!productionGoalsEnabled
            ||(navigationPurpose==AgentGoal.HARVEST_RESOURCE&&!outsideHomeBoundary(current,getEffectiveHome())))
            &&treeTarget!=null && task.canClearObstruction()
            &&routeClears<3&&!waitingForHelp){
          if(evaluateObstructionClearing(current,navigationGoal)){
            return;
          }
        }
        AgentGoal retryGoal=currentNavigationGoal(current);
        if(!authorizeProductionGoal(retryGoal,current,now,true,false,true)){
          navigationRetry.schedule(now);
          return;
        }
        navigationRetry.schedule(now);return;
      }else if(outcome==NavigationWatchdog.Outcome.GIVE_UP){
        taskNavigationRoute.cancelIfOwned(npc);
        getLogger().warning("Navigation route gave up; npc="+coords(current)+", destination="+coords(navigationGoal)+", cargo="+task.cargo+", collected="+task.collected+"/"+task.requested);
        navigationGoal=null;navigationWatchdog.reset();
        routeClears=0;
        AgentGoal failedRouteGoal=currentNavigationGoal(current);
        ProductionGoalAdapter.Result policyResult=evaluateProductionGoal(failedRouteGoal,current,true,false,
            false,false);
        boolean enabled=productionGoalsEnabled;
        AgentGoal policyGoal=policyResult.status()==ProductionGoalAdapter.Status.DISABLED?null:policyResult.goal();
        NavigationGiveUpRecovery.Facts facts=new NavigationGiveUpRecovery.Facts(explorationTarget!=null,
            homeStepTarget!=null,task.active,task.readyToDeliver(),treeTarget!=null,task.cargo>0,enabled,
            policyGoal,now);
        NavigationGiveUpRecovery.apply(facts,new NavigationGiveUpRecovery.Effects(){
          @Override public void clearExplorationTarget(){explorationTarget=null;}
          @Override public void clearHomeStepTarget(){homeStepTarget=null;}
          @Override public void incrementHomeStepAttempt(){homeStepAttempt++;}
          @Override public void markTreeTargetFailed(long failedAt){
            Block failed=treeTarget.getBlock();
            failedTargets.mark(failed.getWorld().getName(),failed.getX(),failed.getY(),failed.getZ(),failedAt);
          }
          @Override public void clearTreeTarget(){treeTarget=null;}
          @Override public void setRetryAfter(long retryAtMillis){treeRetryAfterMs=retryAtMillis;}
          @Override public boolean beginPartialDelivery(boolean cargoAvailable){
            if(!partialDeliveryPolicy.unreachableRoute(cargoAvailable))return false;
            AhmetPlugin.this.beginPartialDelivery("Üç farklı kaynağa giden yolu bulamadım");
            return true;
          }
          @Override public void requestHelp(String reason){
            if(controller.recordHelpRequest(now))say(reason);else warn(reason);
          }
        });
        return;
      }else if(outcome==NavigationWatchdog.Outcome.WAIT&&!npc.getNavigator().isNavigating())return;
    }


    if(!task.active){
      if(task.cargo>0&&!returningHome){authorizeProductionGoal(AgentGoal.REST_IDLE,current,now,false,false);return;}
      Location effectiveHome=getEffectiveHome();
      if(effectiveHome==null||!effectiveHome.getWorld().equals(current.getWorld())){
        authorizeProductionGoal(AgentGoal.REST_IDLE,current,now,false,false);return;
      }
      double distHomeSq=current.distanceSquared(effectiveHome);
      if(distHomeSq<=2.5*2.5){
        AgentGoal idleGoal=returningHome?AgentGoal.RETURN_HOME:AgentGoal.REST_IDLE;
        if(!authorizeProductionGoal(idleGoal,current,now,false,false))return;
        if(returningHome){
          returningHome=false;
          homeStepTarget=null;
          homeStepAttempt=0;
          clearNavigation();
          say("Eve vardım.");
        }
        return;
      }
      if(returningHome || distHomeSq > 16.0){
        if(npc.getNavigator().isNavigating() || now<treeRetryAfterMs)return;
        if(!authorizeProductionGoal(AgentGoal.RETURN_HOME,current,now,false,false))return;
        Location stepTarget=ExplorationPlanner.findStepTowardsHome(current,effectiveHome,16.0,homeStepAttempt,this::isSafeGround);
        if(stepTarget!=null){
          homeStepTarget=stepTarget;
          returningHome=true;
          navigate(NavigationTarget.fromPlanner(stepTarget),AgentGoal.RETURN_HOME);
          return;
        }else{
          homeStepAttempt++;
          if(homeStepAttempt>4){
            homeStepAttempt=0;
            treeRetryAfterMs=now+10000;
            warn("Eve giden yolu bulamıyorum, önümde engel var.");
          }
        }
        if(getConfig().getBoolean("options.production-goals-enabled",false))return;
      }
      authorizeProductionGoal(AgentGoal.REST_IDLE,current,now,false,false);
      return;
    }

    if (!TaskAdmission.canExecuteHarvestOrDeliverStep(task)) {
      return;
    }

    if(explorationTarget!=null){
      List<Block> spotted=findTargets(current,task.materialPreference);
      if(spotted==null)return;
      if(!spotted.isEmpty()){
        taskNavigationRoute.cancelIfOwned(npc);
        navigationGoal=null;navigationPurpose=AgentGoal.REST_IDLE;navigationWatchdog.reset();
        explorationTarget=null;explorationAttempts=0;invalidateTargetScan();
      }
    }

    if(npc.getNavigator().isNavigating() || now<treeRetryAfterMs){
      AgentGoal activeGoal=currentNavigationGoal(current);
      authorizeProductionGoal(activeGoal,current,now,false,false);
      return;
    }

    if(task.readyToDeliver()){
      if(!authorizeProductionGoal(AgentGoal.DELIVER_CARGO,current,now,false,false))return;
      if(chestLocation==null || !chestLocation.getWorld().equals(current.getWorld())){warn("Teslimat sandığı kayıp veya başka dünyada.");return;}
      if(current.distanceSquared(chestLocation.clone().add(.5,0,.5))>3.2*3.2)navigate(NavigationTarget.fromBlock(chestLocation),AgentGoal.DELIVER_CARGO);
      else execute("deposit");
      return;
    }

    if(productionGoalsEnabled&&outsideHomeBoundary(current,getEffectiveHome())){
      if(!authorizeProductionGoal(AgentGoal.RETURN_HOME,current,now,false,false))return;
      Location safeStep=ExplorationPlanner.findStepTowardsHome(current,getEffectiveHome(),16.0,homeStepAttempt,this::isSafeGround);
      if(safeStep!=null){
        homeStepTarget=safeStep;
        navigate(NavigationTarget.fromPlanner(safeStep),AgentGoal.RETURN_HOME);
        warn("Güvenlik sınırının dışındayım, eve doğru ilerliyorum.");
      }
      return;
    }

    if(treeTarget!=null){
      if(!authorizeProductionGoal(AgentGoal.HARVEST_RESOURCE,current,now,false,false))return;
      if(current.distanceSquared(treeTarget.clone().add(.5,0,.5))>2.9*2.9){
        Location approachTarget=NavigationTarget.approachFor(treeTarget,current,this::isSafeGround);
        navigate(approachTarget,AgentGoal.HARVEST_RESOURCE);
      } else execute("harvest");
      return;
    }

    Location effectiveHome=getEffectiveHome();
    int maxDist=getConfig().getInt("options.max-distance-from-home",32);
    if(effectiveHome!=null&&effectiveHome.getWorld().equals(current.getWorld())&&current.distanceSquared(effectiveHome)>Math.pow(maxDist,2)){
      if(!authorizeProductionGoal(AgentGoal.RETURN_HOME,current,now,false,false))return;
      Location stepTarget=ExplorationPlanner.findStepTowardsHome(current,effectiveHome,16.0,homeStepAttempt,this::isSafeGround);
      if(stepTarget!=null){
        homeStepTarget=stepTarget;
        navigate(NavigationTarget.fromPlanner(stepTarget),AgentGoal.RETURN_HOME);
        warn("Güvenlik sınırının dışındayım, eve doğru ilerliyorum.");
        return;
      }
      if(getConfig().getBoolean("options.production-goals-enabled",false))return;
    }

    // JEV only chooses among Java-discovered block targets; travel and item transactions stay deterministic.
    boolean waitForDecision=decisionRetry.shouldWait(now);
    if(waitForDecision&&!productionGoalsEnabled)return;
    List<Block> found=findTargets(current,task.materialPreference);
    if(found==null){
      if(productionGoalsEnabled)authorizeProductionGoal(AgentGoal.HARVEST_RESOURCE,current,now,false,false);
      return;
    }
    if(waitForDecision)return;
    final JsonArray options=new JsonArray();
    if(found.isEmpty()){
      if(task.isFarm()){
        if(task.cargo>0){
          beginPartialDelivery("Bağlı tarlada başka olgun ekin kalmadı");
          return;
        }else{
          if(!authorizeProductionGoal(AgentGoal.REQUEST_PLAYER_HELP,current,now,false,false))return;
          explorationAttempts=0;explorationTarget=null;
          treeRetryAfterMs=now+30000;
          warn("Bağlı tarlada şu an hasat edilebilecek olgun ekin bulamadım.");
          return;
        }
      }
      if(explorationAttempts<MAX_EXPLORATION_ATTEMPTS){
        int nextAttempt=explorationAttempts+1;
        Location waypoint=ExplorationPlanner.findExplorationWaypoint(current,home,protectedHome,protectHomeRadius,maxDist,nextAttempt,this::isSafeGround);
        if(waypoint!=null){
          if(!authorizeProductionGoal(AgentGoal.EXPLORE_RESOURCES,current,now,false,false))return;
          explorationAttempts=nextAttempt;
          explorationTarget=waypoint;
          navigate(NavigationTarget.fromPlanner(waypoint),AgentGoal.EXPLORE_RESOURCES);
          say("Yakında kaynak bulamadım, etrafta araştırıyorum...");
          return;
        }
        explorationAttempts=nextAttempt;
      }
      if(partialDeliveryPolicy.emptyScan(task.cargo>0)){beginPartialDelivery("Yakında ulaşabileceğim başka kaynak bulamadım");return;}
      if(!authorizeProductionGoal(AgentGoal.REQUEST_PLAYER_HELP,current,now,false,false))return;
      explorationAttempts=0;explorationTarget=null;
      treeRetryAfterMs=now+30000;
      warn("Yakınımda güvenle toplayabileceğim uygun kaynak bulamadım. Biraz yaklaş veya sonra tekrar dene.");
      return;
    }
    if(!authorizeProductionGoal(AgentGoal.HARVEST_RESOURCE,current,now,false,false))return;
    explorationAttempts=0;explorationTarget=null;
    partialDeliveryPolicy.targetsFound();
    List<OfferedAction> offeredActionList=new ArrayList<>();
    LinkedHashMap<String,Location> offeredTargets=new LinkedHashMap<>();
    for(int i=0;i<found.size();i++){
      String id="target_"+(char)('a'+i);Block block=found.get(i);
      double distance=Math.sqrt(current.distanceSquared(block.getLocation()));
      String itemName=expectedOutput(block,task.materialPreference);
      String output= itemName==null?block.getType().name():itemName;
      String desc="Walk to "+block.getType().name().toLowerCase(java.util.Locale.ROOT)+" and collect "+output.toLowerCase(java.util.Locale.ROOT)+", distance "+Math.round(distance)+" blocks";
      options.add(candidate(id,desc));
      offeredTargets.put(id,block.getLocation());
      offeredActionList.add(OfferedAction.of(id,ActionType.GATHER_TARGET,desc,block.getLocation()));
    }
    options.add(candidate("wait","Wait briefly to assess safety; no progress"));
    offeredActionList.add(OfferedAction.of("wait",ActionType.WAIT,"Wait briefly to assess safety; no progress"));
    controller.registerOfferedActions(offeredActionList);
    controller.transitionTo(AgentState.AWAITING_DECISION,"Awaiting target choice");
    JsonObject req=new JsonObject();JsonObject observation=new JsonObject();
    if(task.isFarm()){
      observation.addProperty("task","harvest_crop");
      observation.addProperty("requiredAction","select_crop");
    } else {
      boolean woodTask=!task.materialPreference.startsWith("item:");
      observation.addProperty("task",woodTask?"collect_wood":"collect_item");
      observation.addProperty("requiredAction",woodTask?"select_tree":"select_resource");
    }
    observation.addProperty("materialPreference",task.materialPreference);observation.addProperty("requested",task.requested);observation.addProperty("collected",task.collected);
    observation.addProperty("cargo",task.cargo);observation.addProperty("deposited",task.deposited);
    observation.addProperty("chestBound",chestLocation!=null);observation.addProperty("position",coords(current));
    final long generation=controller.jobGeneration();
    GroundedPerception.Snapshot perception=BukkitPerception.capture(npc,taskViewer(),chestLocation,
        getConfig().getBoolean("perception.enabled",true),
        getConfig().getBoolean("perception.include-bound-chest",false),
        getConfig().getBoolean("perception.include-players",false),generation);
    observation.addProperty("generation",generation);observation.add("perception",com.google.gson.JsonParser.parseString(perception.toJson()));
    req.add("observation",observation);req.add("actions",options);
    deciding=true;endOwnedNavigation();
    brain.post("/api/decide",req).whenComplete((response,error)->Bukkit.getScheduler().runTask(this,()->{
      deciding=false;if(!task.active || npc==null || !npc.isSpawned() || controller.jobGeneration()!=generation)return;
      // JEV may select only an offered target ID. Java retains each real block location.
      String selected=error==null&&response.has("id")?response.get("id").getAsString():"wait";
      String source=error==null&&response.has("source")?response.get("source").getAsString():"fallback";
      String model=error==null&&response.has("model")?response.get("model").getAsString():"none";
      if(!Set.of("jev","heuristic","forced","fallback").contains(source))source="unknown";
      model=model.replaceAll("[^A-Za-z0-9._-]","");if(model.length()>64)model=model.substring(0,64);
      OfferedAction validatedAction=controller.validateDecision(selected,source,generation);
      String candidateId=validatedAction!=null?validatedAction.id():"unoffered";
      Location selectedTarget=validatedAction!=null?offeredTargets.get(validatedAction.id()):null;
      if(selectedTarget!=null&&!isEligibleTarget(selectedTarget.getBlock(),task.materialPreference))selectedTarget=null;
      if(selectedTarget!=null){
        decisionRetry.clear();treeTarget=selectedTarget;routeClears=0;treeRetryAfterMs=0;waitDecisions=0;
        controller.transitionTo(AgentState.NAVIGATING,"Target selected: "+candidateId);
        getLogger().info("Gather target selection source: "+source+", model: "+model+", candidate: "+candidateId+", block="+coords(selectedTarget));
      } else {
        decisionRetry.schedule(System.currentTimeMillis());
        getLogger().info("Gather target selection source: "+source+", model: "+model+", candidate: "+candidateId);
        if(++waitDecisions>=3&&!offeredTargets.isEmpty()){
          waitDecisions=0;treeTarget=offeredTargets.values().iterator().next();routeClears=0;
          controller.transitionTo(AgentState.NAVIGATING,"Fallback target selected after wait exhaustion");
        }
      }
    }));
  }

  private void advanceSchedule(){
    boolean journalBusy=journal==null||journal.hasOperations()||journal.hasPendingOperation();
    boolean allowed=VillagerSchedule.allowed(task.active,task.cargo,recoveryBlocked,journalBusy);
    Location effectiveHome=home!=null?home:protectedHome;
    scheduleAdapter.tick(npc,effectiveHome,scheduleBed,getConfig().getBoolean("options.schedule-enabled",true),!allowed,returningHome,
        getConfig().getInt("options.max-distance-from-home",32),this::isSafeGround,
        (current,targetHome)->ExplorationPlanner.findStepTowardsHome(current,targetHome,16.0,0,this::isSafeGround),scheduleChunkAccess());
  }

  private void clearScheduleNavigation(String reason){scheduleAdapter.suspend(npc,reason);}

  private Player taskViewer(){
    if(task.owner==null)return null;
    try{return Bukkit.getPlayer(java.util.UUID.fromString(task.owner));}
    catch(IllegalArgumentException ignored){return null;}
  }

  private ScheduleAdapter.ChunkAccess scheduleChunkAccess(){
    return new ScheduleAdapter.ChunkAccess(){
      @Override public boolean loaded(World world,int x,int z){return world.isChunkLoaded(x>>4,z>>4);}
      @Override public Material material(World world,int x,int y,int z){return world.getBlockAt(x,y,z).getType();}
      @Override public org.bukkit.block.data.BlockData data(World world,int x,int y,int z){return world.getBlockAt(x,y,z).getBlockData();}
      @Override public boolean safeStandingCell(Location location){return isSafeGround(location);}
    };
  }

  private boolean evaluateObstructionClearing(Location current,Location goal){
    if(!TaskAdmission.canExecuteGatherStep(task))return false;
    if(deciding)return false;
    List<ObstructionPolicy.CandidateObstruction> candidates=ObstructionPolicy.findCandidates(current,goal,getProtectedHomeArea(),playerPlacedWood);
    if(candidates.isEmpty()){
      World world=current.getWorld();
      Block floor=world.getBlockAt(current.getBlockX(),current.getBlockY()-1,current.getBlockZ());
      if(ObstructionPolicy.isLeaf(floor.getType())){
        if(controller.recordHelpRequest(System.currentTimeMillis())){
          say("Altımdaki yaprağı güvenle kıramıyorum, düşme tehlikesi var. Yolu açmama yardım eder misin?");
        }else{
          warn("Önümdeki yaprakları güvenle kıramıyorum, düşme tehlikesi var. Yolu açmama yardım eder misin?");
        }
      }

      return false;
    }
    final JsonArray options=new JsonArray();
    final LinkedHashMap<String,ObstructionPolicy.CandidateObstruction> offeredObstructions=new LinkedHashMap<>();
    List<OfferedAction> obstructionActions=new ArrayList<>();
    for(int i=0;i<candidates.size();i++){
      String id="clear_"+(char)('a'+i);
      ObstructionPolicy.CandidateObstruction c=candidates.get(i);
      Block b=c.block();
      double dist=Math.sqrt(current.distanceSquared(b.getLocation().add(0.5,0.5,0.5)));
      String desc="Clear natural "+b.getType().name().toLowerCase(java.util.Locale.ROOT)
          +(c.isSupportLeaf()?" support leaf under feet":" obstruction")
          +", distance "+Math.round(dist)+" blocks";
      options.add(candidate(id,desc));
      offeredObstructions.put(id,c);
      obstructionActions.add(OfferedAction.of(id,ActionType.CLEAR_OBSTRUCTION,desc,c));
    }
    options.add(candidate("retry_route","Retry navigation to target without modifying blocks"));
    obstructionActions.add(OfferedAction.of("retry_route",ActionType.RETRY_ROUTE,"Retry navigation to target without modifying blocks"));
    options.add(candidate("wait","Wait briefly to assess safety; no progress"));
    obstructionActions.add(OfferedAction.of("wait",ActionType.WAIT,"Wait briefly to assess safety; no progress"));
    controller.registerOfferedActions(obstructionActions);
    controller.transitionTo(AgentState.AWAITING_DECISION,"Awaiting obstruction decision");

    JsonObject req=new JsonObject();JsonObject observation=new JsonObject();
    boolean woodTask=!task.materialPreference.startsWith("item:");
    observation.addProperty("task",woodTask?"collect_wood":"collect_item");
    observation.addProperty("materialPreference",task.materialPreference);
    observation.addProperty("requested",task.requested);
    observation.addProperty("collected",task.collected);
    observation.addProperty("cargo",task.cargo);
    observation.addProperty("deposited",task.deposited);
    observation.addProperty("requiredAction","clear_obstruction");
    observation.addProperty("chestBound",chestLocation!=null);
    observation.addProperty("position",coords(current));
    observation.addProperty("routeClears",routeClears);
    final long generation=controller.jobGeneration();
    GroundedPerception.Snapshot perception=BukkitPerception.capture(npc,taskViewer(),chestLocation,
        getConfig().getBoolean("perception.enabled",true),
        getConfig().getBoolean("perception.include-bound-chest",false),
        getConfig().getBoolean("perception.include-players",false),generation);
    observation.addProperty("generation",generation);observation.add("perception",com.google.gson.JsonParser.parseString(perception.toJson()));
    req.add("observation",observation);req.add("actions",options);

    deciding=true;endOwnedNavigation();
    brain.post("/api/decide",req).whenComplete((response,error)->Bukkit.getScheduler().runTask(this,()->{
      deciding=false;
      if(!Bukkit.isPrimaryThread())return;
      if(!task.active||npc==null||!npc.isSpawned()||controller.jobGeneration()!=generation)return;

      String selected=error==null&&response.has("id")?response.get("id").getAsString():"wait";
      String source=error==null&&response.has("source")?response.get("source").getAsString():"fallback";
      String model=error==null&&response.has("model")?response.get("model").getAsString():"none";
      if(!Set.of("jev","heuristic","forced","fallback").contains(source))source="unknown";
      model=model.replaceAll("[^A-Za-z0-9._-]","");
      if(model.length()>64)model=model.substring(0,64);

      OfferedAction validatedAction=controller.validateDecision(selected,source,generation);
      if(selected.startsWith("clear_")){
        if(validatedAction==null||error!=null||!"jev".equals(source)){
          getLogger().info("Clear obstruction choice rejected due to non-JEV source or unoffered ID: "+source+", model: "+model+"; scheduling navigation retry");
          navigationRetry.schedule(System.currentTimeMillis());
          return;
        }

        ObstructionPolicy.CandidateObstruction chosen=offeredObstructions.get(selected);
        if(chosen==null||!task.canClearObstruction()||routeClears>=3){
          navigationRetry.schedule(System.currentTimeMillis());
          return;
        }

        long callbackNow=System.currentTimeMillis();
        if(!authorizeProductionGoal(AgentGoal.CLEAR_OBSTRUCTION,npc.getEntity().getLocation(),callbackNow,true,true)){
          navigationRetry.schedule(callbackNow);
          return;
        }

        Block block=chosen.block();
        Location nowLoc=npc.getEntity().getLocation();
        boolean currentlySupport=ActionPreconditions.isCurrentSupportLeaf(nowLoc,block);
        ExecutionResult preRes=controller.preconditions().validateClear(nowLoc,block,chosen.isSupportLeaf(),routeClears,task.cleared);
        if(!preRes.isSuccess()||!ObstructionPolicy.isEligibleLeaf(block,getProtectedHomeArea(),playerPlacedWood)){
          getLogger().warning("Obstruction block precondition/revalidation failed at "+coords(block.getLocation())+": "+preRes.message());
          if(currentlySupport){
            if(controller.recordHelpRequest(System.currentTimeMillis())){
              say("Altımdaki yaprağı güvenle kıramıyorum, düşme tehlikesi var. Yolu açmama yardım eder misin?");
            }else{
              warn("Altımdaki yaprağı güvenle kıramıyorum, düşme tehlikesi var.");
            }
          }
          navigationRetry.schedule(System.currentTimeMillis());
          return;
        }
        if(!ObstructionPolicy.matchesOfferedActor(chosen,nowLoc)||!ObstructionPolicy.matchesOfferedState(chosen,block)){
          getLogger().warning("Obstruction changed after model selection; refusing to clear at "+coords(block.getLocation()));
          navigationRetry.schedule(System.currentTimeMillis());
          return;
        }

        controller.transitionTo(AgentState.EXECUTING,"Clearing obstruction: "+block.getType().name());
        if(!clearObstruction(block))return;
        routeClears++;
        getLogger().info("Obstruction cleared by JEV ("+model+"): "+block.getType().name()+" at "+coords(block.getLocation())+", routeClears="+routeClears+", jobClears="+task.cleared);
        controller.transitionTo(AgentState.NAVIGATING,"Resuming navigation after clear");
        if(navigationGoal!=null){
          issueNavigationTarget(navigationGoal);
        }
        return;
      }

      if(!authorizeProductionGoal(AgentGoal.HARVEST_RESOURCE,npc.getEntity().getLocation(),System.currentTimeMillis(),true,false,true))return;
      navigationRetry.schedule(System.currentTimeMillis());
    }));
    return true;
  }
  private boolean clearObstruction(Block block){
    OperationJournal.Clear operation=new OperationJournal.Clear(task.taskId,block.getWorld().getName(),
        block.getX(),block.getY(),block.getZ(),block.getType().name());
    return ClearTransaction.execute(
        ()->{task.recordClear();persist();},
        ()->journal.prepareClear(operation),
        ()->block.setType(Material.AIR,false),
        journal::completeClear,
        ()->saveJournalWorlds(block.getWorld()),
        journal::clearAfterWorldSave,
        failure->blockRecovery("Clear transaction failed; recovery is locked and its journal is retained",failure));
  }
  private JsonObject candidate(String id,String description){JsonObject result=new JsonObject();result.addProperty("id",id);result.addProperty("description",description);return result;}

  private void stepCraftTask(Location current, long now) {
    if (!TaskAdmission.canExecuteCraftStep(task)) return;
    if (task.readyToFinish()) {
      finishTaskIfReady();
      return;
    }
    boolean foreignRoute = npc.getNavigator().isNavigating() && !taskNavigationRoute.matches(npc);
    long serverTick = productionGoalAdapter.globalStepId(Bukkit.getCurrentTick());
    ForeignNavigationWait.Observation foreignWait = foreignNavigationWait.observe(task.active, task.taskId,
        taskOwner(), controller.jobGeneration(), serverTick, foreignRoute);
    if (foreignWait.helpSequenceToClear() != null
        && controller.clearHelp(foreignWait.helpSequenceToClear())
        && FOREIGN_NAVIGATION_HELP_REASON.equals(lastPlayerHelpReason)) {
      lastPlayerHelpReason = null;
    }
    if (foreignRoute) {
      if (foreignWait.status() == ForeignNavigationWait.Status.HELP_DUE
          && foreignNavigationWait.beginHelpAttempt(serverTick)
          && controller.recordHelpRequest(now)) {
        foreignNavigationWait.markHelpAccepted(controller.helpRequestSequence());
        lastPlayerHelpReason = FOREIGN_NAVIGATION_HELP_REASON;
        say(lastPlayerHelpReason);
      }
      return;
    }
    if (chestLocation == null || chestLocation.getWorld() == null || !chestLocation.getWorld().equals(current.getWorld())) {
      abortCraftTask("İşaretli sandık kayıp veya başka dünyada; üretim görevini iptal ettim.");
      return;
    }
    if (current.distanceSquared(chestLocation.clone().add(0.5, 0, 0.5)) <= 3.2 * 3.2) {
      if (navigationGoal != null || navigationRetry.pending()) {
        clearNavigation();
      }
      controller.clearHelp();
      execute("craft");
      return;
    }
    if (navigationRetry.pending()) {
      if (!navigationRetry.consumeIfDue(now)) return;
      if (navigationGoal != null) {
        controller.transitionTo(AgentState.NAVIGATING, "Retrying craft chest route");
        issueNavigationTarget(navigationGoal);
      }
      return;
    }
    if (navigationGoal != null) {
      double watchdogDistSq = current.distanceSquared(navigationGoal);
      NavigationWatchdog.Outcome outcome = navigationWatchdog.observe(watchdogDistSq, now);
      if (outcome == NavigationWatchdog.Outcome.REACHED) {
        clearNavigation();
        controller.clearHelp();
        if (current.distanceSquared(chestLocation.clone().add(0.5, 0.5, 0.5)) <= ActionPreconditions.MAX_REACH_SQ) {
          execute("craft");
        }
        return;
      } else if (outcome == NavigationWatchdog.Outcome.RETRY) {
        taskNavigationRoute.cancelIfOwned(npc);
        endOwnedNavigation();
        navigationRetry.schedule(now);
        return;
      } else if (outcome == NavigationWatchdog.Outcome.GIVE_UP) {
        clearNavigation();
        treeRetryAfterMs = now + 30_000;
        String reason = "Sandığa giden yolu bulamadım. Sandık çevresindeki yolu açın.";
        if (controller.recordHelpRequest(now)) say(reason);
        else warn(reason);
        return;
      } else if (outcome == NavigationWatchdog.Outcome.WAIT && !npc.getNavigator().isNavigating()) {
        return;
      }
      return;
    }
    if (now < treeRetryAfterMs) return;
    controller.transitionTo(AgentState.NAVIGATING, "Walking to bound chest for crafting");
    navigate(NavigationTarget.fromBlock(chestLocation), AgentGoal.DELIVER_CARGO);
  }

  private void execute(String action){
    Location here=npc.getEntity().getLocation();
    switch(action){
      case "harvest" -> {
        if(!TaskAdmission.canExecuteHarvestOrDeliverStep(task))return;
        if(treeTarget==null || here.distanceSquared(treeTarget.clone().add(.5,.5,.5))>ActionPreconditions.MAX_REACH_SQ){treeTarget=null;return;}
        Block block=treeTarget.getBlock();
        ExecutionResult pre=controller.preconditions().validateGather(here,block,task.materialPreference,task.cargo);
        if(!pre.isSuccess()||!isEligibleTarget(block,task.materialPreference)){treeTarget=null;invalidateTargetScan();return;}
        controller.transitionTo(AgentState.EXECUTING,"Harvesting "+block.getType().name());
        harvest(block);
      }
      case "deposit" -> {
        if(!TaskAdmission.canExecuteHarvestOrDeliverStep(task))return;
        Block block=chestLocation.getBlock();
        ExecutionResult pre=controller.preconditions().validateDeposit(here,block,task.cargo);
        if(!pre.isSuccess()||!(block.getState() instanceof Chest chest)){warn("İşaretli sandık artık orada değil.");return;}
        controller.transitionTo(AgentState.EXECUTING,"Depositing cargo");
        deposit(chest);
      }
      case "craft" -> {
        if (!TaskAdmission.canExecuteCraftStep(task)) return;
        if (chestLocation == null || chestLocation.getWorld() == null) {
          abortCraftTask("İşaretli sandık artık bağlı değil; üretim görevini iptal ettim.");
          return;
        }
        Block block = chestLocation.getBlock();
        ExecutionResult pre = controller.preconditions().validateCraft(here, block);
        if (!pre.isSuccess() || !(block.getState() instanceof Chest chest)) {
          if (pre.message() != null && pre.message().contains("out of reach")) return;
          abortCraftTask("İşaretli sandık artık orada değil veya yüklü değil; üretim görevini iptal ettim.");
          return;
        }
        controller.transitionTo(AgentState.EXECUTING, "Crafting " + task.craftRecipe().key());
        craft(chest);
      }
      default -> {}
    }
  }
  private void harvest(Block block){
    if(!TaskAdmission.canExecuteHarvestOrDeliverStep(task))return;
    String outputMaterial=expectedOutput(block,task.materialPreference);
    if(outputMaterial==null){treeTarget=null;return;}
    OperationJournal.Harvest operation;
    if(task.isFarm()){
      operation=OperationJournal.Harvest.forCrop(task.taskId,block.getWorld().getName(),
        block.getX(),block.getY(),block.getZ(),task.collected,task.cargo,block.getType().name(),outputMaterial,task.cargoCount(outputMaterial));
    }else{
      operation=new OperationJournal.Harvest(task.taskId,block.getWorld().getName(),
        block.getX(),block.getY(),block.getZ(),task.collected,task.cargo,block.getType().name(),outputMaterial,task.cargoCount(outputMaterial));
    }
    try{
      journal.prepareHarvest(operation);
      // Write-ahead record precedes the world mutation; task YAML follows it.
      if(task.isFarm()){
        if(block.getBlockData() instanceof org.bukkit.block.data.Ageable ageable){
          org.bukkit.block.data.Ageable replanted=(org.bukkit.block.data.Ageable)ageable.clone();
          replanted.setAge(0);
          block.setBlockData(replanted,false);
        }else{
          throw new IllegalStateException("Harvest target is not ageable crop: "+block.getType());
        }
      }else{
        block.setType(Material.AIR,false);
      }
      task.harvestOne(outputMaterial);
      persist();
      journal.completeHarvest();
      presentation.committedWorkCue(npc,true);
      treeTarget=null;
      navigationGoal=null;
      navigationWatchdog.reset();
      routeClears=0;
      partialDeliveryPolicy.harvested();
      invalidateTargetScan();
      if(task.collected>=task.requested)say(task.collected+" kaynağı topladım, sandığa gidiyorum.");
    }catch(Exception e){
      treeTarget=null;
      blockRecovery("Harvest transaction is pending; the job is paused",e);
    }
  }
  private void deposit(Chest chest){
    if(!TaskAdmission.canExecuteHarvestOrDeliverStep(task))return;
    if(task.cargo<=0)return;
    String material=task.nextCargoMaterial();if(material==null)return;
    int amount=task.cargoItems().getOrDefault(material,0);if(amount<=0)return;
    Inventory inventory=deliveryInventory(chest);
    try{
      List<CargoDepositPlanner.Slot> before=inventorySlots(inventory);
      CargoDepositPlanner.Plan plan=CargoDepositPlanner.plan(before,material,amount);
      if(plan.moved()==0){
        getLogger().warning("Delivery chest has no capacity across "+inventory.getSize()+" slots at "+coords(chestLocation)+"; cargo="+task.cargo);
        warn("Sandığın tamamında yer yok ("+inventory.getSize()+" yuva); "+task.cargo+" kaynak hâlâ yanımda.");return;
      }
      List<CargoDepositPlanner.Slot> after=CargoDepositPlanner.apply(before,plan);
      OperationJournal.Deposit operation=new OperationJournal.Deposit(task.taskId,chest.getWorld().getName(),
        chestLocation.getBlockX(),chestLocation.getBlockY(),chestLocation.getBlockZ(),task.cargo,task.deposited,
        plan.moved(),plan.changes(),material,inventory.getSize());
      journal.prepareDeposit(operation);
      writeInventoryPlan(inventory,after,plan.changes());
      task.deposit(material,plan.moved());
      persist();
      saveJournalWorlds(chest.getWorld());
      journal.clearAfterWorldSave();
      if(plan.moved()>0)presentation.committedWorkCue(npc,true);
      if(plan.moved()<amount){warn("Sandık dolu; "+task.cargo+" kaynak hâlâ yanımda.");return;}
      if(task.cargo>0)return;
      finishTaskIfReady();
    }catch(Exception e){
      blockRecovery("Deposit transaction is pending; the job is paused",e);
    }
  }

  private void abortCraftTask(String reason) {
    if (!TaskAdmission.canExecuteCraftStep(task)) return;
    if (!canCancelOrReturnHome()) return;
    if (cancel()) {
      say(reason);
    }
  }

  private void craft(Chest chest) {
    if (!TaskAdmission.canExecuteCraftStep(task)) return;
    CraftRecipe recipe = task.craftRecipe();
    if (recipe == null || chestLocation == null) return;
    if (!chest.getWorld().equals(chestLocation.getWorld())
        || chest.getX() != chestLocation.getBlockX()
        || chest.getY() != chestLocation.getBlockY()
        || chest.getZ() != chestLocation.getBlockZ()) {
      abortCraftTask("İşaretli sandık konumu değişmiş; hiçbir eşyaya dokunmadan üretim görevini iptal ettim.");
      return;
    }
    Inventory inventory = deliveryInventory(chest);
    try {
      List<CraftingPlanner.Slot> before = craftInventorySlots(inventory);
      CraftingPlanner.Result result = CraftingPlanner.plan(before, recipe, task.requested);
      if (result.failure() == CraftingPlanner.Failure.INSUFFICIENT_INPUTS) {
        abortCraftTask("Sandıkta yeterli malzeme kalmamış; hiçbir eşyaya dokunmadan üretim görevini iptal ettim.");
        return;
      }
      if (result.failure() == CraftingPlanner.Failure.OUTPUT_CAPACITY) {
        abortCraftTask("Sandıkta üretilecek ürünler için yer kalmamış; hiçbir eşyaya dokunmadan üretim görevini iptal ettim.");
        return;
      }
      if (!result.succeeded()) {
        abortCraftTask("Üretim planı doğrulanamadı; hiçbir eşyaya dokunmadan üretim görevini iptal ettim.");
        return;
      }
      CraftingPlanner.Plan plan = result.plan();
      OperationJournal.Craft operation = OperationJournal.Craft.fromPlan(
          task.taskId,
          task.owner,
          chest.getWorld().getName(),
          chestLocation.getBlockX(),
          chestLocation.getBlockY(),
          chestLocation.getBlockZ(),
          plan
      );
      journal.prepareCraft(operation);
      writeCraftInventoryPlan(inventory, plan);
      task.completeCraft(plan.outputCount());
      persist();
      saveJournalWorlds(chest.getWorld());
      journal.clearAfterWorldSave();
      presentation.committedWorkCue(npc, true);
      finishTaskIfReady();
    } catch (Exception e) {
      blockRecovery("Craft transaction is pending; the job is paused", e);
    }
  }
  private List<CargoDepositPlanner.Slot> inventorySlots(Inventory inventory){
    List<CargoDepositPlanner.Slot> slots=new ArrayList<>();
    int emptyLimit=Math.min(64,inventory.getMaxStackSize());
    for(ItemStack item:inventory.getContents()){
      if(item==null||item.getType().isAir())slots.add(CargoDepositPlanner.Slot.empty(emptyLimit));
      else slots.add(new CargoDepositPlanner.Slot(item.getType().name(),item.getAmount(),Math.min(64,item.getMaxStackSize()), item.isSimilar(new ItemStack(item.getType()))));
    }
    return List.copyOf(slots);
  }
  private Inventory deliveryInventory(Chest chest){
    // getBlockInventory() returns only one 27-slot half of a double chest. Paper's
    // getInventory() resolves the full 54-slot DoubleInventory when both halves exist.
    return chest.getInventory();
  }
  private void writeInventoryPlan(Inventory inventory,List<CargoDepositPlanner.Slot> after,
      List<CargoDepositPlanner.Change> changes){
    for(CargoDepositPlanner.Change change:changes){
      CargoDepositPlanner.Slot slot=after.get(change.slot());
      if(!WoodMaterials.isAllowedName(slot.material()))throw new IllegalStateException("Deposit plan has an invalid item");
      Material material=Material.matchMaterial(slot.material());
      if(material==null)throw new IllegalStateException("Deposit item material is unavailable");
      inventory.setItem(change.slot(),new ItemStack(material,slot.amount()));
    }
  }
  private List<CraftingPlanner.Slot> craftInventorySlots(Inventory inventory){
    List<CraftingPlanner.Slot> slots=new ArrayList<>();
    int emptyLimit=Math.min(64,inventory.getMaxStackSize());
    for(ItemStack item:inventory.getContents()){
      if(item==null||item.getType().isAir())slots.add(CraftingPlanner.Slot.empty(emptyLimit));
      else slots.add(new CraftingPlanner.Slot(item.getType().name(),item.getAmount(),Math.min(64,item.getMaxStackSize()),item.isSimilar(new ItemStack(item.getType()))));
    }
    return List.copyOf(slots);
  }
  private void writeCraftInventoryPlan(Inventory inventory,CraftingPlanner.Plan plan){
    List<CraftingPlanner.Slot> currentSlots=craftInventorySlots(inventory);
    OperationRecovery.applyCraftChanges(currentSlots,plan,(slot,materialName,amount)->{
      if(materialName==null||amount<=0){
        inventory.setItem(slot,null);
      }else{
        Material material=Material.matchMaterial(materialName);
        if(material==null)throw new IllegalStateException("Craft item material is unavailable: "+materialName);
        inventory.setItem(slot,new ItemStack(material,amount));
      }
    });
  }
  private void recoverJournal(){
    try{
      var pendingClear=journal.pendingClear();
      if(pendingClear.isPresent()){
        OperationJournal.Clear operation=pendingClear.get();
        World world=Bukkit.getWorld(operation.world());
        if(world==null){blockRecovery("Clear journal world is unavailable: "+operation.world(),null);return;}
        Block block=world.getBlockAt(operation.x(),operation.y(),operation.z());
        var disposition=OperationRecovery.classifyClear(operation,task.taskId,world.getName(),block.getType().name());
        switch(disposition){
          case NOT_APPLIED -> journal.discardPendingClear();
          case ALREADY_APPLIED -> journal.completeClear();
          case CONFLICT -> {blockRecovery("Clear journal conflicts with saved block state",null);return;}
        }
      }
      for(OperationJournal.Clear operation:journal.clears()){
        World world=Bukkit.getWorld(operation.world());
        if(world==null||!operation.taskId().equals(task.taskId)){
          blockRecovery("Committed clear does not match saved task state",null);return;
        }
        if(world.getBlockAt(operation.x(),operation.y(),operation.z()).getType()!=Material.AIR){
          blockRecovery("A previously cleared block changed before its world save",null);return;
        }
      }
      var pendingHarvest=journal.pendingHarvest();
      if(pendingHarvest.isPresent()){
        OperationJournal.Harvest operation=pendingHarvest.get();
        World world=Bukkit.getWorld(operation.world());
        if(world==null){blockRecovery("Harvest journal world is unavailable: "+operation.world(),null);return;}
        Block block=world.getBlockAt(operation.x(),operation.y(),operation.z());
        OperationRecovery.HarvestDisposition disposition;
        if(operation.isCropHarvest()){
          if(operation.y()<=world.getMinHeight()||operation.y()>=world.getMaxHeight()){blockRecovery("Crop harvest journal y coordinate is out of world bounds",null);return;}
          Block below=world.getBlockAt(operation.x(),operation.y()-1,operation.z());
          Integer cropAge=block.getBlockData() instanceof org.bukkit.block.data.Ageable a?a.getAge():null;
          disposition=OperationRecovery.classifyCropHarvest(operation,task.taskId,task.collected,task.cargo,task.cargoCount(operation.effectiveOutputMaterial()),block.getType().name(),below.getType().name(),cropAge);
        }else{
          disposition=OperationRecovery.classifyHarvest(operation,task.taskId,task.collected,task.cargo,task.cargoCount(operation.effectiveOutputMaterial()),block.getType().name());
        }
        switch(disposition){
          case NOT_APPLIED -> journal.discardPendingHarvest();
          case APPLY -> {task.harvestOne(operation.effectiveOutputMaterial());persist();journal.completeHarvest();}
          case ALREADY_APPLIED -> journal.completeHarvest();
          case CONFLICT -> {blockRecovery("Harvest journal conflicts with saved task or block state",null);return;}
        }
      }
      for(OperationJournal.Harvest operation:journal.harvests()){
        World world=Bukkit.getWorld(operation.world());
        if(world==null||!operation.taskId().equals(task.taskId)||task.collected<operation.afterCollected()){
          blockRecovery("Committed harvest does not match saved task state",null);return;
        }
        Block block=world.getBlockAt(operation.x(),operation.y(),operation.z());
        if(operation.isCropHarvest()){
          if(operation.y()<=world.getMinHeight()||operation.y()>=world.getMaxHeight()){blockRecovery("Crop harvest journal y coordinate is out of world bounds",null);return;}
          Block below=world.getBlockAt(operation.x(),operation.y()-1,operation.z());
          Integer cropAge=block.getBlockData() instanceof org.bukkit.block.data.Ageable a?a.getAge():null;
          if(!OperationRecovery.isCommittedCropHarvestValid(operation,block.getType().name(),below.getType().name(),cropAge)){
            blockRecovery("A previously harvested crop block changed before its world save",null);return;
          }
        }else{
          if(block.getType()!=Material.AIR){
            blockRecovery("A previously harvested block changed before its world save",null);return;
          }
        }
      }
      var pendingDeposit=journal.pendingDeposit();
      if(pendingDeposit.isPresent()){
        OperationJournal.Deposit operation=pendingDeposit.get();
        World world=Bukkit.getWorld(operation.world());
        if(world==null){blockRecovery("Deposit journal world is unavailable: "+operation.world(),null);return;}
        Block block=world.getBlockAt(operation.x(),operation.y(),operation.z());
        if(!(block.getState() instanceof Chest chest)){blockRecovery("Deposit journal chest is missing",null);return;}
        var disposition=OperationRecovery.classifyDeposit(operation,task.taskId,task.cargo,task.deposited);
        if(disposition==OperationRecovery.DepositDisposition.CONFLICT){
          blockRecovery("Deposit journal conflicts with saved task state",null);return;
        }
        Inventory inventory=deliveryInventory(chest);
        var plan=new CargoDepositPlanner.Plan(operation.effectiveMaterial(),operation.moved(),operation.moved(),0,operation.changes());
        List<CargoDepositPlanner.Slot> currentSlots=inventorySlots(inventory);
        if(currentSlots.size()!=operation.inventorySize()) { blockRecovery("Deposit inventory shape changed",null);return; }
        if(!CargoDepositPlanner.fullyApplied(currentSlots,plan)){
          blockRecovery("Pending deposit slots are before, partial, or ambiguous; manual inspection required",null);return;
        }
        if(disposition==OperationRecovery.DepositDisposition.APPLY)task.deposit(operation.effectiveMaterial(),operation.moved());
        persist();
        saveJournalWorlds(world);
        journal.clearAfterWorldSave();
      }
      var pendingCraft=journal.pendingCraft();
      if(pendingCraft.isPresent()){
        OperationJournal.Craft operation=pendingCraft.get();
        World world=Bukkit.getWorld(operation.world());
        if(world==null){blockRecovery("Craft journal world is unavailable: "+operation.world(),null);return;}
        Block block=world.getBlockAt(operation.x(),operation.y(),operation.z());
        if(!(block.getState() instanceof Chest chest)){blockRecovery("Craft journal chest is missing",null);return;}
        boolean chestBound=chestLocation!=null&&operation.matchesChest(chestLocation.getWorld().getName(),chestLocation.getBlockX(),chestLocation.getBlockY(),chestLocation.getBlockZ());
        Inventory inventory=deliveryInventory(chest);
        List<CraftingPlanner.Slot> currentSlots=craftInventorySlots(inventory);
        var disposition=OperationRecovery.recoverCraft(
            operation,
            task,
            chestBound,
            currentSlots,
            new OperationRecovery.CraftRecoveryEffects(){
              @Override public void applyInventory(CraftingPlanner.Plan plan){
                writeCraftInventoryPlan(inventory,plan);
              }
              @Override public void persistTask() throws IOException {
                persist();
              }
              @Override public void saveWorld() throws IOException {
                saveJournalWorlds(world);
              }
              @Override public void retireJournal() throws IOException {
                journal.clearAfterWorldSave();
              }
            }
        );
        if(disposition==OperationRecovery.CraftDisposition.CONFLICT){
          blockRecovery("Craft journal conflicts with saved task or chest state",null);
          return;
        }
      }
      if(journal.hasCommittedOperationsReadyForCheckpoint()){
        saveJournalWorlds(null);
        journal.clearAfterWorldSave();
      }
      finishTaskIfReady();
    }catch(Exception e){
      blockRecovery("Operation journal recovery failed; journal retained",e);
    }
  }
  private void finishTaskIfReady(){
    if(!task.active||!task.readyToFinish())return;
    boolean partial=task.partialDelivery;int delivered=task.deposited;int requested=task.requested;
    boolean craft=task.isCraft();
    CraftRecipe recipe=task.craftRecipe();
    task.finish();controller.nextGeneration();controller.transitionTo(AgentState.IDLE,"Task completed");controller.clearHelp();persist();event("task_delivered",delivered);
    returningHome=true;homeStepAttempt=0;clearNavigation();
    if(craft)say("Bitti! "+delivered+" "+(recipe!=null?recipe.key().substring("minecraft:".length()):"ürün")+" ürettim ve sandığa bıraktım.");
    else if(partial)say("Şimdilik "+delivered+"/"+requested+" kaynak topladım ve sandığa bıraktım. Ulaşılabilir kaynağı bulamayınca durdum; yolu açınca kalanını tekrar isteyebilirsin.");
    else say("Bitti! "+delivered+" itemi sandığa bıraktım.");
  }
  private void beginPartialDelivery(String reason){
    if(!task.active||task.cargo<=0)return;
    try{
      task.requestPartialDelivery();controller.transitionTo(AgentState.PARTIAL_DELIVERY,"Partial delivery started: "+reason);treeTarget=null;treeRetryAfterMs=0;partialDeliveryPolicy.reset();
      navigationGoal=null;navigationRetry.clear();navigationWatchdog.reset();routeClears=0;
      explorationTarget=null;explorationAttempts=0;homeStepTarget=null;persist();
      say(reason+"; topladığım "+task.cargo+" kaynağı sandığa götürüyorum.");
    }catch(Exception e){blockRecovery("Could not begin partial delivery",e);}
  }
  private void saveJournalWorlds(World additional)throws IOException{
    Set<String> names=new LinkedHashSet<>();
    for(OperationJournal.Harvest operation:journal.harvests())names.add(operation.world());
    for(OperationJournal.Clear operation:journal.clears())names.add(operation.world());
    journal.pendingHarvest().ifPresent(operation->names.add(operation.world()));
    journal.pendingDeposit().ifPresent(operation->names.add(operation.world()));
    journal.pendingClear().ifPresent(operation->names.add(operation.world()));
    journal.pendingCraft().ifPresent(operation->names.add(operation.world()));
    if(additional!=null)names.add(additional.getName());
    for(String name:names){
      World world=Bukkit.getWorld(name);
      if(world==null)throw new IOException("World is not loaded: "+name);
      world.save(true);
    }
  }
  private void flushJournalOnShutdown(){
    if(journal==null||!journal.hasOperations())return;
    if(journal.hasPendingOperation()){
      getLogger().warning("An unfinished operation journal was retained for startup recovery.");return;
    }
    try{saveJournalWorlds(null);journal.clearAfterWorldSave();}
    catch(Exception e){getLogger().severe("Could not flush the operation journal; it was retained: "+e.getMessage());}
  }

  private void blockRecovery(String message,Exception cause){
    lockRecovery();
    getLogger().severe(message+(cause==null?"":": "+cause.getMessage()));
  }
  private void lockRecovery(){
    if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Recovery must be locked on the server main thread");
    recoveryBlocked=true;
    taskNavigationRoute.stopAndRelease(npc,activationRangeGuard::clear);
  }
  private Location getEffectiveHome(){
    return protectedHome!=null?protectedHome:home;
  }
  private boolean isSafeGround(Location loc){
    if(loc==null||loc.getWorld()==null)return false;
    World world=loc.getWorld();
    int bx=loc.getBlockX(),by=loc.getBlockY(),bz=loc.getBlockZ();
    if(by<=world.getMinHeight()||by>=world.getMaxHeight()-1)return false;
    if(!world.isChunkLoaded(bx>>4,bz>>4))return false;
    Block floor=world.getBlockAt(bx,by-1,bz);
    Block feet=world.getBlockAt(bx,by,bz);
    Block head=world.getBlockAt(bx,by+1,bz);
    return safeBlocks(floor,feet,head);
  }
  private boolean safeBlocks(Block floor,Block feet,Block head){
    if(!floor.getType().isSolid()||floor.isLiquid()||!feet.isPassable()||feet.isLiquid()||!head.isPassable()||head.isLiquid())return false;
    for(Block block:List.of(floor,feet,head)){
      if(SafeGroundPolicy.isHazard(block.getType()))return false;
    }
    return true;
  }
  private void navigate(Location location,AgentGoal purpose){
    if(location==null)return;
    if(!location.getWorld().equals(npc.getEntity().getWorld())){warn("Başka dünyaya yürüyemiyorum.");return;}
    Location effectiveHome=getEffectiveHome();
    int maxDist=getConfig().getInt("options.max-distance-from-home",32);
    if(effectiveHome!=null&&effectiveHome.getWorld().equals(location.getWorld())){
      boolean isHomeOrChest=(chestLocation!=null&&sameBlock(location,chestLocation))
          || sameBlock(location,effectiveHome)
          || (home!=null&&sameBlock(location,home))
          || (protectedHome!=null&&sameBlock(location,protectedHome));
      double targetDistSq=effectiveHome.distanceSquared(location);
      double currentDistSq=npc.getEntity().getLocation().distanceSquared(effectiveHome);
      boolean movingCloserToHome=targetDistSq<currentDistSq;
      if(!isHomeOrChest && !movingCloserToHome && targetDistSq>Math.pow(maxDist,2)){
        warn("Hedef evden çok uzak, güvenlik sınırına takıldım.");return;
      }
    }
    Location target=location.clone();
    navigationPurpose=purpose;
    if(!sameBlock(navigationGoal,target)){navigationGoal=target;navigationWatchdog.reset();}
    issueNavigationTarget(target);
  }
  private void issueNavigationTarget(Location target){
    if(npc==null||!npc.isSpawned()||target==null)return;
    var navigator=npc.getNavigator();
    taskNavigationRoute.issue(npc,this::beginOwnedNavigation,()->{
      navigator.getDefaultParameters().destinationTeleportMargin(-1).stationaryTicks(-1).distanceMargin(0.75).pathDistanceMargin(0.75);
      navigator.setTarget(target);
    },()->{
      // Citizens documents these per-target parameters; -1 disables destination and stuck teleports.
      navigator.getLocalParameters().destinationTeleportMargin(-1).stationaryTicks(-1).distanceMargin(0.75).pathDistanceMargin(0.75);
    },this::endOwnedNavigation);
  }
  private void clearNavigation(){
    try{taskNavigationRoute.cancelIfOwned(npc);}
    finally{
      endOwnedNavigation();
      navigationGoal=null;navigationPurpose=AgentGoal.REST_IDLE;navigationWatchdog.reset();navigationRetry.clear();routeClears=0;
      explorationTarget=null;homeStepTarget=null;
    }
  }

  private void beginOwnedNavigation(){
    if(!Bukkit.isPrimaryThread()||npc==null)return;
    activationRangeGuard.reconcile(activationMetadata(npc),true);
    maintainOwnedRouteActivation(true);
  }

  private void endOwnedNavigation(){
    if(!Bukkit.isPrimaryThread())return;
    activationRangeGuard.reconcile(activationMetadata(npc),false);
  }

  private void reconcileActivationRange(){
    if(!Bukkit.isPrimaryThread())return;
    boolean scheduleRoute=scheduleAdapter.ownsRoute(npc);
    boolean pendingOperation=journal==null||journal.hasPendingOperation();
    boolean taskRoute=ActivationRangeGuard.taskRouteEligible(taskNavigationRoute.matches(npc),recoveryBlocked,
        pendingOperation,deciding,navigationGoal!=null,navigationRetry.pending());
    boolean eligible=scheduleRoute||taskRoute;
    activationRangeGuard.reconcile(activationMetadata(npc),eligible);
    maintainOwnedRouteActivation(eligible);
  }

  private void maintainOwnedRouteActivation(boolean eligible){
    if(!Bukkit.isPrimaryThread()||!eligible||npc==null||!npc.isSpawned())return;
    var metadata=activationMetadata(npc);
    if(!activationRangeGuard.shouldForceActivation(metadata,true))return;
    try{
      if(citizensActivation==null)throw new CitizensActivation.Failure("citizens-helper-unavailable");
      citizensActivation.activate(npc.getEntity(),Bukkit.getCurrentTick(),Bukkit::isPrimaryThread);
    }catch(CitizensActivation.Failure failure){
      boolean cleanupFailed=stopOwnedRoutesAfterActivationFailure();
      if(!activationFailureLogged){
        activationFailureLogged=true;
        getLogger().warning("Owned villager route stopped: pinned Citizens activation unavailable ("+failure.code()+")"
            +(cleanupFailed?"; route cleanup incomplete":"; owned routes cleared")+".");
      }
      throw failure;
    }
  }

  private boolean stopOwnedRoutesAfterActivationFailure(){
    boolean failed=false;
    try{taskNavigationRoute.cancelIfOwned(npc);}catch(RuntimeException|Error cleanupFailure){failed=true;}
    try{scheduleAdapter.suspend(npc,"activation helper failure");}catch(RuntimeException|Error cleanupFailure){failed=true;}
    try{activationRangeGuard.clear();}catch(RuntimeException|Error cleanupFailure){failed=true;}
    return failed;
  }

  private ActivationRangeGuard.MetadataAccess activationMetadata(NPC target){
    if(target==null)return null;
    return new ActivationRangeGuard.MetadataAccess(){
      @Override public Object identity(){return target;}
      @Override public boolean spawned(){return target.isSpawned();}
      @Override public boolean hasActivationRange(){return target.data().has(NPC.Metadata.ACTIVATION_RANGE);}
      @Override public Integer activationRange(){return target.data().get(NPC.Metadata.ACTIVATION_RANGE);}
      @Override public void setTransientActivationRange(int value){target.data().set(NPC.Metadata.ACTIVATION_RANGE,value);}
      @Override public void removeActivationRange(){target.data().remove(NPC.Metadata.ACTIVATION_RANGE);}
    };
  }

  private static boolean sameBlock(Location first,Location second){
    return first!=null&&first.getWorld().equals(second.getWorld())&&first.getBlockX()==second.getBlockX()
      &&first.getBlockY()==second.getBlockY()&&first.getBlockZ()==second.getBlockZ();
  }
  private void invalidateTargetScan(){targetScanner.invalidate();scanCandidates.clear();scanEligible.clear();scanResult=List.of();productionCandidateAvailable=false;scanEligibilityCursor=0;scanEvaluated=false;scanCompletedAtMs=0;}
  private List<Block> findTargets(Location origin,String preference){
    productionCandidateAvailable=false;
    World world=origin.getWorld();int r=Math.min(24,Math.max(8,getConfig().getInt("options.search-radius",24)));
    long now=System.currentTimeMillis();
    if(targetScanner.complete()&&scanCompletedAtMs>0&&now-scanCompletedAtMs>=5000)invalidateTargetScan();
    targetScanner.begin(world.getName(),preference,controller.jobGeneration(),origin.getBlockX(),origin.getBlockY(),origin.getBlockZ(),r,-8,12);
    if(targetScanner.visited()==0){scanCandidates.clear();scanEligible.clear();scanEligibilityCursor=0;scanEvaluated=false;}
    if(!targetScanner.complete()){
      for(IncrementalTargetScanner.Point point:targetScanner.advance(512)){
        int xx=point.x(),yy=point.y(),zz=point.z();
        if(yy<world.getMinHeight()||yy>=world.getMaxHeight()||!world.isChunkLoaded(xx>>4,zz>>4))continue;
        Block block=world.getBlockAt(xx,yy,zz);
        if(matchesTarget(block,preference)&&!failedTargets.contains(world.getName(),xx,yy,zz,System.currentTimeMillis()))scanCandidates.add(block);
      }
      scanCandidates.sort(Comparator.comparingDouble(block->origin.distanceSquared(block.getLocation())));
      if(scanCandidates.size()>64)scanCandidates.subList(64,scanCandidates.size()).clear();
      if(!targetScanner.complete())return null;
    }
    if(!scanEvaluated){
      if(scanEligibilityCursor<scanCandidates.size()){
        Block candidate=scanCandidates.get(scanEligibilityCursor++);
        if(isEligibleTarget(candidate,preference))scanEligible.add(candidate);
        if(scanEligibilityCursor<scanCandidates.size())return null;
      }
      scanEvaluated=true;scanCompletedAtMs=now;scanResult=List.copyOf(scanEligible.subList(0,Math.min(8,scanEligible.size())));
    }
    List<Block> current=new ArrayList<>(8);
    for(Block candidate:scanResult){
      if(candidate.getWorld().isChunkLoaded(candidate.getX()>>4,candidate.getZ()>>4)&&matchesTarget(candidate,preference)
          &&!failedTargets.contains(candidate.getWorld().getName(),candidate.getX(),candidate.getY(),candidate.getZ(),now))current.add(candidate);
    }
    productionCandidateAvailable=!current.isEmpty();
    return List.copyOf(current);
  }
  private boolean matchesTarget(Block block,String preference){
    if(FarmCatalog.isPreference(preference)){
      if(farmPlot==null||!farmPlot.contains(block.getWorld().getName(),block.getX(),block.getY(),block.getZ()))return false;
      if(!FarmCatalog.matchesCropBlock(block.getType().name(),preference))return false;
      return block.getBlockData() instanceof org.bukkit.block.data.Ageable ageable
          && ageable.getMaximumAge()==7 && ageable.getAge()==7;
    }
    if(WoodMaterials.matchesPreference(block.getType(),preference))return true;
    return ResourceCatalog.matchesBlockName(block.getType().name(),preference);
  }
  private boolean isEligibleTarget(Block block,String preference){
    if(!block.getWorld().isChunkLoaded(block.getX()>>4,block.getZ()>>4))return false;
    if(!matchesTarget(block,preference))return false;
    if(FarmCatalog.isPreference(preference)){
      if(farmPlot==null||!farmPlot.contains(block.getWorld().getName(),block.getX(),block.getY(),block.getZ()))return false;
      if(isProtectedWood(block))return false;
      if(block.getY()<=block.getWorld().getMinHeight())return false;
      Block below=block.getWorld().getBlockAt(block.getX(),block.getY()-1,block.getZ());
      if(!(block.getBlockData() instanceof org.bukkit.block.data.Ageable ageable))return false;
      return FarmCatalog.isMatureAndFarmable(block.getType().name(),preference,below.getType().name(),ageable.getAge(),ageable.getMaximumAge());
    }
    if(ResourceCatalog.isPreference(preference))return expectedOutput(block,preference)!=null;
    if(WoodMaterials.isCollectableWood(block.getType())&&isProtectedWood(block))return false;
    return hasNaturalWoodMarkers(block);
  }
  private ProtectedHomeArea getProtectedHomeArea(){
    if(protectedHome==null||protectHomeRadius<=0)return null;
    return new ProtectedHomeArea(protectedHome.getWorld().getName(),protectedHome.getBlockX(),protectedHome.getBlockY(),protectedHome.getBlockZ(),protectHomeRadius);
  }
  private boolean isProtectedWood(Block block){
    return protectionHook.isProtected(block, npc==null||!npc.isSpawned()?null:npc.getEntity().getLocation());
  }

  private String expectedOutput(Block block,String preference){
    if(FarmCatalog.isPreference(preference))return isEligibleTarget(block,preference)?FarmCatalog.outputForBlock(block.getType().name(),preference):null;
    if(WoodMaterials.matchesPreference(block.getType(),preference))return block.getType().name();
    var resource=ResourceCatalog.forPreference(preference).orElse(null);
    if(resource==null||!resource.blockMaterials().contains(block.getType().name()))return null;
    Material tool=resource.toolMaterial()==null?null:Material.matchMaterial(resource.toolMaterial());
    if(resource.toolMaterial()!=null&&tool==null)return null;
    ItemStack toolStack=tool==null?null:new ItemStack(tool);
    List<ItemStack> drops=toolStack==null?new ArrayList<>(block.getDrops()):new ArrayList<>(block.getDrops(toolStack));
    if(drops.size()!=1)return null;
    ItemStack drop=drops.getFirst();
    return drop.getType().name().equals(resource.outputMaterial())&&drop.getAmount()==1
        ?resource.outputMaterial():null;
  }
  private boolean hasNaturalWoodMarkers(Block block){
    boolean protectNatural=getConfig().getBoolean("options.only-natural-wood",
        getConfig().getBoolean("options.only-oak-logs-near-leaves",true));
    if(!protectNatural)return true;
    if(block.getType()==Material.BAMBOO){
      int stalkNeighbours=0;
      for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)for(int dy=-3;dy<=3;dy++){
        if(neighborTypeIfLoaded(block,dx,dy,dz)==Material.BAMBOO)stalkNeighbours++;
      }
      return stalkNeighbours>=2;
    }
    // Preserve the old private-world structure guard, generalized to all leaf types and Nether fungi.
    for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++)for(int dy=-3;dy<=9;dy++){
      if(Tag.LEAVES.isTagged(neighborTypeIfLoaded(block,dx,dy,dz)))return true;
    }
    String type=block.getType().name();
    if(type.contains("CRIMSON")||type.contains("WARPED")){
      Set<Material> fungalIndicators=type.contains("CRIMSON")
          ?Set.of(Material.CRIMSON_FUNGUS,Material.NETHER_WART_BLOCK,Material.SHROOMLIGHT)
          :Set.of(Material.WARPED_FUNGUS,Material.WARPED_WART_BLOCK,Material.SHROOMLIGHT);
      for(int dx=-5;dx<=5;dx++)for(int dz=-5;dz<=5;dz++)for(int dy=-5;dy<=9;dy++)
        if(fungalIndicators.contains(neighborTypeIfLoaded(block,dx,dy,dz)))return true;
    }
    return false;
  }
  private Material neighborTypeIfLoaded(Block origin,int dx,int dy,int dz){
    int x=origin.getX()+dx,y=origin.getY()+dy,z=origin.getZ()+dz;
    if(!origin.getWorld().isChunkLoaded(x>>4,z>>4))return Material.AIR;
    return origin.getWorld().getBlockAt(x,y,z).getType();
  }
  private void event(String type,int count){
    JsonObject payload=new JsonObject();payload.addProperty("type",type);payload.addProperty("actor",task.owner);payload.addProperty("count",count);
    brain.post("/api/event",payload).exceptionally(error->{getLogger().fine("Unable to record event: "+error.getMessage());return null;});
  }
  private void warn(String message){
    if(System.currentTimeMillis()-lastWarningMs>8000){lastWarningMs=System.currentTimeMillis();say(message);}
  }
  private static String coords(Location loc){return loc.getBlockX()+","+loc.getBlockY()+","+loc.getBlockZ();}
}
