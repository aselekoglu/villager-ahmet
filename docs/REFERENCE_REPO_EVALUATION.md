# `minecraft-agent` değerlendirmesi ve Ahmet yol haritası

Değerlendirme 2026-10-12 tarihinde 6.1 Sol ile salt okunur yapıldı. Referans commit: [`78b40ed59514e5e2abde33a05ce398ecb2c39e05`](https://github.com/rmalde/minecraft-agent/tree/78b40ed59514e5e2abde33a05ce398ecb2c39e05). Bu belge kaynak kodda görülen yetenekleri ve yerel Ahmet durumunu ayırır; referans README'sindeki video/run başarı iddiaları burada bağımsız çalıştırılmadı.

## Karar

Villager Ahmet'in Citizens tarafından yönetilen gerçek `EntityType.VILLAGER` NPC ve Paper sunucu-otoriter yapısı korunmalı. Referans repo Minecraft Java 1.16.5 için Mineflayer oyuncu istemcisi çalıştırıyor; normal oyuncu protokolüyle hareket ve blok etkileşimi yapıyor. Bu runtime, 1.21.6 Citizens köylüsüne doğrudan bağlanamaz ve ürünün “oyuncu botu değil, gerçek köylü NPC” gereksinimini karşılamaz. Mimari fikirler yeniden uygulanabilir; Mineflayer bağlantılı kod parçaları taşınmamalı.

Referans GitHub API'si `license: null` döndürüyor ve recursive kaynak ağacında LICENSE, COPYING veya NOTICE dosyası bulunmuyor. Bu nedenle şu an için doğrudan kopyalanabilir kod **yok**. Repo public olsa da lisanslı olduğu anlamına gelmez; ancak yazar lisans/izin sağlarsa bağımsız saf yardımcılar ayrıca değerlendirilebilir. [Repo metadatası](https://api.github.com/repos/rmalde/minecraft-agent), [GitHub lisanslama açıklaması](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/licensing-a-repository).

## Yetenek karşılaştırması

| Alan | Referans repoda kaynakta görülen | Villager Ahmet'te mevcut |
|---|---|---|
| Görev üretimi | Astra planner hedefi speedrun aşamalarından çıkarır; genel oyuncu sohbeti arayüzü tespit edilmedi | Chat listener; GPT-6 Luna JSON görev yorumu tasarlandı; anahtar yoksa etiketli heuristic fallback. Mevcut capability `collect_wood` |
| Planlama | Objective, item hedefi ve waypoint; aşama değişince asenkron yeniden planlama | Tek görev durumu; genel alt-görev planı ve kuyruk yok |
| JEV | Gözlemlere göre sunulan eylemlerden seçim | Java'nın bulduğu kütük hedeflerinden ID seçimi; koordinat Java'da kalır |
| Hareket | Pathfinder ile yürüme, yüzme, tekne, detour ve bazı kaz/yerleştirerek rota açma | Citizens navigator, watchdog; rota açma veya keşif yok |
| Kaynak toplama | Kütük, taş, cevher, yatak ve navigasyon blokları; Mineflayer dig/drop | `Tag.LOGS` tabanlı tür seçimi; Paper blok mutasyonu ve materyal bazlı sanal cargo |
| Yerdeki eşya | Drop gözlemi, yaklaşma ve inventory artışı kontrol yardımcıları | Yok |
| Sandık | Supply chest açma, çekme ve loot | Operatörün bağladığı sandığa exact item deposit |
| Inventory/tool | Oyuncu inventory, equip, tool durability, cursor recovery, alan açma | Yalnızca toplanan kütük/stem cargo; genel envanter ve tool sistemi yok |
| Craft/smelt | Recipe seçimi, crafting table ve furnace akışı | Yok |
| Survival | Yeme, zırh, kalkan, uyuma, potion ve tehdide tepki | Yok; villager'a uygun davranış tasarımı gerekir |
| Combat | Hostile melee, Ender Dragon yatak saldırısı, gözlem/reflex | Yok |
| Nether/End | Portal, kaynak, piglin barter, Eyes of Ender ve portal rotası | Yok |
| Hafıza | Run progress, yakın sonuçlar, loot hedefleri ve route progress | Oyuncu başına chat geçmişi ve sınırlı task event'leri |
| Çoklu agent | Ayrı süreç/port ayarları; genel iş koordinasyonu tespit edilmedi | Tek Ahmet, tek aktif görev ve görev sahibi |
| Görsel kayıt | Native mirror, capture ve run evidence | Oyuncunun gördüğü gerçek NPC; recorder yok |

Kaynak noktaları: [ana ajan](https://github.com/rmalde/minecraft-agent/blob/78b40ed59514e5e2abde33a05ce398ecb2c39e05/nether-agent.mjs), [action seti](https://github.com/rmalde/minecraft-agent/blob/78b40ed59514e5e2abde33a05ce398ecb2c39e05/extra-actions.mjs), [planner](https://github.com/rmalde/minecraft-agent/blob/78b40ed59514e5e2abde33a05ce398ecb2c39e05/async-planner.mjs), [Nether supply](https://github.com/rmalde/minecraft-agent/blob/78b40ed59514e5e2abde33a05ce398ecb2c39e05/nether-supplies.mjs), [Nether workshop](https://github.com/rmalde/minecraft-agent/blob/78b40ed59514e5e2abde33a05ce398ecb2c39e05/nether-workshop.mjs).

Referans README'si 1.16.5'te 8:43'lük bir Ender Dragon run raporluyor; bu repo içindeki run kayıtları yayınlanmadığından iddiayı bağımsız doğrulamadım. Özellikle `eyes-agent` içindeki genel Survival akışının uçtan uca başarısı **UNKNOWN**. Kaynak kodda özellik görünmesi, başarılı gameplay kanıtı değildir.

## En kısa uygulanabilir yol

### P0 — Odun yardımcısını çalışır kabul kriterine ulaştır

1. Yeni mixed-log/stem JAR'ı çalışan Paper 1.21.6 dünyasına kuruldu; yeni build açılışı doğrulandı.
2. Resmî Java 1.21.6 istemcisiyle `/ahmet summon`; sonra chest bağını ve görev durumunu doğrula.
3. Bir huş ve bir meşe log içeren görev çalıştır; her blok değişimini, cargo materyalini, JEV seçim kaynağını ve sandık son durumunu karşılaştır.
4. Full chest, owner cancel, restart, journal recovery ve iki ayrı lisanslı Java hesabı kontrollerini çalıştır.
5. OpenAI anahtarı sağlandığında gerçek GPT-6 Luna chat-task sınıflandırmasını ayrı kanıtla. Anahtar yokken `taskSource=heuristic` olarak raporla; JEV TypeSafe çağrısını ayrı tut.

### P1 — Tek capability registry ve genel Job modeli

`follow_player`, `stay`, `return_home`, `collect_wood`, `pickup_item`, `deliver`, `status` ve `cancel` için ortak bir typed contract kur. Her iş JSON schema, precondition, süre/mesafe/miktar sınırı, Paper yürütücüsü ve sonuç kodu taşısın. LLM yalnızca capability ve bounded argümanları seçsin; Java yetki, mesafe, materyal, kaynak ve görev sahibini tekrar doğrulasın. Bu sayede yeni özellikler yeni bir güven sınırı oluşturmadan eklenir.

### P2 — Algı ve kaynak araması

Chat modeline yalnızca gerekli, sınırlı çevre özeti ver: oyuncuya mesafe, NPC/iş durumu, chest availability, görülen kaynak türleri ve tarama kapsamı. Harita keşfini bütçele; taranmış hücreleri ve başarısız hedef cooldown'larını kaydet. Yüklü chunk dışında arama/otomatik chunk yükleme yapma. Kaynak yoksa sınırlı retry sonrasında `no_resource` sonucu üret ve oyuncudan yön iste; tekrarlı chat spam üretme.

### P3 — Item pickup ve genel inventory

Önce yere düşen item'ları görme, erişilebilir olanı seçme, yürüme ve inventory artışıyla doğrulama. Sonra `gather_item` allowlist'i, item metadata, tool durability ve güvenli chest transferi. Genel item akışını log cargo koduna kopyalama; journal exact material/quantity için testli transaction sağlamalı.

### P4 — Crafting, smelting ve görev zincirleri

Recipe registry → eksik materyal hesabı → gather/withdraw → crafting/furnace → teslimat. “Bir balta yap” ancak her alt adım bounded capability ve recovery ile desteklenince açılmalı. Tarım, ekim/hasat ve benzeri işler aynı job altyapısına eklenmeli.

### P5 — Yüksek riskli/uzun yetenekler

Engel açma ve yapı kurma; sonra combat, Nether seyahati ve en son portal/End akışları. Her biri ayrı izin, bölge koruma, kaynak bütçesi ve fresh-world gameplay kanıtı ister. Referansın geniş yetenek listesini tek seferde taşımak yerine kullanıcı için fayda ve doğrulanabilirlik sırasına göre ekle.

## Verimlilik ve doğrulama kuralları

- JEV yalnızca hedef/alternatif seçiminde çağrılır. Seçilen güvenli hedefe yürüme, bir blok toplama ve chest transaction deterministik Paper kodudur; her 2 saniyede model çağrısı yapılmaz. Alan taraması loaded chunk ve 24 blok yarıçapla sınırlı, natural-marker doğrulaması en yakın 64 log adayıyla bütçelenmiştir.
- Bukkit/Citizens/world/inventory işlemleri server main thread'de kalır; LLM/JEV çağrıları async ve timeout'lu olur.
- Her capability için bounds, allowlist, ownership, cancellation, stale response ve recovery testleri gerekir.
- Paper'da item/block before-after, iki oyunculu sahiplik ve restart transaction doğrulanır. Native Citizens villager şartı korunur; Mineflayer oyuncu botu ikame değildir.
