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
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class AhmetPlugin extends JavaPlugin implements Listener {
  private NPC npc;
  private BrainClient brain;
  private OperationJournal journal;
  private final WoodTask task=new WoodTask();
  private Location chestLocation;
  private Location treeTarget;
  private Location navigationGoal;
  private final NavigationWatchdog navigationWatchdog=new NavigationWatchdog();
  private Location home;
  private boolean deciding=false;
  private long jobGeneration=0;
  private int waitDecisions=0;
  private long lastWarningMs=0;
  private long treeRetryAfterMs=0;
  private boolean recoveryBlocked=false;

  @Override public void onEnable() {
    saveDefaultConfig();brain=new BrainClient(this);
    int id=getConfig().getInt("npc-id",-1);
    if(id>=0)npc=CitizensAPI.getNPCRegistry().getById(id);
    chestLocation=loadLoc("chest"); home=loadLoc("home");task.load(getConfig());
    journal=new OperationJournal(getDataFolder().toPath().resolve("operations.json"));
    try{journal.load();recoverJournal();}
    catch(Exception e){blockRecovery("Operation journal could not be recovered",e);}
    if(task.active && npc==null){getLogger().warning("Saved task exists but NPC is missing; task paused until spawned.");}
    persist();
    Bukkit.getPluginManager().registerEvents(this,this);
    Bukkit.getScheduler().runTaskTimer(this,this::step,40L,40L);
    getLogger().info("Villager Ahmet ready. Run /ahmet spawn as an operator to summon Ahmet.");
  }
  @Override public void onDisable(){persist();flushJournalOnShutdown();}
  private void persist(){
    getConfig().set("npc-id",npc==null?-1:npc.getId());saveLoc("chest",chestLocation);saveLoc("home",home);task.save(getConfig());saveConfig();
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
    if(npc==null || !npc.isSpawned())return;
    Location here=npc.getEntity().getLocation();
    for(Player p:here.getWorld().getPlayers())if(p.getLocation().distanceSquared(here)<24*24)p.sendMessage("§6[Ahmet] §f"+message);
  }
  @Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args) {
    if(!cmd.getName().equalsIgnoreCase("ahmet"))return false;
    if(args.length==0){sender.sendMessage("§e/ahmet spawn | summon | chest | ask <metin> | status | cancel | deliver");return true;}
    String op=args[0].toLowerCase();
    if(op.equals("status")){sender.sendMessage("§6Ahmet: §f"+task.describe()+" | Sandık: "+(chestLocation!=null?"bağlı":"bağlı değil")+" | NPC: "+(npc!=null&&npc.isSpawned()?"aktif":"yok")+(recoveryBlocked?" | Kurtarma: manuel inceleme gerekli":""));return true;}
    if(!(sender instanceof Player player)){sender.sendMessage("Bu komut oyun içi oyuncu gerektiriyor.");return true;}
    if(op.equals("spawn")){
      if(!player.hasPermission("ahmet.admin")){player.sendMessage("§cYetkin yok.");return true;}
      if(npc==null){npc=CitizensAPI.getNPCRegistry().createNPC(EntityType.VILLAGER,"Ahmet");}
      if(npc.isSpawned()){player.sendMessage("§eAhmet zaten dünyada.");return true;}
      npc.spawn(player.getLocation());home=player.getLocation().clone();persist();say("Selam! İşe hazırım.");return true;
    }
    if(op.equals("summon")){
      if(!player.hasPermission("ahmet.admin")){player.sendMessage("§cYetkin yok.");return true;}
      if(task.active||recoveryBlocked){player.sendMessage("§cAhmet görevde veya kurtarma bekliyor; şu an yanına çağrılamaz.");return true;}
      Location destination=findSafeSummonLocation(player);
      if(destination==null){player.sendMessage("§cYakınında yüklü ve güvenli bir boş nokta bulamadım.");return true;}
      if(npc==null)npc=CitizensAPI.getNPCRegistry().createNPC(EntityType.VILLAGER,"Ahmet");
      if(npc.isSpawned()){
        npc.getNavigator().cancelNavigation();
        npc.teleport(destination,PlayerTeleportEvent.TeleportCause.PLUGIN);
      }else if(!npc.spawn(destination)){
        player.sendMessage("§cAhmet güvenli noktada oluşturulamadı.");return true;
      }
      home=destination.clone();persist();
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
      if(task.cargo<=0){player.sendMessage("§eTaşınan odun yok.");return true;}
      if(task.active){player.sendMessage("§eÖnce görevi bitir veya iptal et.");return true;}
      if(!player.hasPermission("ahmet.admin") && !task.owner.equals(player.getUniqueId().toString())){player.sendMessage("§cYetkin yok.");return true;}
      task.resumeDelivery();treeTarget=null;jobGeneration++;persist();say("Kalan odunları sandığa götürüyorum.");return true;
    }
    if(op.equals("cancel")){
      if(task.active && (player.hasPermission("ahmet.admin") || task.owner.equals(player.getUniqueId().toString()))){cancel();say("Görevi iptal ettim. Topladığım odunlar şimdilik bende.");}
      else player.sendMessage("§cİptal edebileceğin bir görevin yok.");return true;
    }
    if(op.equals("ask")){
      if(args.length<2){player.sendMessage("§e/ahmet ask <mesaj>");return true;}
      interact(player,String.join(" ",java.util.Arrays.copyOfRange(args,1,args.length)));return true;
    }
    player.sendMessage("§eBilinmeyen komut. /ahmet");return true;
  }
  private Location findSafeSummonLocation(Player player){
    World world=player.getWorld();
    return SummonTarget.find(player.getLocation(),candidate->{
      int blockX=candidate.getBlockX(),blockY=candidate.getBlockY(),blockZ=candidate.getBlockZ();
      if(!world.isChunkLoaded(blockX>>4,blockZ>>4))return false;
      Block feet=world.getBlockAt(blockX,blockY,blockZ);
      Block head=world.getBlockAt(blockX,blockY+1,blockZ);
      Block floor=world.getBlockAt(blockX,blockY-1,blockZ);
      return floor.getType().isSolid()&&!floor.isLiquid()
          &&feet.isPassable()&&!feet.isLiquid()&&head.isPassable()&&!head.isLiquid();
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
    if(npc!=null && npc.isSpawned() && npc.getEntity().getUniqueId().equals(event.getRightClicked().getUniqueId())){
      event.setCancelled(true);event.getPlayer().sendMessage("§6[Ahmet] §fSelam! Bana sohbetten 'Ahmet, ...' yazabilirsin.");
    }
  }
  private void interact(Player player,String message){
    if(recoveryBlocked){player.sendMessage("§cAhmet'in görev verisi kurtarma bekliyor; önce yönetici incelemeli.");return;}
    if(npc==null || !npc.isSpawned()){player.sendMessage("§cÖnce /ahmet spawn ile Ahmet'i oluştur.");return;}
    if(!player.getWorld().equals(npc.getEntity().getWorld()) || player.getLocation().distanceSquared(npc.getEntity().getLocation())>18*18){player.sendMessage("§cAhmet'le konuşmak için ona yaklaş.");return;}
    JsonObject req=new JsonObject();req.addProperty("actorUuid",player.getUniqueId().toString());req.addProperty("actorName",player.getName());req.addProperty("text",message);
    JsonObject context=new JsonObject();context.addProperty("busy",task.active || task.cargo>0);context.addProperty("chestBound",chestLocation!=null);req.add("context",context);
    brain.post("/api/chat",req).whenComplete((reply,error)->Bukkit.getScheduler().runTask(this,()->{
      if(error!=null){player.sendMessage("§cAhmet'in beyni şu an yanıt vermiyor: "+error.getMessage());return;}
      if(!player.isOnline())return;
      String answer=reply.has("reply")?reply.get("reply").getAsString():"Hmm?";
      if(reply.has("cancel")&&reply.get("cancel").getAsBoolean()){
        if(task.active && (task.owner.equals(player.getUniqueId().toString())||player.hasPermission("ahmet.admin")))cancel();
        else {player.sendMessage("§cBaşkasının görevini iptal edemezsin.");return;}
      }
      if(reply.has("task")&&reply.get("task").isJsonObject()){
        JsonObject spec=reply.getAsJsonObject("task");
        if(!task.active && task.cargo==0 && !journal.hasOperations() && chestLocation!=null
            && chestLocation.getWorld().equals(npc.getEntity().getWorld())
            && spec.has("type")&&spec.get("type").getAsString().equals("collect_oak_logs")){
          int amount=spec.get("amount").getAsInt();if(amount>=1&&amount<=64){task.start(player.getUniqueId(),amount);treeTarget=null;jobGeneration++;persist();event("task_started",amount);}
        }
      }
      say(answer);
    }));
  }
  private void cancel(){event("task_cancelled",task.requested);task.cancel();jobGeneration++;treeTarget=null;clearNavigation();persist();}
  private void step(){
    if(recoveryBlocked || journal.pendingHarvest().isPresent() || journal.pendingDeposit().isPresent()
        || !task.active || npc==null || !npc.isSpawned() || deciding)return;
    final Location current=npc.getEntity().getLocation();
    final long now=System.currentTimeMillis();
    if(navigationGoal!=null){
      NavigationWatchdog.Outcome outcome=navigationWatchdog.observe(current.distanceSquared(navigationGoal),now);
      if(outcome==NavigationWatchdog.Outcome.REACHED)navigationGoal=null;
      else if(outcome==NavigationWatchdog.Outcome.RETRY){
        if(npc.getNavigator().isNavigating())npc.getNavigator().cancelNavigation();
        treeRetryAfterMs=now+2000;return;
      }else if(outcome==NavigationWatchdog.Outcome.GIVE_UP){
        if(npc.getNavigator().isNavigating())npc.getNavigator().cancelNavigation();
        navigationGoal=null;navigationWatchdog.reset();
        if(task.collected<task.requested)treeTarget=null;
        treeRetryAfterMs=now+30000;
        warn("Ahmet üç kez yol bulamadı. Yolu açıp tekrar deneyin veya görevi iptal edin.");
        return;
      }else if(outcome==NavigationWatchdog.Outcome.WAIT&&!npc.getNavigator().isNavigating())return;
    }
    if(npc.getNavigator().isNavigating() || now<treeRetryAfterMs)return;
    final JsonArray options=new JsonArray();
    String required;
    if(task.collected>=task.requested){
      if(chestLocation==null || !chestLocation.getWorld().equals(current.getWorld())){warn("Teslimat sandığı kayıp veya başka dünyada.");return;}
      required=current.distanceSquared(chestLocation.clone().add(0.5,0,0.5))>3.2*3.2?"walk_chest":"deposit";
    }else {
      if(treeTarget==null || !isTree(treeTarget.getBlock()))required="find_tree";
      else required=current.distanceSquared(treeTarget.clone().add(.5,0,.5))>2.9*2.9?"walk_tree":"harvest";
    }
    options.add(candidate(required,describeAction(required)));options.add(candidate("wait","Wait briefly to assess safety; no progress"));
    JsonObject req=new JsonObject();JsonObject observation=new JsonObject();
    observation.addProperty("task","collect_oak_logs");observation.addProperty("requested",task.requested);observation.addProperty("collected",task.collected);
    observation.addProperty("cargo",task.cargo);observation.addProperty("deposited",task.deposited);observation.addProperty("requiredAction",required);
    observation.addProperty("chestBound",chestLocation!=null);observation.addProperty("position",coords(current));
    req.add("observation",observation);req.add("actions",options);
    deciding=true;
    final String action=required;
    final long generation=jobGeneration;
    brain.post("/api/decide",req).whenComplete((response,error)->Bukkit.getScheduler().runTask(this,()->{
      deciding=false;if(!task.active || npc==null || !npc.isSpawned() || jobGeneration!=generation)return;
      // Server is authoritative. Never execute an action not in the current candidate list.
      String selected=error==null&&response.has("id")?response.get("id").getAsString():action;
      if(selected.equals(action)){waitDecisions=0;execute(action);}
      else if(selected.equals("wait") && ++waitDecisions>=3){waitDecisions=0;execute(action);}
    }));
  }
  private JsonObject candidate(String id,String description){JsonObject result=new JsonObject();result.addProperty("id",id);result.addProperty("description",description);return result;}
  private String describeAction(String a){return switch(a){case "find_tree"->"Find a nearby natural oak tree";case "walk_tree"->"Walk to chosen oak log";case "harvest"->"Harvest one real oak log if nearby and still naturally present";case "walk_chest"->"Walk to bound chest with collected wood";case "deposit"->"Deposit actually harvested logs into designated chest";default->"Wait";};}
  private void execute(String action){
    Location here=npc.getEntity().getLocation();
    switch(action){
      case "find_tree" -> {
        treeTarget=findTree(here);
        if(treeTarget==null){treeRetryAfterMs=System.currentTimeMillis()+10000;warn("Çevrede güvenli erişebildiğim meşe ağacı bulamadım.");}
      }
      case "walk_tree" -> navigate(treeTarget);
      case "harvest" -> {
        if(treeTarget==null || here.distanceSquared(treeTarget.clone().add(.5,0,.5))>3.5*3.5){treeTarget=null;return;}
        Block block=treeTarget.getBlock();if(!isTree(block)){treeTarget=null;return;}
        harvest(block);
      }
      case "walk_chest" -> navigate(chestLocation);
      case "deposit" -> {
        Block block=chestLocation.getBlock();if(!(block.getState() instanceof Chest chest)){warn("İşaretli sandık artık orada değil.");return;}
        deposit(chest);
      }
      default -> {}
    }
  }
  private void harvest(Block block){
    OperationJournal.Harvest operation=new OperationJournal.Harvest(task.taskId,block.getWorld().getName(),
      block.getX(),block.getY(),block.getZ(),task.collected,task.cargo);
    try{
      journal.prepareHarvest(operation);
      // Write-ahead record precedes the world mutation; task YAML follows it.
      block.setType(Material.AIR,false);
      task.harvestOne();
      persist();
      journal.completeHarvest();
      treeTarget=null;
      if(task.collected>=task.requested)say(task.collected+" odunu topladım, sandığa gidiyorum.");
    }catch(Exception e){
      treeTarget=null;
      blockRecovery("Harvest transaction is pending; the job is paused",e);
    }
  }
  private void deposit(Chest chest){
    if(task.cargo<=0)return;
    Inventory inventory=chest.getBlockInventory();
    try{
      List<CargoDepositPlanner.Slot> before=inventorySlots(inventory);
      CargoDepositPlanner.Plan plan=CargoDepositPlanner.plan(before,task.cargo);
      if(plan.moved()==0){warn("Sandık dolu; "+task.cargo+" odun hâlâ bende.");return;}
      List<CargoDepositPlanner.Slot> after=CargoDepositPlanner.apply(before,plan);
      OperationJournal.Deposit operation=new OperationJournal.Deposit(task.taskId,chest.getWorld().getName(),
        chestLocation.getBlockX(),chestLocation.getBlockY(),chestLocation.getBlockZ(),task.cargo,task.deposited,
        plan.moved(),plan.changes());
      journal.prepareDeposit(operation);
      writeInventoryPlan(inventory,after,plan.changes());
      task.deposit(plan.moved());
      persist();
      saveJournalWorlds(chest.getWorld());
      journal.clearAfterWorldSave();
      if(task.cargo>0){warn("Sandık dolu; "+task.cargo+" odun hâlâ bende.");return;}
      finishTaskIfReady();
    }catch(Exception e){
      blockRecovery("Deposit transaction is pending; the job is paused",e);
    }
  }
  private List<CargoDepositPlanner.Slot> inventorySlots(Inventory inventory){
    List<CargoDepositPlanner.Slot> slots=new ArrayList<>();
    int emptyLimit=Math.min(64,inventory.getMaxStackSize());
    for(ItemStack item:inventory.getContents()){
      if(item==null||item.getType().isAir())slots.add(CargoDepositPlanner.Slot.empty(emptyLimit));
      else slots.add(new CargoDepositPlanner.Slot(item.getType().name(),item.getAmount(),Math.min(64,item.getMaxStackSize())));
    }
    return List.copyOf(slots);
  }
  private void writeInventoryPlan(Inventory inventory,List<CargoDepositPlanner.Slot> after,
      List<CargoDepositPlanner.Change> changes){
    for(CargoDepositPlanner.Change change:changes){
      CargoDepositPlanner.Slot slot=after.get(change.slot());
      if(!CargoDepositPlanner.CARGO_MATERIAL.equals(slot.material()))throw new IllegalStateException("Deposit plan has an invalid item");
      inventory.setItem(change.slot(),new ItemStack(Material.OAK_LOG,slot.amount()));
    }
  }
  private void recoverJournal(){
    try{
      var pendingHarvest=journal.pendingHarvest();
      if(pendingHarvest.isPresent()){
        OperationJournal.Harvest operation=pendingHarvest.get();
        World world=Bukkit.getWorld(operation.world());
        if(world==null){blockRecovery("Harvest journal world is unavailable: "+operation.world(),null);return;}
        Block block=world.getBlockAt(operation.x(),operation.y(),operation.z());
        var disposition=OperationRecovery.classifyHarvest(operation,task.taskId,task.collected,task.cargo,block.getType().name());
        switch(disposition){
          case NOT_APPLIED -> journal.discardPendingHarvest();
          case APPLY -> {task.harvestOne();persist();journal.completeHarvest();}
          case ALREADY_APPLIED -> journal.completeHarvest();
          case CONFLICT -> {blockRecovery("Harvest journal conflicts with saved task or block state",null);return;}
        }
      }
      for(OperationJournal.Harvest operation:journal.harvests()){
        World world=Bukkit.getWorld(operation.world());
        if(world==null||!operation.taskId().equals(task.taskId)||task.collected<operation.afterCollected()){
          blockRecovery("Committed harvest does not match saved task state",null);return;
        }
        if(world.getBlockAt(operation.x(),operation.y(),operation.z()).getType()!=Material.AIR){
          blockRecovery("A previously harvested block changed before its world save",null);return;
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
        Inventory inventory=chest.getBlockInventory();
        var plan=new CargoDepositPlanner.Plan(operation.moved(),operation.moved(),0,operation.changes());
        List<CargoDepositPlanner.Slot> after=CargoDepositPlanner.apply(inventorySlots(inventory),plan);
        writeInventoryPlan(inventory,after,operation.changes());
        if(disposition==OperationRecovery.DepositDisposition.APPLY)task.deposit(operation.moved());
        persist();
        saveJournalWorlds(world);
        journal.clearAfterWorldSave();
      }
      finishTaskIfReady();
    }catch(Exception e){
      blockRecovery("Operation journal recovery failed; journal retained",e);
    }
  }
  private void finishTaskIfReady(){
    if(!task.active||!task.readyToFinish())return;
    task.finish();jobGeneration++;persist();event("task_delivered",task.deposited);
    say("Bitti! "+task.deposited+" meşe odununu sandığa bıraktım.");
  }
  private void saveJournalWorlds(World additional)throws IOException{
    Set<String> names=new LinkedHashSet<>();
    for(OperationJournal.Harvest operation:journal.harvests())names.add(operation.world());
    journal.pendingHarvest().ifPresent(operation->names.add(operation.world()));
    journal.pendingDeposit().ifPresent(operation->names.add(operation.world()));
    if(additional!=null)names.add(additional.getName());
    for(String name:names){
      World world=Bukkit.getWorld(name);
      if(world==null)throw new IOException("World is not loaded: "+name);
      world.save(true);
    }
  }
  private void flushJournalOnShutdown(){
    if(journal==null||!journal.hasOperations())return;
    if(journal.pendingHarvest().isPresent()||journal.pendingDeposit().isPresent()){
      getLogger().warning("An unfinished operation journal was retained for startup recovery.");return;
    }
    try{saveJournalWorlds(null);journal.clearAfterWorldSave();}
    catch(Exception e){getLogger().severe("Could not flush the operation journal; it was retained: "+e.getMessage());}
  }
  private void blockRecovery(String message,Exception cause){
    recoveryBlocked=true;
    getLogger().severe(message+(cause==null?"":": "+cause.getMessage()));
  }
  private void navigate(Location location){
    if(location==null)return;
    if(!location.getWorld().equals(npc.getEntity().getWorld())){warn("Başka dünyaya yürüyemiyorum.");return;}
    if(home!=null && home.getWorld().equals(location.getWorld()) && home.distanceSquared(location)>Math.pow(getConfig().getInt("options.max-distance-from-home",32),2)){
      warn("Hedef evden çok uzak, güvenlik sınırına takıldım.");return;
    }
    Location target=location.clone().add(.5,0,.5);
    if(!sameBlock(navigationGoal,target)){navigationGoal=target;navigationWatchdog.reset();}
    npc.getNavigator().setTarget(target);
    // Citizens documents these per-target parameters; -1 disables destination and stuck teleports.
    npc.getNavigator().getLocalParameters().destinationTeleportMargin(-1).stationaryTicks(-1);

  }
  private void clearNavigation(){
    if(npc!=null&&npc.isSpawned())npc.getNavigator().cancelNavigation();
    navigationGoal=null;navigationWatchdog.reset();
  }
  private static boolean sameBlock(Location first,Location second){
    return first!=null&&first.getWorld().equals(second.getWorld())&&first.getBlockX()==second.getBlockX()
      &&first.getBlockY()==second.getBlockY()&&first.getBlockZ()==second.getBlockZ();
  }
  private Location findTree(Location origin){
    World world=origin.getWorld();int r=Math.min(24,Math.max(3,getConfig().getInt("options.search-radius",16)));
    Location best=null;double bestDist=Double.MAX_VALUE;
    for(int x=-r;x<=r;x++)for(int z=-r;z<=r;z++){
      if(x*x+z*z>r*r)continue;
      for(int y=-2;y<=2;y++){
        int xx=origin.getBlockX()+x, yy=origin.getBlockY()+y, zz=origin.getBlockZ()+z;
        if(yy<world.getMinHeight()||yy>=world.getMaxHeight())continue;
        if(!world.isChunkLoaded(xx>>4,zz>>4))continue;
        Block b=world.getBlockAt(xx,yy,zz);if(!isTree(b))continue;
        double dist=origin.distanceSquared(b.getLocation());
        if(dist<bestDist){bestDist=dist;best=b.getLocation();}
      }
    }
    return best;
  }
  private boolean isTree(Block block){
    if(block.getType()!=Material.OAK_LOG)return false;
    if(!getConfig().getBoolean("options.only-oak-logs-near-leaves",true))return true;
    // Basic house-damage mitigation. A stronger connected-tree/protection policy is required before public servers.
    for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++)for(int dy=0;dy<=5;dy++){
      if(block.getRelative(dx,dy,dz).getType()==Material.OAK_LEAVES)return true;
    }
    return false;
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
