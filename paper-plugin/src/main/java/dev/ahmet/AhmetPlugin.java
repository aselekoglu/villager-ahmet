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
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AhmetPlugin extends JavaPlugin implements Listener, TabCompleter {
  private NPC npc;
  private BrainClient brain;
  private OperationJournal journal;
  private SavedNpcReference savedNpcReference;
  private final WoodTask task=new WoodTask();
  private final PlayerPlacedWoodRegistry playerPlacedWood=new PlayerPlacedWoodRegistry();
  private Location chestLocation;
  private Location treeTarget;
  private Location navigationGoal;
  private final NavigationWatchdog navigationWatchdog=new NavigationWatchdog();
  private final NavigationRetry navigationRetry=new NavigationRetry();
  private final FailedTargets failedTargets=new FailedTargets();
  private Location home;
  private Location protectedHome;
  private int protectHomeRadius=10;
  private boolean woodProtectionStateValid=true;
  private boolean deciding=false;
  private long jobGeneration=0;
  private int waitDecisions=0;
  private final PartialDeliveryPolicy partialDeliveryPolicy=new PartialDeliveryPolicy();
  private long lastWarningMs=0;
  private long treeRetryAfterMs=0;
  private boolean recoveryBlocked=false;
  private Location explorationTarget;
  private int explorationAttempts=0;
  private static final int MAX_EXPLORATION_ATTEMPTS=4;
  private Location homeStepTarget;
  private int homeStepAttempt=0;
  private boolean returningHome=false;

  @Override public void onEnable() {
    saveDefaultConfig();brain=new BrainClient(this);
    try {
      MaterialCatalog.write(getDataFolder().toPath().resolve("item-catalog-1.21.6.json"));
    } catch (IOException e) {
      getLogger().warning("Could not write the vanilla item catalog: " + e.getMessage());
    }
    int id=getConfig().getInt("npc-id",-1);
    savedNpcReference=new SavedNpcReference(id);
    chestLocation=loadLoc("chest"); home=loadLoc("home");
    protectedHome=loadLoc("protection.home");if(protectedHome==null&&home!=null)protectedHome=home.clone();
    protectHomeRadius=getConfig().getInt("options.protect-home-radius",10);
    if(protectHomeRadius<0||protectHomeRadius>32){getLogger().warning("Invalid options.protect-home-radius; using 10 blocks.");protectHomeRadius=10;}
    try{playerPlacedWood.load(getConfig().getStringList("protection.playerPlacedWood"));}
    catch(Exception e){woodProtectionStateValid=false;blockRecovery("Player-placed wood protection data is invalid",e);}
    boolean taskStateValid=true;
    try{task.load(getConfig());}catch(Exception e){taskStateValid=false;blockRecovery("Saved task state is invalid and must be inspected",e);}
    journal=new OperationJournal(getDataFolder().toPath().resolve("operations.json"));
    try{journal.load();if(taskStateValid)recoverJournal();}
    catch(Exception e){blockRecovery("Operation journal could not be recovered",e);}
    if(task.active && npc==null&&!savedNpcReference.pending()){getLogger().warning("Saved task exists but NPC is missing; task paused until spawned.");}
    if(taskStateValid)persist();
    Bukkit.getPluginManager().registerEvents(this,this);
    if(getCommand("ahmet")!=null)getCommand("ahmet").setTabCompleter(this);
    Bukkit.getScheduler().runTaskTimer(this,this::step,40L,40L);
    if(savedNpcReference.pending())Bukkit.getScheduler().runTaskLater(this,this::restoreSavedNpc,1L);
    getLogger().info("Villager Ahmet ready. Run /ahmet spawn as an operator to summon Ahmet.");
  }
  @Override public void onDisable(){persist();flushJournalOnShutdown();}
  private void persist(){
    Integer liveNpcId=npc==null?null:npc.getId();
    getConfig().set("npc-id",savedNpcReference==null?(liveNpcId==null?-1:liveNpcId):savedNpcReference.idForPersistence(liveNpcId));
    saveLoc("chest",chestLocation);saveLoc("home",home);saveLoc("protection.home",protectedHome);
    getConfig().set("options.protect-home-radius",protectHomeRadius);
    if(woodProtectionStateValid)getConfig().set("protection.playerPlacedWood",playerPlacedWood.serialize());
    task.save(getConfig());saveConfig();
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
    if(npc==null || !npc.isSpawned())return;
    Location here=npc.getEntity().getLocation();
    for(Player p:here.getWorld().getPlayers())if(p.getLocation().distanceSquared(here)<24*24)p.sendMessage("§6[Ahmet] §f"+message);
  }
  @Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args) {
    if(!cmd.getName().equalsIgnoreCase("ahmet"))return false;
    if(args.length==0||args[0].equalsIgnoreCase("help")){sendHelp(sender);return true;}
    String op=args[0].toLowerCase();
    if(op.equals("status")){sender.sendMessage("§6Ahmet: §f"+task.describe()+" | Sandık: "+(chestLocation!=null?"bağlı":"bağlı değil")+" | NPC: "+(npc!=null&&npc.isSpawned()?"aktif":"yok")+" | Korunan ev: "+(protectedHome==null||protectHomeRadius==0?"yok":protectHomeRadius+" blok")+" | Yerleştirilmiş odun işareti: "+playerPlacedWood.size()+(recoveryBlocked?" | Kurtarma: manuel inceleme gerekli":""));return true;}
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
    if(op.equals("collect")){
      if(args.length!=3){player.sendMessage("§eKullanım: /ahmet collect <item|odun> <1-64>");return true;}
      TaskRequest request=TaskRequest.parse(args[1],args[2]).orElse(null);
      if(request==null){player.sendMessage("§cBu kaynak desteklenmiyor veya miktar 1-64 arasında değil. Örnek aramalar: /ahmet items oak_log, /ahmet items raw_iron. Genel odun için 'odun' yaz.");return true;}
      if(recoveryBlocked||journal.hasOperations()){player.sendMessage("§cKurtarma bekliyor; yeni görev güvenli değil.");return true;}
      if(npc==null||!npc.isSpawned()){player.sendMessage("§cÖnce /ahmet spawn ile Ahmet'i oluştur.");return true;}
      if(!player.getWorld().equals(npc.getEntity().getWorld())||player.getLocation().distanceSquared(npc.getEntity().getLocation())>18*18){player.sendMessage("§cGörev vermek için Ahmet'e yaklaş.");return true;}
      if(chestLocation==null||!chestLocation.getWorld().equals(npc.getEntity().getWorld())){player.sendMessage("§cÖnce aynı dünyadaki teslimat sandığını /ahmet chest ile bağla.");return true;}
      if(task.active||task.cargo>0){player.sendMessage("§cÖnce mevcut görevi bitir veya /ahmet deliver ile taşıdığı yükü boşalt.");return true;}
      task.start(player.getUniqueId(),request.amount(),request.preference());treeTarget=null;failedTargets.clear();treeRetryAfterMs=0;waitDecisions=0;partialDeliveryPolicy.reset();jobGeneration++;persist();event("task_started",request.amount());
      say("Tamam, "+request.amount()+" "+request.itemName()+" toplamaya başlıyorum.");
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
      if(npc==null&&savedNpcReference.pending()){
        player.sendMessage("§eCitizens eski Ahmet kaydını yüklüyor; birkaç saniye sonra tekrar dene.");return true;
      }
      Location destination=findSafeSummonLocation(player);
      if(destination==null){player.sendMessage("§cYakınında yüklü ve güvenli bir boş nokta bulamadım.");return true;}
      if(npc==null){npc=CitizensAPI.getNPCRegistry().createNPC(EntityType.VILLAGER,"Ahmet");savedNpcReference.created(npc.getId());}
      if(npc.isSpawned()){
        npc.getNavigator().cancelNavigation();
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
      task.resumeDelivery();treeTarget=null;jobGeneration++;persist();say("Kalan kaynakları sandığa götürüyorum.");return true;
    }
    if(op.equals("cancel")){
      if(task.active && (player.hasPermission("ahmet.admin") || task.owner.equals(player.getUniqueId().toString()))){cancel();say("Görevi iptal ettim. Topladığım kaynaklar şimdilik bende.");}
      else player.sendMessage("§cİptal edebileceğin bir görevin yok.");return true;
    }
    if(op.equals("home")||op.equals("return")){
      if(recoveryBlocked){player.sendMessage("§cKurtarma bekliyor; işlem güvenli değil.");return true;}
      if(npc==null||!npc.isSpawned()){player.sendMessage("§cÖnce /ahmet spawn ile Ahmet'i oluştur.");return true;}
      Location effectiveHome=getEffectiveHome();
      if(effectiveHome==null){player.sendMessage("§cKayıtlı ev konumu yok.");return true;}
      if(task.active){
        if(!player.hasPermission("ahmet.admin") && !task.owner.equals(player.getUniqueId().toString())){player.sendMessage("§cYetkin yok.");return true;}
        cancel();
      }
      returningHome=true;homeStepAttempt=0;clearNavigation();persist();
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
    sender.sendMessage("§e/ahmet status §7— görev ve NPC durumu");
    sender.sendMessage("§e/ahmet cargo §7— taşıdığı kaynakların tür ve adetleri");
    sender.sendMessage("§e/ahmet collect <item|odun> <1-64> §7— doğrudan toplama görevi");
    sender.sendMessage("§e/ahmet items [ara] §7— 1.21.6 item kataloğu ve toplama desteği");
    sender.sendMessage("§e/ahmet ask <mesaj> §7— Ahmet'le komut/sohbet");
    sender.sendMessage("§e/ahmet deliver §7— iptalden kalan yükü sandığa teslim et");
    sender.sendMessage("§e/ahmet cancel §7— kendi görevini iptal et");
    sender.sendMessage("§e/ahmet home §7— görevi bırakıp eve dön");
    if(sender.hasPermission("ahmet.admin"))sender.sendMessage("§e/ahmet spawn | summon | chest | protecthome <0-32> §7— yönetici işlemleri");
    sender.sendMessage("§7Ahmet doğal ağaç adaylarını seçer; oyuncu tarafından konan odunları ve korunan ev alanını atlar.");
  }
  @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
    if(!command.getName().equalsIgnoreCase("ahmet")||args.length==0)return List.of();
    List<String> commands=new ArrayList<>(List.of("help","status","cargo","collect","items","ask","deliver","cancel","home"));
    if(sender.hasPermission("ahmet.admin"))commands.addAll(List.of("spawn","summon","chest","protecthome"));
    if(args.length==1)return commands.stream().filter(value->value.startsWith(args[0].toLowerCase(java.util.Locale.ROOT))).toList();
    if(args.length==2&&args[0].equalsIgnoreCase("protecthome")&&sender.hasPermission("ahmet.admin"))return List.of("0","8","10","12","16").stream().filter(value->value.startsWith(args[1])).toList();
    if(args.length==2&&args[0].equalsIgnoreCase("collect")){
      List<String> resources=new ArrayList<>(List.of("odun","oak_log","spruce_log","birch_log","jungle_log","acacia_log","dark_oak_log","mangrove_log","cherry_log","pale_oak_log","crimson_stem","warped_stem","bamboo","dirt","sand","red_sand","cobblestone","coal","raw_iron","raw_gold"));
      return resources.stream().filter(value->value.startsWith(args[1].toLowerCase(java.util.Locale.ROOT))).toList();
    }
    if(args.length==3&&args[0].equalsIgnoreCase("collect"))return List.of("8","16","32","64").stream().filter(value->value.startsWith(args[2])).toList();
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
  @EventHandler(ignoreCancelled=true) public void onPlayerPlaceWood(BlockPlaceEvent event){
    if(!woodProtectionStateValid)return;
    Block block=event.getBlockPlaced();
    if(!WoodMaterials.isCollectableWood(block.getType()))return;
    if(playerPlacedWood.record(block.getWorld().getName(),block.getX(),block.getY(),block.getZ()))persist();
  }
  private void interact(Player player,String message){
    if(recoveryBlocked){player.sendMessage("§cAhmet'in görev verisi kurtarma bekliyor; önce yönetici incelemeli.");return;}
    if(npc==null || !npc.isSpawned()){player.sendMessage("§cÖnce /ahmet spawn ile Ahmet'i oluştur.");return;}
    if(!player.getWorld().equals(npc.getEntity().getWorld()) || player.getLocation().distanceSquared(npc.getEntity().getLocation())>18*18){player.sendMessage("§cAhmet'le konuşmak için ona yaklaş.");return;}
    JsonObject req=new JsonObject();req.addProperty("actorUuid",player.getUniqueId().toString());req.addProperty("actorName",player.getName());req.addProperty("text",message);
    JsonObject context=new JsonObject();context.addProperty("busy",task.active || task.cargo>0);context.addProperty("taskActive",task.active);context.addProperty("cargo",task.cargo);context.addProperty("chestBound",chestLocation!=null);req.add("context",context);
    brain.post("/api/chat",req).whenComplete((reply,error)->Bukkit.getScheduler().runTask(this,()->{
      if(error!=null){player.sendMessage("§cAhmet'in beyni şu an yanıt vermiyor: "+error.getMessage());return;}
      if(!player.isOnline())return;
      String answer=reply.has("reply")?reply.get("reply").getAsString():"Hmm?";
      if(reply.has("taskSource")){
        String source=reply.get("taskSource").getAsString();
        if(source.equals("llm")||source.equals("heuristic"))getLogger().info("Gather task intent source: "+source);
      }
      if(reply.has("goHome")&&reply.get("goHome").getAsBoolean()){
        if(task.active && (task.owner.equals(player.getUniqueId().toString())||player.hasPermission("ahmet.admin")))cancel();
        returningHome=true;homeStepAttempt=0;clearNavigation();persist();
      }
      if(reply.has("cancel")&&reply.get("cancel").getAsBoolean()){
        if(task.active && (task.owner.equals(player.getUniqueId().toString())||player.hasPermission("ahmet.admin")))cancel();
        else {player.sendMessage("§cBaşkasının görevini iptal edemezsin.");return;}
      }
      if(reply.has("task")&&reply.get("task").isJsonObject()){
        JsonObject spec=reply.getAsJsonObject("task");
        String preference=null;int amount=0;
        if(spec.has("type")&&spec.has("amount")){
          amount=spec.get("amount").getAsInt();
          if(spec.get("type").getAsString().equals("collect_wood")&&spec.has("material"))
            preference=spec.get("material").getAsString();
          else if(spec.get("type").getAsString().equals("collect_item")&&spec.has("itemId"))
            preference=ResourceCatalog.preferenceFor(spec.get("itemId").getAsString()).orElse(null);
        }
        if(preference!=null&&amount>=1&&amount<=64&&WoodMaterials.isPreference(preference)
            &&!task.active&&task.cargo==0&&!journal.hasOperations()&&chestLocation!=null
            &&chestLocation.getWorld().equals(npc.getEntity().getWorld())){
          task.start(player.getUniqueId(),amount,preference);treeTarget=null;failedTargets.clear();treeRetryAfterMs=0;waitDecisions=0;partialDeliveryPolicy.reset();jobGeneration++;persist();event("task_started",amount);
        }else{
          answer="Bu item kataloğumda tanımlı, ancak güvenli toplama yeteneğim henüz yok. Şimdilik yaygın odun, toprak, kum, taş, kömür, demir ve altın kaynaklarını isteyebilirsin.";
        }
      }
      say(answer);
    }));
  }
  private void cancel(){event("task_cancelled",task.requested);task.cancel();jobGeneration++;treeTarget=null;failedTargets.clear();treeRetryAfterMs=0;waitDecisions=0;partialDeliveryPolicy.reset();clearNavigation();explorationAttempts=0;returningHome=task.cargo==0;persist();}
  private void step(){
    if(recoveryBlocked || journal.pendingHarvest().isPresent() || journal.pendingDeposit().isPresent()
        || npc==null || !npc.isSpawned() || deciding)return;
    final Location current=npc.getEntity().getLocation();
    final long now=System.currentTimeMillis();
    if(navigationRetry.pending()){
      if(!navigationRetry.consumeIfDue(now))return;
      if(navigationGoal!=null)issueNavigationTarget(navigationGoal);
      return;
    }
    if(navigationGoal!=null){
      NavigationWatchdog.Outcome outcome=navigationWatchdog.observe(current.distanceSquared(navigationGoal),now);
      if(outcome==NavigationWatchdog.Outcome.REACHED){
        navigationGoal=null;
        if(explorationTarget!=null)explorationTarget=null;
        if(homeStepTarget!=null){homeStepTarget=null;homeStepAttempt=0;}
      }else if(outcome==NavigationWatchdog.Outcome.RETRY){
        if(npc.getNavigator().isNavigating())npc.getNavigator().cancelNavigation();
        navigationRetry.schedule(now);return;
      }else if(outcome==NavigationWatchdog.Outcome.GIVE_UP){
        if(npc.getNavigator().isNavigating())npc.getNavigator().cancelNavigation();
        getLogger().warning("Navigation route gave up; npc="+coords(current)+", destination="+coords(navigationGoal)+", cargo="+task.cargo+", collected="+task.collected+"/"+task.requested);
        navigationGoal=null;navigationWatchdog.reset();
        if(explorationTarget!=null){
          explorationTarget=null;
          treeRetryAfterMs=now+1000;
          return;
        }
        if(homeStepTarget!=null){
          homeStepTarget=null;
          homeStepAttempt++;
          treeRetryAfterMs=now+1000;
          return;
        }
        if(task.active){
          if(!task.readyToDeliver()){
            if(treeTarget!=null){Block failed=treeTarget.getBlock();failedTargets.mark(failed.getWorld().getName(),failed.getX(),failed.getY(),failed.getZ(),now);}
            treeTarget=null;treeRetryAfterMs=now+2000;
            if(partialDeliveryPolicy.unreachableRoute(task.cargo>0)){beginPartialDelivery("Üç farklı kaynağa giden yolu bulamadım");return;}
          }else treeRetryAfterMs=now+30000;
          warn(task.readyToDeliver()?"Sandığa giden yolu bulamadım. Sandık çevresindeki yolu açın.":"Ahmet üç kez yol bulamadı. Yolu açıp tekrar deneyeceğim.");
        }
        return;
      }else if(outcome==NavigationWatchdog.Outcome.WAIT&&!npc.getNavigator().isNavigating())return;
    }

    if(!task.active){
      if(task.cargo>0)return;
      Location effectiveHome=getEffectiveHome();
      if(effectiveHome==null||!effectiveHome.getWorld().equals(current.getWorld()))return;
      double distHomeSq=current.distanceSquared(effectiveHome);
      if(distHomeSq<=2.5*2.5){
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
        Location stepTarget=ExplorationPlanner.findStepTowardsHome(current,effectiveHome,16.0,homeStepAttempt,this::isSafeGround);
        if(stepTarget!=null){
          homeStepTarget=stepTarget;
          returningHome=true;
          navigate(stepTarget);
        }else{
          homeStepAttempt++;
          if(homeStepAttempt>4){
            homeStepAttempt=0;
            treeRetryAfterMs=now+10000;
            warn("Eve giden yolu bulamıyorum, önümde engel var.");
          }
        }
      }
      return;
    }

    if(explorationTarget!=null){
      List<Block> spotted=findTargets(current,task.materialPreference);
      if(!spotted.isEmpty()){
        if(npc.getNavigator().isNavigating())npc.getNavigator().cancelNavigation();
        navigationGoal=null;navigationWatchdog.reset();
        explorationTarget=null;explorationAttempts=0;
      }
    }

    if(npc.getNavigator().isNavigating() || now<treeRetryAfterMs)return;

    if(task.readyToDeliver()){
      if(chestLocation==null || !chestLocation.getWorld().equals(current.getWorld())){warn("Teslimat sandığı kayıp veya başka dünyada.");return;}
      if(current.distanceSquared(chestLocation.clone().add(.5,0,.5))>3.2*3.2)navigate(chestLocation);
      else execute("deposit");
      return;
    }

    if(treeTarget!=null&&isEligibleTarget(treeTarget.getBlock(),task.materialPreference)){
      if(current.distanceSquared(treeTarget.clone().add(.5,0,.5))>2.9*2.9)navigate(treeTarget);
      else execute("harvest");
      return;
    }

    Location effectiveHome=getEffectiveHome();
    int maxDist=getConfig().getInt("options.max-distance-from-home",32);
    if(effectiveHome!=null&&effectiveHome.getWorld().equals(current.getWorld())&&current.distanceSquared(effectiveHome)>Math.pow(maxDist,2)){
      Location stepTarget=ExplorationPlanner.findStepTowardsHome(current,effectiveHome,16.0,homeStepAttempt,this::isSafeGround);
      if(stepTarget!=null){
        homeStepTarget=stepTarget;
        navigate(stepTarget);
        warn("Güvenlik sınırının dışındayım, eve doğru ilerliyorum.");
        return;
      }
    }

    // JEV only chooses among Java-discovered block targets; travel and item transactions stay deterministic.
    final JsonArray options=new JsonArray();
    List<Block> found=findTargets(current,task.materialPreference);
    if(found.isEmpty()){
      if(explorationAttempts<MAX_EXPLORATION_ATTEMPTS){
        explorationAttempts++;
        Location waypoint=ExplorationPlanner.findExplorationWaypoint(current,home,protectedHome,protectHomeRadius,maxDist,explorationAttempts,this::isSafeGround);
        if(waypoint!=null){
          explorationTarget=waypoint;
          navigate(waypoint);
          say("Yakında kaynak bulamadım, etrafta araştırıyorum...");
          return;
        }
      }
      explorationAttempts=0;explorationTarget=null;
      if(partialDeliveryPolicy.emptyScan(task.cargo>0)){beginPartialDelivery("Yakında ulaşabileceğim başka kaynak bulamadım");return;}
      treeRetryAfterMs=now+30000;
      warn("Yakınımda güvenle toplayabileceğim uygun kaynak bulamadım. Biraz yaklaş veya sonra tekrar dene.");
      return;
    }
    explorationAttempts=0;explorationTarget=null;
    partialDeliveryPolicy.targetsFound();
    LinkedHashMap<String,Location> offeredTargets=new LinkedHashMap<>();
    for(int i=0;i<found.size();i++){
      String id="target_"+(char)('a'+i);Block block=found.get(i);
      double distance=Math.sqrt(current.distanceSquared(block.getLocation()));
      String itemName=ResourceCatalog.outputMaterial(task.materialPreference);
      String output= itemName==null?block.getType().name():itemName;
      options.add(candidate(id,"Walk to "+block.getType().name().toLowerCase(java.util.Locale.ROOT)+" and collect "+output.toLowerCase(java.util.Locale.ROOT)+", distance "+Math.round(distance)+" blocks"));
      offeredTargets.put(id,block.getLocation());
    }
    options.add(candidate("wait","Wait briefly to assess safety; no progress"));
    JsonObject req=new JsonObject();JsonObject observation=new JsonObject();
    boolean woodTask=!task.materialPreference.startsWith("item:");
    observation.addProperty("task",woodTask?"collect_wood":"collect_item");observation.addProperty("materialPreference",task.materialPreference);observation.addProperty("requested",task.requested);observation.addProperty("collected",task.collected);
    observation.addProperty("cargo",task.cargo);observation.addProperty("deposited",task.deposited);observation.addProperty("requiredAction",woodTask?"select_tree":"select_resource");
    observation.addProperty("chestBound",chestLocation!=null);observation.addProperty("position",coords(current));
    req.add("observation",observation);req.add("actions",options);
    deciding=true;
    final long generation=jobGeneration;
    brain.post("/api/decide",req).whenComplete((response,error)->Bukkit.getScheduler().runTask(this,()->{
      deciding=false;if(!task.active || npc==null || !npc.isSpawned() || jobGeneration!=generation)return;
      // JEV may select only an offered target ID. Java retains each real block location.
      String selected=error==null&&response.has("id")?response.get("id").getAsString():"wait";
      String source=error==null&&response.has("source")?response.get("source").getAsString():"fallback";
      String model=error==null&&response.has("model")?response.get("model").getAsString():"none";
      if(!Set.of("jev","heuristic","forced","fallback").contains(source))source="unknown";
      model=model.replaceAll("[^A-Za-z0-9._-]","");if(model.length()>64)model=model.substring(0,64);
      String candidateId=offeredTargets.containsKey(selected)?selected:"unoffered";
      Location selectedTarget=offeredTargets.get(selected);
      if(selectedTarget!=null){treeTarget=selectedTarget;treeRetryAfterMs=0;waitDecisions=0;getLogger().info("Gather target selection source: "+source+", model: "+model+", candidate: "+candidateId+", block="+coords(selectedTarget));}
      else {
        getLogger().info("Gather target selection source: "+source+", model: "+model+", candidate: "+candidateId);
        if(++waitDecisions>=3&&!offeredTargets.isEmpty()){
          waitDecisions=0;treeTarget=offeredTargets.values().iterator().next();
        }
      }
    }));
  }
  private JsonObject candidate(String id,String description){JsonObject result=new JsonObject();result.addProperty("id",id);result.addProperty("description",description);return result;}
  private void execute(String action){
    Location here=npc.getEntity().getLocation();
    switch(action){
      case "harvest" -> {
        if(treeTarget==null || here.distanceSquared(treeTarget.clone().add(.5,0,.5))>3.5*3.5){treeTarget=null;return;}
        Block block=treeTarget.getBlock();if(!isEligibleTarget(block,task.materialPreference)){treeTarget=null;return;}
        harvest(block);
      }
      case "deposit" -> {
        Block block=chestLocation.getBlock();if(!(block.getState() instanceof Chest chest)){warn("İşaretli sandık artık orada değil.");return;}
        deposit(chest);
      }
      default -> {}
    }
  }
  private void harvest(Block block){
    String outputMaterial=expectedOutput(block,task.materialPreference);
    if(outputMaterial==null){treeTarget=null;return;}
    OperationJournal.Harvest operation=new OperationJournal.Harvest(task.taskId,block.getWorld().getName(),
      block.getX(),block.getY(),block.getZ(),task.collected,task.cargo,block.getType().name(),outputMaterial);
    try{
      journal.prepareHarvest(operation);
      // Write-ahead record precedes the world mutation; task YAML follows it.
      block.setType(Material.AIR,false);
      task.harvestOne(outputMaterial);
      persist();
      journal.completeHarvest();
      treeTarget=null;
      partialDeliveryPolicy.harvested();
      if(task.collected>=task.requested)say(task.collected+" kaynağı topladım, sandığa gidiyorum.");
    }catch(Exception e){
      treeTarget=null;
      blockRecovery("Harvest transaction is pending; the job is paused",e);
    }
  }
  private void deposit(Chest chest){
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
        plan.moved(),plan.changes(),material);
      journal.prepareDeposit(operation);
      writeInventoryPlan(inventory,after,plan.changes());
      task.deposit(material,plan.moved());
      persist();
      saveJournalWorlds(chest.getWorld());
      journal.clearAfterWorldSave();
      if(task.cargo>0){warn("Sandık dolu; "+task.cargo+" kaynak hâlâ yanımda.");return;}
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
        Inventory inventory=deliveryInventory(chest);
        var plan=new CargoDepositPlanner.Plan(operation.effectiveMaterial(),operation.moved(),operation.moved(),0,operation.changes());
        List<CargoDepositPlanner.Slot> after=CargoDepositPlanner.apply(inventorySlots(inventory),plan);
        writeInventoryPlan(inventory,after,operation.changes());
        if(disposition==OperationRecovery.DepositDisposition.APPLY)task.deposit(operation.effectiveMaterial(),operation.moved());
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
    boolean partial=task.partialDelivery;int delivered=task.deposited;int requested=task.requested;
    task.finish();jobGeneration++;persist();event("task_delivered",task.deposited);
    returningHome=true;homeStepAttempt=0;clearNavigation();
    if(partial)say("Şimdilik "+delivered+"/"+requested+" kaynak topladım ve sandığa bıraktım. Ulaşılabilir kaynağı bulamayınca durdum; yolu açınca kalanını tekrar isteyebilirsin.");
    else say("Bitti! "+task.deposited+" itemi sandığa bıraktım.");
  }
  private void beginPartialDelivery(String reason){
    if(!task.active||task.cargo<=0)return;
    try{
      task.requestPartialDelivery();treeTarget=null;treeRetryAfterMs=0;partialDeliveryPolicy.reset();
      navigationGoal=null;navigationRetry.clear();navigationWatchdog.reset();
      explorationTarget=null;explorationAttempts=0;homeStepTarget=null;persist();
      say(reason+"; topladığım "+task.cargo+" kaynağı sandığa götürüyorum.");
    }catch(Exception e){blockRecovery("Could not begin partial delivery",e);}
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
    if(!floor.getType().isSolid()||floor.isLiquid())return false;
    if(floor.getType()==Material.MAGMA_BLOCK||floor.getType()==Material.CACTUS)return false;
    if(!feet.isPassable()||feet.isLiquid())return false;
    if(!head.isPassable()||head.isLiquid())return false;
    return true;
  }
  private void navigate(Location location){
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
    Location target=location.clone().add(.5,0,.5);
    if(!sameBlock(navigationGoal,target)){navigationGoal=target;navigationWatchdog.reset();}
    issueNavigationTarget(target);
  }
  private void issueNavigationTarget(Location target){
    if(npc==null||!npc.isSpawned()||target==null)return;
    npc.getNavigator().setTarget(target);
    // Citizens documents these per-target parameters; -1 disables destination and stuck teleports.
    npc.getNavigator().getLocalParameters().destinationTeleportMargin(-1).stationaryTicks(-1);
  }
  private void clearNavigation(){
    if(npc!=null&&npc.isSpawned())npc.getNavigator().cancelNavigation();
    navigationGoal=null;navigationWatchdog.reset();navigationRetry.clear();
    explorationTarget=null;homeStepTarget=null;
  }
  private static boolean sameBlock(Location first,Location second){
    return first!=null&&first.getWorld().equals(second.getWorld())&&first.getBlockX()==second.getBlockX()
      &&first.getBlockY()==second.getBlockY()&&first.getBlockZ()==second.getBlockZ();
  }
  private List<Block> findTargets(Location origin,String preference){
    World world=origin.getWorld();int r=Math.min(24,Math.max(8,getConfig().getInt("options.search-radius",24)));
    List<Block> candidates=new ArrayList<>();
    for(int x=-r;x<=r;x++)for(int z=-r;z<=r;z++){
      if(x*x+z*z>r*r)continue;
      for(int y=-8;y<=12;y++){
        int xx=origin.getBlockX()+x, yy=origin.getBlockY()+y, zz=origin.getBlockZ()+z;
        if(yy<world.getMinHeight()||yy>=world.getMaxHeight())continue;
        if(!world.isChunkLoaded(xx>>4,zz>>4))continue;
        Block block=world.getBlockAt(xx,yy,zz);
        if(matchesTarget(block,preference)
            &&!failedTargets.contains(world.getName(),xx,yy,zz,System.currentTimeMillis()))candidates.add(block);
      }
    }
    candidates.sort(Comparator.comparingDouble(block->origin.distanceSquared(block.getLocation())));
    List<Block> eligible=new ArrayList<>(8);
    int checks=0;
    for(Block block:candidates){
      if(checks++>=64)break;
      if(isEligibleTarget(block,preference))eligible.add(block);
      if(eligible.size()>=8)break;
    }
    return eligible;
  }
  private boolean matchesTarget(Block block,String preference){
    if(WoodMaterials.matchesPreference(block.getType(),preference))return true;
    return ResourceCatalog.matchesBlockName(block.getType().name(),preference);
  }
  private boolean isEligibleTarget(Block block,String preference){
    if(!matchesTarget(block,preference))return false;
    if(ResourceCatalog.isPreference(preference))return expectedOutput(block,preference)!=null;
    if(WoodMaterials.isCollectableWood(block.getType())&&isProtectedWood(block))return false;
    return hasNaturalWoodMarkers(block);
  }
  private boolean isProtectedWood(Block block){
    if(playerPlacedWood.contains(block.getWorld().getName(),block.getX(),block.getY(),block.getZ()))return true;
    if(protectedHome==null||protectHomeRadius==0||!protectedHome.getWorld().equals(block.getWorld()))return false;
    return new ProtectedHomeArea(protectedHome.getWorld().getName(),protectedHome.getBlockX(),protectedHome.getBlockY(),protectedHome.getBlockZ(),protectHomeRadius)
        .contains(block.getWorld().getName(),block.getX(),block.getY(),block.getZ());
  }
  private String expectedOutput(Block block,String preference){
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
