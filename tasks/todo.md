# Remaining-roadmap execution tracker

- [x] M1 Player/session memory: implement, test, independent review, mock HTTP, document.
- [x] M1 rebuild and verify Paper/Citizens package (ZIP SHA-256 `cb339161ed171957ad7f208895adade6c193ae8a2ffb24aedc1856dea4ab55d8`; Node brain remains separate).
- [x] Publish M1 source (`74f61d580e74d1c942b3f2badd7f498324212339`) and verify the public tree.
- [x] Restart the Node brain from published source and verify startup, health/configuration, idle status, and world save read-back.
- [x] M2 bounded grounded perception: Java adapter, Node sanitization/forwarding, independent source review, disposable Paper runtime, and actual request-body sanitizer compatibility.
- [x] Rebuild and verify the four-entry M2 Paper/Citizens package (ZIP SHA-256 `67f6a1e2a0cc60f6a6bb23d99ec3bc5eabece9119a3e6ec46deb19214f256b64`; embedded core JAR SHA-256 `48669fd65dbdffb7be39d861906a61a30173aeab5f0b88ee85dff8c4f44f195b`; no config secrets or test ZIP).
- [x] Publish M2 source (`ba29c68916562f922b3bae085c72b51f345030c0`, tree `30d87eaf4c82778cfac5d4b9b87112129b600b76`) and deploy/read back the reviewed core JAR from the verified four-entry package; Paper/Node restart, health, idle status, and saved dimensions were confirmed. No provider was invoked; human acceptance remains open.
- [x] M3 production goal selection source, review and disposable runtime verification (operator opt-in, default off).
- [x] Rebuild and verify the final four-entry M3 ZIP locally (SHA-256 `5781ea3489fef9f5828f0c21cc980a414ed9f8c8602a1057b9ce3adb0788c207`; `dist/` is intentionally gitignored, no GitHub asset is claimed).
- [x] M3 release milestone: publish reviewed source (`6b015f1714be8a0a986516132436a28e1341408f`, remote SHA matched), deploy the exact verified package/core, and read back Node/Paper health, idle status and save completion. Prior shutdown cause/time is unknown; no graceful stop is claimed.
- [x] M4 presentation source, focused Java suite, disposable runtime acceptance, and corrected four-entry local package verification; evidence in `docs/VALIDATION_RESULTS.md` (ZIP SHA-256 `6846225df2d99b40142f1ca377a014a964d40cf5fe4b3093745f21c5871691a0`).
- [x] Publish M4 source/docs commit `620b97efa4448aee5e6682fc3d2adb9f99b7b9e4` and verify the exact remote SHA.
- [x] Deploy the exact M4 package and read back installed core, Paper/Node health, idle status and save completion; prior server stop cause/time remains unknown, so no graceful prior stop is claimed.
- [x] M5 Allowlisted transactional crafting: allowlisted recipes (`oak_planks`, `stick`), exact multiples of 4 (4..64, no rounding), 54-slot double-chest planner, custom item preservation, Citizens walking without teleports, arrival re-planning, and journal v4 write-ahead crash recovery. Java 317/317, Node 46/46, disposable Paper/Citizens 1.21.6 6-stage runtime pass (`r13`, `normalTeleports=0`), four-entry package ZIP SHA-256 `fa656459ec6bb47305dbeecd885141d97a17b998007bb8151b3cade978d25a79` (core `68cf01cb17f7f843c4c10b2b980af85c471d0fca6246e1dbb18eb9844a9a1c0f`).
- [ ] M6 Approved-plot mature-crop farming.
- [ ] M7 Stable multiple villagers, roles, delegation and local resource economy.
- [ ] M8 Fine-grained claims, permissions, protection compatibility and bounded tickets.
- [ ] M9 Private observability/admin dashboard and honest usage/cost audit.
- [ ] M10 Disposable failure acceptance, isolated installation, final review/release/deploy.
- [ ] M1 human memory acceptance, OpenAI live proof, and production-memory-store migration inspection remain open; JEV live provider proof is recorded below.
- [x] JEV preview provider acceptance: source/helper review passed; one synthetic TypeSafe call returned alias `jev-preview`, resolved model `jev-1.14.0`, source `jev`, and offered action `wait` (558 ms observed; not a benchmark).
- [x] JEV preview launcher precedence: copied `scripts/run.sh` without a process-level override used a synthetic dotenv value `jev-latest`; health showed `jev-preview`, mock chat and mock decision.
- [x] Fresh isolated release checks: Node **43/43** and `scripts/smoke.sh` passed in mock mode.
- [x] JEV preview source published and live startup verified: published source commit `c8f2777a934cd2355844a0685aacadb1fa715f85`; deployed Node source/launcher matched it byte for byte; Paper startup, configured preview health, idle status, and all-dimension save read-back passed. Installed M2 JAR and distribution were unchanged. No provider call or human interaction occurred during deployment; NPC remained unspawned.
- [ ] Human gameplay, live provider behavior through the current path, production-store inspection, and forced-crash acceptance remain open; M5–M10 remain open.
- [ ] Human/player acceptance for M2 visible-player behavior (runtime used a no-human server and permission proxies only).
- [ ] Human player/two-client acceptance (requires actual user; never mark from proxy).


Implementation and acceptance tracking continues in `tasks/plan.md` and this checklist.
