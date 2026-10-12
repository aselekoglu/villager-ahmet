import test from 'node:test';import assert from 'node:assert/strict';
import {parseTask} from '../src/intent.mjs';
import {chat,decide} from '../src/engine.mjs';
import {MemoryStore} from '../src/store.mjs';
import {mkdtemp,rm} from 'node:fs/promises';import {tmpdir} from 'node:os';import {join} from 'node:path';
test('generic wood requests allow any Minecraft log while named wood stays specific',()=>{
 assert.deepEqual(parseTask('Ahmet bana 20 tane odun topla'),{type:'collect_wood',material:'any',amount:20});
 assert.deepEqual(parseTask('collect 8 oak logs'),{type:'collect_wood',material:'oak',amount:8});
 assert.deepEqual(parseTask('8 huş kütüğü getir'),{type:'collect_wood',material:'birch',amount:8});
 assert.deepEqual(parseTask('10 crimson stem getir'),{type:'collect_wood',material:'crimson',amount:10});
 assert.deepEqual(parseTask('6 pale oak kütüğü topla'),{type:'collect_wood',material:'pale_oak',amount:6});
 assert.deepEqual(parseTask('12 bamboo getir'),{type:'collect_wood',material:'bamboo',amount:12});
 assert.match(parseTask('200 logs collect').error,/1–64/);
 assert.equal(parseTask('how are you?'),null);
});
test('No job without assigned chest and no job when busy',async()=>{
 const d=await mkdtemp(join(tmpdir(),'ahmet-'));
 try {const store=new MemoryStore(join(d,'memory.json'));await store.load();
  const a=await chat({actorUuid:'1',actorName:'Ata',text:'20 meşe odunu topla',context:{chestBound:false,busy:false}},{store});assert.equal(a.task,null);
  const b=await chat({actorUuid:'1',actorName:'Ata',text:'20 meşe odunu topla',context:{chestBound:true,busy:true}},{store});assert.equal(b.task,null);
  const c=await chat({actorUuid:'1',actorName:'Ata',text:'20 meşe odunu topla',context:{chestBound:true,busy:false}},{store});assert.equal(c.task.amount,20);
  const fresh=new MemoryStore(join(d,'memory.json'));await fresh.load();assert.equal(fresh.history('1').length,3);
 }finally{await rm(d,{recursive:true,force:true});}
});
test('LLM structured task is accepted only after bounded validation',async()=>{
 const d=await mkdtemp(join(tmpdir(),'ahmet-'));
 try {const store=new MemoryStore(join(d,'memory.json'));await store.load();
  const result=await chat({actorUuid:'2',actorName:'Ece',text:'Ahmet, bulduğun herhangi bir ağaçtan 20 odun topla',context:{chestBound:true,busy:false}},{store,
   models:{interpretTurn:async()=>({reply:'Tamam, bulduğum odunları getireceğim.',task:{type:'collect_wood',material:'any',amount:20}})}});
  assert.deepEqual(result.task,{type:'collect_wood',material:'any',amount:20});
  assert.equal(result.taskSource,'llm');
 }finally{await rm(d,{recursive:true,force:true});}
});

test('invalid LLM task fields cannot escape the wood type and quantity allowlists',async()=>{
 const d=await mkdtemp(join(tmpdir(),'ahmet-'));
 try {const store=new MemoryStore(join(d,'memory.json'));await store.load();
  const result=await chat({actorUuid:'3',actorName:'Ece',text:'bana odun getir',context:{chestBound:true,busy:false}},{store,
   models:{interpretTurn:async()=>({reply:'Tamam.',task:{type:'run_command',material:'diamond',amount:999}})}});
  assert.equal(result.task,null);
 }finally{await rm(d,{recursive:true,force:true});}
});
test('decision mocks safely and validates offered actions',async()=>{
 const a=await decide({observation:{task:'wood'},actions:[{id:'harvest',description:'Harvest wood'},{id:'wait',description:'Wait'}]},{decisionMode:'mock'});
 assert.equal(a.id,'harvest');
 const b=await decide({actions:[{id:'deposit',description:'Deposit cargo'}]},{decisionMode:'mock'});
 assert.equal(b.id,'deposit');
 await assert.rejects(()=>decide({actions:[{id:'delete_server',description:'No'} ,{id:'teleport(1)',description:'No'}]},{decisionMode:'mock'}),/Invalid action id/);
});
test('decision timeout is labelled and falls back to an offered action',async()=>{
 const previous=globalThis.fetch;let signal;
 try {
  globalThis.fetch=async(_url,init)=>{signal=init.signal;throw new DOMException('request timed out','TimeoutError');};
  const result=await decide({observation:{},actions:[{id:'harvest',description:'Harvest'},{id:'wait',description:'Wait'}]},{key:'test-only',decisionMode:'jev'});
  assert.equal(result.id,'harvest');assert.equal(result.source,'fallback');
  assert.ok(signal instanceof AbortSignal);
 }finally{globalThis.fetch=previous;}
});
