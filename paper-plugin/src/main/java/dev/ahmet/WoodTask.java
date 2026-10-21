package dev.ahmet;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
final class WoodTask {
  enum Kind { GATHER, CRAFT, FARM }
  Kind kind = Kind.GATHER;
  CraftRecipe craftRecipe = null;
  String taskId="";
  boolean active=false;
  boolean partialDelivery=false;
  String owner="";
  String materialPreference="any";
  int requested=0,collected=0,deposited=0,cargo=0,cleared=0;
  static final int MAX_JOB_CLEARS=12;
  private final Map<String,Integer> cargoItems=new LinkedHashMap<>();

  boolean isGather() { return kind == Kind.GATHER; }
  boolean isCraft() { return kind == Kind.CRAFT; }
  boolean isFarm() { return kind == Kind.FARM; }
  CraftRecipe craftRecipe() { return craftRecipe; }

  void start(UUID user,int count){start(user,count,"any");}
  void start(UUID user,int count,String preference){
    if(user==null)throw new IllegalArgumentException("Task owner is required");
    if(count<1||count>64)throw new IllegalArgumentException("Task amount must be between 1 and 64");
    if(!WoodMaterials.isPreference(preference))throw new IllegalArgumentException("Unsupported wood preference");
    if(active||cargo>0)throw new IllegalStateException("Finish or recover the current task before starting another");
    taskId=UUID.randomUUID().toString();active=true;partialDelivery=false;kind=Kind.GATHER;craftRecipe=null;owner=user.toString();materialPreference=preference;requested=count;collected=0;deposited=0;cargo=0;cleared=0;cargoItems.clear();
  }

  void startFarm(UUID user,int count){startFarm(user,count,FarmCatalog.ANY_PREFERENCE);}
  void startFarm(UUID user,int count,String preference){
    if(user==null)throw new IllegalArgumentException("Task owner is required");
    if(count<1||count>64)throw new IllegalArgumentException("Task amount must be between 1 and 64");
    if(!FarmCatalog.isPreference(preference))throw new IllegalArgumentException("Unsupported crop preference");
    if(active||cargo>0)throw new IllegalStateException("Finish or recover the current task before starting another");
    taskId=UUID.randomUUID().toString();active=true;partialDelivery=false;kind=Kind.FARM;craftRecipe=null;owner=user.toString();materialPreference=preference;requested=count;collected=0;deposited=0;cargo=0;cleared=0;cargoItems.clear();
  }

  void startCraft(UUID user,CraftRecipe recipe,int exactOutputCount){
    if(user==null)throw new IllegalArgumentException("Task owner is required");
    if(recipe==null)throw new IllegalArgumentException("Craft recipe is required");
    if(exactOutputCount<4||exactOutputCount>64||exactOutputCount%4!=0)throw new IllegalArgumentException("Craft output amount must be 4..64 and divisible by 4");
    if(active||cargo>0)throw new IllegalStateException("Finish or recover the current task before starting another");
    taskId=UUID.randomUUID().toString();active=true;partialDelivery=false;kind=Kind.CRAFT;craftRecipe=recipe;owner=user.toString();materialPreference="any";requested=exactOutputCount;collected=0;deposited=0;cargo=0;cleared=0;cargoItems.clear();
  }

  void completeCraft(int craftedOutputCount){
    if(!active||kind!=Kind.CRAFT||craftRecipe==null)throw new IllegalStateException("No active craft task");
    if(craftedOutputCount!=requested||collected!=0||deposited!=0||cargo!=0)throw new IllegalStateException("Craft completion count does not match active craft task");
    collected=craftedOutputCount;deposited=craftedOutputCount;
  }

  boolean canClearObstruction(){return active&&kind==Kind.GATHER&&cleared<MAX_JOB_CLEARS;}
  void recordClear(){
    if(!active||kind!=Kind.GATHER||cleared>=MAX_JOB_CLEARS)throw new IllegalStateException("Task clear budget exhausted, inactive, or not a gather task");
    cleared++;
  }

  void harvestOne(){
    if(kind==Kind.FARM) {
      if(FarmCatalog.ANY_PREFERENCE.equals(materialPreference)) {
        harvestOne("WHEAT");
      } else {
        harvestOne(materialPreference.substring("crop:".length()));
      }
    } else {
      harvestOne("OAK_LOG");
    }
  }
  void harvestOne(String material){
    if(kind==Kind.CRAFT)throw new IllegalStateException("Craft task cannot harvest blocks");
    if(!active||requested<1||requested>64||collected>=requested||cargo>=64)
      throw new IllegalStateException("Task cannot collect another wood block");
    if(kind==Kind.FARM){
      if(!FarmCatalog.isCropOutput(material))throw new IllegalArgumentException("Unsupported crop item");
      if(!FarmCatalog.ANY_PREFERENCE.equals(materialPreference)){
        String expected=materialPreference.substring("crop:".length());
        if(!expected.equals(material))throw new IllegalArgumentException("Harvested crop does not match crop preference");
      }
    } else {
      if(!WoodMaterials.isAllowedName(material))throw new IllegalArgumentException("Unsupported wood item");
    }
    collected++;cargo++;cargoItems.merge(material,1,Integer::sum);
  }
  void cancel(){active=false;}
  void requestPartialDelivery(){
    if(kind==Kind.CRAFT)throw new IllegalStateException("Craft task cannot request partial cargo delivery");
    if(!active||collected<=0||cargo<=0)throw new IllegalStateException("There is no collected cargo to deliver partially");
    partialDelivery=true;
  }
  void resumeDelivery(){
    if(kind==Kind.CRAFT)throw new IllegalStateException("Craft task cannot resume cargo delivery");
    if(active||cargo<=0)throw new IllegalStateException("There is no cancelled cargo to deliver");
    requested=collected;active=true;
  }
  void deposit(int moved){deposit(nextCargoMaterial()!=null?nextCargoMaterial():"OAK_LOG",moved);}
  void deposit(String material,int moved){
    if(kind==Kind.CRAFT)throw new IllegalStateException("Craft task cannot deposit gather cargo");
    if(moved<0||moved>cargo)throw new IllegalArgumentException("Deposit exceeds carried cargo");
    int held=cargoItems.getOrDefault(material,0);
    if(!WoodMaterials.isAllowedName(material)||moved>held)throw new IllegalArgumentException("Deposit exceeds this wood type's cargo");
    if(moved==held)cargoItems.remove(material);else cargoItems.put(material,held-moved);
    cargo-=moved;deposited+=moved;
  }
  Map<String,Integer> cargoItems(){return Map.copyOf(cargoItems);}
  int cargoCount(String material){return cargoItems.getOrDefault(material,0);}
  String nextCargoMaterial(){return cargoItems.keySet().stream().findFirst().orElse(null);}
  boolean readyToFinish() {
    if (kind == Kind.CRAFT) {
      return craftRecipe != null && requested >= 4 && collected == requested && deposited == requested && cargo == 0;
    }
    return (collected >= requested || partialDelivery) && cargo == 0;
  }
  boolean readyToDeliver() { return (kind == Kind.GATHER || kind == Kind.FARM) && (collected >= requested || partialDelivery); }
  void finish(){
    if(!readyToFinish())throw new IllegalStateException("Task is not ready to finish");
    active=false;partialDelivery=false;
  }
  String describe(){
    if(!active)return "boşta (taşınan kaynak: "+cargo+")";
    if(kind==Kind.CRAFT)
      return (craftRecipe!=null?craftRecipe.key().substring("minecraft:".length()):"üretim")+" "+deposited+"/"+requested+" üretildi";
    if(kind==Kind.FARM) {
      String resource = FarmCatalog.displayItemName(materialPreference);
      if (resource == null) resource = "ekin";
      return resource+" "+collected+"/"+requested+" toplandı, "+deposited+" teslim"+(partialDelivery?" (kısmi teslimat)":"");
    }
    String resource=materialPreference.startsWith("item:")?materialPreference.substring("item:".length()).toLowerCase(java.util.Locale.ROOT):"kütük";
    return resource+" "+collected+"/"+requested+" toplandı, "+deposited+" teslim"+(partialDelivery?" (kısmi teslimat)":"");
  }
  void load(FileConfiguration c){
    boolean loadedActive = c.getBoolean("task.active");
    boolean loadedPartialDelivery = c.getBoolean("task.partialDelivery", false);
    String loadedOwner = c.getString("task.owner", "");
    int loadedRequested = c.getInt("task.requested");
    int loadedCollected = c.getInt("task.collected");
    int loadedDeposited = c.getInt("task.deposited");
    int loadedCargo = c.getInt("task.cargo");
    String loadedPreference = c.getString("task.materialPreference", "any");
    int loadedCleared = c.getInt("task.cleared", 0);

    Kind loadedKind;
    Object rawKind = c.get("task.kind");
    if (rawKind == null) {
      loadedKind = Kind.GATHER;
    } else if (rawKind instanceof String s) {
      if ("GATHER".equals(s)) {
        loadedKind = Kind.GATHER;
      } else if ("CRAFT".equals(s)) {
        loadedKind = Kind.CRAFT;
      } else if ("FARM".equals(s)) {
        loadedKind = Kind.FARM;
      } else {
        throw new IllegalStateException("Saved task kind is unsupported");
      }
    } else {
      throw new IllegalStateException("Saved task kind is unsupported");
    }

    CraftRecipe loadedRecipe = null;
    Map<String, Integer> loadedCargoItems = new LinkedHashMap<>();
    String loadedTaskId = c.getString("task.id", "");

    Object rawRecipe = c.get("task.craftRecipe");
    if (loadedKind == Kind.GATHER) {
      if (!WoodMaterials.isPreference(loadedPreference)) {
        throw new IllegalStateException("Saved wood preference is unsupported");
      }
      if (rawRecipe != null && (!(rawRecipe instanceof String s) || !s.isEmpty())) {
        throw new IllegalStateException("Gather task cannot have a craft recipe");
      }
      if (loadedRequested < 0 || loadedRequested > 64 || loadedCollected < 0 || loadedCollected > loadedRequested
          || loadedDeposited < 0 || loadedDeposited > 64 || loadedCargo < 0 || loadedCargo > 64
          || loadedCollected != loadedDeposited + loadedCargo) {
        throw new IllegalStateException("Saved task counters violate cargo conservation");
      }
      Object stored = c.get("task.cargoItems");
      int itemsTotal = 0;
      if (stored instanceof org.bukkit.configuration.ConfigurationSection section) {
        for (String key : section.getKeys(false)) itemsTotal += parseCargoItem(key, section.get(key), loadedCargoItems);
      } else if (stored instanceof Map<?,?> items) {
        for (var entry : items.entrySet()) {
          if (!(entry.getKey() instanceof String key)) throw new IllegalStateException("Saved cargo has an invalid material key");
          itemsTotal += parseCargoItem(key, entry.getValue(), loadedCargoItems);
        }
      } else if (stored != null) {
        throw new IllegalStateException("Saved cargo map has an unsupported format");
      }
      if (stored != null && itemsTotal != loadedCargo) {
        throw new IllegalStateException("Saved cargo map does not match total cargo");
      }
      if (stored == null && loadedCargo > 0) {
        if (loadedCargo > 64) throw new IllegalStateException("Legacy saved cargo exceeds the task bound");
        loadedCargoItems.put("OAK_LOG", loadedCargo);
      }
      if (loadedTaskId.isBlank() && (loadedActive || loadedCargo > 0)) {
        loadedTaskId = UUID.randomUUID().toString();
      }
      if (loadedCleared < 0 || loadedCleared > 64) {
        throw new IllegalStateException("Saved task cleared count is invalid");
      }
    } else if (loadedKind == Kind.FARM) {
      if (!FarmCatalog.isPreference(loadedPreference)) {
        throw new IllegalStateException("Saved farm task crop preference is unsupported");
      }
      if (rawRecipe != null && (!(rawRecipe instanceof String s) || !s.isEmpty())) {
        throw new IllegalStateException("Farm task cannot have a craft recipe");
      }
      if (loadedRequested < 0 || loadedRequested > 64 || loadedCollected < 0 || loadedCollected > loadedRequested
          || loadedDeposited < 0 || loadedDeposited > 64 || loadedCargo < 0 || loadedCargo > 64
          || loadedCollected != loadedDeposited + loadedCargo) {
        throw new IllegalStateException("Saved task counters violate cargo conservation");
      }
      if (loadedCleared != 0) {
        throw new IllegalStateException("Saved farm task cannot have obstruction clears");
      }
      Object stored = c.get("task.cargoItems");
      int itemsTotal = 0;
      if (stored instanceof org.bukkit.configuration.ConfigurationSection section) {
        for (String key : section.getKeys(false)) {
          if (!FarmCatalog.isCropOutput(key)) {
            throw new IllegalStateException("Saved farm task cargo contains non-crop material: " + key);
          }
          itemsTotal += parseCargoItem(key, section.get(key), loadedCargoItems);
        }
      } else if (stored instanceof Map<?,?> items) {
        for (var entry : items.entrySet()) {
          if (!(entry.getKey() instanceof String key)) throw new IllegalStateException("Saved cargo has an invalid material key");
          if (!FarmCatalog.isCropOutput(key)) {
            throw new IllegalStateException("Saved farm task cargo contains non-crop material: " + key);
          }
          itemsTotal += parseCargoItem(key, entry.getValue(), loadedCargoItems);
        }
      } else if (stored != null) {
        throw new IllegalStateException("Saved cargo map has an unsupported format");
      }
      if (stored != null && itemsTotal != loadedCargo) {
        throw new IllegalStateException("Saved cargo map does not match total cargo");
      }
      if (stored == null && loadedCargo > 0) {
        throw new IllegalStateException("Saved farm task is missing cargo items map");
      }
      if (!FarmCatalog.ANY_PREFERENCE.equals(loadedPreference)) {
        String expectedCrop = loadedPreference.substring("crop:".length());
        for (String key : loadedCargoItems.keySet()) {
          if (!expectedCrop.equals(key)) {
            throw new IllegalStateException("Saved farm cargo does not match crop preference");
          }
        }
      }
      if (loadedTaskId.isBlank() && (loadedActive || loadedCargo > 0)) {
        loadedTaskId = UUID.randomUUID().toString();
      }
    } else {
      if (!"any".equals(loadedPreference)) {
        throw new IllegalStateException("Saved craft task cannot have a wood material preference");
      }
      if (!(rawRecipe instanceof String recipeKey)) {
        throw new IllegalStateException("Saved craft task is missing a valid recipe");
      }
      loadedRecipe = CraftRecipe.forKey(recipeKey);
      if (loadedRecipe == null) {
        throw new IllegalStateException("Saved craft recipe is unsupported");
      }
      Object stored = c.get("task.cargoItems");
      int itemsTotal = 0;
      if (stored instanceof org.bukkit.configuration.ConfigurationSection section) {
        for (String key : section.getKeys(false)) itemsTotal += parseCargoItem(key, section.get(key), loadedCargoItems);
      } else if (stored instanceof Map<?,?> items) {
        for (var entry : items.entrySet()) {
          if (!(entry.getKey() instanceof String key)) throw new IllegalStateException("Saved cargo has an invalid material key");
          itemsTotal += parseCargoItem(key, entry.getValue(), loadedCargoItems);
        }
      } else if (stored != null) {
        throw new IllegalStateException("Saved cargo map has an unsupported format");
      }
      if (loadedPartialDelivery || loadedCargo != 0 || !loadedCargoItems.isEmpty() || loadedCleared != 0) {
        throw new IllegalStateException("Saved craft task has invalid gather/cargo state");
      }
      if (loadedRequested < 4 || loadedRequested > 64 || loadedRequested % 4 != 0) {
        throw new IllegalStateException("Saved craft output amount is invalid");
      }
      if (!((loadedCollected == 0 && loadedDeposited == 0) || (loadedCollected == loadedRequested && loadedDeposited == loadedRequested))) {
        throw new IllegalStateException("Saved craft task counters are invalid");
      }
      if (loadedTaskId.isBlank() && loadedActive) {
        loadedTaskId = UUID.randomUUID().toString();
      }
    }

    this.active = loadedActive;
    this.partialDelivery = loadedPartialDelivery;
    this.owner = loadedOwner;
    this.requested = loadedRequested;
    this.collected = loadedCollected;
    this.deposited = loadedDeposited;
    this.cargo = loadedCargo;
    this.materialPreference = loadedPreference;
    this.cleared = loadedCleared;
    this.kind = loadedKind;
    this.craftRecipe = loadedRecipe;
    this.taskId = loadedTaskId;
    this.cargoItems.clear();
    this.cargoItems.putAll(loadedCargoItems);
  }
  private static int parseCargoItem(String material, Object rawAmount, Map<String, Integer> targetMap){
    if(!WoodMaterials.isAllowedName(material)||!(rawAmount instanceof Number number))
      throw new IllegalStateException("Saved cargo contains an unsupported wood material");
    int amount=number.intValue();
    if(amount<1||amount>64)throw new IllegalStateException("Saved cargo amount is outside the task bound");
    targetMap.put(material,amount);
    int total=targetMap.values().stream().mapToInt(Integer::intValue).sum();
    if(total>64)throw new IllegalStateException("Saved cargo exceeds the task bound");
    return amount;
  }
  void save(FileConfiguration c){
    c.set("task.id",taskId);c.set("task.active",active);c.set("task.partialDelivery",partialDelivery);c.set("task.owner",owner);c.set("task.requested",requested);
    c.set("task.collected",collected);c.set("task.deposited",deposited);c.set("task.cargo",cargo);c.set("task.cleared",cleared);
    c.set("task.materialPreference",materialPreference);c.set("task.cargoItems",new LinkedHashMap<>(cargoItems));
    c.set("task.kind",kind.name());
    c.set("task.craftRecipe",kind==Kind.CRAFT&&craftRecipe!=null?craftRecipe.key():null);
  }
}

