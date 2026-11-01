# 股票記帳 — 架構說明（1.0.0）

## 總覽

單一 Android App（Kotlin、Jetpack Compose、Material 3），資料全部存在手機裡，沒有自建伺服器。
外部服務只有兩個：Yahoo Finance（股價、匯率、股票名稱、歷史日線）與 Google Drive（選用的雲端備份）。

```
┌──────────────────────── UI（Compose）────────────────────────┐
│ LoginScreen      StockRecordApp（導覽列：持股／觀察／配置／質押／月報酬／通知／設定）│
│ screens/*Screen.kt                                              │
└───────────────────────────┬────────────────────────────────────┘
                            │ StateFlow
                    ┌───────▼────────┐
                    │  AppViewModel  │  每個登入帳號一個
                    └───┬────────┬───┘
          純計算（可單元測試）│        │ 讀寫資料
        ┌───────────────▼─┐    ┌─▼──────────────────────────┐
        │ domain/          │    │ data/（Room、SharedPreferences）│
        │ 損益、配置、質押、 │    │ MarketRepository ─► network/  │
        │ 報酬、手續費      │    │                    YahooApi   │
        └──────────────────┘    └──────────────────────────────┘
  背景：alerts/AlertWorker（每 15 分鐘）  sync/SyncWorker（自動備份）
```

## 資料夾結構

```
app/src/main/java/com/rboneone/stockrecord/
├── StockApp.kt            Application：帳號 session、每個帳號的資料庫／設定／報價庫
├── MainActivity.kt        未登入顯示登入頁；離開 5 分鐘自動登出
├── auth/                  帳號（PBKDF2 密碼雜湊）、指紋／臉部辨識
├── data/                  Room 資料表與 DAO、設定、報價快取
├── domain/                純計算邏輯（不依賴 Android，全部有單元測試）
├── network/               Yahoo Finance：搜尋名稱、即時報價、歷史日線
├── alerts/                股價通知與質押維持率通知（檢查、背景工作、系統通知）
├── sync/                  備份格式、Google Drive、備份檔匯入匯出、自動同步
├── export/                Excel（.xlsx）產生
└── ui/                    ViewModel、導覽、各頁面、格式化、主題色
```

## 資料儲存

每個帳號一個獨立的 SQLite 資料庫 `stock_<帳號ID>.db`（Room，版本 7）與一份設定檔 `settings_<帳號ID>`。

| 資料表 | 內容 |
|---|---|
| `trades` | 買賣紀錄：市場、代號、名稱、買／賣、日期、股數、價格、手續費、交易稅 |
| `dividends` | 股利：現金股利、扣稅（二代健保／美股預扣）、匯費、配股股數 |
| `stock_category` | 每檔股票的資產分類：原型／正二／類現金／其他 |
| `price_alerts` | 股價通知條件與上次通知的交易日 |
| `pledges` | 質押：股數、金額、利率、日期 |
| `watchlist` | 觀察股票與排列順序 |
| `quotes` | 報價快取（含美元匯率 `TWD=X`），離線時顯示 |
| `stock_info` | 股票名稱與 Yahoo 代號快取 |

設定檔（SharedPreferences）：手續費設定、股價刷新秒數、現金金額、庫存股排序、質押「包含正二」、同步設定、上次質押通知日。
帳號清單另存於 `accounts`（只存加鹽雜湊後的密碼）。

舊版資料庫升級（v1→v7）的 Migration 保留在 `AppDatabase.kt`，讓任何舊版本都能直接覆蓋安裝；
`app/schemas/.../7.json` 是 Room 匯出的最終資料表結構。

## 主要計算（domain/）

| 檔案 | 內容 |
|---|---|
| `FeeCalculator` | 台股手續費（0.1425% × 折扣，最低 20 元）、證交稅、股利扣稅建議值 |
| `Portfolio` | 平均成本法：持股、成本、已實現損益、股息、每筆賣出的已實現損益 |
| `Valuation` | 加上報價：市值、未實現損益（預扣賣出費用）、總損益、日損益、市場彙總、自訂排序 |
| `Allocation` | 資產配置：A 原型 + B 正二 + C 類現金 + D 現金 + E 其他；曝險 = A + 2B |
| `PledgeCalculator` | 利息支出、個股與整體維持率、三色等級、可質押市值 |
| `DailyReturns` | 月報酬月曆的每日損益與報酬率（週／月以連乘） |
| `MonthlyIncome` | 每月已實現損益與股利（圖表、Excel） |

## 主要流程

**股價更新**：持股／觀察頁依設定秒數呼叫 `AppViewModel.refreshQuotes()` → `MarketRepository` 先查 `stock_info` 取得 Yahoo 代號（沒有就搜尋）→ 抓報價寫入 `quotes` → Room Flow 自動更新畫面 → 順便執行 `AlertChecker` 檢查通知。

**通知**：`AlertWorker` 每 15 分鐘（App 關閉時）對所有帳號抓通知與質押股票的報價；條件成立且當天（交易所時區）尚未通知過才發出系統通知。

**備份**：`Backup` 把交易、股利、分類、現金、通知、質押、觀察、排序、手續費序列化成 JSON。
- 手動：存成檔案（系統檔案選擇器，可選 Google 雲端硬碟）或從檔案還原
- 自動：`SyncWorker` 依週期上傳到 Google Drive 的 App 專用資料夾（需 Google Cloud 設定）

## 建置與發布

- `./gradlew testDebugUnitTest`：單元測試（計算、解析、備份格式、Excel）
- `./gradlew assembleRelease`：正式版，R8 縮減與最佳化（規則見 `app/proguard-rules.pro`）
- 測試版與正式版都用 `app/debug.keystore` 簽章，所有版本都能直接覆蓋安裝
- GitHub Actions（`.github/workflows/build.yml`）：每次推送都跑測試並建置兩種 APK；
  推送到 main 時，若 `versionName` 對應的 Release 不存在且有 `docs/release-notes/v<版本>.md`，自動發布 Release 並附上 APK

## 外部相依

AndroidX（Compose、Room、Navigation、Lifecycle、WorkManager、Biometric）、Google Play Services Auth（Drive 授權）、
sh.calvin.reorderable（拖曳排序）。股價資料來自 Yahoo Finance 非官方 API，介面可能變動，解析集中在 `network/YahooApi.kt`。
