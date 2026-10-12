import {readFileSync} from 'node:fs';
export function loadEnv(path='.env') {try {for(const line of readFileSync(path,'utf8').split(/\r?\n/)){const match=line.match(/^([A-Z][A-Z0-9_]*)=(.*)$/);if(match && process.env[match[1]]===undefined)process.env[match[1]]=match[2];}}catch(e){if(e.code!=='ENOENT')throw e;}}
