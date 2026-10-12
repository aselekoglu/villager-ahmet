package dev.ahmet;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.UUID;
final class WoodTask {
  String taskId="";
  boolean active=false;
  String owner="";
  int requested=0,collected=0,deposited=0,cargo=0;
  void start(UUID user,int count){
    if(user==null)throw new IllegalArgumentException("Task owner is required");
    if(count<1||count>64)throw new IllegalArgumentException("Task amount must be between 1 and 64");
    if(active||cargo>0)throw new IllegalStateException("Finish or recover the current task before starting another");
    taskId=UUID.randomUUID().toString();active=true;owner=user.toString();requested=count;collected=0;deposited=0;cargo=0;
  }
  void harvestOne(){
    if(!active||requested<1||requested>64||collected>=requested||cargo>=64)
      throw new IllegalStateException("Task cannot collect another log");
    collected++;cargo++;
  }
  void cancel(){active=false;}
  void resumeDelivery(){
    if(active||cargo<=0)throw new IllegalStateException("There is no cancelled cargo to deliver");
    requested=collected;active=true;
  }
  void deposit(int moved){
    if(moved<0||moved>cargo)throw new IllegalArgumentException("Deposit exceeds carried cargo");
    cargo-=moved;deposited+=moved;
  }
  boolean readyToFinish(){return collected>=requested&&cargo==0;}
  void finish(){
    if(!readyToFinish())throw new IllegalStateException("Task is not ready to finish");
    active=false;
  }
  String describe(){return !active?"boşta (taşınan odun: "+cargo+")":"odun "+collected+"/"+requested+" toplandı, "+deposited+" teslim";}
  void load(FileConfiguration c){
    active=c.getBoolean("task.active");owner=c.getString("task.owner","");requested=c.getInt("task.requested");
    collected=c.getInt("task.collected");deposited=c.getInt("task.deposited");cargo=c.getInt("task.cargo");
    taskId=c.getString("task.id","");
    if(taskId.isBlank()&&(active||cargo>0))taskId=UUID.randomUUID().toString();
  }
  void save(FileConfiguration c){
    c.set("task.id",taskId);c.set("task.active",active);c.set("task.owner",owner);c.set("task.requested",requested);
    c.set("task.collected",collected);c.set("task.deposited",deposited);c.set("task.cargo",cargo);
  }
}
