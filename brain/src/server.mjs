import http from 'node:http';
import {loadEnv} from './load-env.mjs';
loadEnv();
import {resolve} from 'node:path';
import {MemoryStore} from './store.mjs';
import {chat,decide} from './engine.mjs';
import {configuredJevModel} from './models.mjs';
const port=Number(process.env.BRAIN_PORT??3197);
const host=process.env.BRAIN_HOST??'127.0.0.1';
const token=process.env.BRIDGE_TOKEN;
const store=new MemoryStore(resolve(process.env.MEMORY_PATH??'./data/memory.json'));
await store.load();
const send=(res,status,value)=>{res.writeHead(status,{'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store'});res.end(JSON.stringify(value));};
http.createServer(async(req,res)=>{
  if(req.method==='GET'&&req.url==='/health'){send(res,200,{ok:true,service:'villager-ahmet-brain',decisionMode:process.env.AHMET_DECISION_MODE??'jev',chatMode:process.env.AHMET_CHAT_MODE??'llm',configuredJevModel:configuredJevModel()});return;}
  if(!token||req.headers.authorization!==`Bearer ${token}`){send(res,401,{error:'Unauthorized or missing BRIDGE_TOKEN'});return;}
  if(req.method!=='POST'||!['/api/chat','/api/decide','/api/event'].includes(req.url)){send(res,404,{error:'Not found'});return;}
  try {
    let raw=''; for await(const part of req){raw+=part;if(raw.length>16000)throw new Error('Body too large');}
    const body=JSON.parse(raw);
    const result=req.url==='/api/chat'?await chat(body,{store}):req.url==='/api/decide'?await decide(body):await (async()=>{
      if(!['task_started','task_cancelled','task_delivered'].includes(body.type)) throw new Error('Invalid event type');
      await store.recordEvent({event:body.type,actor:String(body.actor??'').slice(0,80),count:Number(body.count??0)});return {ok:true};
    })();
    send(res,200,result);
  }catch(e){send(res,400,{error:String(e.message).slice(0,300)});}
}).listen(port,host,()=>console.log(`Ahmet brain listening on http://${host}:${port}`));
