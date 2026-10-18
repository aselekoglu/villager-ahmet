import {parseTask,isCancel,isReturnHome,validateTask} from './intent.mjs';
import {chooseAction,conversationalReply,interpretTurn} from './models.mjs';
import {sanitizePerception} from './perception.mjs';
function busyReply(req) {
  const cargo=Number(req.context?.cargo??0);
  if(!req.context?.taskActive&&Number.isInteger(cargo)&&cargo>0)
    return `Yanımda ${cargo} kaynak var. Önce /ahmet deliver komutuyla sandığa teslim edeyim, sonra yeni görev alabilirim.`;
  return 'Şu anda bir görevdeyim. Önce bitirmemi bekle ya da /ahmet cancel yaz.';
}
export async function chat(req,{store,models={chooseAction,conversationalReply,interpretTurn},key,chatMode}={}) {
  const actor=String(req.actorUuid??'').slice(0,80);
  const name=String(req.actorName??'oyuncu').slice(0,40);
  const message=String(req.text??'').slice(0,500);
  const busy=Boolean(req.context?.busy);
  let task=null,reply,taskSource='none';
  const returnHome=isReturnHome(message);
  if(returnHome)reply='Tamam, eve dönüyorum.';
  else if(isCancel(message))reply='Tamam, duruyorum. Görevi iptal ettim.';
  else {
    const memory=store.context(actor);
    const generation=req.context?.generation;
    const perception=sanitizePerception(req.context?.perception,{expectedGeneration:generation});
    const modelOptions={key,mode:chatMode,memory,events:memory.verifiedEvents,perception,generation:perception.generation};
    let turn=null,modelFailed=false;
    try {turn=await models.interpretTurn(message,name,store.history(actor),modelOptions);}
    catch {modelFailed=true;}
    if(turn){
      reply=typeof turn.reply==='string'?turn.reply.slice(0,350):'Hmm, bir daha söyler misin?';
      if(turn.task!==null&&turn.task!==undefined){
        const validated=validateTask(turn.task);
        if(validated){
          if(busy)reply=busyReply(req);
          else if(!req.context?.chestBound)reply='Topladıklarımı nereye koyacağımı bilmiyorum. Bir sandığa bakıp /ahmet chest komutunu kullan.';
          else {task=validated;taskSource='llm';}
        }else reply='Bu isteği güvenli ve desteklenen bir kaynak görevi olarak çözemiyorum. Türü ve 1–64 arası miktarı belirt.';
      }
    }else if(modelFailed){
      const parsed=parseTask(message);
      if(parsed?.error)reply=parsed.error;
      else if(parsed&&busy)reply=busyReply(req);
      else if(parsed&&!req.context?.chestBound)reply='Topladıklarımı nereye koyacağımı bilmiyorum. Bir sandığa bakıp /ahmet chest komutunu kullan.';
      else if(parsed){task=validateTask(parsed);taskSource='heuristic';reply=`Tamam ${name}, ${parsed.amount} kaynak toplayıp işaretlediğin sandığa bırakacağım.`;}
      else {
        try {reply=await models.conversationalReply(message,name,store.history(actor),modelOptions);}
        catch {reply=`Selam ${name}! Buradayım. Yaygın odun ve kaynakları toplamamı istersen söyle.`;}
      }
    }
  }
  await store.record(actor,message,reply,task,name);
  return {reply,task,taskSource,cancel:isCancel(message)||returnHome,goHome:returnHome};
}
export async function decide(req,{models={chooseAction},key,decisionMode}={}) {
  if(!req||!Array.isArray(req.actions)||req.actions.length===0||req.actions.length>16)throw new Error('Invalid actions');
  const actions=req.actions.map(a=>({id:String(a.id).slice(0,40),description:String(a.description??a.id).slice(0,180)}));
  if(actions.some(a=>!/^[a-z_]+$/.test(a.id)))throw new Error('Invalid action id');
  const observation=req.observation??{};
  const perception=sanitizePerception(observation.perception,{expectedGeneration:observation.generation});
  try{return await models.chooseAction({...observation,perception},actions,{key,mode:decisionMode});}
  catch(e){
    const safeFallback=actions.find(a=>!a.id.startsWith('clear_')&&a.id!=='wait')?.id
      ??actions.find(a=>!a.id.startsWith('clear_'))?.id
      ??actions[0].id;
    return {id:safeFallback,source:'fallback',reason:String(e.message).slice(0,140)};
  }
}

