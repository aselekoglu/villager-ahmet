import test from 'node:test';import assert from 'node:assert/strict';
import {parseTask,validateTask} from '../src/intent.mjs';
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
test('common resource requests map language to canonical vanilla item ids',()=>{
 assert.deepEqual(parseTask('Ahmet 12 toprak topla'),{type:'collect_item',itemId:'minecraft:dirt',amount:12});
 assert.deepEqual(parseTask('8 kum getir'),{type:'collect_item',itemId:'minecraft:sand',amount:8});
 assert.deepEqual(parseTask('10 taş topla'),{type:'collect_item',itemId:'minecraft:cobblestone',amount:10});
 assert.deepEqual(parseTask('4 kömür topla'),{type:'collect_item',itemId:'minecraft:coal',amount:4});
 assert.deepEqual(parseTask('5 ham demir getir'),{type:'collect_item',itemId:'minecraft:raw_iron',amount:5});
 assert.deepEqual(parseTask('5 demir cevheri getir'),{type:'collect_item',itemId:'minecraft:raw_iron',amount:5});
 assert.deepEqual(parseTask('3 ham altın getir'),{type:'collect_item',itemId:'minecraft:raw_gold',amount:3});
 assert.deepEqual(validateTask({type:'collect_item',itemId:'minecraft:raw_iron',amount:5}),{type:'collect_item',itemId:'minecraft:raw_iron',amount:5});
 assert.equal(validateTask({type:'collect_item',itemId:'other:raw_iron',amount:5}),null);
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
test('carried cargo without an active task asks the player to deliver before starting another job',async()=>{
 const d=await mkdtemp(join(tmpdir(),'ahmet-'));
 try {const store=new MemoryStore(join(d,'memory.json'));await store.load();
  const result=await chat({actorUuid:'cargo-player',actorName:'Ata',text:'Ahmet, 5 ham demir getir',context:{chestBound:true,busy:true,taskActive:false,cargo:2}},{store,
   models:{interpretTurn:async()=>({reply:'Ham demiri getiriyorum.',task:{type:'collect_item',itemId:'minecraft:raw_iron',amount:5}})}});
  assert.equal(result.task,null);
  assert.match(result.reply,/2 kaynak/);
  assert.match(result.reply,/\/ahmet deliver/);
 }finally{await rm(d,{recursive:true,force:true});}
});
test('common-resource request becomes a bounded item intent through the chat fallback',async()=>{
 const d=await mkdtemp(join(tmpdir(),'ahmet-'));
 try {const store=new MemoryStore(join(d,'memory.json'));await store.load();
  const result=await chat({actorUuid:'resource-player',actorName:'Ata',text:'Ahmet, 7 ham demir getir',context:{chestBound:true,busy:false}},{store});
  assert.deepEqual(result.task,{type:'collect_item',itemId:'minecraft:raw_iron',amount:7});
  assert.equal(result.taskSource,'heuristic');
 }finally{await rm(d,{recursive:true,force:true});}
});
test('LLM common-resource intents accept only bounded Minecraft item IDs',async()=>{
 const d=await mkdtemp(join(tmpdir(),'ahmet-'));
 try {const store=new MemoryStore(join(d,'memory.json'));await store.load();
  const result=await chat({actorUuid:'resource-llm',actorName:'Ece',text:'7 ham demir getir',context:{chestBound:true,busy:false}},{store,
   models:{interpretTurn:async()=>({reply:'Demiri getireceğim.',task:{type:'collect_item',itemId:'minecraft:raw_iron',amount:7}})}});
  assert.deepEqual(result.task,{type:'collect_item',itemId:'minecraft:raw_iron',amount:7});
  assert.equal(result.taskSource,'llm');
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

test('decision fallback and mock modes never select a clear mutation action',async()=>{
 const previous=globalThis.fetch;
 try {
  globalThis.fetch=async()=>{throw new DOMException('request timed out','TimeoutError');};
  const result=await decide({observation:{requiredAction:'clear_obstruction'},actions:[
    {id:'clear_a',description:'Clear oak leaves'},
    {id:'retry_route',description:'Retry route'},
    {id:'wait',description:'Wait'}
  ]},{key:'test-only',decisionMode:'jev'});
  assert.equal(result.id,'retry_route');
  assert.equal(result.source,'fallback');

  const mockChoice=await decide({observation:{requiredAction:'clear_obstruction'},actions:[
    {id:'clear_a',description:'Clear oak leaves'},
    {id:'retry_route',description:'Retry route'},
    {id:'wait',description:'Wait'}
  ]},{decisionMode:'mock'});
  assert.equal(mockChoice.id,'retry_route');
 }finally{globalThis.fetch=previous;}
});


test('return home intent cancels and flags goHome',async()=>{
 const d=await mkdtemp(join(tmpdir(),'ahmet-'));
 try {
  const store=new MemoryStore(join(d,'memory.json'));await store.load();
  const result=await chat({actorUuid:'4',actorName:'Ali',text:'Ahmet, eve dön',context:{chestBound:true,busy:false}},{store});
  assert.equal(result.cancel,true);
  assert.equal(result.goHome,true);
  assert.match(result.reply,/eve dönüyorum/);
 }finally{await rm(d,{recursive:true,force:true});}
});
