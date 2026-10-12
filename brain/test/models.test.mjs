import test from 'node:test';import assert from 'node:assert/strict';
import {chooseAction,conversationalReply} from '../src/models.mjs';
test('JEV uses TypeSafe System One and only accepts offered actions',async()=>{
 const previous=globalThis.fetch;let seen={};
 try {
  globalThis.fetch=async(url,init)=>{seen={url,body:JSON.parse(init.body),headers:init.headers};return {ok:true,json:async()=>({model:'jev-latest',answers:{action:{choice:'a1',confidence:0.93}}})};};
  const r=await chooseAction({requested:20},[{id:'find_tree',description:'Find a tree'},{id:'harvest',description:'Harvest'}],{key:'test-only',mode:'jev'});
  assert.equal(r.id,'harvest');assert.equal(r.source,'jev');assert.equal(seen.url,'https://api.typesafe.ai/v1/systemone');
  assert.equal(seen.body.model,'jev-latest');assert.equal(seen.headers.Authorization,'Bearer test-only');
  assert.deepEqual(Object.keys(seen.body.questions.action.criteria),['a0','a1']);
  globalThis.fetch=async()=>({ok:true,json:async()=>({answers:{action:{choice:'a99'}}})});
  await assert.rejects(()=>chooseAction({},[{id:'harvest',description:'Harvest'},{id:'wait',description:'Wait'}],{key:'test-only',mode:'jev'}),/invalid candidate/);
 }finally{globalThis.fetch=previous;}
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
