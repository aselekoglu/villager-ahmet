import test from 'node:test';import assert from 'node:assert/strict';
import {chooseAction,conversationalReply,interpretTurn,configuredJevModel,isResolvedJevModel} from '../src/models.mjs';
test('JEV uses TypeSafe System One and only accepts offered actions',async()=>{
 const previous=globalThis.fetch;const previousModel=process.env.JEV_MODEL;delete process.env.JEV_MODEL;let seen={};
 try {
  globalThis.fetch=async(url,init)=>{seen={url,body:JSON.parse(init.body),headers:init.headers};return {ok:true,json:async()=>({model:'jev-1.14.0',answers:{action:{choice:'a1',confidence:0.93}}})};};
  const r=await chooseAction({requested:20},[{id:'find_tree',description:'Find a tree'},{id:'harvest',description:'Harvest'}],{key:'test-only',mode:'jev'});
  assert.equal(r.id,'harvest');assert.equal(r.source,'jev');assert.equal(seen.url,'https://api.typesafe.ai/v1/systemone');
  assert.equal(seen.body.model,'jev-preview');assert.equal(r.model,'jev-1.14.0');assert.equal(seen.headers.Authorization,'Bearer test-only');
  assert.deepEqual(Object.keys(seen.body.questions.action.criteria),['a0','a1']);
  globalThis.fetch=async()=>({ok:true,json:async()=>({model:'jev-1.14.0',answers:{action:{choice:'a99'}}})});
  await assert.rejects(()=>chooseAction({},[{id:'harvest',description:'Harvest'},{id:'wait',description:'Wait'}],{key:'test-only',mode:'jev'}),/invalid candidate/);
 }finally{globalThis.fetch=previous;if(previousModel===undefined)delete process.env.JEV_MODEL;else process.env.JEV_MODEL=previousModel;}
});
test('chat uses the direct OpenAI completion endpoint, separate from TypeSafe Jev',async()=>{
 const previous=globalThis.fetch;let seen;
 try {globalThis.fetch=async(url,init)=>{seen={url,headers:init.headers,body:JSON.parse(init.body)};return {ok:true,json:async()=>({choices:[{message:{content:'Merhaba arkadaşım!'}}]})};};
  const reply=await conversationalReply('Selam','Ata',[],{key:'test-only',mode:'llm'});
  assert.equal(reply,'Merhaba arkadaşım!');assert.equal(seen.url,'https://api.openai.com/v1/chat/completions');
  assert.equal(seen.headers.Authorization,'Bearer test-only');assert.equal(seen.body.max_completion_tokens,170);
 }finally{globalThis.fetch=previous;}
});
test('conversation defaults to GPT-6 Luna without changing the JEV model',async()=>{
 const previous=globalThis.fetch;const previousModel=process.env.CHAT_MODEL;delete process.env.CHAT_MODEL;let body;
 try {globalThis.fetch=async(_url,init)=>{body=JSON.parse(init.body);return {ok:true,json:async()=>({choices:[{message:{content:'Selam!'}}]})};};
  await conversationalReply('Selam','Ata',[],{key:'test-only',mode:'llm'});
  assert.equal(body.model,'gpt-6-luna');assert.equal(body.max_completion_tokens,170);assert.equal(body.max_tokens,undefined);
 }finally{globalThis.fetch=previous;if(previousModel===undefined)delete process.env.CHAT_MODEL;else process.env.CHAT_MODEL=previousModel;}
});
test('GPT-6 Luna returns a JSON task intent separately from JEV',async()=>{
 const previous=globalThis.fetch;let seen;
 try {globalThis.fetch=async(url,init)=>{seen={url,body:JSON.parse(init.body)};return {ok:true,json:async()=>({choices:[{message:{content:JSON.stringify({reply:'Odunları topluyorum.',task:{type:'collect_wood',material:'any',amount:8}})}}]})};};
  const result=await interpretTurn('Ahmet bulduğun odunları getir','Ata',[],{key:'test-only',mode:'llm'});
  assert.equal(result.task.material,'any');assert.equal(result.task.amount,8);
  assert.equal(seen.url,'https://api.openai.com/v1/chat/completions');
  assert.equal(seen.body.model,'gpt-6-luna');assert.deepEqual(seen.body.response_format,{type:'json_object'});
 }finally{globalThis.fetch=previous;}
});

test('provider serialization re-sanitizes perception and keeps it advisory',async()=>{
 const previous=globalThis.fetch;let seen=[];
 try {
  globalThis.fetch=async(url,init)=>{seen.push({url,body:JSON.parse(init.body)});return url.includes('typesafe')
    ?{ok:true,json:async()=>({model:'jev-1.14.0',answers:{action:{choice:'a0'}}})}
    :{ok:true,json:async()=>({choices:[{message:{content:'Tamam.'}}]})};};
  const snapshot={version:1,available:true,capturedAt:Date.now(),generation:2,phase:'WORK',blocks:{sampledCells:1,types:[{material:'STONE',count:1,secret:'drop'}],secret:'drop'},inventory:{enabled:false,available:false,items:[]},players:{enabled:false,available:false,bands:[]},secret:'drop'};
  await chooseAction({generation:2,perception:snapshot,secret:'other'},[{id:'wait',description:'Wait'}],{key:'test-only',mode:'jev'});
  await conversationalReply('Hi','Ata',[],{key:'test-only',mode:'llm',generation:2,perception:snapshot});
  assert.equal(seen.length,2);assert.equal(seen[0].body.state.secret,'other');
  assert.equal(seen[0].body.state.perception.secret,undefined);
  assert.equal(seen[0].body.state.perception.blocks.secret,undefined);
  assert.equal(seen[0].body.state.perception.blocks.types[0].secret,undefined);
  const prompt=seen[1].body.messages.map(m=>m.content??'').join('\n');
  assert.match(prompt,/bounded, partial, read-only observation/);assert.doesNotMatch(prompt,/"secret"/);
 }finally{globalThis.fetch=previous;}
});

test('JEV response must include a plausible resolved version and offered choice',async()=>{
 const previous=globalThis.fetch;const previousModel=process.env.JEV_MODEL;delete process.env.JEV_MODEL;
 try {
  globalThis.fetch=async()=>({ok:true,json:async()=>({answers:{action:{choice:'a0'}}})});
  await assert.rejects(()=>chooseAction({},[{id:'harvest',description:'Harvest'}],{key:'test-only',mode:'jev'}),/resolved model/);
  globalThis.fetch=async()=>({ok:true,json:async()=>({model:'secret-injected-value',answers:{action:{choice:'a0'}}})});
  await assert.rejects(()=>chooseAction({},[{id:'harvest',description:'Harvest'}],{key:'test-only',mode:'jev'}),/resolved model/);
  globalThis.fetch=async()=>({ok:true,json:async()=>({model:'jev-1.14.0',answers:{action:{choice:'a9'}}})});
  await assert.rejects(()=>chooseAction({},[{id:'harvest',description:'Harvest'}],{key:'test-only',mode:'jev'}),/invalid candidate/);
 }finally{globalThis.fetch=previous;if(previousModel===undefined)delete process.env.JEV_MODEL;else process.env.JEV_MODEL=previousModel;}
});

test('JEV model selection defaults to preview and accepts only known aliases or version IDs',()=>{
 const previous=process.env.JEV_MODEL;
 try {
  delete process.env.JEV_MODEL;assert.equal(configuredJevModel(),'jev-preview');
  process.env.JEV_MODEL='jev-1.13.0';assert.equal(configuredJevModel(),'jev-1.13.0');
  process.env.JEV_MODEL='jev-secret-value';assert.equal(configuredJevModel(),null);
  const oversized='jev-1.14.0-'+('a'.repeat(64));
  process.env.JEV_MODEL=oversized;assert.equal(configuredJevModel(),null);
  assert.equal(isResolvedJevModel(oversized),false);
  assert.equal(isResolvedJevModel('jev-1.14.0'),true);
 }finally{if(previous===undefined)delete process.env.JEV_MODEL;else process.env.JEV_MODEL=previous;}
});

test('provider errors contain no raw response body, key, or private state',async()=>{
 const previous=globalThis.fetch;
 try {
  globalThis.fetch=async()=>({ok:false,status:401,json:async()=>({error:{message:'sk-fake-secret private-state-123'}})});
  await assert.rejects(()=>chooseAction({private:'private-state-123'},[{id:'harvest',description:'Harvest'}],{key:'sk-fake-secret',mode:'jev'}),error=>{
   assert.match(error.message,/TypeSafe.*http_4xx/);assert.equal(error.httpStatus,401);assert.doesNotMatch(error.message,/sk-fake-secret|private-state-123/);return true;
  });
  globalThis.fetch=async()=>({ok:false,status:429,json:async()=>({error:{message:'rate limited'}})});
  await assert.rejects(()=>chooseAction({},[{id:'harvest',description:'Harvest'}],{key:'test-only',mode:'jev'}),error=>error.category==='http_4xx'&&error.httpStatus===429);
  globalThis.fetch=async()=>({ok:false,status:503,json:async()=>({error:{message:'internal details'}})});
  await assert.rejects(()=>chooseAction({},[{id:'harvest',description:'Harvest'}],{key:'test-only',mode:'jev'}),error=>error.category==='http_5xx'&&error.httpStatus===503);
  globalThis.fetch=async()=>{throw new Error('sk-fake-secret private-state-123 network details');};
  await assert.rejects(()=>chooseAction({private:'private-state-123'},[{id:'harvest',description:'Harvest'}],{key:'sk-fake-secret',mode:'jev'}),error=>{
   assert.match(error.message,/TypeSafe.*network/);assert.doesNotMatch(error.message,/sk-fake-secret|private-state-123/);return true;
  });
 }finally{globalThis.fetch=previous;}
});
