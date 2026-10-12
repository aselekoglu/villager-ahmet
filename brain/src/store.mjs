import {mkdir, readFile, rename, writeFile} from 'node:fs/promises';
import {dirname} from 'node:path';
export class MemoryStore {
  constructor(path) {this.path=path; this.data={version:1,conversations:{},events:[]}; this.queue=Promise.resolve();}
  async load() {
    try {const value=JSON.parse(await readFile(this.path,'utf8'));
      if (value?.version===1 && typeof value.conversations==='object') this.data=value;
    } catch(e) {if(e.code!=='ENOENT') throw e;}
  }
  async record(player,text,reply,task) {
    const key=String(player).slice(0,80),row={at:new Date().toISOString(),text:String(text).slice(0,500),reply:String(reply).slice(0,500),task:task??null};
    (this.data.conversations[key]??=[]).push(row);
    this.data.conversations[key]=this.data.conversations[key].slice(-20);
    this.data.events.push({at:row.at,player:key,event:'chat',task:task??null});
    this.data.events=this.data.events.slice(-500);
    this.queue=this.queue.catch(()=>{}).then(()=>this.persist());
    await this.queue;
  }
  async recordEvent(event) {
    this.data.events.push({at:new Date().toISOString(),...event});
    this.data.events=this.data.events.slice(-500);
    this.queue=this.queue.catch(()=>{}).then(()=>this.persist());
    await this.queue;
  }
  async persist() {
    await mkdir(dirname(this.path),{recursive:true});
    const tmp=`${this.path}.${process.pid}.tmp`;
    await writeFile(tmp,JSON.stringify(this.data,null,2),{mode:0o600});await rename(tmp,this.path);
  }
  history(player) {return this.data.conversations[String(player)]?.slice(-6)??[];}
}
