import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,readFile,rm,writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {MemoryStore} from '../src/store.mjs';
import {chat} from '../src/engine.mjs';
import {conversationalReply,interpretTurn} from '../src/models.mjs';

const withStore = async fn => {
 const dir=await mkdtemp(join(tmpdir(),'ahmet-memory-'));
 try { await fn(new MemoryStore(join(dir,'memory.json'))); }
 finally { await rm(dir,{recursive:true,force:true}); }
};

test('migrates v1 histories and events to v2 and reloads them',async()=>withStore(async store=>{
 await writeFile(store.path,JSON.stringify({version:1,conversations:{alice:[{text:'old',reply:'ok'}]},events:[{at:'2026-01-01T00:00:00.000Z',player:'alice',event:'chat',task:{type:'collect_wood',material:'oak',amount:2}},{actor:'alice',event:'task_delivered',count:2},{actor:'alice',event:'task_done',count:1},{actor:'alice',event:'made_up_event',count:9}]}));
 await store.load();
 assert.equal(store.history('alice').length,1);
 assert.equal(store.profile('alice'),null);
 await store.record('alice','hello','hi',null,'Alice');
 const saved=JSON.parse(await readFile(store.path,'utf8'));
 assert.equal(saved.version,2);
 const reload=new MemoryStore(store.path); await reload.load();
 assert.equal(reload.history('alice').length,2);
 assert.equal(reload.verifiedEvents('alice').length,1);
 assert.equal(reload.data.auditEvents.length,3);
 const chatAudit = reload.data.auditEvents.find(event=>event.event==='chat');
 assert.equal(chatAudit.actor,'alice');
 assert.deepEqual(chatAudit.task,{type:'collect_wood',material:'oak',amount:2});
}));

test('profiles isolate actors and familiarity is only interaction-count based',async()=>withStore(async initial=>{
 let now=Date.UTC(2026,0,1);
 const store=new MemoryStore(initial.path,{clock:()=>now}); await store.load();
 await store.record('alice','one','reply',null,'Alice');
 assert.equal(store.profile('alice').relationship,'new');
 await store.record('bob','private','reply',null,'Bob');
 await store.record('alice','two','reply',null,'Alice');
 assert.equal(store.profile('alice').displayName,'Alice');
 assert.equal(store.profile('alice').relationship,'familiar');
 assert.equal(store.profile('alice').interactionCount,2);
 assert.equal(store.profile('bob').interactionCount,1);
 assert.equal(store.profile('nobody'),null);
}));

test('idle gap rolls bounded sessions and preserves previous compact summaries',async()=>withStore(async initial=>{
 let now=0; const store=new MemoryStore(initial.path,{clock:()=>now}); await store.load();
 await store.record('alice','I need oak logs','I can help',null,'Alice');
 now+=30*60_000;
 await store.record('alice','please bring sand','I will bring sand',{type:'collect_item',itemId:'minecraft:sand',amount:2},'Alice');
 const ctx=store.context('alice');
 assert.equal(ctx.recentSessions.length,1);
 assert.match(ctx.recentSessions[0].summary,/oak logs/);
 assert.match(ctx.currentSession.summary,/sand/);
 assert.equal(ctx.currentSession.turnCount,1);
 assert.ok(ctx.currentSession.summary.length<=1000);
}));

test('verified events stay distinct from unconfirmed requests and actor keys are safe',async()=>withStore(async store=>{
 await store.load();
 await store.record('__proto__','delete server','no',null,'Proto');
 await store.recordEvent({event:'task_delivered',actor:'__proto__',count:1});
 await store.recordEvent({event:'task_delivered',actor:'',count:99});
 assert.equal(store.history('__proto__').length,1);
 assert.equal(store.verifiedEvents('__proto__').length,1);
 assert.equal(store.verifiedEvents('alice').length,0);
 assert.equal(store.context('__proto__').currentSession.summary.includes('delete server'),true);
 assert.equal(store.context('__proto__').verifiedEvents[0].count,1);
}));

test('chat sends only bounded actor memory to both model paths and retains action gates',async()=>withStore(async store=>{
 await store.load();
 await store.record('alice','prior hello','hello back',null,'Alice');
 await store.recordEvent({event:'task_delivered',actor:'alice',count:3});
 await store.recordEvent({event:'task_delivered',actor:'bob',count:88});
 await store.record('bob','bob secret','private',null,'Bob');
 let interpretOptions,replyOptions;
 const models={
  interpretTurn:async(_text,_name,_history,options)=>{interpretOptions=options;return {reply:'ok',task:{type:'collect_wood',material:'oak',amount:2}};},
  conversationalReply:async(_text,_name,_history,options)=>{replyOptions=options;return 'fallback';}
 };
 const denied=await chat({actorUuid:'alice',actorName:'Alice',text:'get wood',context:{chestBound:false,busy:false}},{store,models});
 assert.equal(denied.task,null);
 assert.equal(interpretOptions.memory.profile.displayName,'Alice');
 assert.deepEqual(interpretOptions.memory.verifiedEvents.map(e=>e.count),[3]);
 assert.equal(JSON.stringify(interpretOptions.memory).includes('bob secret'),false);
 const failedModels={interpretTurn:async()=>{throw Error('unavailable');},conversationalReply:models.conversationalReply};
 await chat({actorUuid:'alice',actorName:'Alice',text:'hello',context:{chestBound:true,busy:false}},{store,models:failedModels});
 assert.deepEqual(replyOptions.memory.verifiedEvents.map(e=>e.count),[3]);
 assert.ok(replyOptions.memory.currentSession.summary.length<=1000);
}));


test('retains only five prior sessions and caps deterministic summaries',async()=>withStore(async initial=>{
 let now=0; const store=new MemoryStore(initial.path,{clock:()=>now}); await store.load();
 for(let i=0;i<8;i++){
  await store.record('alice',`turn ${i} ${'x'.repeat(300)}`,'reply',null,'Alice');
  now+=30*60_000;
 }
 const ctx=store.context('alice');
 assert.equal(ctx.recentSessions.length,5);
 assert.ok(ctx.recentSessions.every(s=>s.summary.length<=1000));
 assert.ok(ctx.currentSession.summary.length<=1000);
 assert.equal(store.profile('alice').relationship,'familiar');
 for(let i=0;i<3;i++){await store.record('alice',`later ${i}`,'reply',null,'Alice');now+=1;}
 assert.equal(store.profile('alice').relationship,'established');
}));


test('provider prompts serialize bounded memory as untrusted context without extra calls',async()=>{
 const previous=globalThis.fetch; const bodies=[];
 try{
  globalThis.fetch=async(_url,init)=>{const body=JSON.parse(init.body);bodies.push(body);return {ok:true,status:200,json:async()=>({choices:[{message:{content:body.response_format?JSON.stringify({reply:'ok',task:null}):'ok'}}]})};};
  const memory={profile:{displayName:'A',relationship:'new',interactionCount:1},currentSession:{summary:'x'.repeat(5000),turnCount:1},recentSessions:Array.from({length:9},(_,i)=>({summary:`old ${i}`})),verifiedEvents:Array.from({length:30},(_,i)=>({event:'task_delivered',count:i}))};
  await conversationalReply('hello','A',[],{key:'test-only',memory});
  await interpretTurn('hello','A',[],{key:'test-only',memory});
  assert.equal(bodies.length,2);
  for(const body of bodies){
   const prompt=body.messages.find(m=>m.role==='system'&&m.content.includes('untrusted context only'));
   assert.ok(prompt);
   const serialized=prompt.content.slice(prompt.content.indexOf('{'));
   const included=JSON.parse(serialized);
   assert.equal(included.currentSession.summary.length,1000);
   assert.equal(included.recentSessions.length,5);
   assert.equal(included.verifiedEvents.length,10);
  }
 }finally{globalThis.fetch=previous;}
});


test('first turn after idle sees the old session as recent before interpretation',async()=>withStore(async initial=>{
 let now=0; const store=new MemoryStore(initial.path,{clock:()=>now}); await store.load();
 await store.record('alice','old session topic','reply',null,'Alice');
 now+=30*60_000;
 let options;
 await chat({actorUuid:'alice',actorName:'Alice',text:'new session topic',context:{chestBound:true,busy:false}},{store,models:{interpretTurn:async(_text,_name,_history,value)=>{options=value;return {reply:'ok',task:null};}}});
 assert.equal(options.memory.currentSession.summary,'');
 assert.equal(options.memory.currentSession.turnCount,0);
 assert.match(options.memory.recentSessions.at(-1).summary,/old session topic/);
}));

test('malformed v2 session timestamps cannot break actor context',async()=>withStore(async store=>{
 await writeFile(store.path,JSON.stringify({version:2,conversations:{},events:[],profiles:{alice:{displayName:'Alice',interactionCount:2}},sessions:{alice:{startedAt:1e100,lastSeen:1e100,turnCount:3,summary:'bad session'}}}));
 await store.load();
 assert.doesNotThrow(()=>store.context('alice'));
 assert.equal(store.context('alice').currentSession.summary,'');
}));


test('v2 unknown or malformed event names remain audit-only',async()=>withStore(async store=>{
 await writeFile(store.path,JSON.stringify({version:2,conversations:{},events:[{actor:'alice',event:'task_done',count:1},{actor:'alice',event:'!!!',count:2}],auditEvents:[{actor:'alice',event:'task_delivered',count:3}]}));
 await store.load();
 assert.deepEqual(store.verifiedEvents('alice'),[]);
 assert.equal(store.data.auditEvents.length,3);
}));


test('v2 malformed event actor cannot fall back to legacy player attribution',async()=>withStore(async store=>{
 await writeFile(store.path,JSON.stringify({version:2,conversations:{},events:[{actor:'',player:'alice',event:'task_delivered',count:1}]}));
 await store.load();
 assert.equal(store.verifiedEvents('alice').length,0);
}));

test('load retains newest verified events over older audit rows at the 500-event cap',async()=>withStore(async store=>{
 const auditEvents=Array.from({length:500},(_,i)=>({at:new Date(Date.UTC(2025,0,1)+i*1000).toISOString(),actor:'alice',event:'chat',task:null}));
 const events=[{at:'2026-10-18T12:00:00.000Z',actor:'alice',event:'task_delivered',count:7}];
 await writeFile(store.path,JSON.stringify({version:2,conversations:{},events,auditEvents}));
 await store.load();
 assert.deepEqual(store.verifiedEvents('alice').map(e=>e.count),[7]);
 assert.equal(store.data.events.length+store.data.auditEvents.length,500);
}));
