import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,writeFile,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {loadEnv} from '../src/load-env.mjs';

test('launch preview selection wins over an old dotenv default while explicit launch overrides remain intact',async()=>{
 const dir=await mkdtemp(join(tmpdir(),'ahmet-fake-env-'));
 const previous=process.env.JEV_MODEL;
 const fakeEnv=join(dir,'.env');
 try {
  await writeFile(fakeEnv,'JEV_MODEL=jev-latest\n');
  process.env.JEV_MODEL='jev-preview';
  loadEnv(fakeEnv);
  assert.equal(process.env.JEV_MODEL,'jev-preview');
  process.env.JEV_MODEL='jev-1.13.0';
  loadEnv(fakeEnv);
  assert.equal(process.env.JEV_MODEL,'jev-1.13.0');
 } finally {
  if(previous===undefined)delete process.env.JEV_MODEL;else process.env.JEV_MODEL=previous;
  await rm(dir,{recursive:true,force:true});
 }
});
