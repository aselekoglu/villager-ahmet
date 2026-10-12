import {parseTask,isCancel} from './intent.mjs';
import {chooseAction,conversationalReply} from './models.mjs';
export async function chat(req,{store,models={chooseAction,conversationalReply},key,chatMode}={}) {
  const actor=String(req.actorUuid??'unknown').slice(0,80);
  const name=String(req.actorName??'oyuncu').slice(0,40);
  const message=String(req.text??'').slice(0,500);
  const busy=Boolean(req.context?.busy);
  let task=null,reply;
  if(isCancel(message))reply='Tamam, duruyorum. Görevi iptal ettim.';
  else {
    const parsed=parseTask(message);
    if(parsed?.error)reply=parsed.error;
    else if(parsed&&busy)reply='Şu anda bir görevdeyim. Önce bitirmemi bekle ya da /ahmet cancel yaz.';
    else if(parsed&&!req.context?.chestBound)reply='Odunları nereye koyacağımı bilmiyorum. Bir sandığa bakıp /ahmet chest komutunu kullan.';
    else if(parsed){task=parsed;reply=`Tamam ${name}, ${parsed.amount} meşe odunu toplayıp işaretlediğin sandığa bırakacağım!`;}
    else {
      try {reply=await models.conversationalReply(message,name,store.history(actor),{key,mode:chatMode,events:store.data.events.filter(e=>e.actor===actor).slice(-10)});}
      catch {reply=`Selam ${name}! Buradayım. Odun toplamak için bana görev verebilirsin.`;}
    }
  }
  await store.record(actor,message,reply,task);
  return {reply,task,cancel:isCancel(message)};
}
export async function decide(req,{models={chooseAction},key,decisionMode}={}) {
  if(!req||!Array.isArray(req.actions)||req.actions.length===0||req.actions.length>16)throw new Error('Invalid actions');
  const actions=req.actions.map(a=>({id:String(a.id).slice(0,40),description:String(a.description??a.id).slice(0,180)}));
  if(actions.some(a=>!/^[a-z_]+$/.test(a.id)))throw new Error('Invalid action id');
  try{return await models.chooseAction(req.observation??{},actions,{key,mode:decisionMode});}
  catch(e){return {id:actions.find(a=>a.id!=='wait')?.id??actions[0].id,source:'fallback',reason:String(e.message).slice(0,140)};}
}
