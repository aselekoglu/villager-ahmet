package dev.ahmet;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
final class WoodTask {
  String taskId="";
  boolean active=false;
  String owner="";
  String materialPreference="any";
  int requested=0,collected=0,deposited=0,cargo=0;
  private final Map<String,Integer> cargoItems=new LinkedHashMap<>();
  void start(UUID user,int count){start(user,count,"any");}
  void start(UUID user,int count,String preference){
    if(user==null)throw new IllegalArgumentException("Task owner is required");
    if(count<1||count>64)throw new IllegalArgumentException("Task amount must be between 1 and 64");
    if(!WoodMaterials.isPreference(preference))throw new IllegalArgumentException("Unsupported wood preference");
    if(active||cargo>0)throw new IllegalStateException("Finish or recover the current task before starting another");
    taskId=UUID.randomUUID().toString();active=true;owner=user.toString();materialPreference=preference;requested=count;collected=0;deposited=0;cargo=0;cargoItems.clear();
  }
  void harvestOne(){harvestOne("OAK_LOG");}
  void harvestOne(String material){
    if(!active||requested<1||requested>64||collected>=requested||cargo>=64)
      throw new IllegalStateException("Task cannot collect another wood block");
    if(!WoodMaterials.isAllowedName(material))throw new IllegalArgumentException("Unsupported wood item");
    collected++;cargo++;cargoItems.merge(material,1,Integer::sum);
  }
  void cancel(){active=false;}
  void resumeDelivery(){
    if(active||cargo<=0)throw new IllegalStateException("There is no cancelled cargo to deliver");
    requested=collected;active=true;
  }
  void deposit(int moved){deposit("OAK_LOG",moved);}
  void deposit(String material,int moved){
    if(moved<0||moved>cargo)throw new IllegalArgumentException("Deposit exceeds carried cargo");
    int held=cargoItems.getOrDefault(material,0);
    if(!WoodMaterials.isAllowedName(material)||moved>held)throw new IllegalArgumentException("Deposit exceeds this wood type's cargo");
    if(moved==held)cargoItems.remove(material);else cargoItems.put(material,held-moved);
    cargo-=moved;deposited+=moved;
  }
  Map<String,Integer> cargoItems(){return Map.copyOf(cargoItems);}
  String nextCargoMaterial(){return cargoItems.keySet().stream().findFirst().orElse(null);}
  boolean readyToFinish(){return collected>=requested&&cargo==0;}
  void finish(){
    if(!readyToFinish())throw new IllegalStateException("Task is not ready to finish");
    active=false;
  }
  String describe(){
    String resource=materialPreference.startsWith("item:")?materialPreference.substring("item:".length()).toLowerCase(java.util.Locale.ROOT):"kütük";
    return !active?"boşta (taşınan kaynak: "+cargo+")":resource+" "+collected+"/"+requested+" toplandı, "+deposited+" teslim";
  }
  void load(FileConfiguration c){
    active=c.getBoolean("task.active");owner=c.getString("task.owner","");requested=c.getInt("task.requested");
    collected=c.getInt("task.collected");deposited=c.getInt("task.deposited");cargo=c.getInt("task.cargo");
    materialPreference=c.getString("task.materialPreference","any");
    if(!WoodMaterials.isPreference(materialPreference))throw new IllegalStateException("Saved wood preference is unsupported");
    cargoItems.clear();
    Object stored=c.get("task.cargoItems");
    int itemsTotal=0;
    if(stored instanceof org.bukkit.configuration.ConfigurationSection section){
      for(String key:section.getKeys(false))itemsTotal+=loadCargoItem(key,section.get(key));
    }else if(stored instanceof Map<?,?> items){
      for(var entry:items.entrySet()){
        if(!(entry.getKey() instanceof String key))throw new IllegalStateException("Saved cargo has an invalid material key");
        itemsTotal+=loadCargoItem(key,entry.getValue());
      }
    }else if(stored!=null)throw new IllegalStateException("Saved cargo map has an unsupported format");
    if(stored!=null&&itemsTotal!=cargo)throw new IllegalStateException("Saved cargo map does not match total cargo");
    if(stored==null&&cargo>0){
      if(cargo>64)throw new IllegalStateException("Legacy saved cargo exceeds the task bound");
      cargoItems.put("OAK_LOG",cargo);
    }
    taskId=c.getString("task.id","");
    if(taskId.isBlank()&&(active||cargo>0))taskId=UUID.randomUUID().toString();
  }
  private int loadCargoItem(String material,Object rawAmount){
    if(!WoodMaterials.isAllowedName(material)||!(rawAmount instanceof Number number))
      throw new IllegalStateException("Saved cargo contains an unsupported wood material");
    int amount=number.intValue();
    if(amount<1||amount>64)throw new IllegalStateException("Saved cargo amount is outside the task bound");
    cargoItems.put(material,amount);
    int total=cargoItems.values().stream().mapToInt(Integer::intValue).sum();
    if(total>64)throw new IllegalStateException("Saved cargo exceeds the task bound");
    return amount;
  }
  void save(FileConfiguration c){
    c.set("task.id",taskId);c.set("task.active",active);c.set("task.owner",owner);c.set("task.requested",requested);
    c.set("task.collected",collected);c.set("task.deposited",deposited);c.set("task.cargo",cargo);
    c.set("task.materialPreference",materialPreference);c.set("task.cargoItems",new LinkedHashMap<>(cargoItems));
  }
}
