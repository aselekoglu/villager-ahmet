# Validation results — 2026-10-12

## M3 opt-in production goal policy — final source/runtime/package, 2026-10-20

- Final Java core SHA-256 `5e8395902b24f374b1176e98cc14957bccde63d07ba9795da940d9ef1500f8a3` passed **259/259 Java tests**. Node passed **43/43** and `scripts/smoke.sh` passed in mock mode. The independent source review marked all M3 source gates addressed. An earlier `c582eaa` / 242-test source checkpoint is superseded; current M3 status uses the final source evidence recorded here.
- `options.production-goals-enabled` defaults to `false`. When enabled, the production controller selects typed goals only inside an explicitly authorized job; it does not invent village needs or admit new work. Task ID, owner, generation, extended global server-step identity and owned route sequence guard selection and application. Task/cargo/recovery/journal and schedule priorities remain in force. Productive transitions cap at 32, then require safe return and delivery. Obstruction clearing is limited to **3 per route / 12 per job**, separate from the **64 output-item** request limit.
- Final normal M3 runtime probe passed in a disposable mock environment and exited 0; the server saved its worlds with no errors: **96.028 blocks** walked, **507 moving ticks**, maximum sampled step **0.198 blocks/tick**, **0 normal teleports**, **6 mock decisions**, **1 mock chat**, and **0 provider calls**. Foreign-route handling preserved the exact foreign strategy, target, task, budget, source block, chest and model state. At 100 ticks the first help attempt was correctly rejected by the shared 30-second cooldown; the waiter retried without fake acknowledgements or timestamp resets, one real acknowledgement arrived at 267 ticks, and explicit fixture release then allowed same-job harvest and delivery in 65 ticks. This validates the controlled fixture path, not human acceptance.
- Final default-off common-resource runtime probe passed **7 groups / 11 variants / 11 deposited items**, with empty cargo and **0 teleports**. Fresh-world and same-world second-process schedule probes passed; saved NPC/home/bed/task identities were restored after restart. Both had **0 normal teleports**; the fresh fixture used four explicit setup teleports, and the restart used none and did not rebuild its arena. All three dimensions were saved.
- Pending JOURNAL and recovery-only holds in the goal probe were injected states and then released; they are not a forced-crash test. An earlier 120-tick fixture attempt occurred while the shared help cooldown was already active and timed out; the final probe retained genuine cooldown/ack state and passed. No human client, current-path live JEV/OpenAI call, production-store inspection, or forced crash was involved. The separate JEV provider proof below is not evidence of a provider call through M3.
- The final four-entry ZIP was rebuilt locally with the corrected Turkish README and verified: ZIP SHA-256 `5781ea3489fef9f5828f0c21cc980a414ed9f8c8602a1057b9ce3adb0788c207`, README SHA-256 `87d01fdacdb51f5326b7adc43d91c262f9348e9506020cac204a790bebe3ce50`, embedded core SHA-256 `5e8395902b24f374b1176e98cc14957bccde63d07ba9795da940d9ef1500f8a3`, and pinned Citizens SHA-256 `dd1bff69eb2339a459e56b21aed0810919b40a14a9561577264e3ff94cef9158`; archive has four entries and passed CRC verification. The local `dist/` package is intentionally gitignored; no GitHub release asset is published.

## M3 source publication and live deployment read-back — 2026-10-20

- Source and documentation were published by normal non-force push as commit `6b015f1714be8a0a986516132436a28e1341408f`; remote SHA read-back matched. It includes source fix commit `f1fb6af34a63f38052a448f1615983c8258f2445`.
- The exact core from the verified four-entry ZIP was installed after a secure verified backup. The backup was unchanged at the pre-install check. The server was already stopped; prior shutdown time and cause are unknown, so this does not claim an observed graceful stop.
- Node started through its normal app launcher. Paper started in **9.621 seconds** with no error lines. Health returned `ok`, configured JEV alias `jev-preview`, decision mode `jev`, and chat mode `llm`. These are configuration/readiness values; no provider request was made.
- Installed core SHA-256 read-back matched `5e8395902b24f374b1176e98cc14957bccde63d07ba9795da940d9ef1500f8a3`. `/ahmet status` returned IDLE, cargo 0, chest bound, bed unbound, and NPC unspawned. The production-goals option was absent and therefore used its default `false`. `save-all flush` returned `Saved the game`.
- No collect, summon, home/bed change, model call, or world experiment was issued during read-back. M3 source/runtime/package/publication/deployment is complete. Human gameplay, live provider behavior through this path, production-store inspection, and forced-crash acceptance remain open; M4–M10 remain open.

## JEV preview source and provider acceptance — 2026-10-19

- Node defaults to the TypeSafe `jev-preview` alias at launch; an explicitly supplied valid process-level `JEV_MODEL` override remains supported and takes precedence over dotenv defaults. Health may expose only the validated configured alias (version IDs are capped at 64 characters). A real choice requires a plausible bounded versioned response `model` plus a currently offered action.
- Provider HTTP, network and timeout errors expose a bounded category and validated numeric HTTP status where available. Synthetic tests confirm fake credentials and private input fragments do not enter provider errors or decision fallback reasons. OpenAI GPT-6 Luna's endpoint, configured model and successful response paths remain covered by the Node suite.
- `brain` Node suite passed **43/43**. This includes mock TypeSafe metadata/choice tests, configuration precedence using a synthetic temporary dotenv file, and provider-error redaction. A separate private guard test passed without adding any dependency on ignored helper files to the public suite. `bash -n scripts/run.sh` and `git diff --check` passed.
- Source/helper review passed. One explicit synthetic TypeSafe choice call through `jev-preview` returned `source=jev`, resolved model `jev-1.14.0`, and selected the offered `wait` action. Exactly one provider request was made; observed elapsed time was **558 ms**. This is one call's timing, not a benchmark. Sanitized evidence is stored privately.
- The provider proof covers JEV selection only. It did not access or mutate a Minecraft world, invoke OpenAI, or involve a human player. A separate copied-source `scripts/run.sh` check, without a process-level override, used a synthetic dotenv value of `jev-latest`; health reported `jev-preview` with mock chat and decision modes. This establishes launcher precedence only. Fresh isolated Node **43/43** and exact-release `scripts/smoke.sh` checks passed in mock mode; neither is live-provider or Minecraft evidence.
- Publication and live startup are verified below. The installed M2 core JAR (`48669fd65dbdffb7be39d861906a61a30173aeab5f0b88ee85dff8c4f44f195b`) and four-entry distribution (`67f6a1e2a0cc60f6a6bb23d99ec3bc5eabece9119a3e6ec46deb19214f256b64`) remain unchanged. Runtime JEV invocation, Minecraft task/movement acceptance, human acceptance, OpenAI proof, and production-memory-store inspection remain open; M3–M10 remain open.

## JEV preview publication and live startup — 2026-10-19

- Published source commit `c8f2777a934cd2355844a0685aacadb1fa715f85` includes reviewed Node JEV commit `a3e82ffbf0372fd2f979ca1fc7530fa737b76962`. Deployed Node source and launcher files matched the published source byte for byte. Paper plugin artifacts were unchanged: core JAR SHA-256 `48669fd65dbdffb7be39d861906a61a30173aeab5f0b88ee85dff8c4f44f195b` and four-entry distribution SHA-256 `67f6a1e2a0cc60f6a6bb23d99ec3bc5eabece9119a3e6ec46deb19214f256b64`.
- Paper startup completed in **7.834 seconds** with 0 error lines. Health reported `ok`, configured `jev-preview`, decision mode `jev`, and chat mode `llm`. Status read-back showed IDLE, cargo 0, chest bound, bed unbound, recovery unblocked, NPC unspawned, and 0 connected players. All three dimensions flush-saved.
- No provider calls or human-player interactions occurred during deployment. This verifies published-source read-back, startup, configuration, idle state, and saves; it does not verify JEV calls during runtime or Minecraft behavior. The NPC was unspawned, so no movement is claimed. The separate provider proof above remains distinct. Human, OpenAI, and production-memory-store acceptance remain open; M3–M10 remain open.

## M2 published source and live deployment read-back — 2026-10-18

- Published source commit: `ba29c68916562f922b3bae085c72b51f345030c0`; reviewed tree: `30d87eaf4c82778cfac5d4b9b87112129b600b76`. Deployed the reviewed core JAR (SHA-256 `48669fd65dbdffb7be39d861906a61a30173aeab5f0b88ee85dff8c4f44f195b`) from the verified four-entry package (ZIP SHA-256 `67f6a1e2a0cc60f6a6bb23d99ec3bc5eabece9119a3e6ec46deb19214f256b64`).
- Deployment followed a graceful save and stop, then private backup, then install and restart. Paper and Node restarted successfully; Paper startup completed in **8.207 seconds**. Health reported configured JEV/LLM modes; **no provider was invoked**.
- Read-back status was IDLE, cargo 0, chest bound, bed unbound, NPC unspawned, and 0 connected players. All three dimensions flush-saved; startup reported 0 errors. The private LAN settings remained `online-mode=false` with whitelist enabled. No summon, bed change, or manual world edit was performed.
- This proves deployment/startup and read-back only. The NPC was unspawned, so program status text about walking does not demonstrate actual movement. M1 human/provider/store inspection and M2 human/provider acceptance remain open; M3–M10 remain open.

## Bounded grounded perception (M2) — 2026-10-18

- Java 21 offline Maven package passed **175/175** tests; Node passed **38/38**; `scripts/smoke.sh` passed in mock mode. Reviewed core Paper JAR SHA-256: `48669fd65dbdffb7be39d861906a61a30173aeab5f0b88ee85dff8c4f44f195b`. M2 source is published as `ba29c68916562f922b3bae085c72b51f345030c0`; the reviewed source tree is `30d87eaf4c82778cfac5d4b9b87112129b600b76`.
- Disposable Paper 1.21.6 build 48 + Citizens 2.0.41 build 4138 + Java 21 perception probe passed all four markers: `AHMET_PERCEPTION_SNAPSHOT_PASS`, `AHMET_PERCEPTION_SETUP_SETTLE_PASS`, `AHMET_PERCEPTION_STALE_CHAT_PASS`, and `AHMET_PERCEPTION_PROBE_PASS`; there was no `AHMET_PERCEPTION_PROBE_ERROR`. Probe JAR SHA-256: `86e9abd0f76e6dfcc6212a2b596b132f07d9d9743b66559c3b7859c9f7e1f24f`. Paper stopped gracefully and saved all three dimensions.
- A real Citizens VILLAGER produced a snapshot of **37 cells / 3 material types**, with one visible gold marker and no diamond marker behind the opaque wall. Snapshot capture emitted **0 chunk-load events**, passed the unloaded-boundary check, and left seeded blocks and chest contents unchanged. An actual double chest exposed **54 slots**; second-half entries aggregated as `DIRT ×7` and `STONE ×3`, while the synthetic display name/lore did not enter the DTO. Default-off inventory/player sections, non-admin/offline/wrong-world/far/unloaded-partner inventory denial, and unspawned/off-thread guards passed. The server had **0 connected human players**; opt-in produced an available empty player-band list. Player visibility filtering remains unit/proxy evidence, not human-player evidence.
- In the normal R8 run the first production snapshot was captured immediately after setup with **0 capture-time chunk loads**. A following 10-tick settle interval recorded **8 deferred neighboring-chunk loads before any chat/task activity**; after that, the counter was reset and the observation/HTTP/task window had **0 new loads**. Separate R6 setup-only control recorded eight loads with zero perception calls. The initiator of the deferred events is unknown; they are not attributed to perception.
- A synthetic same-generation chat reply started one bounded dirt task. The production JEV path forwarded its observation to a loopback mock; Java processed the offered `wait` response and the task was cancelled before harvest. A second delayed chat response carried task/cancel/home fields but arrived after a newer bounded task was admitted. After 20 main-thread ticks, the newer task ID/generation, zero cargo/collection, source block and chest remained unchanged; `returningHome` was **false before and after** the stale-response window. Runtime totals were **2 chat, 1 decision, and 5 event requests**, **0 NPC teleports**, **0 human players**, and **no live providers**.
- Captured synthetic `/api/chat` and `/api/decide` bodies passed the production Node sanitizer with a controlled `capturedAt + 1 second`; stale `20.001`-second, missing-generation, and mismatched-generation snapshots were rejected, unknown fields were omitted, and raw input remained unchanged. Bodies contain only synthetic fixture data and omit authorization headers.
- Common-resource regression against the same core JAR passed all **7 groups / 11 source variants / 11 delivered items**, with final cargo 0 and no normal Citizens teleports. Raw gold completed in **641 ticks**. The scanner measured 74 ticks, **2,081 µs average**, **16,116 µs maximum**, and one-tick invalidation. These results are disposable/mock evidence, not live provider or human gameplay evidence.
- M2 implementation, disposable acceptance, and final Paper/Citizens package verification are complete. The four-entry ZIP SHA-256 is `67f6a1e2a0cc60f6a6bb23d99ec3bc5eabece9119a3e6ec46deb19214f256b64`; its embedded core JAR matches the hash above, and the package contains no config secrets or test ZIP. `dist/SHA256SUMS.txt` is updated locally. Source publication and live deployment/read-back are complete. Published commit: `ba29c68916562f922b3bae085c72b51f345030c0` (tree `30d87eaf4c82778cfac5d4b9b87112129b600b76`). M1 human/provider acceptance and production-memory-store inspection remain open; no human-player acceptance is claimed by the synthetic proxy used in the stale-task control.

## Bounded player/session memory — 2026-10-18

- M1 source was published on `main` as `74f61d580e74d1c942b3f2badd7f498324212339`; GitHub GET confirmed the published tree matched local source and the local ref update was clean. The Node brain now has bounded UUID-isolated profiles and deterministic session context, with schema v1→v2 migration, safe actor-key maps, 30-minute idle rollover, the current and up to five prior summaries capped at 1,000 characters each, and separate allowlisted server events. Familiarity labels are count-only; summaries are snippets, not semantic personal-fact extraction. Model requests reuse the existing call and validators.
- Local Node suite: **28/28** (`local scratch evidence`). Independent isolated mock HTTP run: **14/14** (`local scratch evidence`). The earlier round-1 mock HTTP run reported old chat audit retention false (`local scratch evidence`); round 3 corrected the migration and passed. Local mock smoke passed (`local scratch evidence`). These are local/mock results, not live provider calls.
- Independent M1 specification and quality review passed without findings after targeted memory fixes. No real provider call or human gameplay acceptance of the new memory was performed. Java source was unchanged for M1; its latest separate verification remains **155/155**. The Paper JAR remains `4041f4bb70a67d0abc2ae2cd9db226da98916d758d5605f8c65863ef6aebbda1`, and the rebuilt four-entry Paper/Citizens ZIP is `cb339161ed171957ad7f208895adade6c193ae8a2ffb24aedc1856dea4ab55d8`. Its archive and embedded hashes are verified and recorded in `dist/SHA256SUMS.txt`; the ZIP still does not bundle Node. Paper/Citizens package preparation is complete; the updated Node process was started from published source and read back successfully. No production-store migration was inspected. A healthy port-3197 probe alone does not establish which source version is running.


## M1 published Node deployment read-back — 2026-10-18

- Published the sanitized M1 source to `main` by a non-force update. Public revision: `74f61d580e74d1c942b3f2badd7f498324212339`. GitHub GET confirmed the published tree matched local source; local references were reconciled cleanly.
- Gracefully stopped the prior service after a full save. The stop completed with exit code 0 after all three dimensions were saved. A mode-0600 stopped-state backup was created with credential files excluded. No user-world files were manually changed.
- Started the Node brain from the published source at **17:35:30 Istanbul**; startup completed in **8.004 seconds**. The health endpoint returned healthy with JEV/LLM modes configured. No provider invocation was made.
- At **17:36:34 Istanbul**, status read-back showed IDLE, zero cargo, chest bound, bed unbound, and NPC unspawned; zero players were connected. All three dimensions completed the subsequent flush/save.
- Node suite remains **28/28**, isolated mock HTTP **14/14**, and mock smoke passes. These validate source and mock paths only. Production memory data was not inspected, so schema migration of existing live data is unknown. Human memory gameplay and live provider behavior remain unverified.
- The Paper JAR remains unchanged at SHA-256 `4041f4bb70a67d0abc2ae2cd9db226da98916d758d5605f8c65863ef6aebbda1`. The four-entry Paper/Citizens ZIP remains `cb339161ed171957ad7f208895adade6c193ae8a2ffb24aedc1856dea4ab55d8` and does not bundle Node.

## Final schedule recovery fix — 2026-10-18

- Added explicit WANDER recovery state after final review found that an outside-radius return route was cancelled and reissued every tick. The route now survives repeated ticks and uses the watchdog retry delay (2 seconds) and three-failure cooldown (15 seconds). Re-entering the six-block radius does not finish recovery; Ahmet continues toward home until within 2.5 blocks, then ordinary WANDER resumes and resolved WANDER failure status clears.
- Red proof against the pre-fix `db183a0614d0c10ac017ea0ed473aa808ea6f35b` adapter failed as expected: the recovery regression observed 20 route target issues across 20 ticks instead of one. The fixed focused suite passed **17/17**; the full Java 21 Maven package passed **155/155** (`local scratch evidence`). At that checkpoint Node was **16/16**, and mock HTTP smoke passed; these are mock/fallback checks, not live provider calls.
- Current plugin JAR SHA-256 remains `4041f4bb70a67d0abc2ae2cd9db226da98916d758d5605f8c65863ef6aebbda1`. After M1, the rebuilt four-entry distribution ZIP passed archive and embedded-hash verification; ZIP SHA-256 `cb339161ed171957ad7f208895adade6c193ae8a2ffb24aedc1856dea4ab55d8`, embedded plugin JAR hash matches, and embedded Citizens JAR SHA-256 is `dd1bff69eb2339a459e56b21aed0810919b40a14a9561577264e3ff94cef9158`. `dist/SHA256SUMS.txt` is updated. This Paper/Citizens package excludes the Node brain.
- The current JAR passed the complete fresh disposable schedule probe at `local scratch evidence`, marker `AHMET_SCHEDULE_PROBE_FRESH_PASSED` at 11:25 Istanbul. The new outside-radius return stage passed in **48 ticks**, with **8.855 blocks** walked, maximum sampled step **0.198 blocks/tick**, ending at `(test location)` within 2.5 horizontal blocks of home. WANDER recovery status cleared and ordinary wandering resumed. The complete fresh run covered WAKE/WORK home returns, bounded WANDER, safe bed walk/native sleep/wake, task/cargo/recovery preemption, invalid bed cases, two DIRT deposited, **0 production teleports**, and **4 setup teleports**. The final `maxRouteStep=0.000` in the marker belongs to the last WAKE measurement window, not the whole-run movement bound.
- The same JAR passed a same-world second-process restart at `local scratch evidence`, marker `AHMET_SCHEDULE_PROBE_RESTART_PASSED` at 11:25:56 Istanbul. Without rebuilding the arena, it restored Citizens VILLAGER identity, home and bed matched the known test arena, with the same task identity restored; the probe checked idle task, zero cargo, clear recovery/journal, safe route walking before sleep, wake at WAKE, and zero teleport/setup-teleport events.
- The same JAR passed common-resource acceptance at `local scratch evidence`, marker `AHMET_RUNTIME_PROBE_ALL_PASSED` at 11:25:58 Istanbul: all **7 groups / 11 source variants** collected and deposited **11 items**. Movement checks reported no teleports and a maximum sampled step of **0.198 blocks/tick**; raw gold completed in **641 ticks**. Scanning made 74 invocations plus next-tick validation (average **1,185 µs**, maximum **6,771 µs**, invalidation **347 µs**, candidate count 1→0). The brain used sanitized mock/heuristic behavior, not live JEV/OpenAI.
- At the time disposable acceptance was recorded, live player acceptance and deployment read-back were still pending; the following section records the later startup and save verification.

## Private server deployment read-back — 2026-10-18

- Code is published on `origin/main`: schedule fix commit `6acb5db47533c9869ef31c714647a67b301c6a51`; documentation/release evidence is on HEAD `60aaaceda8d84e4412ff9bd8fb8a0ce21e909f30`. The published tree matched the local tree before deployment.
- The private server was already stopped and its ports were absent at the pre-deployment check, so no graceful stop from a running process is claimed. A pre-install backup was created at `local scratch evidence`, mode `0600`, size **22,976,028 bytes**; it excludes `brain/.env` and model credentials. No manual world edits were made.
- Installed the exact tested JAR SHA-256 `4041f4bb70a67d0abc2ae2cd9db226da98916d758d5605f8c65863ef6aebbda1` at `private server plugin directory`. `./scripts/run.sh` started the server; startup evidence is preserved privately. Paper 1.21.6 build 48, Citizens 2.0.41 build 4138, and Java 21 completed startup at **11:32:58 Istanbul** (`Done (7.807s)`). Citizens loaded two NPC records and the plugin restored saved NPC id `1` after registry load.
- At **11:33:07 Istanbul**, `/ahmet status` reported task IDLE, cargo 0, chest bound, bed not bound, protected home 10, and 15 placed-wood markers. It also reported `Program: work (walking)` while `NPC: yok`; because the NPC was unspawned at read-back, this does not establish actual live movement. No operator `/ahmet summon` or `/ahmet bed` action was performed. `save-all` completed with all three dimensions saved at 11:33:07.
- `server.properties` remains `online-mode=false`, whitelist enabled, port `25565`. The local brain health endpoint returned healthy and is configured for JEV/LLM modes, but no model call was made. Live player schedule behavior and live provider acceptance remain unverified.

## Previous deterministic Citizens schedule checkpoint — 2026-10-18 (superseded JAR)

- Source now includes a deterministic Citizens schedule, enabled by default. World-time phases are WAKE `[0,1000)`, WORK `[1000,9000)`, WANDER `[9000,12000)`, and SLEEP `[12000,24000)` modulo 24,000. WORK is idle availability at home, not automatic harvesting. `/ahmet bed` binds an existing bed within six blocks; only its location is saved. Schedule status and the current bed fallback reason appear in `/ahmet status`.
- The final local plugin JAR at this checkpoint has SHA-256 `d91ee295b59a46353e69154ac6a3969bc710f5b01adacea0f37d0c22ed0cd95e`; Java package verification passed **153/153** (`local scratch evidence`). Node passed **16/16** and mock HTTP smoke passed in mock/fallback mode; no live provider call is implied.
- Final-binary fresh disposable runtime passed at 2026-10-18 11:14 Istanbul using `-Dahmet.scheduleProbe=true`: `local scratch evidence`, marker `AHMET_SCHEDULE_PROBE_FRESH_PASSED`. The fixture used a fresh isolated flat arena, sanitized mock brain, and the exact JAR hash above. It observed two distinct WANDER targets and actual Citizens movement within six blocks of home; WORK and WAKE returned home by walking; Ahmet walked to a safe adjacent cell before native sleep and woke at WAKE. Task admission synchronously took over schedule sleep/navigation; one DIRT was collected, cancellation retained that cargo without resuming schedule, delivery deposited it, and schedule resumed. Recovery preempted both owned sleep and owned walking. Removing the bed, occupying it, blocking all safe approaches, binding an unloaded bed, and providing a wrong-world bed each produced the expected safe fallback/rejection and current status reason.
- Across measured schedule walking samples, maximum sampled movement was **0.198 blocks/tick**. The fresh marker's final `maxRouteStep=0.000` belongs only to the last WAKE measurement window and is not a whole-run movement bound. Cumulative production teleport events were **0**; the fixture separately recorded **3 setup teleports** used to position the test NPC. No world blocks were changed by schedule behavior; fixture arena edits are isolated test setup.
- The same-world, same-JAR second-process restart probe then passed at 2026-10-18 11:15 Istanbul: `local scratch evidence`, marker `AHMET_SCHEDULE_PROBE_RESTART_PASSED`. It did not rebuild the arena; it restored the same Citizens VILLAGER identity and task identity in the known test arena, and asserted inactive task, zero cargo, unblocked recovery, and empty journal. It observed more than one block of walking to a strict safe standing point before sleep at `(test location)`, wake at WAKE, and zero teleport events or setup teleports.
- Exact-JAR common-resource regression also passed at 2026-10-18 11:15 Istanbul: `local scratch evidence`. The disposable Paper run covered all seven common-resource groups, all 11 source variants and outputs, and deposited all 11 items; `maxNormalStep=0.198`, all Citizens movement checks reported `teleportDetected=false`, and raw gold completed in 642 ticks. The once-per-real-tick scan made 74 scan invocations plus one next-tick validation (`avg=1,256 µs`, `max=8,055 µs`, invalidation `178 µs`, candidate count 1→0). All dimensions saved cleanly at 11:15:33. Brain was isolated mock/heuristic; this is not live JEV/OpenAI evidence.
- The final transfer package was rebuilt and verified after the disposable runs. `local scratch evidence` confirmed four archive entries, ZIP integrity, and the embedded JAR hash matches the tested JAR. `dist/villager-ahmet-plugins-paper-1.21.6.zip` SHA-256 is `8c23ec6bb0a7e94786854f05ab605f3f9d6557b54aaa66b065e5e2222db727c8`; embedded `VillagerAhmet.jar` is `d91ee295b59a46353e69154ac6a3969bc710f5b01adacea0f37d0c22ed0cd95e`; embedded Citizens JAR is `dd1bff69eb2339a459e56b21aed0810919b40a14a9561577264e3ff94cef9158`. `dist/SHA256SUMS.txt` contains these checksums.
- **Still pending at this historical checkpoint:** push, live deployment/startup read-back, and live player schedule acceptance had not yet been reported. The later deployment read-back appears above; earlier common-resource probe failures remain in their dated entries and are not erased by passing runs.
- Failed runtime attempts remain evidence, not passes: R1 failed the WORK return threshold (fixture expected 2.0 blocks while production home arrival allowed 2.5); R2 failed proxy command dispatch and included setup-teleport travel in measurements; R3 failed because the fixture looked for a nonexistent `ownsSleep` field; R4 exposed Citizens' integer target flooring and stopped before fixture acceptance; R5 exposed a false pre-sleep pose oracle; R6 exposed stale bed fallback status; R7 validated status refresh before the final cooldown correction. The production causes were addressed in subsequent reviewed changes. See dated logs under `local scratch evidence`, `local scratch evidence`, and `local scratch evidence`; the failed logs were preserved.

## Follow-up command implementation — 2026-10-12 19:28 EDT

- Added `/ahmet help`, `/ahmet cargo`, direct bounded `/ahmet collect <item|odun> <1-64>`, and context-aware tab completion. Direct collection uses the same allowlisted resource preferences, chest/world checks, task state, persistence, event history, and JEV target selection as chat-created jobs; it cannot pass server commands or coordinates.
- Java 21 `mvn -B -ntp -Dmaven.repo.local="<scratch Maven repository>" package` passed: **40 tests, 0 failures**. Added focused parser coverage for generic wood, species-specific logs/stems, supported common items, unsupported items, and quantity bounds.
- `npm test` passed: **14 tests, 0 failures**. `./scripts/smoke.sh` passed all mock HTTP checks (chat, common-resource intent, offered JEV action validation, memory event, and bearer-token rejection).
- A separate temporary Paper 1.21.6 + Citizens 2.0.41 build 4138 world booted on loopback port 25566 with the new plugin. Console dispatch ran `/ahmet help`, `/ahmet status`, `/ahmet cargo`, and `/ahmet items raw_iron`; the catalog had 1,414 entries and marked raw iron gatherable. The temporary world shut down cleanly. Player-only `/ahmet collect`, tab completion, and harvesting were not exercised in-game.
- The imported-world server still had one online player at 19:29 EDT. The updated JAR is staged in the private server plugin directory, but the running JVM has its previous plugin classes loaded; it was not restarted to avoid interrupting that player. Restart only after the server is empty.

## Authorized LAN restart — 2026-10-12 20:11 EDT

- The user confirmed disconnect. Server console showed 0 players before shutdown. Paper saved the imported world and stopped cleanly.
- Restarted the private server copy on Java 21 / Paper 1.21.6 build 48 with Citizens 2.0.41 build 4138 and the updated VillagerAhmet JAR. The brain is listening on `localhost:3197`; Paper is listening on port 25565. Startup completed without plugin exceptions, Citizens loaded two saved NPC records, and VillagerAhmet restored saved NPC id 1.
- Console `/ahmet help` and `/ahmet status` succeeded. Status reports the saved wood task at 0/8, chest bound, and NPC currently unspawned; an operator can use `/ahmet spawn` in-game to resume it at the saved home. This restart does not establish successful movement or harvesting.

## Route-stall recovery — 2026-10-12 20:22 EDT

- Live read-only status showed the player's current request was still gathering: `5/64` collected, `0` deposited, five `BIRCH_LOG` in cargo, chest bound. The server log showed repeated JEV target selections, and the screenshot showed three path-failure messages. This confirms the task had not reached the chest-delivery phase yet.
- Added persisted partial-delivery state. After three distinct failed resource routes, or two consecutive scans with no eligible nearby targets while carrying cargo, Ahmet stops that request and tries to deposit the exact collected cargo; the completion message reports it as partial. Full/unreachable chest behavior still retains cargo.
- Java 21 package build passed: **45 tests, 0 failures**. Tests cover the three-route/two-empty-scan thresholds, reset behavior after targets are found or harvested, partial-delivery state, exact cargo requirement, and restart persistence. A separate disposable Paper 1.21.6 + Citizens 2.0.41 build 4138 server loaded the updated plugin and executed `/ahmet help` and `/ahmet status`, then shut down cleanly.
- Updated JAR is staged in the live plugin folder and transfer ZIP. The live server is still running with the previous plugin classes; do not restart while the player is connected. This patch has not yet been verified in gameplay.

## Route recovery enabled on LAN server — 2026-10-12 20:25 EDT

- Console confirmed 0 players before shutdown. Paper stopped cleanly; the imported world and plugin task state were saved.
- Restarted with the final partial-delivery JAR. Paper 1.21.6 build 48, Citizens 2.0.41 build 4138, and VillagerAhmet all enabled without exceptions. Citizens loaded two records and the plugin restored saved NPC id 1.
- Read-only `/ahmet status` reports the saved request preserved at `5/64`, 0 deposited, chest bound, NPC unspawned; `/list` reports 0 players. The player should join as operator and use `/ahmet spawn` to resume. Gameplay of the new partial-delivery fallback remains unverified.

## Double-chest capacity fix — 2026-10-12 20:32 EDT

- The user's GUI showed a `Large Chest` with most of its lower half empty while Ahmet said the chest was full. Live state at the time had a bound chest at `private world location`, 5 birch logs carried, and a 5-item delivery task with 0 deposited. The deposit code used `Chest.getBlockInventory()`, which Paper 1.21.6 documents as only the bound 27-slot half of a double chest.
- Switched normal deposit and journal recovery to `Chest.getInventory()`. Inspection of the pinned Paper 1.21.6 runtime's `CraftChest` bytecode confirms this returns `CraftInventoryDoubleChest` when its menu provider resolves a `DoubleInventory`. A no-room message now includes the number of slots checked.
- Java 21 Maven package passed: **46 tests, 0 failures**. Added a planner regression for a full 27-slot first half plus empty second half.
- A disposable Paper 1.21.6 + Citizens test world ran a ChestProbe: first-half inventory size 27, combined inventory size 54; after filling the first half with cobblestone, it wrote five birch logs into slot 27 of the empty second half. The probe server shut down cleanly. This integration check validates the API view and slot write; live-world delivery still needs player confirmation.
- The live-world config still has the five birch logs and 5/5 delivery task saved. Do not lose or manually clear that cargo; activate the updated JAR only after confirming the server is empty.
- Final JAR and ZIP are built and staged. At the latest console check (20:34 EDT), one player was online and the live server still had its previous plugin classes loaded; no restart was performed. The 5-item delivery remains active until a safe restart applies the fix.

## Double-chest fix enabled — 2026-10-12 20:36 EDT

- Console confirmed 0 players before the restart. Paper saved and shut down cleanly; the updated VillagerAhmet JAR loaded with Citizens on startup and restored NPC id 1.
- Read-only console checks show the delivery task survived at 5/5, 0 delivered, cargo `BIRCH_LOG: 5`, chest bound, NPC unspawned, and 0 players online. The operator must reconnect and use `/ahmet spawn`; this will resume the saved chest delivery using the corrected 54-slot inventory view.
- The double-chest API/slot integration probe passed in a separate disposable Paper world. Actual insertion into the user's chest still awaits the operator resuming Ahmet in-game.

## Player-placed wood protection — 2026-10-12 20:43 EDT

- Added a persistent registry of player-placed log/stem coordinates from `BlockPlaceEvent`; these blocks are excluded even when adjacent to natural leaves. Added a configurable protected-home sphere (default radius 10) for pre-existing builds and OP command `/ahmet protecthome <0-32>` to set its center/radius.
- Registry and home-radius unit tests cover coordinate persistence, invalid registry data, radius boundaries, and world isolation. Full Java 21 package passed: **50 tests, 0 failures**. The updated plugin booted in a disposable Paper 1.21.6 + Citizens test world without exceptions.
- Updated JAR and transfer ZIP are staged. The running LAN JVM still has previous classes loaded; activate only after confirming no player is online. Old edits outside the protected home area and changes made through commands/other plugins cannot be inferred from vanilla block state.

## Wood protection enabled on LAN server — 2026-10-12 20:55 EDT

- Confirmed zero players connected before restart via both server logs (player `primary test player` disconnected at 20:42:01) and a direct Minecraft Server List Ping handshake on `localhost:25565` (`0 / 6` players online).
- Inspected and preserved live task/cargo state before restart: task idle (cancelled by user after harvesting 3 birch logs), cargo retained at `3` (`BIRCH_LOG: 3`), delivery chest bound at `private world location`, home at `private world location`. Backups saved to `config.yml.bak` and `operations.json.bak`.
- Gracefully stopped the running server via SIGTERM: Paper flushed all chunk I/O, saved chunks across Overworld, Nether, and The End, and shut down cleanly.
- Staged plugin JAR SHA-256 (`9d1b60f48c86693f9b5c727ff7d24a3be0ba637ad17d114382384e5c2c84e5b5`) matches the staged distribution ZIP, containing `PlayerPlacedWoodRegistry` and `ProtectedHomeArea`.
- Restarted the private server copy with `ACCEPT_MINECRAFT_EULA=true` and `AHMET_SERVER_DIR=<private server directory>`.
- Node brain started on `localhost:3197`; `GET /health` probe confirmed `{"ok":true,"service":"villager-ahmet-brain","decisionMode":"jev","chatMode":"llm"}`.
- Paper 1.21.6 build 48 initialized Citizens 2.0.41 build 4138 and VillagerAhmet 0.1.0 without exceptions (`Done (20.405s)`). Citizens loaded two saved NPC records; VillagerAhmet restored saved NPC id 1 after registry load.
- Console `/ahmet status` responded: `Ahmet: boşta (taşınan kaynak: 3) | Sandık: bağlı | NPC: yok | Korunan ev: 10 blok | Yerleştirilmiş odun işareti: 0`.
- Console `/ahmet cargo` responded: `Ahmet'in taşıdığı kaynaklar (3): • 3 × birch_log`.
- Saved configuration verified: `options.protect-home-radius: 10`, `protection.home` defaulted to `private world location`, `protection.playerPlacedWood: []`, bound chest `private world location`.
- Server is active on private LAN `private LAN host` with `online-mode=false` and whitelist enabled.
- Operator actions remaining in-game: operator joins as `primary test player`, runs `/ahmet protecthome 10` from the center of the build (if different from the default home), runs `/ahmet spawn` (or `/ahmet summon`) to bring Ahmet into the world, runs `/ahmet deliver` to deposit the 3 carried birch logs into the 54-slot chest, and issues a collection request to verify that house logs within the 10-block protection radius are skipped while natural logs outside the protected area are collected.

## Live in-game wood protection and player-placed tracking verification — 2026-10-12 21:04 EDT

- The primary test player connected to the private server; the exact in-world position is omitted.
- Player ran `/ahmet deliver`, successfully depositing the 3 carried birch logs into the bound delivery chest (`Chest.getInventory()`) and triggering a clean world save.
- Player placed replacement wood blocks into the house structure at `(1, 70-74, 146)` and `(-3, 70-72, 148)`. The `BlockPlaceEvent` handler dynamically recorded all 8 placements in real time (`Yerleştirilmiş odun işareti: 8`), verified in `config.yml` under `protection.playerPlacedWood`.
- Natural-language Turkish gathering request `ahmet 15 odun topla` was processed via OpenAI GPT-6 Luna (`taskSource: llm`).
- Target selection ran through TypeSafe JEV (`source: jev, model: jev-1.13.0`).
- **Protected-home verification**: The house logs at `(test location)`—which were previously targeted before the fix—were skipped because they fell within the 10-block home protection sphere around `(test location)`. JEV selected natural tree targets outside the house:
  - First target: `test block` (canopy log). When the watchdog determined the path could not reach Y=78, it cleanly timed out and selected `test block` at ground level outside the house.
  - After the operator summoned Ahmet to outside coordinates `(test location)` and re-issued `ahmet 15 odun topla`, JEV selected target `test block` (ground-level birch log, well outside the house).
- Player subsequently ran `/ahmet cancel` and checked `/ahmet cargo` (`Ahmet'in taşıdığı kaynak yok`).
- Console status confirmed: `Ahmet: boşta (taşınan kaynak: 0) | Sandık: bağlı | NPC: aktif | Korunan ev: 10 blok | Yerleştirilmiş odun işareti: 8`.

## Crash protection, disk recovery, and live chest delivery verification — 2026-10-12 22:38 EDT

- **Host disk exhaustion incident**: When harvesting block 4 (`private world location`), the host APFS volume reached 100% capacity (641 MiB free), causing `AhmetPlugin.persist()` to encounter `FileNotFoundException: plugins/VillagerAhmet/config.yml (No space left on device)`.
- **Transaction safety verified under failure**: The write-ahead journal (`operations.json`) successfully retained the `pendingHarvest` entry for `(test location)`, `recoveryBlocked` was automatically engaged, and Ahmet safely halted the task (`Kurtarma: manuel inceleme gerekli`) without item duplication or silent world corruption.
- **Disk cleanup**: Safely removed ~1.6 GB of ephemeral scratch test directories from local scratch storage, restoring host disk free space to **2.3 GiB**.
- **Zero-player clean restart**: Verified zero players online (`/list`), cleanly stopped the running server with full dimension and chunk saves (`flushJournalOnShutdown()` cleanly flushed chunk I/O and preserved journal integrity), and restarted Paper 1.21.6 build 48 and Node brain daemon (`localhost:3197`).
- **Startup restoration**: Citizens restored NPC id 1; `recoveryBlocked` cleared; `operations.json` and `config.yml` cleanly restored Ahmet with **4 carried birch logs** in virtual cargo (`Ahmet'in taşıdığı kaynaklar (4): • 4 × birch_log`) with zero item loss.
- **In-game operator summon and chest delivery**:
  - Operator `primary test player` connected in-game. Because Ahmet's previous outdoor position was 46 blocks away from the chest (exceeding the safety limit `max-distance-from-home: 32`), the operator ran `/ahmet summon` near the house to safely reposition Ahmet and reset home coordinates.
  - Operator ran `/ahmet deliver`. Ahmet navigated autonomously to the bound double chest at `private world location` (entity location `[test location]`), deposited all 4 birch logs using the 54-slot `Chest.getInventory()` view, and triggered a clean world save.
  - Final console inspection confirmed: `Ahmet: boşta (taşınan kaynak: 0) | Sandık: bağlı | NPC: aktif | Korunan ev: 10 blok | Yerleştirilmiş odun işareti: 8` and `Ahmet'in taşıdığı kaynak yok.`
  - Persisted `config.yml` confirmed: `requested: 4, collected: 4, deposited: 4, cargo: 0, cargoItems: {}`.
- **Brain service status**: Node brain verified active on loopback `http://localhost:3197`; `GET /health` returned `{"ok":true,"service":"villager-ahmet-brain","decisionMode":"jev","chatMode":"llm"}`.

## Resource exploration, wandering, and step-by-step home return — 2026-10-12 23:10 EDT

- **Resource Exploration / Wandering**: Added `ExplorationPlanner.java` with `findExplorationWaypoint(...)`. When no eligible resources exist in Ahmet's immediate 24-block search radius (`findTargets().isEmpty()`), Ahmet initiates an exploration routine across up to 4 attempts. He navigates to safe waypoints across 8 rotated directions (radii 8–18 blocks), constrained strictly within `max-distance-from-home` (32 blocks) and outside the operator's protected-home radius.
- **Continuous In-Transit Detection**: While walking towards an exploration waypoint, Ahmet actively scans for targets on every tick. The moment an eligible resource enters range, exploration navigation is cancelled immediately and Ahmet locks onto the newly discovered resource.
- **Step-by-step Home Navigation**: Added `ExplorationPlanner.findStepTowardsHome(...)`. When Ahmet is far from home (or returning home when idle after delivery/cancellation), he calculates 16-block intermediate waypoints along the vector towards home on safe ground with angular obstacle offsets (`attempt` rotation). This prevents Citizens pathfinding follow-range timeouts across long distances and advances Ahmet step-by-step until reaching home (<= 2.5 blocks) without teleportation.
- **Return Home Command & Chat**: Added `/ahmet home` / `/ahmet return` command (and tab completion), plus natural-language chat intent recognition (`isReturnHome`, e.g. "Ahmet, eve dön").
- **Fixed Boundary Enforcement**: In `navigate()`, destinations that move Ahmet closer to home than his current position, or target the chest or home directly, are never blocked by the max-distance check, allowing recovery when summoned or wandering outdoors beyond 32 blocks.
- **Test Verification**:
  - `ExplorationPlannerTest.java` added with 9 tests covering boundary checks, protected area exclusion, direction rotation, arrival conditions, vector advancement, elevation deltas, and angle offsets. Full Java package passed: **59 tests, 0 failures**.
  - Node brain tests in `brain/test/brain.test.mjs` passed: **15 tests, 0 failures**.
- **Live Deployment**:
  - Checked `/list` (0 players online) and safely stopped server with clean chunk saves.
  - Built and staged updated JAR (SHA-256: `400346320a4d55f36e12c608b0e12749db7aeebb80059d22f980f620be6690c7`), updated transfer ZIP `dist/villager-ahmet-plugins-paper-1.21.6.zip` and checksums.
  - Restarted live server with `scripts/run.sh`. Startup completed cleanly, Citizens restored NPC id 1, and console `/ahmet help` verified `/ahmet home` active on live server.

## Verified

- `./scripts/doctor.sh` identifies Java 21.0.12.1, Maven 3.10.0 and Node.js 22.19.0 when Homebrew Java/Maven are on `PATH`. Paper is pinned to 1.21.6 build 48; Citizens is pinned to 2.0.41 build 4138 with the 1.21_R5 adapter.
- `mvn -B -ntp -Dmaven.repo.local="<scratch Maven repository>" clean package` succeeded on Java 21: **16 tests, 0 failures**. This includes bounded task/cancellation, journal recovery, chest planning, navigation retry and safe adjacent summon-target tests.
- `npm test` passed: **8 tests, 0 failures**. Tests cover direct TypeSafe JEV request shape, direct OpenAI endpoint/model contract, bounded offered-action validation, Turkish chat tasks and labelled fallback behavior.
- `./scripts/smoke.sh` passed: addressed Turkish chat, bounded mock decision, memory event persistence and missing-token rejection. It ran in mock mode; it did not call a live model.
- `./scripts/setup.sh` completed and copied the tested plugin JAR to `server/plugins/VillagerAhmet.jar`. The source and installed JAR SHA-256 values matched.
- Created `dist/villager-ahmet-plugins-paper-1.21.6.zip` for transfer to the existing server. It contains the tested Ahmet JAR, pinned Citizens JAR, Turkish installation notes and SHA-256 checksums; archive contents and embedded JAR hashes were verified. The ZIP itself is ignored by Git.
- A fresh project-owned Paper server booted with Java 21, Paper `1.21.6-48-main@4d854e6`, Citizens `2.0.41` build `4138`, and VillagerAhmet `0.1.0`. Startup logged `Villager Ahmet ready` and `Done (39.399s)` with no plugin exception. It created a new world under `server/`; no existing user world was present in that test directory.
- The user accepted the Minecraft EULA. `server/eula.txt` now records `eula=true` for that fresh project server. `online-mode=true` and `white-list=true` remain enabled.
- The local brain health check returned HTTP 200 on `localhost:3197`; it is not bound to a public interface.
- The authorized TypeSafe key was copied to ignored `brain/.env` with mode `0600`; its value was never printed. `GET /v1/models` returned `jev-latest` and `jev-preview`. A live `/v1/systemone` request using `jev-latest` returned actual model `jev-1.13.0` and selected the offered `walk_tree` action. This is a real JEV call, separate from fallback/mock checks.
- `javap` against the pinned Citizens JAR confirmed `NPC.teleport(Location, PlayerTeleportEvent.TeleportCause)` exists. The plugin keeps normal Citizens navigation non-teleporting. The new operator-only `/ahmet summon` teleports an idle NPC to a loaded, passable block beside the operator and is rejected during active work or journal recovery.

## Not yet verified

- Two-client multiplayer permissions, `/ahmet spawn`, chest persistence across restart, Citizens pathing, oak harvesting/deposit, cancellation, restart recovery and chest-full behavior still need in-game checks. One client summon, chest binding and a failed collection attempt are recorded below.
- The user's current server at `secondary LAN host` answered a read-only Minecraft status handshake: version `1.21.6`, 2/8 players. Earlier endpoint attempts did not yield a status response. No login or command was sent. The server's plugin directory/console route is still unavailable, so Ahmet has **not** been installed or teleported there. The local test server at port `25565` was a separate fresh world and has been stopped cleanly.
- The secondary offline TLauncher profile was not used. The separate Microsoft-authenticated `primary test player` account joined the local server and was used for the observed summon/chest session recorded below.
- `OPENAI_API_KEY` is still unset. The chat route is implemented and mocked tests pass for direct `https://api.openai.com/v1/chat/completions` with `gpt-6-luna`, but a real OpenAI conversation was not sent. The secure OpenAI key picker/local destination flow has not completed.
- The hosted GitHub Actions workflow has not run because no Git remote is configured. A local repository is used for this handoff; equivalent Node, local HTTP smoke and Maven checks were run locally.
- The journal's crash-recovery behavior has fixture coverage but has not been crash-tested against a running Minecraft world. Tree identification remains a private-world oak/leaves heuristic, not a region-protection system or native villager mining.

## Imported-save local server — 2026-10-12

- The user's save at `private world save` was copied to a separate private server world directory. A SHA-256 manifest verified all **45 files / 30,188,019 bytes** matched; only `.DS_Store` and the transient `session.lock` were excluded from the copy. The source folder was left untouched.
- The copy's `level.dat` DataVersion is 4435, matching Minecraft 1.21.6. Paper 1.21.6 build 48 booted this copied world with Citizens 2.0.41 build 4138 and VillagerAhmet 0.1.0. Paper's Nether and End folder migrations completed in the private server copy; those changes did not touch the source save.
- At that validation stage, the local server ran on TCP port 25565 with `online-mode=true` and whitelist enabled. Official Java account `primary test player` was added to whitelist and operators (`ops.json`, level 4). This setting was later changed per the user's explicit LAN request, recorded below.
- The brain started on `localhost:3197`. Live OpenAI chat is unavailable because `OPENAI_API_KEY` is not configured; the real JEV result recorded above is separate from this world boot.
- The user then joined the imported world with Minecraft 1.21.6 as `primary test player`. Server logs show successful joins and `/ahmet summon`; the supplied screenshot shows the named villager NPC beside the player and Ahmet's “yanına geldim” response. This is observed in-game summon evidence for the Citizens villager implementation.
- In the same session, `/ahmet chest` saved the delivery chest at `private world location`. Chat started an 8-log task; the persisted task state showed `requested=8`, `collected=0`, `cargo=0`. The screenshot and server state show that tree selection/harvesting did **not** succeed.
- Diagnosis: the current MVP resolves generic “odun” requests to **oak logs only**. `findTree` scans loaded chunks within a 16-block horizontal radius and only ±2 vertical blocks from Ahmet; `isTree` additionally requires nearby oak leaves. “Git bul” does not expand that bounded search or authorize a new movement action. The pale trunks visible through the windows look like birch, but that species identification is an image-based inference, not a block-state inspection. The no-tree warning repeats on bounded retries, so it can appear as chat spam.
- A client from `secondary LAN host` reached this server during connection attempts. The user confirmed the attempted `secondary test player` TLauncher profile is offline; `Invalid session` is consistent with `online-mode=true` rejecting unauthenticated sessions. The correct server address on the LAN is `private LAN host`; the server is already bound to all interfaces. No authentication bypass was attempted.

## General wood implementation — 2026-10-12

- Generic natural-language wood requests now produce a bounded GPT-6 Luna JSON intent (`collect_wood`, `material=any|species`, `amount=1..64`); invalid model values are rejected. If the OpenAI key is missing or the call fails, a deterministic, explicitly labelled heuristic parser remains as fallback. Direct OpenAI execution has not been live-tested because `OPENAI_API_KEY` is absent.
- JEV is offered up to eight server-discovered, loaded-chunk log/stem candidate IDs. Java retains the actual block locations and accepts only an offered target ID; JEV cannot invent coordinates or Minecraft commands.
- Paper uses pinned 1.21.6 Bukkit `Tag.LOGS` materials plus natural bamboo stalks, natural leaf/fungus guards, radius 24 (loaded chunks only), and per-material cargo/deposit operations. Natural-marker checks are capped at the nearest 64 matching log blocks. Journal version 1 remains readable as oak. JEV chooses a safe target; walk/harvest/deposit run deterministically. Gameplay harvesting/deposit with mixed species remains unverified.
- Final automated results: Node **10/10**; Java **24/24**; `scripts/smoke.sh` passed in mock mode; shell syntax and `git diff --check` passed. A separate empty Paper 1.21.6 + Citizens 2.0.41 build 4138 integration server loaded the final JAR, answered `/ahmet status`, and shut down cleanly.
- One live TypeSafe call with offered `target_a`/`target_b` wood candidates returned `target_a`, source `jev`, actual model `jev-1.13.0`. This validates the JEV choice contract only; it did not read or mutate a Minecraft world. The direct OpenAI GPT-6 Luna call was not live-tested because `OPENAI_API_KEY` is absent.
- Updated transfer ZIP: `dist/villager-ahmet-plugins-paper-1.21.6.zip`, SHA-256 `6ca94bb21646e299338f765cec3e54010bb2da9a76b1cba06e9272bfff3fd5e9`. The archive checksum and embedded JAR checksums were verified.
- With the user's permission after they disconnected, Paper was cleanly stopped, final JAR SHA-256 `fb251b71ed3e5888b1a4fedcf1ada83644bd23e37efc086b5fcb00eabb8b8be5` installed in `private server plugin directory`, and the imported world restarted at 17:33. Startup loaded Citizens 2.0.41 build 4138 and VillagerAhmet 0.1.0 with `Done (8.322s)` and no plugin exception. Citizens reports one NPC record loaded, but `/ahmet status` says `NPC: yok` because the saved NPC is currently unspawned; the operator must use `/ahmet summon` after reconnecting. The task remains idle with zero cargo.
- During that earlier test, the LAN server was `private LAN host` with `online-mode=true`. The other computer at `secondary LAN host` reached the server, but its `secondary test player` TLauncher profile was offline and could not authenticate. The user later requested offline-mode for the trusted LAN; see the reconfiguration result below.

## Full item catalog and common-resource path — 2026-10-12

- Paper 1.21.6 `Material` API is the item source. On live startup, the final plugin generated `private server plugin data directory` with **1,414 unique obtainable non-legacy item IDs**, no air entries. Resource item IDs carry a separate capability label; e.g. `raw_iron=collect_resource`, while `raw_iron_block=none`. This is lookup/recognition, not evidence that all 1,414 items can be gathered.
- `/ahmet items oak_log` executed in the live copied-world Paper console and reported 1,414 catalog entries, six matches, and wood capability labels. `/ahmet items raw_iron` ran in a separate fresh Paper 1.21.6 + Citizens 2.0.41 build 4138 test world and listed `minecraft:raw_iron` as `collect_resource` while `minecraft:raw_iron_block` remained unsupported. `/ahmet status` returned idle; that test world had no NPC.
- Chat tasks now include bounded canonical common-resource IDs: dirt, sand/red sand, cobblestone, coal, raw iron, and raw gold, in addition to existing generic/species wood requests. Java offers only server-discovered targets to JEV. Paper maps dirt/grass, sand, stone, coal ore, iron ore and gold ore; ore candidates use wooden/stone/iron pickaxe tiers and must yield exactly one expected Paper-calculated drop. The journal now stores distinct mined-block and credited-output IDs. Tool durability is not consumed.
- Fresh verification: Node **13/13**; Maven clean package **30/30 Java tests**; mock HTTP smoke passed, including raw-iron intent; shell syntax and `git diff --check` passed. Tests cover common ID aliases, supported/unsupported item mapping, bounded intent, mixed cargo, and distinct journal outputs.
- A new empty Paper test world booted the current plugin with Citizens build 4138, generated its catalog, responded to `/ahmet items raw_iron` and `/ahmet status`, and shut down cleanly. This is plugin startup/command integration; no NPC harvest job was executed.
- One separate live TypeSafe `/v1/systemone` decision using the common-resource candidate descriptions chose offered `target_a`, source `jev`, actual model `jev-1.13.0`, confidence `0.98`. This is a real JEV call, not a Paper harvest test. Direct OpenAI GPT-6 Luna remains untested because `OPENAI_API_KEY` is missing.
- Final plugin SHA-256: `4dcc473030832984a49ba0518b9bb6659e25c813afdea2ee35853d5536b56e69`. The transfer ZIP `dist/villager-ahmet-plugins-paper-1.21.6.zip` passed `unzip -t`; ZIP SHA-256: `f2e9b1476eddcfed64658b6c3256e828760862a9c43715682bc22d7b62933374`.
- After confirming zero clients, the private server world copy was cleanly restarted with the final plugin at 18:06. Paper 1.21.6 build 48, Citizens build 4138 and VillagerAhmet loaded without plugin errors; `/ahmet items oak_log` succeeded. Citizens loaded one saved NPC record, but it remains unspawned until operator `/ahmet summon`. The live copied world has not had any resource collection performed in this verification turn.

## OpenAI key connection — 2026-10-12

- Created the OpenAI Platform key named `minecraft-agent` through the secure setup flow and wrote it only as `OPENAI_API_KEY` in ignored `brain/.env`. The file permission is `0600`; the key value was not printed or committed.
- A real direct OpenAI GPT-6 Luna structured chat/task call for `Ahmet, 5 ham demir getir` succeeded. It returned a Turkish reply and validated `{type: collect_item, itemId: minecraft:raw_iron, amount: 5}`. This was a live model/API test, not an in-game task test.
- `./scripts/doctor.sh` now reports `[OK] OpenAI chat key configured`. The local brain and Paper server were restarted with zero players online; both are listening on loopback `localhost:3197` and LAN port `25565`. Citizens loaded the saved NPC record, which still needs operator `/ahmet summon` after reconnecting.

## Navigation retry, NPC restore and private LAN mode — 2026-10-12

- A live task remained at `2/8` collected with cargo retained; server logs showed repeated JEV selections of `target_a`. Code inspection found that the stall watchdog cancelled Citizens navigation, but the following ticks returned while the navigator was stopped instead of reissuing the target.
- The fix schedules a route restart after two seconds while preserving the watchdog attempt count. After three unsuccessful routes, the target block is suppressed for 60 seconds, allowing the next bounded scan/JEV choice to consider other blocks.
- Added unit tests for one-shot delayed route restart, temporary failed-target suppression, and delayed Citizens registry restoration without clobbering the saved id. Full Maven clean package passed **37/37 Java tests**; Node **14/14**, mock HTTP smoke, doctor and shell syntax checks passed. Final plugin JAR SHA-256: `446c77fd45cd779bd62d82102e16f317ba77f8951fc10d7319376c8e81473c89`. Transfer ZIP SHA-256: `70c2448a243167c892a68c0fe507ce14bc710dd680021c130da30b2ec3cd146a`.
- A fresh Paper 1.21.6 + Citizens build 4138 test world loaded the new JAR, returned `/ahmet status` and `/ahmet items oak_log`, then shut down cleanly. This verifies load/commands only, not NPC movement or resource harvesting.
- The final JAR was installed after confirming zero players; the copied world restarted cleanly. Citizens loaded two NPC records, and delayed lookup restored configured id 1 instead of overwriting it or creating a new one. The plugin does not create another NPC during the delayed registry window. A second Citizens `Ahmet` record still appears in the world; no duplicate was deleted.

## Private LAN offline-mode and NPC restore — 2026-10-12

- Per the user's explicit request, `private server properties` has `online-mode=false` and `white-list=true`. A mode-600 backup of `server.properties`, `ops.json`, `whitelist.json`, and `usercache.json` is at `private identity backup directory`.
- Offline UUIDs for `primary test player` were added to whitelist and ops lists; `secondary test player` was added to the whitelist. The local `primary test player` offline profile then joined from `localhost`; the second computer has not yet produced a successful join.
- Startup of the final plugin logged `Restored saved Citizens NPC id 1 after registry load.` The config retains `npc-id: 1`, the task remains idle with 2 birch logs in virtual cargo, and the active console reports the Citizens NPC reference present. The game UI still shows two villager entities labelled Ahmet; the extra Citizens record was left untouched to avoid deleting the wrong entity.
- `online-mode=false` permits same-LAN username impersonation and creates different player UUIDs. Existing online-mode player data remains in its original UUID files and is preserved; the new offline identity may have a separate inventory/position. Keep the whitelist enabled and do not port-forward or expose this server to WAN.
- Current server: Paper 1.21.6 build 48 + Citizens 2.0.41 build 4138 + VillagerAhmet. The brain now receives `taskActive` and cargo count and tells the player to use `/ahmet deliver` when an old task is cancelled but cargo remains. `/ahmet status` currently reports idle with 2 birch logs in virtual cargo; use `/ahmet deliver` to put them in the bound chest before starting another job.

## Local test-server commands

On this Mac, with Homebrew Java 21 and Maven installed:

```bash
cd <repo>
export JAVA_HOME="<Java 21 runtime>"
export PATH="$JAVA_HOME/bin:<Maven runtime>/bin:$PATH"
./scripts/doctor.sh
AHMET_MAVEN_REPO="<scratch Maven repository>" ./scripts/setup.sh
ACCEPT_MINECRAFT_EULA=true ./scripts/run.sh
```

The Maven repository override is useful in this restricted environment; omit it on a normal machine if `~/.m2` is writable. The run command starts both the brain and Paper server. The fresh server has a whitelist enabled; add and op the correct Java username before connecting. As operator, use `/ahmet spawn` for a new villager or `/ahmet summon` for an existing idle Ahmet.

The user's copied save is already prepared and running separately. To launch it after a clean shutdown (or on the same Mac after the current process stops):

```bash
cd <repo>
export JAVA_HOME="<Java 21 runtime>"
export PATH="$JAVA_HOME/bin:<Maven runtime>/bin:$PATH"
AHMET_SERVER_DIR="<private server directory>" ACCEPT_MINECRAFT_EULA=true ./scripts/run.sh
```

The current private LAN server copy has `online-mode=false` by explicit request and `white-list=true`; local username `primary test player` has an offline UUID whitelist/operator entry, and `secondary test player` is whitelisted. Connect using the private server host address on the trusted LAN. After restart, if Ahmet is unspawned run `/ahmet summon` (current private server copy state has `cargo: 0`; if any future cancelled task leaves cargo, run `/ahmet deliver` before requesting a new collection job). The EULA was accepted by the user; the environment flag is only needed because this script also supports fresh server folders.

## Provider references

- TypeSafe API schema: <https://api.typesafe.ai/openapi.json>
- OpenAI GPT-6 Luna model page: <https://developers.openai.com/api/docs/models/gpt-6-luna>
- OpenAI Chat Completions reference: <https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create>

## Disposable Paper item catalog and drop verification — 2026-10-13

- Created `local scratch evidence` with no copied world data. It ran the verified remediated plugin JAR (SHA-256 `9f3593a80f215fb4158cfc05329e97a2fac93c2734e0753f0852f984e2bc0e64`), Paper 1.21.6 build 48, and Citizens 2.0.41 build 4138. Startup completed with `Done (18.853s)`.
- The disposable Paper server bound only to `localhost:58828`, with `online-mode=true` and whitelist enabled. Its separate brain bound to `localhost:3199`, used `mock` chat/decision modes, a fresh test-only bridge token, and a sanitized environment without model API credentials. No live server or user world was accessed. Both processes stopped cleanly.
- Console `/ahmet items raw_iron` showed `minecraft:raw_iron` as `collect_resource`; `/ahmet items dirt`, `cobblestone`, `coal`, and `raw_gold` showed their supported output entries. `/ahmet items diamond` listed diamond as not gatherable. The catalog reported 1,414 entries. `/ahmet items coarse_dirt` listed the item as not gatherable.
- A temporary main-thread probe set each source block in the disposable world, called the pinned Paper runtime `Block.getDrops` with the configured minimum tool, recorded the exact drops, then restored each position to air:

  | Source block | Tool | Paper drop |
  |---|---|---|
  | DIRT | none | DIRT ×1 |
  | GRASS_BLOCK | none | DIRT ×1 |
  | COARSE_DIRT | none | COARSE_DIRT ×1 |
  | SAND | none | SAND ×1 |
  | RED_SAND | none | RED_SAND ×1 |
  | STONE | WOODEN_PICKAXE | COBBLESTONE ×1 |
  | COAL_ORE | WOODEN_PICKAXE | COAL ×1 |
  | DEEPSLATE_COAL_ORE | WOODEN_PICKAXE | COAL ×1 |
  | IRON_ORE | STONE_PICKAXE | RAW_IRON ×1 |
  | DEEPSLATE_IRON_ORE | STONE_PICKAXE | RAW_IRON ×1 |
  | GOLD_ORE | IRON_PICKAXE | RAW_GOLD ×1 |
  | DEEPSLATE_GOLD_ORE | IRON_PICKAXE | RAW_GOLD ×1 |

- **Mapping discrepancy observed at probe time:** `ResourceCatalog` then included `COARSE_DIRT` in the dirt input group, but Paper dropped `COARSE_DIRT`, not the configured `DIRT` output. The plugin’s exact-output check rejected it as a target. The 2026-10-13 follow-up removed `COARSE_DIRT` from the dirt candidate list; other probed mappings produced the configured single-item result.
- This was a console/catalog and direct API drop probe only. No player connected, and `/ahmet collect`, NPC movement, harvesting, and delivery were not tested; their manual acceptance items remain unchecked.
- Separately, the live deployment was verified on 2026-10-13: `private server plugin directory` SHA-256 `9f3593a80f215fb4158cfc05329e97a2fac93c2734e0753f0852f984e2bc0e64`; Paper startup logged `Done (10.676s)`. This deployment evidence does not establish common-resource gameplay.

## Clear-journal admission recovery fix — 2026-10-13

- Sol review evidence identified a live admission lock: `operations.json` had two committed v3 clear records for the inactive task (cargo 0, chest bound). Since `hasOperations()` correctly treats uncheckpointed committed records as blocking, chat task admission rejected the otherwise valid request through its generic unsupported-item reply. Clear execution also previously returned no success result and did not checkpoint the changed world before journal retirement.
- Clear transactions now persist/reserve the task-wide clear budget, write the clear intent, mutate the block, commit the journal, save every journal-referenced world, and only then retire the journal. Failures return false, lock recovery, retain the outstanding journal when one exists, and skip route-budget increments, success logging, and navigation resume.
- Startup recovery validates clear task IDs, world identity and AIR block state. A clear-only committed journal (including multiple v3 clear records) is saved and retired only after validation; mismatches remain fail-closed. No live journal or world was manually edited during this change.
- Task admission now distinguishes unsupported intent, pending recovery, existing task/cargo, and missing chest. `COARSE_DIRT` was removed from the dirt target list because the disposable Paper probe returned `COARSE_DIRT`, not the required `DIRT`; exact-output validation remains in place.
- Verification: isolated Maven package passed **124 Java tests, 0 failures**; `npm test` passed **16/16**. No live server restart or deployment was performed; the live player remains connected. Disposable drop-probe evidence is in the preceding section. Player `/ahmet collect`, movement, harvesting, and delivery remain unchecked.


## Mixed committed clear/harvest recovery checkpoint — 2026-10-13

- Moved journal checkpointing to the end of startup reconciliation. After all pending operations are reconciled and every committed harvest/clear task ID, world, and AIR state validates, the plugin now saves referenced worlds and retires any committed-history-only journal, including mixed harvest+clear histories. Any pending operation or conflict prevents this checkpoint.
- Added a v3 mixed clear/harvest journal regression, plus checkpoint admission and pending-record guards. Focused journal tests and the complete Maven package passed; total Java tests: **124/124**. Node tests passed **16/16**.
- No restart or deployment was performed; the connected live server and its world/journal were not touched.

## Clear-journal fix deployment — 2026-10-13

- Commit `775ca1e` was pushed to `origin/main`. The live `VillagerAhmet.jar` SHA-256 is `57172d57df10eb13821fd9a907025d8422c3b7bb3dd3a5ec90618c7223eca83a`.
- Paper 1.21.6 build 48, Citizens 2.0.41 build 4138, and Java 21 started successfully (`Done (10.035s)`). The brain is listening on loopback `localhost:3197`.
- `/ahmet status` returned IDLE, cargo 0, chest bound, and recovery unblocked. Citizens restored saved NPC id 1, but it is not spawned (`NPC: yok`).
- The previous server shutdown saved the worlds and retired the existing committed clear journal before this startup. Therefore this deployment did **not** exercise startup replay/recovery of those old clear records; that path is covered by source-level tests, not this runtime observation.
- Player chat and gathering gameplay remain unchecked. No further server action was taken during this documentation update.

## Review fixes & disposable Paper P0.5 runtime verification — 2026-10-16

- **Independent code/docs review findings fixed**:
  1. `TaskAdmission.java` (`evaluate` lines 8-15) and `AhmetPlugin.java` (`/ahmet collect` line 212): Because `harvest()` commits harvested blocks to `journal.harvests()` and retains them until `deposit()` checkpoints and retires `operations.json`, `journal.hasOperations()` is `true` whenever `task.cargo > 0`. Evaluating `recoveryBlocked || journalBusy` before `taskActive || cargo > 0` misclassified active gathering (after the 1st harvested block) and cancelled tasks with carried cargo as `RECOVERY_REQUIRED` instead of `BUSY`. Fixed by checking `recoveryBlocked` first, then `taskActive || cargo > 0` (`BUSY`), and only then `journalBusy` (`RECOVERY_REQUIRED`), with regression coverage in `TaskAdmissionTest`.
  2. `AhmetPlugin.java` (`deposit` line 811): `deposit()` plans one material per tick (`task.nextCargoMaterial()`). Checking `if (task.cargo > 0)` after depositing the first material of a multi-material cargo falsely warned `"Sandık dolu"` even when `plan.moved() == amount`. Fixed to warn only when `plan.moved() < amount`.
  3. `HarvestApproachPlanner.java` and `AhmetPlugin.java` (`step` and `issueNavigationTarget`): When approaching a target block across open flat ground, `HarvestApproachPlanner.findApproach` previously sorted candidates solely by distance to the NPC, selecting outer-ring standing cells (`2.0` blocks from target center) ahead of cardinal/inner-ring cells (`1.0`–`1.41` blocks). Combined with Citizens' default `distanceMargin`/`pathDistanceMargin` and `NavigationWatchdog`'s `2.5`-block arrival threshold (`6.25`), the NPC could stop at `~2.85`–`3.42` blocks from the target where `NavigationWatchdog` returned `WAIT` while `!isNavigating()`, returning early from `step()` before `execute("harvest")`. Fixed by preferring cardinal (`<= 1.01`) then inner-ring (`<= 1.5`) standing cells in `HarvestApproachPlanner`, configuring `.distanceMargin(0.75).pathDistanceMargin(0.75)` on Citizens navigator parameters, and treating entry into the `2.9`-block harvest radius or `3.2`-block chest deposit radius as `REACHED` in `step()`.
- **Disposable Paper 1.21.6 runtime verification (`scripts/fixtures/RuntimeCommonResourceProbe.java`)**:
  - The earlier run executed against an isolated disposable Paper 1.21.6 build 48 + Citizens 2.0.41 build 4138 server (`localhost:58829`) and isolated mock brain (`localhost:3199`, `decisionMode=mock`, `chatMode=mock`, no live API keys). The private world copy and other worlds were untouched. It preceded the corrected scan probe; the controller is preparing a fresh disposable run. The deployed private server JAR remains commit `775ca1e`; the 2026-10-16 working-tree fixes have not yet been deployed.
  - `/ahmet items <query>` console dispatch verified all 10 queries (`dirt`, `coarse_dirt`, `sand`, `red_sand`, `cobblestone`, `coal`, `raw_iron`, `raw_gold`, `oak_log`, `diamond`) across the 1,414-item catalog (`coarse_dirt` and `diamond` reported `şimdilik toplanmıyor`; all 7 common resources reported `collect_resource`; `oak_log` reported `collect_wood`).
  - `AHMET_DROP_PROBE` verified all **11 registered source block variants** (`DIRT`, `GRASS_BLOCK`, `SAND`, `RED_SAND`, `STONE`, `COAL_ORE`, `DEEPSLATE_COAL_ORE`, `IRON_ORE`, `DEEPSLATE_IRON_ORE`, `GOLD_ORE`, `DEEPSLATE_GOLD_ORE`) plus `COARSE_DIRT` negative control (`drops=COARSE_DIRTx1`, `pluginExpectedOutput=null`).
  - The earlier `AHMET_SCAN_PROBE` used a synchronous loop, invoking `findTargets` repeatedly in a single server tick. Its `ticks=74` and `pendingNullTicks=73` were loop-call counts, not server ticks; `avgTickUs=1064` and `maxTickUs=5017` were individual invocation timings, not per-tick server responsiveness. Candidate invalidation was observed (`foundBeforeMutation=1`, `foundAfterMutation=0`), but scan budgeting, actual tick deltas, and server responsiveness remain pending the corrected once-per-scheduler-tick disposable run.
  - End-to-end bounded collection, Citizens villager walking (`teleportDetected=false`), virtual cargo tracking, and chest delivery passed for all **7 common-resource groups** (11 total blocks harvested and 11 items deposited into the bound chest):
    - `dirt` (`DIRT` + `GRASS_BLOCK` $\rightarrow$ `DIRT ×2`, `pathWalked=4.74`, `ticks=53`)
    - `sand` (`SAND` $\rightarrow$ `SAND ×1`, `pathWalked=5.59`, `ticks=44`)
    - `red_sand` (`RED_SAND` $\rightarrow$ `RED_SAND ×1`, `pathWalked=5.78`, `ticks=45`)
    - `cobblestone` (`STONE` $\rightarrow$ `COBBLESTONE ×1`, `pathWalked=5.07`, `ticks=42`)
    - `coal` (`COAL_ORE` + `DEEPSLATE_COAL_ORE` $\rightarrow$ `COAL ×2`, `pathWalked=3.65`, `ticks=52`)
    - `raw_iron` (`IRON_ORE` + `DEEPSLATE_IRON_ORE` $\rightarrow$ `RAW_IRON ×2`, `pathWalked=3.46`, `ticks=46`)
    - `raw_gold` (`GOLD_ORE` + `DEEPSLATE_GOLD_ORE` $\rightarrow$ `RAW_GOLD ×2`, `pathWalked=3.64`, `ticks=47`)
    - Final chest summary: `totalItems=11 contents={DIRT=2, SAND=1, RED_SAND=1, COBBLESTONE=1, COAL=2, RAW_IRON=2, RAW_GOLD=2}`; final `/ahmet status` `IDLE`, `cargo: 0`.
  - Automated suites: Java **125/125** tests pass (`mvn test`), Node **16/16** tests pass (`npm test`).

## Independent handoff verification — 2026-10-16

- Java 21 offline Maven package: **125 tests, 0 failures/errors/skips**, BUILD SUCCESS. Node: **16/16**. `scripts/smoke.sh` passed with elevated loopback permission in **mock mode**; no live provider test was performed.
- Independent review found the prior scan fixture called the scanner in a synchronous loop. Those historical 74 calls were not 74 server ticks. The corrected opt-in fixture uses one callback per real tick and performs invalidation on the next tick.
- Fresh run `local scratch evidence`: `scanInvocations=74 validationInvocations=1 firstTick=40 scanLastTick=113 validationTick=114 scanTickSpan=74 validationTickDelta=1 pendingTicks=73 avgInvocationUs=1209 maxInvocationUs=11176 invalidationUs=339 foundBeforeMutation=1 foundAfterMutation=0`.
- All 7 common-resource groups passed, with 11/11 source blocks removed and 11/11 exact items deposited, final cargo 0; sampled maximum movement was 0.198 blocks/tick, `teleportDetected=false`. All 11 drop variants, COARSE_DIRT negative control and 10 catalog queries passed. Brain was isolated **mock/heuristic**, not live JEV/OpenAI. Paper shut down cleanly and saved all dimensions.
- First run `local scratch evidence` passed six resource groups but timed out on the second raw-gold target at 600 job ticks. The fresh rerun reproduced a stationary Citizens navigator, then the existing watchdog retries recovered without a production change: gold completed in **641 ticks** (~32 seconds). The fixture now logs routing diagnostics and allows 1,200 ticks so the approximately 49-second full watchdog retry sequence can be observed. Original failure evidence is retained; collection latency is not claimed uniformly short.
- Stopped live-server world/plugin backup: `local scratch evidence` (mode 0600). No user-world files were manually changed.
- Distribution rebuilt and archive/embedded JAR checksums verified: plugin SHA-256 `a94f12a0d62b91065abcd623e3d17912e6847bfc709daefc0c24f36725b5f626`; ZIP SHA-256 `a4743a1d5dbe053f17385ab7a512013b65b1555e05b02e2d088b99566252970d`.


## M4 presentation runtime and package rebuild gate — 2026-10-21

### Source and unit verification

- Reviewed and published production source commit: `8572c61afb3591130e4499604f26defc10c1db2f`. Exact core JAR SHA-256: `3784fe50fe47dc385b62c34044371dacb33d57185077e749a9598e2314f6aa1c`.
- Java package: **270 tests, 0 failures, 0 errors, 0 skipped**; focused presentation policy/adapter tests: **11/11**. Node remains at the previously recorded **43/43** with mock smoke; Node was not rerun for this Java and runtime increment.
- Source and fixture review covered RuntimeException containment for NPC/entity resolution, policy/source reads and face/swing/sound/message effects; 20-tick idle-look scan reservation; invisible/spectator filtering; actor-only private replies; and the shared public/private UUID speech cooldown.

### Disposable Paper runtime

The calibrated presentation fixture used the exact core above, fixture JAR SHA-256 `9b829ab5d06f6b74102a0e6111007274b51db13e384567357ceb0751de67bc84`, and pinned Citizens SHA-256 `dd1bff69eb2339a459e56b21aed0810919b40a14a9561577264e3ff94cef9158`. The probe created a real Citizens `EntityType.VILLAGER`, then exercised bounded job and presentation behavior in fresh disposable Paper 1.21.6/Citizens 2.0.41 instances.

- **r10 normal:** exit 0, all three dimensions saved, no ERROR lines and one final `AHMET_PRESENTATION_RUNTIME_PROBE_PASS`. Normal harvest/deposit, injected swing failure, injected sound failure and full-chest hold/retry each completed delivery with collected 1, deposited 1 and cargo 0. During the full-chest hold, cargo remained 1 and no deposit cue occurred; after test-chest capacity was restored, the same cargo delivered. Two cues were recorded; journal clear, recovery false, navigation clean and normal teleports zero. Per-job path lengths were **16.07, 17.45, 18.97 and 18.97 blocks**; maximum sampled step was **0.20 blocks/tick rounded**. Native Citizens/Bukkit effect delegation was observed where no fault was injected.
- **r11 face-delegate** and **r12 sound-invariant:** each fresh negative control saved all three dimensions and emitted its expected terminal FAIL, with no final runtime PASS. These controls verify detection of failures inside normally best-effort callbacks; they are not positive acceptance runs.
- The fixture used synthetic Player and LOS proxies for recipient/visibility paths and an embedded mock chat bridge for two HTTP requests; **zero live providers** were called. A shared public/private per-UUID one-second cooldown can suppress a private response after public speech; delivery is best effort. The measured look-specific chunk-preflight count applies only while task/cargo is active; face and LOS remained zero during the job/return window. The probe does not establish all native-navigation look gating or measure runtime 20-tick scan cadence.

### Package and release status

The corrected four-entry M4 ZIP was rebuilt and independently verified, including entry list, CRCs, inner JAR hashes and outer ZIP hash. ZIP SHA-256 **`6846225df2d99b40142f1ca377a014a964d40cf5fe4b3093745f21c5871691a0`**; core SHA-256 `3784fe50fe47dc385b62c34044371dacb33d57185077e749a9598e2314f6aa1c`; pinned Citizens SHA-256 `dd1bff69eb2339a459e56b21aed0810919b40a14a9561577264e3ff94cef9158`; Turkish package README SHA-256 `6aaa7aad02f3b79e8c7f83fb629ca7b4f3360f5fb9e0f09800c2cf32d0b84feb`. The local `dist/` directory is intentionally Git-ignored; no GitHub release asset is claimed.

M4 source, disposable server-side runtime acceptance, corrected local package verification, source/docs publication and live startup/save read-back are complete. Publication commit `620b97efa4448aee5e6682fc3d2adb9f99b7b9e4` was pushed through the normal Git CLI and matched the remote SHA. A post-deployment documentation-only follow-up records the read-back without self-referencing its future commit hash.

Human client acceptance (visible face/arm animation, understandable audio and two-human multiplayer), live TypeSafe/OpenAI behavior through the current path, production-store inspection and forced-crash durability remain unverified. Earlier M3 evidence above is historical evidence for its named artifacts and does not substitute for the M4 runtime or deployment evidence below.


## M4 same-core regression probes and live package read-back — 2026-10-21

### Additional disposable regressions on the exact M4 core

The following fresh probes all used the exact M4 core SHA-256 `3784fe50fe47dc385b62c34044371dacb33d57185077e749a9598e2314f6aa1c`; these results are not inherited from the earlier M3 core. They used mock decision/chat behavior and establish only their stated disposable-server paths.

- **Common-resource r02:** passed all 7 resource groups, 11 source variants and 11 delivered items, with zero normal Citizens teleports.
- **Schedule r03:** fresh-server and same-world second-process probes passed, restored saved identities, and recorded zero normal teleports. Fresh setup had four fixture setup teleports; restart had zero. The saved arena was restored without rebuilding it.
- **Production-goal r04:** passed with 96.028 blocks walked over 507 moving ticks, maximum sampled step 0.198 blocks/tick and zero normal teleports; six mock decisions, one mock chat, zero providers. In the foreign-route scenario, the first help attempt at tick 100 was rejected by the shared cooldown; acknowledgement arrived at tick 267, and after explicit fixture release the same job resumed through harvest and delivery in 65 ticks.

Node remains at the prior October 20 result of 43 tests plus mock smoke; it was not freshly rerun for this M4 increment.

### Source publication, package installation and startup/save read-back

- Corrected four-entry ZIP SHA-256: `6846225df2d99b40142f1ca377a014a964d40cf5fe4b3093745f21c5871691a0`; exact installed core SHA-256: `3784fe50fe47dc385b62c34044371dacb33d57185077e749a9598e2314f6aa1c`. Pinned Citizens and package README hashes are recorded above.
- Source/docs commit `620b97efa4448aee5e6682fc3d2adb9f99b7b9e4` was pushed normally and exact remote SHA was read back. Paper started in **11.243 seconds** with **zero ERROR** lines. The normal Node application dotenv loader was used; credentials were never read, copied or logged by this read-back. Node health returned `ok=true`, configured `jev-preview`, decision mode `jev`, and chat mode `llm`. This reports configured mode only; no provider request or fresh resolved-model metadata was obtained.
- Console showed **0 players**. `/ahmet status` showed IDLE, cargo 0, chest bound, bed unbound, and NPC unspawned. Any status text about prior walking is not movement evidence. `save-all flush` acknowledged `Saved the game`. No collect, summon, home/bed change, model request, or world experiment was issued. The server was left running after read-back. It had already been stopped before deployment; the previous stop cause/time is unknown and no graceful prior stop is claimed.
- Before atomic JAR installation, a stopped-server backup was verified: **145 files / 29,244,412 bytes**, archive SHA-256 `231413f4d9858a7afee875db410778d3a7b398b76b169e21cb779ce41bc827db`. The plugin/world snapshot was unchanged before installation. No manual world edit or key/brain-store access occurred.

Startup, configured health, idle state and save completion do not establish live gameplay, NPC movement, provider operation, human presentation quality, or crash durability. Human/client, provider, production-store and forced-crash acceptance remain open; M5–M10 remain open. No GitHub release asset is claimed.

## M5 allowlisted transactional crafting runtime and package gate — 2026-10-21

### Source and unit verification
- Java offline Maven package: **317 tests, 0 failures, 0 errors, 0 skipped** (`mvn -B -ntp -o test` in `paper-plugin/`).
- Node Brain test suite: **46 tests, 0 failures** (`node --test test/*.test.mjs` in `brain/`).
- Core JAR SHA-256: `68cf01cb17f7f843c4c10b2b980af85c471d0fca6246e1dbb18eb9844a9a1c0f`.

### Disposable Paper 1.21.6 / Citizens 2.0.41 runtime evidence (`r13`)
- Executed in `/private/tmp/ahmet-crafting-runtime-20261021-r13` with Paper 1.21.6 build 48 (`1.21.6-48-4d854e6`) + Citizens `2.0.41-SNAPSHOT (build 4138)` + Java 21. Exit code 0, all 3 dimensions (`world`, `DIM-1`, `DIM1`) saved cleanly, 0 ERROR lines.
- Probe JAR SHA-256: `a1258bbfb357f094aed825fc881f25f078abf0fbd4bb35c62bcdf7697671ec21` (`/private/tmp/ahmet-crafting-probe-r13.jar`).
- Probe outcome: `AHMET_CRAFTING_RUNTIME_PROBE_PASS stages=6 normalTeleports=0`.
- Detailed stages:
  1. **Stage 1 (`oak_planks_walk_craft`)**: `/ahmet craft oak_planks 8` walked 5.01 blocks (`maxStep=0.20`, `teleports=0`) to the 54-slot double chest, consumed `OAK_LOG x1` (slot 0) + `STRIPPED_OAK_LOG x1` (slot 30, leaving `x1`), placed `OAK_PLANKS x8` in slot 0, and preserved `DIAMOND x5` in slot 40.
  2. **Stage 2 (`stick_walk_craft_custom_preserved`)**: `/ahmet craft stick 8` walked 5.01 blocks (`maxStep=0.20`, `teleports=0`), preserved custom `"Heirloom Plank"` (`OAK_PLANKS x1` in slot 2), consumed plain `OAK_PLANKS x2` (slot 5) + `BIRCH_PLANKS x2` (slot 35, leaving `x1`), and placed `STICK x8`.
  3. **Stage 2B (`chat_craft_item_and_events`)**: `/ahmet ask 8 meşe tahtası üret` via loopback mock HTTP bridge admitted `craft_item` (`minecraft:oak_planks`, amount 8), walked 5.01 blocks (`maxStep=0.20`, `teleports=0`), converted `OAK_LOG x2` into `OAK_PLANKS x8`, and emitted `task_started` and `task_delivered`.
  4. **Stage 3 (`refusals_and_arrival_shortage`)**: command refusals for non-batch count (`6`), insufficient inputs (`16` with 1 log), full-chest output capacity, concurrent busy craft command, and non-owner cancel; operator `/ahmet cancel` while walking left chest untouched; arrival-time shortage (chest mutated from 2 logs to 1 while walking) aborted cleanly with `task_cancelled` and `fakeDeliveredEvent=false`.
  5. **Stage 4 (`journal_recovery_and_conflict`)**: verified injected startup reconciliation for `APPLY_REMAINING` (`oak_planks` x8 applied once), `COMPLETE_ALREADY_APPLIED` (`stick` x8 reconciled without double-crediting), and `CONFLICT` (tampered slot locked `recoveryBlocked=true` and retained `pendingCraft`). Note explicitly that Stage 4 uses injected journal/task states in a disposable server and is not a forced-crash (`kill -9`) test.
  6. **Stage 5 (`gather_oak_log_then_craft_chain`)**: end-to-end regression on the exact M5 core: `/ahmet collect oak_log 1` harvested a leaf-marked `OAK_LOG` and deposited `OAK_LOG x1` into the bound double chest, followed by `/ahmet craft oak_planks 4` (`OAK_PLANKS x4`) and `/ahmet craft stick 4` (`OAK_PLANKS x2` + `STICK x4` remaining), with `normalTeleports=0`.

### Package verification
The four-entry M5 ZIP package (`dist/villager-ahmet-plugins-paper-1.21.6.zip`) was built locally and verified:
- **Outer ZIP SHA-256**: `fa656459ec6bb47305dbeecd885141d97a17b998007bb8151b3cade978d25a79`
- **Core JAR SHA-256**: `68cf01cb17f7f843c4c10b2b980af85c471d0fca6246e1dbb18eb9844a9a1c0f` (`villager-ahmet-0.1.0-SNAPSHOT.jar`)
- **Pinned Citizens SHA-256**: `dd1bff69eb2339a459e56b21aed0810919b40a14a9561577264e3ff94cef9158` (`Citizens-2.0.41-b4138.jar`)
- **Turkish Package README SHA-256**: `da5c616dfb18c4f14df3a1868843daeca2eb9f239f3cdf0d089b497fe588aac9` (`README-TR.txt`)

### Source publication, live package installation and startup/save read-back
- Source commits `d187abd29ad593cfdef4b3721f90f6111bb46e2c` and `6bbad145abbce242cc7564cd50904249fce706e3` were pushed through the normal Git CLI and matched `origin/main`.
- Before atomic JAR installation on `server-live`, a stopped-server backup was verified: **165 files / 29,900,655 bytes**, archive SHA-256 `12240fb1a126f0a367bdf8dee8b59556b7b0ba7866832b0067497d8ca58a16bc` (mode `0600`).
- Exact core JAR `68cf01cb17f7f843c4c10b2b980af85c471d0fca6246e1dbb18eb9844a9a1c0f` was extracted from the verified four-entry ZIP and installed atomically into `server-live/plugins/VillagerAhmet.jar`.
- Paper 1.21.6 build 48 started in **9.849 seconds** (`Done (9.849s)!`) with **0 ERROR** lines and restored saved Citizens NPC id 1. Node health at `http://127.0.0.1:3197/health` returned `ok=true`, `decisionMode=jev`, `chatMode=llm`, and `configuredJevModel=jev-preview` (configured mode only; no live provider call was issued).
- Console `list` reported `There are 0 of a max of 6 players online`. `/ahmet status` reported `Ahmet: boşta (taşınan kaynak: 0) | Durum: IDLE | Program: sleep (walking; no bed bound) | Yatak: bağlı değil | Sandık: bağlı | NPC: yok | Korunan ev: 10 blok | Yerleştirilmiş odun işareti: 15` (`recoveryBlocked=false`). `save-all flush` acknowledged `Saved the game` across all three dimensions (`world`, `DIM-1`, `DIM1`). The live server was left running after read-back.

Human in-game crafting gameplay, live provider calls on the M5 path, production-store inspection, and forced-crash durability remain unverified.

## M6 operator-approved farm plots and mature crop harvesting runtime and package gate — 2026-10-21

### Source and unit verification
- Java offline Maven package: **345 tests, 0 failures, 0 errors, 0 skipped** (`mvn -B -ntp -o test` in `paper-plugin/`).
- Node Brain test suite: **49 tests, 0 failures** (`node --test test/*.test.mjs` in `brain/`).
- Core JAR SHA-256: `5107b86190a3b6cd34f178478f022433e11f2abcca9818298e594198753d1921`.

### Disposable Paper 1.21.6 / Citizens 2.0.41 runtime evidence (`r02`)
- Executed in `/private/tmp/ahmet-farming-runtime-20261021-r02` with Paper 1.21.6 build 48 (`1.21.6-48-4d854e6`) + Citizens `2.0.41-SNAPSHOT (build 4138)` + Java 21. Exit code 0, all 3 dimensions (`world`, `DIM-1`, `DIM1`) saved cleanly, 0 ERROR lines.
- Probe JAR SHA-256: `9bddcd0a02bb5f6bc4886743df4d8dc493ee21b2af9c30a3ef982a6e1cdcb50f` (`FarmingProbe.jar`).
- Pinned Citizens JAR SHA-256: `5f98ca5541dc55fc7b55e438a2adbc0bd0117c7a0c154a909ba5a67e46f435a2` (in runtime r02).
- Probe outcome: `AHMET_FARMING_RUNTIME_PROBE_PASS stages=6 normalTeleports=0`.
- Detailed stages:
  1. **Stage 1 (`plot_and_wheat_harvest_replant`)**: `AHMET_FARMING_STAGE_PASS stage=plot_and_wheat_harvest_replant walked=6.28 maxStep=0.20 teleports=0 wheat=2 replanted=2 immaturePreserved=true outOfPlotPreserved=true beetrootSkipped=true`
     - Operator `/ahmet farmplot 3` approved circular plot around farmland patch. `/ahmet farm wheat 2` walked 6.28 blocks (`maxStep=0.20`, `teleports=0`), harvested 2 mature wheat blocks, replanted both in place (`replanted=2`), preserved immature wheat (`immaturePreserved=true`), preserved out-of-plot wheat as protected player-owned crop (`outOfPlotPreserved=true`), and skipped beetroot (`beetrootSkipped=true`).
  2. **Stage 2 (`carrot_and_potato_mixed_harvest`)**: `AHMET_FARMING_STAGE_PASS stage=carrot_and_potato_mixed_harvest teleports=0 carrot=1 potato=1 poisonousPotato=0 replantedCarrot=true replantedPotato=true`
     - Mixed crop patch harvested 1 carrot and 1 potato with zero normal teleports (`teleports=0`). Poisonous potato was strictly excluded (`poisonousPotato=0`), and both crops were replanted at age 0 with farmland underneath intact (`replantedCarrot=true replantedPotato=true`).
  3. **Stage 3 (`chat_and_partial_delivery_quota`)**: `AHMET_FARMING_STAGE_PASS stage=chat_and_partial_delivery_quota teleports=0 requested=3 harvested=1 deposited=1 immaturePreserved=true taskStartedEvent=true taskDeliveredEvent=true`
     - Turkish chat command `/ahmet ask 3 buğday topla` admitted `harvest_crop` (`minecraft:wheat`, amount 3) via loopback mock HTTP bridge. Harvested available 1 mature wheat, preserved immature wheat, and delivered 1 wheat into the bound chest, emitting `task_started` and `task_delivered` events with `teleports=0`.
  4. **Stage 4 (`refusals_cancel_and_full_chest_deliver`)**: `AHMET_FARMING_STAGE_PASS stage=refusals_cancel_and_full_chest_deliver unboundPlotRefused=true unsupportedCropRefused=true outOfRangeRefused=true fullChestHold=true cancelWithCargo=true deliverSuccess=true`
     - Verified refusals when farm plot is unbound (`unboundPlotRefused=true`), when crop is unsupported like beetroot (`unsupportedCropRefused=true`), and when crop is outside configured search radius (`outOfRangeRefused=true`). When bound chest was full, Ahmet held cargo without discarding (`fullChestHold=true`). Operator cancel while carrying cargo preserved items (`cancelWithCargo=true`), and subsequent delivery completed when chest space was cleared (`deliverSuccess=true`).
  5. **Stage 5 (`crop_journal_recovery_and_conflict`)**: `AHMET_FARMING_STAGE_PASS stage=crop_journal_recovery_and_conflict notApplied=true apply=true conflictBlocked=true`
     - Verified crash-recovery reconciliation on restart for `pendingCropHarvest`: `notApplied=true` when block remained unharvested, `apply=true` when harvest had been applied without credit, and `conflictBlocked=true` when crop block had unexpected state.
  6. **Stage 6 (`gather_craft_farm_regression_chain`)**: `AHMET_FARMING_STAGE_PASS stage=gather_craft_farm_regression_chain gatheredOakLog=1 craftedOakPlanks=4 harvestedPotato=1 teleports=0`
     - End-to-end multi-milestone regression chain: gathered 1 oak log (`gatheredOakLog=1`), crafted 4 oak planks (`craftedOakPlanks=4`), and harvested 1 potato (`harvestedPotato=1`) with `normalTeleports=0`.

### Package verification
The four-entry M6 ZIP package (`dist/villager-ahmet-plugins-paper-1.21.6.zip`) was built locally and verified:
- **Outer ZIP SHA-256**: `d519450ca042559f5f8aed972ebdf2639db7ac185ae6f3f285027ec093b0f370`
- **Core JAR SHA-256**: `5107b86190a3b6cd34f178478f022433e11f2abcca9818298e594198753d1921` (`villager-ahmet-0.1.0-SNAPSHOT.jar`)
- **Pinned Citizens SHA-256**: `dd1bff69eb2339a459e56b21aed0810919b40a14a9561577264e3ff94cef9158` (`Citizens-2.0.41-b4138.jar`)
- **Turkish Package README SHA-256**: `7c9912818c7c9b502fd01ce736192865fceeeb21bfd423a7f35c96060237487b` (`README-TR.txt`)

### Source publication, live package installation and startup/save read-back
- Source commit `627fc8312e1f1c3947a42dc3e8e070a4b10f1f7a` was pushed through the normal Git CLI and matched `origin/main`.
- Before atomic JAR installation on `server-live`, a stopped-server backup was verified: `/private/tmp/ahmet-m6-live-backup-20261021/stopped-server-state.tar.gz`, **229 entries / 29,289,937 bytes**, archive SHA-256 `ea5b554fac7e46cf2aa12181f602d38994403ac7ba34d3eef4da015a8955d91c` (mode `0600`).
- Exact core JAR `5107b86190a3b6cd34f178478f022433e11f2abcca9818298e594198753d1921` was extracted from the verified four-entry ZIP `d519450ca042559f5f8aed972ebdf2639db7ac185ae6f3f285027ec093b0f370` and installed atomically into `server-live/plugins/VillagerAhmet.jar`.
- Paper 1.21.6 build 48 started in **7.100 seconds** (`Done (7.100s)! For help, type "help"`) with **0 ERROR** lines. Node health at `http://127.0.0.1:3197/health` returned `ok=true`, `service="villager-ahmet-brain"`, `decisionMode="jev"`, `chatMode="llm"`, and `configuredJevModel="jev-preview"` (configured mode only; no live provider call was issued).
- Console `list` reported `There are 0 of a max of 6 players online: `. `/ahmet status` reported `Ahmet: boşta (taşınan kaynak: 0) | Durum: IDLE | Program: work (walking) | Yatak: bağlı değil | Sandık: bağlı | NPC: yok | Korunan ev: 10 blok | Yerleştirilmiş odun işareti: 15 | Tarla: bağlı değil`. `save-all flush` acknowledged `Saved the game (world, DIM-1, DIM1)` across all three dimensions. The live server was left running after read-back.

Human in-game farming gameplay, live provider calls on the M6 path, production-store inspection, and forced-crash durability remain unverified.

## M7 multi-villager roster, specialist roles, resource locks, task delegation, and local economy ledger — 2026-10-21

### Source and unit verification
- Java offline Maven package: **385 tests, 0 failures, 0 errors, 0 skipped** (`mvn -B -ntp -o test` in `paper-plugin/`).
- Node Brain test suite: **49 tests, 0 failures** (`node --test test/*.test.mjs` in `brain/`).
- Core JAR SHA-256: `25f0fba9ad6ec049243124fafdfa6783ccb4450860372a9893935437d929fbe2` (`villager-ahmet-0.1.0-SNAPSHOT.jar`).

### Disposable Paper 1.21.6 / Citizens 2.0.41 runtime evidence (`r01`)
- Executed in `/private/tmp/ahmet-multivillager-runtime-20261021-r01` with Paper 1.21.6 build 48 (`1.21.6-48-4d854e6`) + Citizens `2.0.41-SNAPSHOT (build 4138)` + Java 21. Exit code 0, all 3 dimensions (`world`, `DIM-1`, `DIM1`) saved cleanly, 0 ERROR lines.
- Probe JAR SHA-256: `d3b4b5dea7662f348217bce5ba034d667afd6d626a079d5631a208c325b76e25` (`MultiVillagerProbe.jar`).
- Pinned Citizens JAR SHA-256: `dd1bff69eb2339a459e56b21aed0810919b40a14a9561577264e3ff94cef9158` (in runtime r01).
- Probe outcome: `[MultiVillagerProbe] AHMET_MULTIVILLAGER_RUNTIME_PROBE_PASS stages=6 normalTeleports=0`.
- Detailed stages:
  1. **Stage 1 (`stage1_role_refusals`)**: `[MultiVillagerProbe] AHMET_MULTIVILLAGER_STAGE_PASS stage=stage1_role_refusals teleports=0`
     - Evaluated `/ahmet role worker` on Ahmet and `/ahmet npc mehmet role farmer` on Mehmet. Tested cross-role task requests: `/ahmet farm wheat 1` and `/ahmet craft oak_planks 4` on WORKER Ahmet were deterministically rejected with `ROLE_MISMATCH` refusal messages without activating tasks; `/ahmet npc mehmet collect oak_log 1` and `/ahmet npc mehmet craft oak_planks 4` on FARMER Mehmet were deterministically rejected with `ROLE_MISMATCH` refusal messages without activating tasks. `normalTeleports=0`.
  2. **Stage 2 (`stage2_task_delegation`)**: `[MultiVillagerProbe] AHMET_MULTIVILLAGER_STAGE_PASS stage=stage2_task_delegation teleports=0`
     - Operator `/ahmet delegate collect oak_log 1` evaluated candidates and delegated task to specialist WORKER Ahmet (`Görev Ahmet (işçi) köylüsüne delege edildi.`); Ahmet walked to tree, harvested 1 oak log, walked to double chest, and deposited 1 oak log. Operator `/ahmet delegate farm wheat 1` evaluated candidates and delegated task to specialist FARMER Mehmet (`Görev Mehmet (çiftçi) köylüsüne delege edildi.`); Mehmet walked to farmplot, harvested mature wheat, replanted wheat at age 0, walked to double chest, and deposited wheat into the double chest. Both tasks completed with `normalTeleports=0`.
  3. **Stage 3 (`stage3_concurrent_execution_and_block_locks`)**: `[MultiVillagerProbe] AHMET_MULTIVILLAGER_STAGE_PASS stage=stage3_concurrent_execution_and_block_locks teleports=0`
     - Ahmet executed oak log collection while Mehmet concurrently executed carrot farming (`bool(taskAhmet, "active") && bool(taskMehmet, "active")`). Verified `ResourceLockRegistry` conflict prevention: arbitrary target block lock acquired by Ahmet rejected Mehmet's attempt (`tryAcquireTargetBlock` returned false, `isBlockLockedByOther` returned true); lock released by Ahmet allowed Mehmet acquisition. Both concurrent tasks completed, depositing oak log and carrot into the bound double chest, replanting carrot at age 0, with `normalTeleports=0`.
  4. **Stage 4 (`stage4_chest_craft_lock_double_spend`)**: `[MultiVillagerProbe] AHMET_MULTIVILLAGER_STAGE_PASS stage=stage4_chest_craft_lock_double_spend teleports=0`
     - Both villagers assigned CRAFTER role. Mehmet started `/ahmet npc mehmet craft oak_planks 4` locking the bound double chest. Ahmet simultaneously attempted `/ahmet craft oak_planks 4` and was immediately rejected with `CHEST_LOCKED` (`§cBu sandık şu an başka bir köylünün görevinde kilitli.`), preventing double-spend of inputs. Mehmet finished craft task, placing 4 `OAK_PLANKS` into chest and releasing the chest lock, with `normalTeleports=0`.
  5. **Stage 5 (`stage5_cross_npc_journal_recovery`)**: `[MultiVillagerProbe] AHMET_MULTIVILLAGER_STAGE_PASS stage=stage5_cross_npc_journal_recovery teleports=0`
     - Verified journal isolation: seeded pending crop harvest in `operations-mehmet.json` (crop age 0 -> `APPLY`). Triggered `recoverJournal` on Mehmet's isolated context; Mehmet successfully applied and completed the pending harvest (`collected=1, cargo=1, recoveryBlocked=false`). Ahmet's journal remained completely clean with no pending operations, zero cargo, and `recoveryBlocked=false`, confirming cross-NPC journal isolation with `normalTeleports=0`.
  6. **Stage 6 (`stage6_local_economy_ledger_readback`)**: `[MultiVillagerProbe] AHMET_MULTIVILLAGER_STAGE_PASS stage=stage6_local_economy_ledger_readback teleports=0`
     - Verified `LocalEconomyLedger` tracking: Ahmet recorded gathered items and credits; Mehmet recorded farmed items, crafted items, and credits. Net balances matched produced minus consumed totals across all items. Operator command `/ahmet ledger` output summary verified, concluding with `[MultiVillagerProbe] AHMET_MULTIVILLAGER_RUNTIME_PROBE_PASS stages=6 normalTeleports=0`.

### Package verification
The four-entry M7 ZIP package (`dist/villager-ahmet-plugins-paper-1.21.6.zip`) was built locally and verified:
- **Outer ZIP SHA-256**: `475c80dbd23b435c1976ce7c0bf6835fc1671fea6f09f2aac84425407e3ddaff`
- **Core JAR SHA-256**: `25f0fba9ad6ec049243124fafdfa6783ccb4450860372a9893935437d929fbe2` (`villager-ahmet-0.1.0-SNAPSHOT.jar`)
- **Pinned Citizens SHA-256**: `dd1bff69eb2339a459e56b21aed0810919b40a14a9561577264e3ff94cef9158` (`Citizens-2.0.41-b4138.jar`)
- **Turkish Package README SHA-256**: `a0d261946bb98cf488fbbab0949f88db0f621c42aeda6e669c3133b25f38959a` (`README-TR.txt`)

### Source publication, live package installation and startup/save read-back
- Source commit `240b2ff577c5a70aef9de61e750d59902745b545` was pushed through the normal Git CLI and matched `origin/main`.
- Before atomic JAR installation on `server-live`, a stopped-server backup was verified: `/private/tmp/ahmet-m7-live-backup-20261021/stopped-server-state.tar.gz`, **229 entries / 29,312,779 bytes**, archive SHA-256 `77939b7535ccd6ed6fda9761360943edb361c16279728c1c2096d627fc0445d9` (mode `0600`).
- Exact core JAR `25f0fba9ad6ec049243124fafdfa6783ccb4450860372a9893935437d929fbe2` was extracted from the verified four-entry ZIP `475c80dbd23b435c1976ce7c0bf6835fc1671fea6f09f2aac84425407e3ddaff` and installed atomically into `server-live/plugins/VillagerAhmet.jar`.
- Paper 1.21.6 build 48 started in **9.227 seconds** (`Done (9.227s)! For help, type "help"`) with **0 ERROR** lines. Node health at `http://127.0.0.1:3197/health` returned `ok=true`, `service="villager-ahmet-brain"`, `decisionMode="jev"`, `chatMode="llm"`, and `configuredJevModel="jev-preview"` (configured mode only; no live provider call was issued).
- Console `list` reported `There are 0 of a max of 6 players online: `. `/ahmet status` reported `Ahmet: boşta (taşınan kaynak: 0) | Durum: IDLE | Rol: genel | Program: wander (walking; wander radius exceeded) | Yatak: bağlı değil | Sandık: bağlı | NPC: yok | Korunan ev: 10 blok | Yerleştirilmiş odun işareti: 15 | Tarla: bağlı değil`. `/ahmet roster` reported `Kayıtlı köylüler (1/4): • Ahmet (id: ahmet, rol: genel, NPC: yok, görev: boşta)`. `save-all flush` acknowledged `Saved the game (world, DIM-1, DIM1)` across all three dimensions. The live server was left running after read-back.

Human in-game multi-villager gameplay, live provider calls on the M7 path, production-store inspection, and forced-crash durability across concurrent journals remain unverified.

## M8 fine-grained claims, permissions, protection compatibility, and bounded chunk tickets — 2026-10-22

### Source and unit verification
- Java offline Maven package: **416 tests, 0 failures, 0 errors, 0 skipped** (`mvn -B -ntp -o test` in `paper-plugin/`).
- Core JAR SHA-256: `45920ecab875dbc81a672b9f05082c1d1c4461c5ca2b69085661a7806ccdfc3e` (`villager-ahmet-0.1.0-SNAPSHOT.jar`).

### Disposable Paper 1.21.6 / Citizens 2.0.41 runtime evidence (`r01`)
- Executed in `/private/tmp/ahmet-claims-runtime-20261022-r01` with Paper 1.21.6 build 48 (`1.21.6-48-4d854e6`) + Citizens `2.0.41-SNAPSHOT (build 4138)` + Java 21. Exit code 0, all 3 dimensions (`world`, `DIM-1`, `DIM1`) saved cleanly, 0 ERROR lines.
- Probe JAR SHA-256: `81bc96441419033925769520293b43cbc4fd266c150bd8b801bccdcc1beb1a99` (`ProtectionClaimsProbe.jar`).
- Pinned Citizens JAR SHA-256: `dd1bff69eb2339a459e56b21aed0810919b40a14a9561577264e3ff94cef9158`.
- Probe outcome: `[ProtectionClaimsProbe] PROBE COMPLETED SUCCESSFULLY WITH 0 TELEPORTS.` (Report: `/private/tmp/ahmet-claims-verification.json`: status `PASS`, normalTeleports `0`, claimsValid `true`, chunkTicketsClean `true`).
- Detailed stages:
  1. **Stage 1 (`stage1_claim_registration_and_persistence`)**:
     - Operator executed `/ahmet claim add village_farm -10 60 -10 10 70 10 ahmet HARVEST_CROPS world`. Verified claim registration in `LandClaimRegistry`, persistent write-ahead storage in `claims.json`, and read-back via `/ahmet claim list` and `/ahmet claim info village_farm`.
  2. **Stage 2 (`stage2_foreign_claim_refusal`)**:
     - Evaluated boundary enforcement inside `village_farm`: primary villager Ahmet permitted for `HARVEST_CROPS`; foreign secondary villager Mehmet rejected (`isProtected = true`); wood gathering rejected even for Ahmet because the claim strictly limits permitted actions to `HARVEST_CROPS`. Unclaimed wilderness blocks permitted as usual.
  3. **Stage 3 (`stage3_claim_action_granularity`)**:
     - Operator added wildcard claim `/ahmet claim add common_forest 20 60 20 30 70 30 * ALL world`. Both Ahmet and Mehmet permitted for wood gathering and leaf clearing under wildcard authorization.
  4. **Stage 4 (`stage4_external_protection_reflection_and_fail_closed`)**:
     - Evaluated `ExternalProtectionAdapter`: absent third-party protection plugins fallback gracefully to wilderness (`false`); foreign-owned claim without build trust rejected (`true`); simulated external provider exception strictly fails closed (`true`, protected).
  5. **Stage 5 (`stage5_chunk_ticket_lifecycle_and_bounded_budget`)**:
     - Evaluated `ChunkTicketManager`: verified strict per-villager chunk ticket budget (`MAX_TICKETS_PER_VILLAGER = 9`). First 9 chunk tickets granted; 10th ticket deterministically refused (`budget exceeded`). Duplicate requests idempotently handled. `releaseTickets` cleanly freed all held tickets, returning active count to 0 without ticket leaks.
  6. **Stage 6 (`stage6_teleport_check_and_shutdown`)**:
     - Operator executed `/ahmet claim remove village_farm`. Verified clean deletion from memory and `claims.json`. Verified `normalTeleports = 0` throughout all operations. Gracefully saved worlds and stopped server.

### Package verification
The four-entry M8 ZIP package (`dist/villager-ahmet-plugins-paper-1.21.6.zip`) was built locally and verified:
- **Outer ZIP SHA-256**: `deaadcf71c1aaa6a769200371d5e0c5d7ea195832ed90968719d66e27b084004`
- **Core JAR SHA-256**: `45920ecab875dbc81a672b9f05082c1d1c4461c5ca2b69085661a7806ccdfc3e` (`villager-ahmet-0.1.0-SNAPSHOT.jar`)
- **Pinned Citizens SHA-256**: `dd1bff69eb2339a459e56b21aed0810919b40a14a9561577264e3ff94cef9158` (`Citizens-2.0.41-b4138.jar`)
- **Turkish Package README SHA-256**: `6db3df6ee6197116fc53f868ef63661beff3bbd16dbbf8fc725459345ff51a5d` (`README-TR.txt`)

### Source publication, live package installation and startup/save read-back
- Source commit `921c11e` was pushed through the normal Git CLI and matched `origin/main`.
- Before atomic JAR installation on `server-live`, a stopped-server backup was verified: `/private/tmp/ahmet-m8-live-backup-20261022/stopped-server-state.tar.gz`, **363 entries / 287,793,979 bytes**, archive SHA-256 `a746301b3306ec6131823427f56486bccd0eb78128330443e1b0bbcc8e7d4b28` (mode `0600`).
- Exact core JAR `45920ecab875dbc81a672b9f05082c1d1c4461c5ca2b69085661a7806ccdfc3e` was extracted from the verified four-entry ZIP `deaadcf71c1aaa6a769200371d5e0c5d7ea195832ed90968719d66e27b084004` and installed atomically into `server-live/plugins/VillagerAhmet.jar`.
- Paper 1.21.6 build 48 started with **0 ERROR** lines. Node health at `http://127.0.0.1:3197/health` returned `ok=true`, `service="villager-ahmet-brain"`, `decisionMode="jev"`, `chatMode="llm"`, and `configuredJevModel="jev-preview"` (configured mode only; no live provider call was issued).
- Console `list` reported `There are 0 of a max of 6 players online: `. `/ahmet status` reported `Ahmet: boşta (taşınan kaynak: 0) | Durum: IDLE | Rol: genel | Program: work (walking) | Yatak: bağlı değil | Sandık: bağlı | NPC: yok | Korunan ev: 10 blok | Yerleştirilmiş odun işareti: 15 | Tarla: bağlı değil`. `/ahmet roster` reported `Kayıtlı köylüler (1/4): • Ahmet (id: ahmet, rol: genel, NPC: yok, görev: boşta)`. `/ahmet claims` reported `Kayıtlı korumalı alan yok.`. `save-all flush` acknowledged `Saved the game (world, DIM-1, DIM1)` across all three dimensions. The live server was left running after read-back.

Human claims gameplay, live provider calls on the M8 path, and forced-crash durability remain unverified.

## M9 private observability dashboard, honest usage/cost audit, and bounded telemetry — 2026-10-22

### Source and unit verification
- Node Brain test suite: **61 tests, 0 failures, 0 errors, 0 skipped** (`cd brain && node --test test/*.test.mjs`), including 12 new tests in `brain/test/dashboard.test.mjs`.
- Local mock HTTP smoke tests passed (`bash scripts/smoke.sh`).
- Implementation components:
  1. `brain/src/metrics.mjs`: `MetricsTracker` class managing circular FIFO buffer bounds capped at 100 entries for `recentCalls` and 100 entries for `eventAuditLog`.
  2. `brain/src/dashboard.mjs`: Offline-safe, dark-theme HTML dashboard with zero external CDN dependencies, strict CSP headers, auto-refresh toggle, and XSS-safe input escaping.
  3. `brain/src/server.mjs`: Authenticated endpoints (`GET /dashboard`, `GET /api/dashboard/summary`), constant-time token comparison (`crypto.timingSafeEqual`), query string token redaction, and bounded telemetry hooks on `/health`, `/api/chat`, `/api/decide`, and `/api/event`.
- Provider accounting integrity:
  - Monetary cost is strictly reported as `"UNKNOWN"` with explicit disclosure: *"Upstream model providers (TypeSafe JEV / OpenAI) do not return billing or monetary cost in responses. Never estimated or fabricated."*
  - Token counts are reported as `"UNKNOWN"` unless provided by upstream responses.
- Independent code review returned **APPROVE** with zero critical and zero important findings; timing-safe token comparison suggestion was adopted.
- Live HTTP verification probe on ephemeral port 3199 verified:
  - 401 Unauthorized for requests without valid token.
  - 200 OK for `/dashboard?token=...` serving self-contained HTML with strict CSP (`default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; connect-src 'self'`).
  - 200 OK for `/api/dashboard/summary` with `Bearer` authentication, returning valid metrics schema and honest accounting without token leakage.

### Source publication, live package installation and startup/save read-back
- Source commits `6814db7` and `3399bde` were published to `origin/main` via standard Git push.
- Before restarting the live service, a verified stopped-server backup was created: `/private/tmp/ahmet-m9-live-backup-20261022/stopped-server-state.tar.gz`, **798 entries / 288,030,155 bytes**, archive SHA-256 `3ce698369c589529c8429d8cd6c475ebbd90f00fac89079570fe616ba4ca2cce` (mode `0600`).
- Paper 1.21.6 build 48 started in **9.397 seconds** (`Done (9.397s)! For help, type "help"`) with **0 ERROR** lines.
- Node brain started on `127.0.0.1:3197`:
  - `GET /health` returned `ok=true`, `service="villager-ahmet-brain"`, `decisionMode="jev"`, `chatMode="llm"`, and `configuredJevModel="jev-preview"`.
  - Unauthenticated `GET /dashboard` and `GET /api/dashboard/summary` returned `401 Unauthorized`.
- Console `list` reported `There are 0 of a max of 6 players online: `. `/ahmet status` reported `Ahmet: boşta (taşınan kaynak: 0) | Durum: IDLE | Rol: genel | Program: sleep (walking; no bed bound) | Yatak: bağlı değil | Sandık: bağlı | NPC: yok | Korunan ev: 10 blok | Yerleştirilmiş odun işareti: 15 | Tarla: bağlı değil`. `/ahmet roster` reported `Kayıtlı köylüler (1/4): • Ahmet (id: ahmet, rol: genel, NPC: yok, görev: boşta)`. `/ahmet claims` reported `Kayıtlı korumalı alan yok.`. `save-all flush` acknowledged `Saved the game (world, DIM-1, DIM1)` across all three dimensions. The live server was left running after read-back.

Human in-browser dashboard inspection and real-world multi-client network validation remain unverified.
