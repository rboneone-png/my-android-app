# 股票記帳 — 架構說明（2.10.0）

## 總覽

單一 Android App（Kotlin、Jetpack Compose、Material 3），資料全部存在手機裡，沒有自建伺服器。
外部服務：
- 即時報價：Google 財經（台股上市、美股、美元匯率、大盤指數）、臺灣證券交易所 MIS（台股上櫃）、期貨交易所 MIS（台指期近月），失敗時改用 Yahoo Finance
- Yahoo Finance：股票名稱、上市／上櫃判斷、歷史日線、2 年最高價
- Google Drive：選用的雲端備份

```
┌──────────────────────── UI（Compose）────────────────────────┐
│ LoginScreen      StockRecordApp（導覽列：持股／觀察／配置／質押／月報酬／帳務／設定；股價通知從設定頁進入）│
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
├── network/               Google 財經與證交所即時報價；Yahoo Finance 名稱、歷史日線
├── alerts/                股價通知與質押維持率通知（檢查、背景工作、系統通知）
├── sync/                  備份格式、Google Drive、備份檔匯入匯出、自動同步
  - 1.13.1：授權失效時自動關閉並提示重新連結、token 過期自動重試（`GoogleDrive.withToken`）
├── export/                Excel（.xlsx）產生與讀取（XlsxWriter／XlsxReader／ExcelImporter）
└── ui/                    ViewModel、導覽、各頁面、格式化、主題色
```

## 資料儲存

每個帳號一個獨立的 SQLite 資料庫 `stock_<帳號ID>.db`（Room，版本 13）與一份設定檔 `settings_<帳號ID>`。

| 資料表 | 內容 |
|---|---|
| `trades` | 買賣紀錄：市場、代號、名稱、買／賣、日期、股數、價格、手續費、交易稅 |
| `dividends` | 股利：每股股利、現金股利（= 每股股利 × 當天持有股數）、扣稅（二代健保／美股預扣）、匯費、配股股數 |
| `stock_category` | 每檔股票的資產分類：原型／正二／類現金／其他 |
| `price_alerts` | 股價通知條件與上次通知的交易日 |
| `pledges` | 質押：股數、金額、利率、日期 |
| `watchlist` | 觀察股票、大盤指數（symbol 在 `MarketIndices` 清單內者顯示在「指數」分頁）與排列順序 |
| `cash_flows` | 帳務管理的現金存入／領出紀錄（單一台幣帳戶；market 欄位保留但不再區分） |
| `high_watch` | 「股票漲跌」清單、排列順序與近 2 年最高價快取 |
| `quotes` | 報價快取（含美元匯率 `TWD=X`），離線時顯示 |
| `stock_info` | 股票名稱與 Yahoo 代號快取 |

設定檔（SharedPreferences）：手續費設定、股價刷新秒數、現金金額、庫存股排序、質押「包含正二」、同步設定、上次質押通知日。
帳號清單另存於 `accounts`（只存加鹽雜湊後的密碼）。

舊版資料庫升級（v1→v13）的 Migration 保留在 `AppDatabase.kt`，讓任何舊版本都能直接覆蓋安裝；
`app/schemas/.../10.json` 是 Room 匯出的最終資料表結構。

## 主要計算（domain/）

| 檔案 | 內容 |
|---|---|
| `FeeCalculator` | 台股手續費（0.1425% × 折扣，最低 20 元）、證交稅、股利扣稅建議值 |
| `DividendMath` | 現金股利總額 = 每股股利 × 發放日持有股數；舊紀錄反推每股股利 |
| `Portfolio` | 平均成本法：持股、成本、已實現損益、股息、每筆賣出的已實現損益；逐日檢查賣出股數是否足夠（`firstShortage`） |
| `Valuation` | 加上報價：市值、未實現損益（預扣賣出費用）、總損益、日損益、市場彙總、自訂排序 |
| `Allocation` | 資產配置：A 原型 + B 正二 + C 類現金 + D 現金（= 帳務管理的當日可用餘額）+ E 其他；曝險 = A + 2B |
| `PledgeCalculator` | 利息支出、個股／月份／整體維持率、三色等級、可質押市值、依月份分組；新增質押的可質押股數（捨去到 1000 股並扣除已質押）、質押股數檢查（千股倍數、不超過可質押股數）、金額單位檢查、質押率（上限 60%）、維持率（下限 166%） |
| `MarketIndex`（data） | 可加入觀察的大盤指數清單：Yahoo 代號（^ 開頭，存入觀察清單）與 Google 財經代號 |
| `SplitMath` | 股票分割：檢查（暫停日 < 恢復日、倍數 ≥ 2）、到期判斷、買賣／股利／質押／通知門檻的股數與價格換算（可還原）；`SplitApplier` 在恢復交易日把這些資料實際改成分割後的數字（交易內再確認狀態，避免調整兩次） |
| `Clec` | CLEC 退休投資策略：配置代碼（三碼＝幾成原型／正二／類現金）、Beta、幾倍年生活費（小數一位四捨五入）、對照表與區間邊界 |
| `LoanMath` | 貸款：本息平均攤還的每月繳款、自動扣款日期（建立日之後的繳款日、月底處理）、已繳期數與累計扣款 |
| `Accounting` | 帳務管理（單一台幣帳戶）：當日賣出、買進（美股以當日匯率換算）、當日帳務（賣出 − 買進）、當日質借金額、可用餘額（存入 − 領出 + 帳務累計 + 質借金額累計 + 股利累計 − 貸款累計，歷來累計、隔天不會消失），金額四捨五入 |
| `HighWatch` | 股票漲跌：顯示用最高價（盤中創新高取現價）、漲跌幅 = 現價 ÷ 最高價 |
| `DailyReturns` | 月報酬月曆的每日損益與報酬率（週／月以連乘）；今天以即時報價計算（`LiveClose`），持股頁的持股日損益用同一個算式（`today`） |
| `AssetTrend` | 資產走勢：每月月底持股市值與平均成本法成本、圖表刻度 |
| `ExcelImporter` | 匯入 Excel：依標題列名稱對應欄位、日期／數字／代號容錯、費用自動計算、錯誤列號；`plan()` 算合併計畫（新增略過重複、取代、匯入後股數不足檢查）。`XlsxReader` 用 DOM 讀 .xlsx（共用字串、內嵌字串、日期序號） |
| `MonthlyIncome` | 每月已實現損益與股利（圖表、Excel） |

## 主要流程

**股價更新**：持股／觀察頁依設定秒數呼叫 `AppViewModel.refreshQuotes()` → `MarketRepository` 先查 `stock_info` 取得 Yahoo 代號（沒有就搜尋）→ 依來源順序抓即時報價（Google 財經／證交所 → Yahoo）寫入 `quotes` → Room Flow 自動更新畫面 → 順便執行 `AlertChecker` 檢查通知。

**通知**：`AlertWorker` 每 15 分鐘（App 關閉時）對所有帳號抓通知與質押股票的報價；條件成立且當天（交易所時區）尚未通知過才發出系統通知。

**備份**：`Backup` 把交易、股利、分類、現金、通知、質押、觀察、排序、手續費序列化成 JSON。
- 手動：存成檔案（系統檔案選擇器，可選 Google 雲端硬碟）或從檔案還原
- 自動：`SyncWorker` 依週期寫入使用者選的備份檔（系統檔案選擇器 + 長期 URI 權限，可放 Google 雲端硬碟）；檔案不見或權限被收回時自動關閉並提示重選。1.15.0 起移除 Google 授權 API（`GoogleDrive`）

## 建置與發布

- `./gradlew testDebugUnitTest`：單元測試（計算、解析、備份格式、Excel）
- `./gradlew assembleRelease`：正式版，R8 縮減與最佳化（規則見 `app/proguard-rules.pro`）
- 測試版與正式版都用 `app/debug.keystore` 簽章，所有版本都能直接覆蓋安裝
- GitHub Actions（`.github/workflows/build.yml`）：每次推送都跑測試並建置兩種 APK；
  推送到 main 時，若 `versionName` 對應的 Release 不存在且有 `docs/release-notes/v<版本>.md`，自動發布 Release 並附上 APK

## 外部相依

AndroidX（Compose、Room、Navigation、Lifecycle、WorkManager、Biometric）、Google Play Services Auth（Drive 授權）、
sh.calvin.reorderable（拖曳排序）。股價資料來自 Google 財經、證交所與 Yahoo Finance 的非官方介面，可能變動；解析分別集中在 `network/GoogleFinance.kt`、`network/TwseMis.kt`、`network/YahooApi.kt`，來源順序在 `MarketRepository.realtimeQuote()`。


## 外觀（2.7.0）
- `ui/theme/Theme.kt`：固定的亮／暗色配色（不用動態色彩）、圓角（Shapes）、字型樣式（標題加粗、數字 tnum 等寬）。
- `AppCard`：全 App 共用的白底圓角輕陰影卡片；各畫面以 `import ...AppCard as Card` 取代 Material 的 `Card`。`HeroCard`：總覽摘要用的淡藍漸層卡片。
- `ScreenTopBar`（`Common.kt`）：淡藍底頂欄；`StockRecordApp` 的狀態列區塊與頂欄同色（Scaffold 只處理左右與下方的系統邊距）。
- 配色模式存在 `StockApp`（SharedPreferences `app_prefs`，整個 App 共用，預設淺色），`MainActivity` 依它套用 `StockRecordTheme(dark)` 並同步狀態列／導覽列圖示顏色。
- 改外觀時請用 Robolectric + Roborazzi 截圖檢查（暫時加進 build.gradle.kts，檢查完移除，不留在儲存庫）。
